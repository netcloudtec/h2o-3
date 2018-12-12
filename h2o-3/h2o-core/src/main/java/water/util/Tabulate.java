package water.util;

import hex.Interaction;
import water.*;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;

/**
 * Simple Co-Occurrence based tabulation of X vs Y, where X and Y are two Vecs in a given dataset
 * Uses histogram of given resolution in X and Y
 * Handles numerical/categorical data and missing values
 * Supports observation weights
 *
 * Fills up two double[][] arrays:
 * _countData[xbin][ybin] contains the sum of observation weights (or 1) for co-occurrences in bins xbin/ybin
 * _responseData[xbin][2] contains the mean value of Y and the sum of observation weights for a given bin for X
 */
public class Tabulate extends Keyed<Tabulate> {
  public final Job<Tabulate> _job;
  public Frame _dataset;
  public Key[] _vecs = new Key[2];
  public String _predictor;
  public String _response;
  public String _weight;
  int _nbins_predictor = 20;
  int _nbins_response = 10;

  // result
  double[][] _count_data;
  double[][] _response_data;
  public TwoDimTable _count_table;
  public TwoDimTable _response_table;

  // helper to speed up stuff
  static private class Stats extends Iced {
    Stats(Vec v) {
      _min = v.min();
      _max = v.max();
      _isCategorical = v.isCategorical();
      _isInt = v.isInt();
      _cardinality = v.cardinality();
      _missing = v.naCnt() > 0 ? 1 : 0;
      _domain = v.domain();
    }
    final double _min;
    final double _max;
    final boolean _isCategorical;
    final boolean _isInt;
    final int _cardinality;
    final int _missing; //0 or 1
    final String[] _domain;
  }
  final private Stats[] _stats = new Stats[2];

  public Tabulate() {
    _job = new Job(Key.<Tabulate>make(), Tabulate.class.getName(), "Tabulate job");
  }

  private int bins(int v) {
    return v==1 ? _nbins_response : _nbins_predictor;
  }

  private int res(final int v) {
    final int missing = _stats[v]._missing;
    if (_stats[v]._isCategorical)
      return _stats[v]._cardinality + missing;
    return bins(v) + missing;
  }

  private int bin(final int v, final double val) {
    if (Double.isNaN(val)) {
      return 0;
    }
    int b;
    int bins = bins(v);
    if (_stats[v]._isCategorical) {
      assert((int)val == val);
      b = (int) val;
    } else {
      double d = (_stats[v]._max - _stats[v]._min) / bins;
      b = (int) ((val - _stats[v]._min) / d);
      assert(b>=0 && b<= bins);
      b = Math.min(b, bins -1);//avoid AIOOBE at upper bound
    }
    return b+_stats[v]._missing;
  }

  private String labelForBin(final int v, int b) {
    int missing = _stats[v]._missing;
    if (missing == 1 && b==0) return "missing(NA)";
    if (missing == 1) b--;
    if (_stats[v]._isCategorical)
      return _stats[v]._domain[b];
    int bins = bins(v);
    if (_stats[v]._isInt && (_stats[v]._max - _stats[v]._min + 1) <= bins)
      return Integer.toString((int)(_stats[v]._min + b));
    double d = (_stats[v]._max - _stats[v]._min)/bins;
    return String.format("%5f", _stats[v]._min + (b + 0.5) * d);
  }

  public Tabulate execImpl() {
    if (_dataset == null)     throw new H2OIllegalArgumentException("Dataset not found");
    if (_nbins_predictor < 1) throw new H2OIllegalArgumentException("Number of bins for predictor must be >= 1");
    if (_nbins_response < 1)  throw new H2OIllegalArgumentException("Number of bins for response must be >= 1");
    Vec x = _dataset.vec(_predictor);
    if (x == null)            throw new H2OIllegalArgumentException("Predictor column " + _predictor + " not found");
    if (x.cardinality() > _nbins_predictor) {
      Interaction in = new Interaction();
      in._source_frame = _dataset._key;
      in._factor_columns = new String[]{_predictor};
      in._max_factors = _nbins_predictor -1;
      in.execImpl(null);
      x = in._job._result.get().anyVec();
    } else if (x.isInt() && (x.max() - x.min() + 1) <= _nbins_predictor) {
      x = x.toCategoricalVec();
    }
    Vec y = _dataset.vec(_response);
    if (y == null) throw new H2OIllegalArgumentException("Response column " + _response + " not found");
    if (y.cardinality() > _nbins_response) {
      Interaction in = new Interaction();
      in._source_frame = _dataset._key;
      in._factor_columns = new String[]{_response};
      in._max_factors = _nbins_response -1;
      in.execImpl(null);
      y = in._job._result.get().anyVec();
    } else if (y.isInt() && (y.max() - y.min() + 1) <= _nbins_response) {
      y = y.toCategoricalVec();
    }
    if (y!=null && y.cardinality() > 2)
      Log.warn("Response column has more than two factor levels - mean response depends on lexicographic order of factors!");
    Vec w = _dataset.vec(_weight); //can be null
    if (w != null && (!w.isNumeric() && w.min() < 0)) throw new H2OIllegalArgumentException("Observation weights must be numeric with values >= 0");

    if (x!=null) {
      _vecs[0] = x._key;
      _stats[0] = new Stats(x);
    }
    if (y!=null) {
      _vecs[1] = y._key;
      _stats[1] = new Stats(y);
    }
    Tabulate sp = w != null ? new CoOccurrence(this).doAll(x, y, w)._sp : new CoOccurrence(this).doAll(x, y)._sp;
    _count_table = sp.tabulationTwoDimTable();
    _response_table = sp.responseCharTwoDimTable();

    Log.info(_count_table.toString(2, false));
    Log.info(_response_table.toString(2, false));
    return sp;
  }

  private static class CoOccurrence extends MRTask<CoOccurrence> {
    final Tabulate _sp;
    CoOccurrence(Tabulate sp) {_sp = sp;}
    @Override
    protected void setupLocal() {
      _sp._count_data = new double[_sp.res(0)][_sp.res(1)];
      _sp._response_data = new double[_sp.res(0)][2];
    }

    @Override
    public void map(Chunk x, Chunk y) {
      map(x, y, (Chunk)null);
    }
    @Override
    public void map(Chunk x, Chunk y, Chunk w) {
      for (int r=0; r<x.len(); ++r) {
        int xbin = _sp.bin(0, x.atd(r));
        int ybin = _sp.bin(1, y.atd(r));
        double weight = w!=null?w.atd(r):1;
        if (Double.isNaN(weight)) continue;
        AtomicUtils.DoubleArray.add(_sp._count_data[xbin], ybin, weight); //increment co-occurrence count by w
        if (!y.isNA(r)) {
          AtomicUtils.DoubleArray.add(_sp._response_data[xbin], 0, weight * y.atd(r)); //add to mean response for x
          AtomicUtils.DoubleArray.add(_sp._response_data[xbin], 1, weight); //increment total for x
        }
      }
    }

    @Override
    public void reduce(CoOccurrence mrt) {
      if (_sp._response_data == mrt._sp._response_data) return;
      ArrayUtils.add(_sp._response_data, mrt._sp._response_data);
    }

    @Override
    protected void postGlobal() {
      //compute mean response
      for (int i=0; i<_sp._response_data.length; ++i) {
        _sp._response_data[i][0] /= _sp._response_data[i][1];
      }
    }
  }

  public TwoDimTable tabulationTwoDimTable() {
    if (_response_data == null) return null;
    int predN = _count_data.length;
    int respN = _count_data[0].length;
    String tableHeader = "(Weighted) co-occurrence counts of '" + _predictor + "' and '" + _response + "'";
    String[] rowHeaders = new String[predN * respN];
    String[] colHeaders = new String[3]; //predictor response wcount
    String[] colTypes = new String[colHeaders.length];
    String[] colFormats = new String[colHeaders.length];

    colHeaders[0] = _predictor;
    colHeaders[1] = _response;
    colTypes[0] = "string"; colFormats[0] = "%s";
    colTypes[1] = "string"; colFormats[1] = "%s";
    colHeaders[2] = "counts";   colTypes[2] = "double"; colFormats[2] = "%f";
    TwoDimTable table = new TwoDimTable(
            tableHeader, null/*tableDescription*/, rowHeaders, colHeaders,
            colTypes, colFormats, null);

    for (int p=0; p<predN; ++p) {
      String plabel = labelForBin(0, p);
      for (int r=0; r<respN; ++r) {
        String rlabel = labelForBin(1, r);
        for (int c=0; c<3; ++c) {
          table.set(r*predN + p, 0, plabel);
          table.set(r*predN + p, 1, rlabel);
          table.set(r*predN + p, 2, _count_data[p][r]);
        }
      }
    }
    return table;
  }

  public TwoDimTable responseCharTwoDimTable() {
    if (_response_data == null) return null;
    String tableHeader = "Mean value of '" + _response  + "' and (weighted) counts for '" + _predictor + "' values";
    int predN = _count_data.length;
    String[] rowHeaders = new String[predN]; //X
    String[] colHeaders = new String[3];    //Y
    String[] colTypes = new String[colHeaders.length];
    String[] colFormats = new String[colHeaders.length];

    colHeaders[0] = _predictor;
    colTypes[0] = "string"; colFormats[0] = "%s";
    colHeaders[1] = "mean " + _response; colTypes[2] = "double"; colFormats[2] = "%f";
    colHeaders[2] = "counts";            colTypes[1] = "double"; colFormats[1] = "%f";

    TwoDimTable table = new TwoDimTable(
            tableHeader, null/*tableDescription*/, rowHeaders, colHeaders,
            colTypes, colFormats, null);

    for (int p=0; p<predN; ++p) {
      String plabel = labelForBin(0, p);
      table.set(p, 0, plabel);
      table.set(p, 1, _response_data[p][0]);
      table.set(p, 2, _response_data[p][1]);
    }
    return table;
  }
}
