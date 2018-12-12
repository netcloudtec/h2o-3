package ai.h2o.automl;

import ai.h2o.automl.UserFeedbackEvent.*;
import ai.h2o.automl.collectors.MetaCollector;
import ai.h2o.automl.colmeta.ColMeta;
import ai.h2o.automl.utils.AutoMLUtils;
import hex.tree.DHistogram;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import water.*;
import water.fvec.*;
import water.rapids.Rapids;
import water.rapids.Val;
import water.rapids.ast.prims.advmath.AstKurtosis;
import water.rapids.ast.prims.advmath.AstSkewness;
import water.util.ArrayUtils;
import water.util.Log;

import java.util.*;

import static ai.h2o.automl.utils.AutoMLUtils.intListToA;

/**
 * Cache common questions asked upon the frame.
 */
public class FrameMetadata extends Iced {
  final String _datasetName;
  public final Frame _fr;
  public int[] _catFeats;
  public int[] _numFeats;
  public int[] _intCols;
  public int[] _dblCols;
  public int[] _binaryCols;
  public int[] _intNotBinaryCols;
  public int _response;
  private boolean _isClassification;
  private String[] _ignoredCols;
  private String[] _includeCols;
  private long _naCnt=-1;  // count of nas across whole frame
  private int _numFeat=-1; // count of numerical features
  private int _catFeat=-1; // count of categorical features
  private long _nclass=-1; // number of classes if classification problem
  private double[][] _dummies=null; // dummy predictions
  public ColMeta[] _cols;
  public Vec[]  _trainTestWeight;  // weight vecs for train/test splits
  private long _featsWithNa=-1;    // count of features with nas
  private long _rowsWithNa=-1;     // count of rows with nas
  private SimpleStats _statsSkewness;
  private SimpleStats _statsKurtosis;
  private SimpleStats _statsCardinality;

  private UserFeedback _userFeedback;

  public static final double SQLNAN = -99999;

  public void delete() {
    for(Vec v: _trainTestWeight)
      if( null!=v ) v.remove();
  }

  // TODO: UGH: use reflection!
  public final static String[] METAVALUES = new String[]{
    "DatasetName", "NRow", "NCol", "LogNRow", "LogNCol", "NACount", "NAFraction",
    "NumberNumericFeat", "NumberCatFeat", "RatioNumericToCatFeat", "RatioCatToNumericFeat",
    "DatasetRatio", "LogDatasetRatio", "InverseDatasetRatio", "LogInverseDatasetRatio",
    "Classification", "DummyStratMSE", "DummyStratLogLoss", "DummyMostFreqMSE",
    "DummyMostFreqLogLoss", "DummyRandomMSE", "DummyRandomLogLoss", "DummyMedianMSE",
    "DummyMeanMSE", "NClass", "FeatWithNAs","RowsWithNAs","MinSkewness","MaxSkewness",
    "MeanSkewness","StdSkewness","MedianSkewness","MinKurtosis","MaxKurtosis",
    "MeanKurtosis","StdKurtosis","MedianKurtosis", "MinCardinality","MaxCardinality",
    "MeanCardinality","StdCardinality","MedianCardinality"
  };

  // TODO: UGH: use reflection!
  public static HashMap<String, Object> makeEmptyFrameMeta() {
    HashMap<String,Object> hm = new LinkedHashMap<>(); // preserve insertion order
    for(String key: FrameMetadata.METAVALUES) hm.put(key,null);
    return hm;
  }

  // Takes empty frame meta hashmap and fills in the metadata not requiring MRTask
  // TODO: make helper functions so that it's possible to iterate over METAVALUES only
  public void fillSimpleMeta(HashMap<String, Object> fm) {
    fm.put("DatasetName", _datasetName);
    fm.put("NRow", (double)_fr.numRows());
    fm.put("NCol", (double)_fr.numCols());
    fm.put("LogNRow", Math.log((double)fm.get("NRow")));
    fm.put("LogNCol", Math.log((double)fm.get("NCol")));
    fm.put("NACount", _fr.naCount());
    fm.put("NAFraction", _fr.naFraction());
    fm.put("NumberNumericFeat", (double)numberOfNumericFeatures());
    fm.put("NumberCatFeat", (double) numberOfCategoricalFeatures());
    fm.put("RatioNumericToCatFeat", Double.isInfinite((double) fm.get("NumberCatFeat"))     ? SQLNAN : (double) fm.get("NumberNumericFeat") / (double) fm.get("NumberCatFeat"));
    fm.put("RatioCatToNumericFeat", Double.isInfinite((double) fm.get("NumberNumericFeat")) ? SQLNAN : (double) fm.get("NumberCatFeat")     / (double) fm.get("NumberNumericFeat"));
    fm.put("DatasetRatio", (double) _fr.numCols() / (double) _fr.numRows());
    fm.put("LogDatasetRatio", Math.log((double) fm.get("DatasetRatio")));
    fm.put("InverseDatasetRatio", (double)_fr.numRows() / (double) _fr.numCols() );
    fm.put("LogInverseDatasetRatio", Math.log((double)fm.get("InverseDatasetRatio")));
    fm.put("Classification", _isClassification?1:0);
    fm.put("FeatWithNAs", (double)na_FeatureCount());
    fm.put("RowsWithNAs",(double)rowsWithNa());
    fm.put("NClass",(double)nClass());
    _statsSkewness = populateStats(StatsType.Skewness, _statsSkewness, fm);
    _statsKurtosis = populateStats(StatsType.Kurtosis, _statsKurtosis, fm);
    _statsCardinality = populateStats(StatsType.Cardinality, _statsCardinality, fm);
  }

  private enum StatsType { Skewness, Kurtosis, Cardinality }

  private SimpleStats populateStats(StatsType st, SimpleStats s, Map<String, Object> output) {
    if (s == null)
      s = calculateStats(st);
    s.toMap(st.name(), output);
    return s;
  }

  private SimpleStats calculateStats(StatsType st) {
    if (! isAnyNumeric())
      return SimpleStats.na();

    DescriptiveStatistics stats = new DescriptiveStatistics();
    for (ColMeta col : _cols) {
      if (!col._ignored && !col._response && col._isNumeric) {
        double v;
        switch (st) {
          case Skewness:
            v = col._skew;
            break;
          case Kurtosis:
            v = col._kurtosis;
            break;
          case Cardinality:
            v = col._cardinality;
            break;
          default:
            throw new IllegalStateException("Unsupported type " + st);
        }
        stats.addValue(v);
      }
    }
    return SimpleStats.from(stats);
  }

  private static class SimpleStats extends Iced<SimpleStats> {
    double _min = Double.NaN;
    double _max = Double.NaN;
    double _mean = Double.NaN;
    double _std = Double.NaN;
    double _median = Double.NaN;

    static SimpleStats from(DescriptiveStatistics stats) {
      SimpleStats ss = new SimpleStats();
      ss._min = stats.getMin();
      ss._max = stats.getMax();
      ss._mean = stats.getMean();
      ss._std = stats.getStandardDeviation();
      ss._median = stats.getPercentile(50);
      return ss;
    }

    static SimpleStats na() {
      return new SimpleStats();
    }

    void toMap(String type, Map<String, Object> output) {
      output.put("Min" + type, _min);
      output.put("Max" + type, _max);
      output.put("Mean" + type, _mean);
      output.put("Std" + type, _std);
      output.put("Median" + type, _median);
    }
  }

  /**
   * Get the non-ignored columns that are not in the filter; do not include the response.
   * @param filterThese remove these columns
   * @return an int[] of the non-ignored column indexes
   */
  public int[] diffCols(int[] filterThese) {
    HashSet<Integer> filter = new HashSet<>();
    for(int i:filterThese)filter.add(i);
    ArrayList<Integer> res = new ArrayList<>();
    for(int i=0;i<_cols.length;++i) {
      if( _cols[i]._ignored || _cols[i]._response || filter.contains(i) ) continue;
      res.add(i);
    }
    return intListToA(res);
  }

  //count of features with nas
  public long na_FeatureCount() {
    if( _featsWithNa!=-1 ) return _featsWithNa;
    long cnt=0;

    for(int i=0;i<_cols.length;++i) {
      if( !_cols[i]._ignored && !_cols[i]._response && _cols[i]._percentNA!=0) {
        cnt += 1;   // check if const columns along with user ignored columns are included in ignored
      }
    }
    return (_featsWithNa=cnt);
  }

  //count of rows with nas
  public long rowsWithNa() {
    if( _rowsWithNa!=-1 ) return _rowsWithNa;
    String x = String.format("(na.omit %s)", _fr._key);
    Val res = Rapids.exec(x);
    Frame f = res.getFrame();
    long cnt = _fr.numRows()  -  f.numRows();
    f.delete();
    return (_rowsWithNa=cnt);

  }

  //number of classes if classification problem
  public long nClass() {
    if( _nclass!=-1 ) return _nclass;
    if(_isClassification==true){
      long cnt=0;
      cnt = _fr.vec(_response).domain().length;
      /*for(int i=0;i<_cols.length;++i) {
      if(_cols[i]._response==true) cnt = _cols[i]._v.domain().length;
    }*/
      return(_nclass=cnt);
    }else{
      return(_nclass=0);
    }
  }

  /** checks if there are any numeric features in the frame*/
  public boolean isAnyNumeric(){
    int cnt =0;
    for(int i=0;i<_cols.length;++i) {
      if( !_cols[i]._ignored && !_cols[i]._response && _cols[i]._isNumeric) {
        cnt = 1;
        break;
      }
    }
    if(cnt ==1) return true;
    else return false;
  }

  /** checks if there are any categorical features in the frame*/
  public boolean isAnyCategorical(){
    int cnt =0;
    for(int i=0;i<_cols.length;++i) {
      if( !_cols[i]._ignored && !_cols[i]._response && _cols[i]._isCategorical) {
        cnt = 1;
        break;
      }
    }
    if(cnt ==1) return true;
    else return false;
  }

  /**
   * If predictors were passed, then any values computed/cached are based on those
   * predictors
   * @return
   */
  public int numberOfNumericFeatures() {
    if( _numFeat!=-1 ) return _numFeat;
    ArrayList<Integer> idxs = new ArrayList<>();
    ArrayList<Integer> intCols = new ArrayList<>();
    ArrayList<Integer> dblCols = new ArrayList<>();
    ArrayList<Integer> binCols = new ArrayList<>();
    ArrayList<Integer> intNotBinCols = new ArrayList<>();
    int cnt=0;
    int idx=0;
    for(Vec v: _fr.vecs()) {
      boolean ignored = _cols[idx]._ignored;
      boolean response= _cols[idx]._response;
      if( v.isNumeric() && !ignored && !response) {
        cnt += 1;
        idxs.add(idx);
        if( v.isInt() ) intCols.add(idx);
        if( v.isBinary() ) binCols.add(idx);
        if( v.isInt() && !v.isBinary() ) intNotBinCols.add(idx);
        if( v.isNumeric() && !v.isInt() ) dblCols.add(idx);
      }
      idx++;
    }
    _numFeats = intListToA(idxs);
    _intCols  = intListToA(intCols);
    _dblCols  = intListToA(dblCols);
    _binaryCols  = intListToA(binCols);
    _intNotBinaryCols = intListToA(intNotBinCols);
    return (_numFeat=cnt);
  }

  public int numberOfCategoricalFeatures() {
    if( _catFeat!=-1 ) return _catFeat;
    ArrayList<Integer> idxs = new ArrayList<>();
    int cnt=0;
    int idx=0;
    for(Vec v: _fr.vecs())  {
      boolean ignored = _cols[idx]._ignored;
      boolean response= _cols[idx]._response;
      if( v.isCategorical() && !ignored && !response) {
        cnt += 1;
        idxs.add(idx);
      }
      idx++;
    }
    _catFeats = intListToA(idxs);
    return (_catFeat=cnt);
  }

  public FrameMetadata(UserFeedback userFeedback, Frame fr, int response, String datasetName) {
    _datasetName=datasetName;
    _fr=fr;
    _response=response;
    _cols = new ColMeta[_fr.numCols()];
    _userFeedback = userFeedback;
  }

  public FrameMetadata(UserFeedback userFeedback, Frame fr, int response, String datasetName, boolean isClassification) {
    this(userFeedback, fr,response,datasetName);
    _isClassification=isClassification;
  }

  public FrameMetadata(UserFeedback userFeedback, Frame fr, int response, int[] predictors, String datasetName, boolean isClassification) {
    this(userFeedback, fr, response, intAtoStringA(predictors, fr.names()), datasetName, isClassification);
  }

  public FrameMetadata(UserFeedback userFeedback, Frame fr, int response, String[] predictors, String datasetName, boolean isClassification) {
    this(userFeedback, fr, response, datasetName, isClassification);
    _includeCols = predictors;
    if( null==_includeCols )
      for (int i = 0; i < _fr.numCols(); ++i)
          _cols[i] = new ColMeta(_fr.vec(i),_fr.name(i),i,i==_response);
    else {
      HashSet<String> preds = new HashSet<>();
      Collections.addAll(preds,_includeCols);
      for(int i=0;i<_fr.numCols();++i)
        _cols[i] = new ColMeta(_fr.vec(i),_fr.name(i),i,i==_response,!preds.contains(_fr.name(i)));
    }
  }

  public boolean isClassification() { return _isClassification; }

  public String[] ignoredCols() {  // publishes private field
    if( _ignoredCols==null ) {
      ArrayList<Integer> cols = new ArrayList<>();
      for(ColMeta c: _cols)
        if( c._ignored ) cols.add(c._idx);
      _ignoredCols=new String[cols.size()];
      for(int i=0;i<cols.size();++i)
        _ignoredCols[i]=_fr.name(cols.get(i));
    }
    return _ignoredCols;
  }

  public String[] includedCols() {
    if( _includeCols==null ) {
      if( null==ignoredCols() ) return _includeCols = _fr.names();
      _includeCols = ArrayUtils.difference(_fr.names(), ignoredCols());  // clones _fr.names, so line above avoids one more copy
    }
    return _includeCols;
  }

  public ColMeta response() {
    if( -1==_response ) {
      for(int i=0;i<_cols.length;++i)
        if(_cols[i]._response) {
          _response=i; break;
        }
    }
    return _cols[_response];
  }


  public boolean stratify() { return response()._stratify; }

  public Vec[] weights() {
    if( null!=_trainTestWeight) return _trainTestWeight;
    return _trainTestWeight = stratify() ? AutoMLUtils.makeStratifiedWeights(response()._v,0.8, response()._weightMult)
                                  : AutoMLUtils.makeWeights(          response()._v,0.8, response()._weightMult);
  }

  // blocking call to compute 1st pass of column metadata
  public FrameMetadata computeFrameMetaPass1() {
    MetaPass1[] tasks = new MetaPass1[_fr.numCols()];
    for(int i=0; i<tasks.length; ++i)
      tasks[i] = new MetaPass1(i,this);
    _isClassification = tasks[_response]._isClassification;
    MetaCollector.ParallelTasks metaCollector = new MetaCollector.ParallelTasks<>(tasks);
    long start = System.currentTimeMillis();
    H2O.submitTask(metaCollector).join();
    _userFeedback.info(Stage.FeatureAnalysis,
                       "Frame metadata analyzer pass 1 completed in " +
                       (System.currentTimeMillis()-start)/1000. +
                       " seconds");
    double sumTimeToMRTaskPerCol=0;
    ArrayList<Integer> dropCols=new ArrayList<>();
    for(MetaPass1 cmt: tasks) {
      if( cmt._colMeta._ignored ) dropCols.add(cmt._colMeta._idx);
      else                        _cols[cmt._colMeta._idx] = cmt._colMeta;
      sumTimeToMRTaskPerCol+= cmt._elapsed;
    }
    _userFeedback.info(Stage.FeatureAnalysis,
                       "Average time to analyze each column: " +
                       String.format("%.5f", (sumTimeToMRTaskPerCol/tasks.length) / 1000.0) +
                       " seconds");
    if( dropCols.size()>0 )
      dropIgnoredCols(intListToA(dropCols));

    return this;
  }

  private void dropIgnoredCols(int[] dropCols) {
    _userFeedback.info(Stage.FeatureAnalysis, "AutoML dropping " + dropCols.length + " ignored columns");
    Vec[] vecsToRemove = _fr.remove(dropCols);
    for(Vec v: vecsToRemove) v.remove();
    ColMeta cm[] = new ColMeta[_fr.numCols()];
    int idx=0;
    for(int i=0;i<_fr.numCols();++i) {
      while(null==_cols[idx]) idx++;
      cm[i]=_cols[idx++];
    }
    _cols=cm;
    flushCachedItems();
  }

  private void flushCachedItems() {
    _catFeats=null;
    _numFeats=null;
    _intCols=null;
    _dblCols=null;
    _binaryCols=null;
    _intNotBinaryCols=null;
    _response=-1;
    _naCnt=-1;
    _numFeat=-1;
    _catFeat=-1;
    _nclass=-1;
    _ignoredCols=null;
    _includeCols=null;
    _featsWithNa=-1;
    _rowsWithNa=-1;
    _statsSkewness = null;
    _statsKurtosis = null;
    _statsCardinality = null;
  }

  public static String[] intAtoStringA(int[] select, String[] names) {
    String[] preds = new String[select.length];
    int i=0;
    for(int p: select) preds[i++] = names[p];
    return preds;
  }

  private static class MetaPass1 extends H2O.H2OCountedCompleter<MetaPass1> {
    private final boolean _response;   // compute class distribution & more granular histo
    private boolean _isClassification; // is this a classification problem?
    private double _mean;              // mean of the column, passed
    private final ColMeta _colMeta;    // result; also holds onto the DHistogram
    private long _elapsed;             // time to mrtask
    static double log2(double numerator) { return (Math.log(numerator))/Math.log(2)+1e-10; }
    public MetaPass1(int idx, FrameMetadata fm) {
      Vec v = fm._fr.vec(idx);
      _response=fm._response==idx;
      String colname = fm._fr.name(idx);
        _colMeta = new ColMeta(v, colname, idx, _response);
      if( _response ) _isClassification = _colMeta.isClassification();
      _mean = v.mean();
      if(v.isCategorical()){
        _colMeta._cardinality = v.cardinality();
      }else{
        _colMeta._cardinality = 0;
      }

      int nbins = (int) Math.ceil(1 + log2(v.length()));  // Sturges nbins
      int xbins = (char) ((long) v.max() - (long) v.min());

      if(!(_colMeta._ignored) && !(_colMeta._v.isBad()) && xbins > 0) {
        _colMeta._histo = MetaCollector.DynamicHisto.makeDHistogram(colname, nbins, nbins, (byte) (v.isCategorical() ? 2 : (v.isInt() ? 1 : 0)), v.min(), v.max());
      }

      // Skewness & Kurtosis
      _colMeta._skew = AstSkewness.skewness(v, true);
      _colMeta._kurtosis = AstKurtosis.kurtosis(v, true);
    }

    public ColMeta meta() { return _colMeta; }
    public long elapsed() { return _elapsed; }

    @Override public void compute2() {
      long start = System.currentTimeMillis();
      int xbins = (char) ((long) _colMeta._v.max() - (long) _colMeta._v.min());
      if (!(_colMeta._ignored) && !(_colMeta._v.isBad()) && xbins > 0) {
        HistTask t = new HistTask(_colMeta._histo, _mean).doAll(_colMeta._v);
        _elapsed = System.currentTimeMillis() - start;
        _colMeta._thirdMoment = t._thirdMoment / ((_colMeta._v.length() - _colMeta._v.naCnt()) - 1);
        _colMeta._fourthMoment = t._fourthMoment / ((_colMeta._v.length() - _colMeta._v.naCnt()) - 1);
        _colMeta._MRTaskMillis = _elapsed;
        Log.info("completed MetaPass1 for col number: " + _colMeta._idx);
        //_colMeta._skew = _colMeta._thirdMoment / Math.sqrt(_colMeta._variance*_colMeta._variance*_colMeta._variance);
        //_colMeta._kurtosis = _colMeta._fourthMoment / (_colMeta._variance * _colMeta._variance);
      }
      tryComplete();
    }

    private static class HistTask extends MRTask<HistTask> {
      private DHistogram _h;
      private double _thirdMoment;     // used for skew/kurtosis; NaN if not numeric
      private double _fourthMoment;    // used for skew/kurtosis; NaN if not numeric
      private double _mean;

      HistTask(DHistogram h, double mean) { _h = h; _mean=mean; }
      @Override public void setupLocal() { _h.init(); }
      @Override public void map( Chunk C ) {
        double min = _h.find_min();
        double max = _h.find_maxIn();
        double[] bins = new double[_h._nbin];
        double colData;
        for(int r=0; r<C._len; ++r) {
          if( Double.isNaN(colData=C.atd(r)) ) continue;
          if( colData < min ) min = colData;
          if( colData > max ) max = colData;
          bins[_h.bin(colData)]++;          double delta = colData - _mean;
          double threeDelta = delta*delta*delta;
          _thirdMoment  += threeDelta;
          _fourthMoment += threeDelta*delta;
        }
        _h.setMin(min); _h.setMaxIn(max);
        for(int b=0; b<bins.length; ++b)
          if( bins[b]!=0 )
            _h.addWAtomic(b, bins[b]);
      }

      @Override public void reduce(HistTask t) {
        if( _h==t._h ) return;
        if( _h==null ) _h=t._h;
        else if( t._h!=null )
          _h.add(t._h);

        if( !Double.isNaN(t._thirdMoment) ) {
          if( Double.isNaN(_thirdMoment) ) _thirdMoment = t._thirdMoment;
          else _thirdMoment += t._thirdMoment;
        }

        if( !Double.isNaN(t._fourthMoment) ) {
          if( Double.isNaN(_fourthMoment) ) _fourthMoment = t._fourthMoment;
          else _fourthMoment += t._fourthMoment;
        }
      }
    }
  }
}
