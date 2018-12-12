package hex.kmeans;

import hex.ClusteringModel;
import hex.ModelMetrics;
import hex.ModelMetricsClustering;
import hex.ToEigenVec;
import hex.genmodel.IClusteringModel;
import hex.util.LinearAlgebraUtils;
import water.DKV;
import water.Job;
import water.Key;
import water.MRTask;
import water.codegen.CodeGenerator;
import water.codegen.CodeGeneratorPipeline;
import water.exceptions.JCodeSB;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.udf.CFuncRef;
import water.util.ArrayUtils;
import water.util.JCodeGen;
import water.util.SBPrintStream;

import java.util.Arrays;

import static hex.genmodel.GenModel.Kmeans_preprocessData;

public class KMeansModel extends ClusteringModel<KMeansModel,KMeansModel.KMeansParameters,KMeansModel.KMeansOutput> {
  @Override public ToEigenVec getToEigenVec() { return LinearAlgebraUtils.toEigen; }

  public static class KMeansParameters extends ClusteringModel.ClusteringParameters {
    public String algoName() { return "KMeans"; }
    public String fullName() { return "K-means"; }
    public String javaName() { return KMeansModel.class.getName(); }
    @Override public long progressUnits() { return _estimate_k ? _k : _max_iterations; }
    public int _max_iterations = 10;     // Max iterations for Lloyds
    public boolean _standardize = true;    // Standardize columns
    public KMeans.Initialization _init = KMeans.Initialization.Furthest;
    public Key<Frame> _user_points;
    public boolean _pred_indicator = false;   // For internal use only: generate indicator cols during prediction
                                              // Ex: k = 4, cluster = 3 -> [0, 0, 1, 0]
    public boolean _estimate_k = false;       // If enabled, iteratively find up to _k clusters
  }

  public static class KMeansOutput extends ClusteringModel.ClusteringOutput {
    // Iterations executed
    public int _iterations;

    // Sum squared distance between each point and its cluster center.
    public double[/*k*/] _withinss;   // Within-cluster sum of square error

    // Sum squared distance between each point and its cluster center.
    public double _tot_withinss;      // Within-cluster sum-of-square error
    public double[/*iterations*/] _history_withinss = new double[]{Double.NaN};

    // Sum squared distance between each point and grand mean.
    public double _totss;            // Total sum-of-square error to grand mean centroid

    // Sum squared distance between each cluster center and grand mean, divided by total number of observations.
    public double _betweenss;    // Total between-cluster sum-of-square error (totss - tot_withinss)

    // Number of categorical columns trained on
    public int _categorical_column_count;

    // Training time
    public long[/*iterations*/] _training_time_ms = new long[]{System.currentTimeMillis()};
    public double[/*iterations*/] _reassigned_count = new double[]{Double.NaN};
    public int[/*iterations*/] _k = new int[]{0};

    public KMeansOutput( KMeans b ) { super(b); }
  }

  public KMeansModel(Key selfKey, KMeansParameters parms, KMeansOutput output) { super(selfKey,parms,output); }

  @Override public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    assert domain == null;
    return new ModelMetricsClustering.MetricBuilderClustering(_output.nfeatures(),_output._k[_output._k.length-1]);
  }

  @Override protected Frame predictScoreImpl(Frame orig, Frame adaptedFr, String destination_key, final Job j, boolean computeMetrics, CFuncRef customMetricFunc) {
    if (!_parms._pred_indicator) {
      return super.predictScoreImpl(orig, adaptedFr, destination_key, j, computeMetrics, customMetricFunc);
    } else {
      final int len = _output._k[_output._k.length-1];
      String prefix = "cluster_";
      Frame adaptFrm = new Frame(adaptedFr);
      for(int c = 0; c < len; c++)
        adaptFrm.add(prefix + Double.toString(c+1), adaptFrm.anyVec().makeZero());
      new MRTask() {
        @Override public void map( Chunk chks[] ) {
          if (isCancelled() || j != null && j.stop_requested()) return;
          double tmp [] = new double[_output._names.length];
          double preds[] = new double[len];
          for(int row = 0; row < chks[0]._len; row++) {
            Arrays.fill(preds,0);
            double p[] = score_indicator(chks, row, tmp, preds);
            for(int c = 0; c < preds.length; c++)
              chks[_output._names.length + c].set(row, p[c]);
          }
          if (j != null) j.update(1);
        }
      }.doAll(adaptFrm);

      // Return the predicted columns
      int x = _output._names.length, y = adaptFrm.numCols();
      Frame f = adaptFrm.extractFrame(x, y); // this will call vec_impl() and we cannot call the delete() below just yet

      f = new Frame(Key.<Frame>make(destination_key), f.names(), f.vecs());
      DKV.put(f);
      makeMetricBuilder(null).makeModelMetrics(this, orig, null, null);
      return f;
    }
  }

  public double[] score_indicator(Chunk[] chks, int row_in_chunk, double[] tmp, double[] preds) {
    assert _parms._pred_indicator;
    assert tmp.length == _output._names.length && preds.length == _output._centers_raw.length;
    for(int i = 0; i < tmp.length; i++)
      tmp[i] = chks[i].atd(row_in_chunk);

    double[] clus = new double[1];
    score0(tmp, clus);   // this saves cluster number into clus[0]

    assert preds != null && ArrayUtils.l2norm2(preds) == 0 : "preds must be a vector of all zeros, got " + Arrays.toString(preds);
    assert clus[0] >= 0 && clus[0] < preds.length : "Cluster number must be an integer in [0," + String.valueOf(preds.length) + ")";
    preds[(int)clus[0]] = 1;
    return preds;
  }

  public double[] score_ratio(Chunk[] chks, int row_in_chunk, double[] tmp) {
    assert _parms._pred_indicator;
    assert tmp.length == _output._names.length;
    for(int i = 0; i < tmp.length; i++)
      tmp[i] = chks[i].atd(row_in_chunk);

    double[][] centers = _parms._standardize ? _output._centers_std_raw : _output._centers_raw;
    double[] preds = hex.genmodel.GenModel.KMeans_simplex(centers,tmp,_output._domains);
    assert preds.length == _output._k[_output._k.length-1];
    assert Math.abs(ArrayUtils.sum(preds) - 1) < 1e-6 : "Sum of k-means distance ratios should equal 1";
    return preds;
  }

  @Override
  protected double[] score0(double[] data, double[] preds, double offset) {
    return score0(data, preds);
  }

  @Override protected double[] score0(double data[/*ncols*/], double preds[/*nclasses+1*/]) {
    double[][] centers = _parms._standardize ? _output._centers_std_raw : _output._centers_raw;
    Kmeans_preprocessData(data, _output._normSub, _output._normMul, _output._mode);
    preds[0] = hex.genmodel.GenModel.KMeans_closest(centers,data,_output._domains);
    return preds;
  }

  @Override protected double data(Chunk[] chks, int row, int col){
    return Kmeans_preprocessData(chks[col].atd(row),col,_output._normSub,_output._normMul,_output._mode);
  }

  @Override
  protected Class<?>[] getPojoInterfaces() {
    return new Class<?>[]{IClusteringModel.class};
  }

  // Override in subclasses to provide some top-level model-specific goodness
  @Override protected void toJavaPredictBody(SBPrintStream body,
                                             CodeGeneratorPipeline classCtx,
                                             CodeGeneratorPipeline fileCtx,
                                             final boolean verboseCode) {
    // This is model name
    final String mname = JCodeGen.toJavaId(_key.toString());

    if(_parms._standardize) {
      fileCtx.add(new CodeGenerator() {
        @Override
        public void generate(JCodeSB out) {
          JCodeGen.toClassWithArray(out, null, mname + "_MEANS", _output._normSub,
                                    "Column means of training data");
          JCodeGen.toClassWithArray(out, null, mname + "_MULTS", _output._normMul,
                                    "Reciprocal of column standard deviations of training data");
          JCodeGen.toClassWithArray(out, null, mname + "_MODES", _output._mode,
                                    "Mode for categorical columns");
          JCodeGen.toClassWithArray(out, null, mname + "_CENTERS", _output._centers_std_raw,
                                    "Normalized cluster centers[K][features]");
        }
      });

      // Predict function body: Standardize data first
      body.ip("Kmeans_preprocessData(data,")
              .pj(mname + "_MEANS", "VALUES,")
              .pj(mname + "_MULTS", "VALUES,")
              .pj(mname + "_MODES", "VALUES")
              .p(");").nl();
      // Predict function body: main work function is a utility in GenModel class.
      body.ip("preds[0] = KMeans_closest(")
          .pj(mname + "_CENTERS", "VALUES")
          .p(", data, DOMAINS); ").nl(); // at function level
    } else {
      fileCtx.add(new CodeGenerator() {
        @Override
        public void generate(JCodeSB out) {
          JCodeGen.toClassWithArray(out, null, mname + "_CENTERS", _output._centers_raw,
                                    "Denormalized cluster centers[K][features]");
        }
      });

      // Predict function body: main work function is a utility in GenModel class.
      body.ip("preds[0] = KMeans_closest(")
          .pj(mname + "_CENTERS", "VALUES")
          .p(",data, DOMAINS);").nl(); // at function level
    }
  }

  @Override
  protected SBPrintStream toJavaTransform(SBPrintStream ccsb,
                                          CodeGeneratorPipeline fileCtx,
                                          boolean verboseCode) { // ccsb = classContext
    ccsb.nl();
    ccsb.ip("// Pass in data in a double[], in a same way as to the score0 function.").nl();
    ccsb.ip("// Cluster distances will be stored into the distances[] array. Function").nl();
    ccsb.ip("// will return the closest cluster. This way the caller can avoid to call").nl();
    ccsb.ip("// score0(..) to retrieve the cluster where the data point belongs.").nl();
    ccsb.ip("public final int distances( double[] data, double[] distances ) {").nl();
    toJavaDistancesBody(ccsb.ii(1));
    ccsb.ip("return cluster;").nl();
    ccsb.di(1).ip("}").nl();

    ccsb.nl();
    ccsb.ip("// Returns number of cluster used by this model.").nl();
    ccsb.ip("public final int getNumClusters() {").nl();
    toJavaGetNumClustersBody(ccsb.ii(1));
    ccsb.ip("return nclusters;").nl();
    ccsb.di(1).ip("}").nl();

    // Output class context
    CodeGeneratorPipeline classCtx = new CodeGeneratorPipeline(); //new SB().ii(1);
    classCtx.generate(ccsb.ii(1));
    ccsb.di(1);
    return ccsb;
  }

  private void toJavaDistancesBody(SBPrintStream body) {

    // This is model name
    final String mname = JCodeGen.toJavaId(_key.toString());

    if(_parms._standardize) {
      // Distances function body: Standardize data first
      body.ip("Kmeans_preprocessData(data,")
              .pj(mname + "_MEANS", "VALUES,")
              .pj(mname + "_MULTS", "VALUES,")
              .pj(mname + "_MODES", "VALUES")
              .p(");").nl();
      // Distances function body: main work function is a utility in GenModel class.
      body.ip("int cluster = KMeans_distances(")
              .pj(mname + "_CENTERS", "VALUES")
              .p(", data, DOMAINS, distances); ").nl(); // at function level
    } else {
      // Distances function body: main work function is a utility in GenModel class.
      body.ip("int cluster = KMeans_distances(")
              .pj(mname + "_CENTERS", "VALUES")
              .p(",data, DOMAINS, distances);").nl(); // at function level
    }
  }

  private void toJavaGetNumClustersBody(SBPrintStream body) {

    // This is model name
    final String mname = JCodeGen.toJavaId(_key.toString());

    body.ip("int nclusters = ").pj(mname + "_CENTERS", "VALUES").p(".length;").nl();
  }

  @Override
  protected boolean toJavaCheckTooBig() {
    return _parms._standardize ?
            _output._centers_std_raw.length * _output._centers_std_raw[0].length > 1e6 :
            _output._centers_raw.length * _output._centers_raw[0].length > 1e6;
  }

  @Override
  public KMeansMojoWriter getMojo() {
    return new KMeansMojoWriter(this);
  }

}
