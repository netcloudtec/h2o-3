package water.rapids.transforms;

import hex.genmodel.GenMunger;
import water.H2O;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.util.ArrayUtils;

public class H2OScaler extends Transform<H2OScaler> {

  double[] means;
  double[] sdevs;

  H2OScaler(String name, String ast, boolean inplace, String[] newNames) { super(name,ast,inplace,newNames); }

  @Override public Transform<H2OScaler> fit(Frame f) {
    means = new double[f.numCols()];
    sdevs = new double[f.numCols()];
    for(int i=0;i<f.numCols();++i) {
      means[i] = f.vec(i).mean();
      sdevs[i] = f.vec(i).sigma();
    }
    return this;
  }

  // TODO: handle Categorical, String, NA
  @Override protected Frame transformImpl(Frame f) {
    final double[] fmeans = means;
    final double[] fmults = ArrayUtils.invert(sdevs);
    return new MRTask() {
      @Override public void map(Chunk[] cs, NewChunk[] ncs) {
        double[] in = new double[cs.length];
        for(int row=0; row<cs[0]._len; row++) {
          for(int col=0; col<cs.length; col++)
            in[col] = cs[col].atd(row);
          GenMunger.scaleInPlace(fmeans, fmults, in);
          for(int col=0; col<ncs.length; col++)
            ncs[col].addNum(in[col]);
        }
      }
    }.doAll(f.numCols(), Vec.T_NUM, f).outputFrame(f.names(), f.domains());
  }

  @Override Frame inverseTransform(Frame f) { throw H2O.unimpl(); }
  @Override public String genClassImpl() {
    throw H2O.unimpl();
  }
}
