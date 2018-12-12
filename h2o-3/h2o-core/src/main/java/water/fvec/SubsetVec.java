package water.fvec;

import water.*;

/**
 *  A simple wrapper for looking at only a subset of rows
 */
public class SubsetVec extends WrappedVec {
  final Key _subsetRowsKey;
  transient Vec _rows;          // Cached copy of the rows-Vec
  public SubsetVec(Key key, int rowLayout, Key masterVecKey, Key subsetRowsKey) {
    super(key, rowLayout, masterVecKey);
    _subsetRowsKey = subsetRowsKey;
  }
  public Vec rows() {
    if( _rows==null ) _rows = DKV.get(_subsetRowsKey).get();
    return _rows;
  }

  // A subset chunk
  @Override public Chunk chunkForChunkIdx(int cidx) {
    Chunk crows = rows().chunkForChunkIdx(cidx);
    return new SubsetChunk(crows,this,masterVec());
  }

  @Override public Futures remove_impl(Futures fs) {
    Keyed.remove(_subsetRowsKey,fs);
    return fs;
  }

  /** Write out K/V pairs */
  @Override protected AutoBuffer writeAll_impl(AutoBuffer ab) { 
    ab.putKey(_subsetRowsKey);
    return super.writeAll_impl(ab);
  }
  @Override protected Keyed readAll_impl(AutoBuffer ab, Futures fs) { 
    ab.getKey(_subsetRowsKey,fs);
    return super.readAll_impl(ab,fs);
  }

  // 
  static class SubsetChunk extends Chunk {
    final Chunk _crows;
    final Vec _masterVec;
    protected SubsetChunk(Chunk crows, SubsetVec vec, Vec masterVec) {
      _vec = vec;
      _masterVec = masterVec;
      _len = crows._len;
      _start = crows._start;
      _crows  = crows;
      _cidx = crows._cidx;
    }
    @Override protected double atd_impl(int idx) {
      long rownum = _crows.at8_impl(idx);
      return _masterVec.at(rownum);
    }
    @Override protected long   at8_impl(int idx) {
      long rownum = _crows.at8_impl(idx);
      return _masterVec.at8(rownum);
    }
    @Override protected boolean isNA_impl(int idx) {
      long rownum = _crows.at8_impl(idx);
      return _masterVec.isNA(rownum);
    }

    @Override boolean set_impl(int idx, long l)   { return false; }
    @Override boolean set_impl(int idx, double d) { return false; }
    @Override boolean set_impl(int idx, float f)  { return false; }
    @Override boolean setNA_impl(int idx)         { return false; }

    @Override
    public ChunkVisitor processRows(ChunkVisitor nc, int from, int to) {
      throw H2O.unimpl();
    }

    @Override
    public ChunkVisitor processRows(ChunkVisitor nc, int... rows) {
      throw H2O.unimpl();
    }

    @Override public boolean hasFloat() { return false; }

    public static AutoBuffer write_impl(SubsetChunk sc, AutoBuffer bb) { throw H2O.fail(); }
    @Override protected final void initFromBytes () { throw H2O.fail(); }
  }
}
