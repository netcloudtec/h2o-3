package water.parser;

import water.Futures;
import water.Iced;
import water.fvec.AppendableVec;
import water.fvec.C1Chunk;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.util.ArrayUtils;

import java.util.Arrays;
import java.util.UUID;

/** Parsed data output specialized for fluid vecs.
 * @author tomasnykodym
 */
public class FVecParseWriter extends Iced implements StreamParseWriter {
  protected AppendableVec[] _vecs;
  protected transient NewChunk[] _nvs;
  protected transient final Categorical [] _categoricals;
  protected transient final byte[] _ctypes;
  long _nLines;
  int _nCols;
  int _col = -1;
  final int _cidx;
  final int _chunkSize;
  ParseErr [] _errs = new ParseErr[0];
  private final Vec.VectorGroup _vg;
  private long _errCnt;

  public FVecParseWriter(Vec.VectorGroup vg, int cidx, Categorical[] categoricals, byte[] ctypes, int chunkSize, AppendableVec[] avs){
    _ctypes = ctypes;           // Required not-null
    _vecs = avs;
    _nvs = new NewChunk[avs.length];
    for(int i = 0; i < avs.length; ++i)
      _nvs[i] = _vecs[i].chunkForChunkIdx(cidx);
    _categoricals = categoricals;
    _nCols = avs.length;
    _cidx = cidx;
    _vg = vg;
    _chunkSize = chunkSize;
  }

  @Override public FVecParseWriter reduce(StreamParseWriter sdout){
    FVecParseWriter dout = (FVecParseWriter)sdout;
    _nCols = Math.max(_nCols,dout._nCols); // SVMLight: max of columns
    if( _vecs != dout._vecs ) {
      if( dout._vecs.length > _vecs.length ) { // Swap longer one over the returned value
        AppendableVec[] tmpv = _vecs;  _vecs = dout._vecs;  dout._vecs = tmpv;
      }
      for(int i = 0; i < dout._vecs.length; ++i)
        _vecs[i].reduce(dout._vecs[i]);
    }
    _errCnt += ((FVecParseWriter) sdout)._errCnt;
    if(_errs.length < 20 && ((FVecParseWriter) sdout)._errs.length > 0) {
      _errs = ArrayUtils.append(_errs, ((FVecParseWriter) sdout)._errs);
      if(_errs.length > 20)
        _errs = Arrays.copyOf(_errs,20);
    }
    return this;
  }
  @Override public FVecParseWriter close(){
    Futures fs = new Futures();
    close(fs);
    fs.blockForPending();
    return this;
  }
  @Override public FVecParseWriter close(Futures fs){
    if( _nvs == null ) return this; // Might call close twice
    for(int i=0; i < _nvs.length; i++) {
      _nvs[i].close(_cidx, fs);
      _nvs[i] = null; // free immediately, don't wait for all columns to close
    }
    _nvs = null;  // Free for GC
    return this;
  }
  @Override public FVecParseWriter nextChunk(){
    return  new FVecParseWriter(_vg, _cidx+1, _categoricals, _ctypes, _chunkSize, _vecs);
  }

  @Override public void newLine() {
    if(_col >= 0){
      ++_nLines;
      for(int i = _col+1; i < _nCols; ++i)
        addInvalidCol(i);
    }
    _col = -1;
  }
  @Override public void addNumCol(int colIdx, long number, int exp) {
    if( colIdx < _nCols ) {
      _nvs[_col = colIdx].addNum(number, exp);
      if(_ctypes != null && _ctypes[colIdx] == Vec.T_BAD ) _ctypes[colIdx] = Vec.T_NUM;
    }
  }

  @Override public final void addInvalidCol(int colIdx) {
    if(colIdx < _nCols) _nvs[_col = colIdx].addNA();
  }

  @Override
  public void addNAs(int colIdx, int nrows) {
    (_nvs[colIdx] = _vecs[colIdx].chunkForChunkIdx(_cidx)).addNAs(nrows);
  }

  @Override public boolean isString(int colIdx) { return (colIdx < _nCols) && (_ctypes[colIdx] == Vec.T_CAT || _ctypes[colIdx] == Vec.T_STR);}

  @Override public void addStrCol(int colIdx, BufferedString str) {
    if(colIdx < _nvs.length){
      if(_ctypes[colIdx] == Vec.T_NUM){ // support enforced types
        addInvalidCol(colIdx);
        return;
      }
      if(_ctypes[colIdx] == Vec.T_BAD && ParseTime.isTime(str))
        _ctypes[colIdx] = Vec.T_TIME;
      if( _ctypes[colIdx] == Vec.T_BAD && ParseUUID.isUUID(str))
        _ctypes[colIdx] = Vec.T_UUID;

      if( _ctypes[colIdx] == Vec.T_TIME ) {
        long l = ParseTime.attemptTimeParse(str);
        if( l == Long.MIN_VALUE ) addInvalidCol(colIdx);
        else {
          addNumCol(colIdx, l, 0);               // Record time in msec
          _nvs[_col]._timCnt++; // Count histo of time parse patterns
        }
      } else if( _ctypes[colIdx] == Vec.T_UUID ) { // UUID column?  Only allow UUID parses
        UUID uuid = ParseUUID.attemptUUIDParse(str);
        // FIXME: what if colIdx > _nCols
        if( colIdx < _nCols ) _nvs[_col = colIdx].addUUID(uuid);
      } else if( _ctypes[colIdx] == Vec.T_STR ) {
        _nvs[_col = colIdx].addStr(str);
      } else { // categoricals
        if(!_categoricals[colIdx].isMapFull()) {
          int id = _categoricals[_col = colIdx].addKey(str);
          if (_ctypes[colIdx] == Vec.T_BAD && id > 1) _ctypes[colIdx] = Vec.T_CAT;
          if(_ctypes[colIdx] == Vec.T_CAT) {
            _nvs[colIdx].addNum(id, 0); // if we are sure we have a categorical column, we can only store the integer (more efficient than remembering this value was categorical)
          } else
            _nvs[colIdx].addCategorical(id);
        } else { // maxed out categorical map
          throw new ParseDataset.H2OParseException("Exceeded categorical limit on column #"+(colIdx+1)+" (using 1-based indexing).  Consider reparsing this column as a string.");
        }
      }
    }
  }

  /** Adds double value to the column. */
  @Override public void addNumCol(int colIdx, double value) {
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      addInvalidCol(colIdx);
    } else {
      if( colIdx < _nCols ) {
        _nvs[_col = colIdx].addNumDecompose(value);
        if(_ctypes != null && _ctypes[colIdx] == Vec.T_BAD ) _ctypes[colIdx] = Vec.T_NUM;
      }
    }
  }
  @Override public void setColumnNames(String [] names){}
  @Override public final void rollbackLine() {}

  @Override public void invalidLine(ParseErr err) {
    addErr(err);
    newLine();
  }

  @Override
  public void addError(ParseErr err) {
    if(_errs == null)
      _errs = new ParseErr[]{err};
    else  if(_errs.length < 20)
      _errs = ArrayUtils.append(_errs,err);
    _errCnt++;
  }

  @Override public void setIsAllASCII(int colIdx, boolean b) {
    if(colIdx < _nvs.length)
      _nvs[colIdx]._isAllASCII = b;
  }

  @Override
  public boolean hasErrors() {
    return _errs != null && _errs.length > 0;
  }
  @Override
  public ParseErr[] removeErrors() {
    ParseErr [] res = _errs;
    _errs = null;
    return res;
  }

  @Override
  public long lineNum() {return _nLines;}

  public void addErr(ParseErr err){
    if(_errs.length < 20)
      _errs = ArrayUtils.append(_errs,err);
    ++_errCnt;
  }
}
