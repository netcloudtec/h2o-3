package water.rapids.ast.prims.advmath;

import sun.misc.Unsafe;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.nbhm.UtilUnsafe;
import water.rapids.ast.prims.reducers.AstMad;
import water.rapids.Env;
import water.rapids.Val;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.ast.params.AstNum;
import water.rapids.ast.params.AstNumList;
import water.rapids.ast.params.AstStr;
import water.util.ArrayUtils;

public class AstHist extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary", "breaks"};
  }

  @Override
  public int nargs() {
    return 1 + 2;
  } // (hist x breaks)

  @Override
  public String str() {
    return "hist";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    // stack is [ ..., ary, breaks]
    // handle the breaks
    Frame fr2;
    Frame f = stk.track(asts[1].exec(env)).getFrame();
    if (f.numCols() != 1) throw new IllegalArgumentException("Hist only applies to single numeric columns.");
    Vec vec = f.anyVec();
    if (!vec.isNumeric()) throw new IllegalArgumentException("Hist only applies to single numeric columns.");
    //TODO Add case when vec is a constant numeric
    if(vec.isConst()) throw new IllegalArgumentException("Hist does not apply to constant numeric columns.");

    AstRoot a = asts[2];
    String algo = null;
    int numBreaks = -1;
    double[] breaks = null;

    if (a instanceof AstStr) algo = a.str().toLowerCase();
    else if (a instanceof AstNumList) breaks = ((AstNumList) a).expand();
    else if (a instanceof AstNum) numBreaks = (int) a.exec(env).getNum();

    AstHist.HistTask t;
    double h;
    double x1 = vec.max();
    double x0 = vec.min();
    if (breaks != null) t = new AstHist.HistTask(breaks, -1, -1/*ignored if _h==-1*/).doAll(vec);
    else if (algo != null) {
      switch (algo) {
        case "sturges":
          numBreaks = sturges(vec);
          h = (x1 - x0) / numBreaks;
          break;
        case "rice":
          numBreaks = rice(vec);
          h = (x1 - x0) / numBreaks;
          break;
        case "sqrt":
          numBreaks = sqrt(vec);
          h = (x1 - x0) / numBreaks;
          break;
        case "doane":
          numBreaks = doane(vec);
          h = (x1 - x0) / numBreaks;
          break;
        case "scott":
          h = scotts_h(vec);
          numBreaks = scott(vec, h);
          break;  // special bin width computation
        case "fd":
          h = fds_h(vec);
          numBreaks = fd(vec, h);
          break;  // special bin width computation
        default:
          numBreaks = sturges(vec);
          h = (x1 - x0) / numBreaks;         // just do sturges even if junk passed in
      }
      t = new AstHist.HistTask(computeCuts(vec, numBreaks), h, x0).doAll(vec);
    } else {
      h = (x1 - x0) / numBreaks;
      t = new AstHist.HistTask(computeCuts(vec, numBreaks), h, x0).doAll(vec);
    }
    // wanna make a new frame here [breaks,counts,mids]
    final double[] brks = t._breaks;
    final long[] cnts = t._counts;
    final double[] mids_true = t._mids;
    final double[] mids = new double[t._breaks.length - 1];
    for (int i = 1; i < brks.length; ++i) mids[i - 1] = .5 * (t._breaks[i - 1] + t._breaks[i]);
    Vec layoutVec = Vec.makeZero(brks.length);
    fr2 = new MRTask() {
      @Override
      public void map(Chunk[] c, NewChunk[] nc) {
        int start = (int) c[0].start();
        for (int i = 0; i < c[0]._len; ++i) {
          nc[0].addNum(brks[i + start]);
          if (i == 0) {
            nc[1].addNA();
            nc[2].addNA();
            nc[3].addNA();
          } else {
            nc[1].addNum(cnts[(i - 1) + start]);
            nc[2].addNum(mids_true[(i - 1) + start]);
            nc[3].addNum(mids[(i - 1) + start]);
          }
        }
      }
    }.doAll(4, Vec.T_NUM, new Frame(layoutVec)).outputFrame(null, new String[]{"breaks", "counts", "mids_true", "mids"}, null);
    layoutVec.remove();
    return new ValFrame(fr2);
  }

  public static int sturges(Vec v) {
    return (int) Math.ceil(1 + log2(v.length()));
  }

  public static int rice(Vec v) {
    return (int) Math.ceil(2 * Math.pow(v.length(), 1. / 3.));
  }

  public static int sqrt(Vec v) {
    return (int) Math.sqrt(v.length());
  }

  public static int doane(Vec v) {
    return (int) (1 + log2(v.length()) + log2(1 + (Math.abs(third_moment(v)) / sigma_g1(v))));
  }

  public static int scott(Vec v, double h) {
    return (int) Math.ceil((v.max() - v.min()) / h);
  }

  public static int fd(Vec v, double h) {
    return (int) Math.ceil((v.max() - v.min()) / h);
  }   // Freedman-Diaconis slightly modified to use MAD instead of IQR

  public static double fds_h(Vec v) {
    return 2 * AstMad.mad(new Frame(v), null, 1.4826) * Math.pow(v.length(), -1. / 3.);
  }

  public static double scotts_h(Vec v) {
    return 3.5 * Math.sqrt(AstVariance.getVar(v)) / (Math.pow(v.length(), 1. / 3.));
  }

  public static double log2(double numerator) {
    return (Math.log(numerator)) / Math.log(2) + 1e-10;
  }

  public static double sigma_g1(Vec v) {
    return Math.sqrt((6 * (v.length() - 2)) / ((v.length() + 1) * (v.length() + 3)));
  }

  public static double third_moment(Vec v) {
    final double mean = v.mean();
    AstHist.ThirdMomTask t = new AstHist.ThirdMomTask(mean).doAll(v);
    double m2 = t._ss / v.length();
    double m3 = t._sc / v.length();
    return m3 / Math.pow(m2, 1.5);
  }

  public static class ThirdMomTask extends MRTask<AstHist.ThirdMomTask> {
    double _ss;
    double _sc;
    final double _mean;

    ThirdMomTask(double mean) {
      _mean = mean;
    }

    @Override
    public void setupLocal() {
      _ss = 0;
      _sc = 0;
    }

    @Override
    public void map(Chunk c) {
      for (int i = 0; i < c._len; ++i) {
        if (!c.isNA(i)) {
          double d = c.atd(i) - _mean;
          double d2 = d * d;
          _ss += d2;
          _sc += d2 * d;
        }
      }
    }

    @Override
    public void reduce(AstHist.ThirdMomTask t) {
      _ss += t._ss;
      _sc += t._sc;
    }
  }

  public static double fourth_moment(Vec v) {
    final double mean = v.mean();
    AstHist.FourthMomTask t = new AstHist.FourthMomTask(mean).doAll(v);
    double m2 = t._ss / v.length();
    double m4 = t._sc / v.length();
    return m4 / Math.pow(m2, 2.0);
  }

  public static class FourthMomTask extends MRTask<AstHist.FourthMomTask> {
    double _ss;
    double _sc;
    final double _mean;

    FourthMomTask(double mean) {
      _mean = mean;
    }

    @Override
    public void setupLocal() {
      _ss = 0;
      _sc = 0;
    }

    @Override
    public void map(Chunk c) {
      for (int i = 0; i < c._len; ++i) {
        if (!c.isNA(i)) {
          double d = c.atd(i) - _mean;
          double d2 = d * d;
          _ss += d2;
          _sc += d2 * d * d;
        }
      }
    }

    @Override
    public void reduce(AstHist.FourthMomTask t) {
      _ss += t._ss;
      _sc += t._sc;
    }
  }

  public double[] computeCuts(Vec v, int numBreaks) {
    if (numBreaks <= 0) throw new IllegalArgumentException("breaks must be a positive number");
    // just make numBreaks cuts equidistant from each other spanning range of [v.min, v.max]
    double min;
    double w = (v.max() - (min = v.min())) / numBreaks;
    double[] res = new double[numBreaks];
    for (int i = 0; i < numBreaks; ++i) res[i] = min + w * (i + 1);
    return res;
  }

  public static class HistTask extends MRTask<AstHist.HistTask> {
    final private double _h;      // bin width
    final private double _x0;     // far left bin edge
    final private double[] _min;  // min for each bin, updated atomically
    final private double[] _max;  // max for each bin, updated atomically
    // unsafe crap for mins/maxs of bins
    private static final Unsafe U = UtilUnsafe.getUnsafe();
    // double[] offset and scale
    private static final int _dB = U.arrayBaseOffset(double[].class);
    private static final int _dS = U.arrayIndexScale(double[].class);

    private static long doubleRawIdx(int i) {
      return _dB + _dS * i;
    }

    // long[] offset and scale
    private static final int _8B = U.arrayBaseOffset(long[].class);
    private static final int _8S = U.arrayIndexScale(long[].class);

    private static long longRawIdx(int i) {
      return _8B + _8S * i;
    }

    // out
    private final double[] _breaks;
    private final long[] _counts;
    private final double[] _mids;

    HistTask(double[] cuts, double h, double x0) {
      _breaks = cuts;
      _min = new double[_breaks.length - 1];
      _max = new double[_breaks.length - 1];
      _counts = new long[_breaks.length - 1];
      _mids = new double[_breaks.length - 1];
      _h = h;
      _x0 = x0;
    }

    @Override
    public void map(Chunk c) {
      // if _h==-1, then don't have fixed bin widths... must loop over bins to obtain the correct bin #
      for (int i = 0; i < c._len; ++i) {
        int x = 1;
        if (c.isNA(i)) continue;
        double r = c.atd(i);
        if (_h == -1) {
          for (; x < _counts.length; x++)
            if (r <= _breaks[x]) break;
          x--; // back into the bin where count should go
        } else
          x = Math.min(_counts.length - 1, (int) Math.floor((r - _x0) / _h));     // Pick the bin   floor( (x - x0) / h ) or ceil( (x-x0)/h - 1 ), choose the first since fewer ops
        bumpCount(x);
        setMinMax(Double.doubleToRawLongBits(r), x);
      }
    }

    @Override
    public void reduce(AstHist.HistTask t) {
      if (_counts != t._counts) ArrayUtils.add(_counts, t._counts);
      for (int i = 0; i < _mids.length; ++i) {
        _min[i] = t._min[i] < _min[i] ? t._min[i] : _min[i];
        _max[i] = t._max[i] > _max[i] ? t._max[i] : _max[i];
      }
    }

    @Override
    public void postGlobal() {
      for (int i = 0; i < _mids.length; ++i) _mids[i] = 0.5 * (_max[i] + _min[i]);
    }

    private void bumpCount(int x) {
      long o = _counts[x];
      while (!U.compareAndSwapLong(_counts, longRawIdx(x), o, o + 1))
        o = _counts[x];
    }

    private void setMinMax(long v, int x) {
      double o = _min[x];
      double vv = Double.longBitsToDouble(v);
      while (vv < o && U.compareAndSwapLong(_min, doubleRawIdx(x), Double.doubleToRawLongBits(o), v))
        o = _min[x];
      setMax(v, x);
    }

    private void setMax(long v, int x) {
      double o = _max[x];
      double vv = Double.longBitsToDouble(v);
      while (vv > o && U.compareAndSwapLong(_min, doubleRawIdx(x), Double.doubleToRawLongBits(o), v))
        o = _max[x];
    }
  }
}
