package water.fvec;

import water.MemoryManager;
import water.util.UnsafeUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * The "few unique doubles"-compression function
 */
public class CUDChunk extends Chunk {
  public static int MAX_UNIQUES=256;
  public static int computeByteSize(int uniques, int len) {
    return 4 + 4 // _len + numUniques
            + (uniques << 3) //unique double values
            + (len << 1); //mapping of row -> unique value index (0...255)
  }
  int numUniques;
  CUDChunk() {}
  CUDChunk(byte[] bs, HashMap<Long,Byte> hs, int len) {
    _start = -1;
    numUniques = hs.size();
    set_len(len);
    _mem = MemoryManager.malloc1(computeByteSize(numUniques, _len), false);
    UnsafeUtils.set4(_mem, 0, _len);
    UnsafeUtils.set4(_mem, 4, numUniques);
    int j=0;
    //create the mapping and also store the unique values (as longs)
    for (Map.Entry<Long,Byte> e : hs.entrySet()) {
      e.setValue(new Byte((byte)(j-128))); //j is in 0...256  -> byte value needs to be in -128...127 for storage
      UnsafeUtils.set8(_mem, 8 + (j << 3), e.getKey());
      j++;
    }
    // store the mapping
    for (int i=0; i<len; ++i)
      UnsafeUtils.set1(_mem, 8 + (numUniques << 3) + i, hs.get(Double.doubleToLongBits(UnsafeUtils.get8d(bs, i << 3))));
  }
  @Override protected final long   at8_impl( int i ) {
    double res = atd_impl(i);
    if( Double.isNaN(res) ) throw new IllegalArgumentException("at8_impl but value is missing");
    return (long)res;
  }
  @Override protected final double   atd_impl( int i ) {
    int whichUnique = (UnsafeUtils.get1(_mem, 8 + (numUniques << 3) + i)+128);
    return Double.longBitsToDouble(UnsafeUtils.get8(_mem, 8 + (whichUnique << 3)));
  }

  @Override public double [] getDoubles(double [] vals, int from, int to) {
    return getDoubles(vals,from,to,Double.NaN);
  }
  @Override public double [] getDoubles(double [] vals, int from, int to, double NA) {
    double [] uniques = new double[numUniques];
    for(int i = 0; i < numUniques; ++i) {
      uniques[i] = Double.longBitsToDouble(UnsafeUtils.get8(_mem, 8 + (i << 3)));
      if(Double.isNaN(uniques[i]))
        uniques[i] = NA;
    }
    for(int i = 0; i < _len; ++i)
      vals[i] = uniques[(UnsafeUtils.get1(_mem, 8 + (numUniques << 3) + i)+128)];
    return vals;
  }

  @Override protected final boolean isNA_impl( int i ) { return Double.isNaN(atd_impl(i)); }
  @Override boolean set_impl(int idx, long l) { return false; }
  @Override boolean set_impl(int i, double d) {
    for (int j = 0; j < numUniques; ++j) {
      if (Double.compare(Double.doubleToLongBits(d), UnsafeUtils.get8(_mem, 8 + (j << 3))) == 0) {
        UnsafeUtils.set1(_mem, 8 + (numUniques << 3) + i, (byte) (j-128));
        return true;
      }
    }
    return false;
  }
  @Override boolean set_impl(int i, float f ) {
    return set_impl(i, (double)f);
  }
  @Override boolean setNA_impl(int idx) {
    return set_impl(idx, Double.NaN);
  }
  @Override public ChunkVisitor processRows(ChunkVisitor nc, int from, int to){
    for(int i = from; i < to; i++)
      nc.addValue(atd(i));
    return nc;
  }

  @Override public ChunkVisitor processRows(ChunkVisitor nc, int... rows){
    for(int i:rows)
      nc.addValue(atd(i));
    return nc;
  }
  @Override protected final void initFromBytes () {
    _start = -1;  _cidx = -1;
    _len = UnsafeUtils.get4(_mem, 0);
    numUniques = UnsafeUtils.get4(_mem, 4);
    set_len(_len);
  }
}
