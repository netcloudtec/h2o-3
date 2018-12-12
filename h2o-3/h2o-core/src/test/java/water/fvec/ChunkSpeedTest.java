package water.fvec;

import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.util.Log;
import water.util.PrettyPrint;

public class ChunkSpeedTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  final int cols = 100;
  final int rows = 100000;
  final int rep = 10;
  final double[][] raw = new double[cols][rows];
  Chunk[] chunks = new Chunk[cols];


  @Test
  public void run() {
    for (int j = 0; j < cols; ++j) {
      for (int i = 0; i < rows; ++i) {
        raw[j][i] = get(j, i);
      }
    }
    for (int j = 0; j < cols; ++j) {
      chunks[j] = new NewChunk(raw[j]).compress();
      Log.info("Column " + j + " compressed into: " + chunks[j].getClass().toString());
    }
    Log.info("COLS: " + cols);
    Log.info("ROWS: " + rows);
    Log.info("REPS: " + rep);

    int ll = 5;
    for (int i = 0; i < ll; ++i)
      raw();
    for (int i = 0; i < ll; ++i)
      chunks();
    for (int i = 0; i < ll; ++i)
      chunks_bulk();
    for (int i = 0; i < ll; ++i)
      chunks_part();
    for (int i = 0; i < ll; ++i)
      chunks_visitor();
    for (int i = 0; i < ll; ++i)
      chunksInline();
//    for (int i = 0; i < ll; ++i)
//      mrtask(false);
//    for (int i = 0; i < ll; ++i)
//      rollups(false);
//    Log.info("Now doing funny stuff.\n\n");
//    for (int i = 0; i < ll; ++i)
//      mrtask(true);
//    for (int i = 0; i < ll; ++i)
//      rollups(true);
//    for (int i = 0; i < ll; ++i)
//      chunksInverted();
//    for (int i = 0; i < ll; ++i)
//      rawInverted();

  }

  double get(int j, int i) {
//        switch (j%1+0) { //just do 1 byte chunks
//        switch (j%1+1) { //just do 2 byte chunks
//        switch (j % 2) { //just do 1/2 byte chunks
    switch (j%4) { // do 3 chunk types
//        switch (j%4) { // do 4 chunk types
      case 0:
        return i % 200; //C1NChunk - 1 byte integer
      case 1:
        return i % 500; //C2Chunk - 2 byte integer
      case 2:
        return  i*Integer.MAX_VALUE;
      case 3:
        return i == 17 ? 1 : 0; //CX0Chunk - sparse
      default:
        throw H2O.unimpl();
    }
  }

  void raw()
  {
    long start = 0;
    double sum = 0;
    for (int r = 0; r < rep; ++r) {
      if (r==rep/10)
        start = System.currentTimeMillis();
      for (int j=0; j<cols; ++j) {
        for (int i = 0; i < rows; ++i) {
          sum += raw[j][i];
        }
      }
    }
    long done = System.currentTimeMillis();
    Log.info("Sum: " + sum);
    Log.info("Data size: " + PrettyPrint.bytes(rows * cols * 8));
    Log.info("Time for RAW double[]: " + PrettyPrint.msecs(done - start, true));
    Log.info("");
  }

  void rawInverted()
  {
    long start = 0;
    double sum = 0;
    for (int r = 0; r < rep; ++r) {
      if (r==rep/10)
        start = System.currentTimeMillis();
      for (int i = 0; i < rows; ++i) {
        for (int j=0; j<cols; ++j) {
          sum += raw[j][i];
        }
      }
    }
    long done = System.currentTimeMillis();
    Log.info("Sum: " + sum);
    Log.info("Data size: " + PrettyPrint.bytes(rows * cols * 8));
    Log.info("Time for INVERTED RAW double[]: " + PrettyPrint.msecs(done - start, true));
    Log.info("");
  }

  double walkChunk(final Chunk c) {
    double sum =0;
    for (int i = 0; i < rows; ++i) {
      sum += c.atd(i);
    }
    return sum;
  }
  double walkChunkBulk(final Chunk c, double [] vals) {
    double sum =0;
    c.getDoubles(vals,0,c._len);
    for (int i = 0; i < rows; ++i)
      sum += vals[i];
    return sum;
  }

  double walkChunkParts(final Chunk c, double [] vals) {
    double sum =0;
    int from = 0;
    while(from != c._len) {
      int to = Math.min(c._len,from+vals.length);
      int n = to - from;
      c.getDoubles(vals,from,to);
      for (int i = 0; i < n; ++i)
        sum += vals[i];
      from = to;
    }
    return sum;
  }



  double loop() {
    double sum =0;
    for (int j=0; j<cols; ++j) {
      sum += walkChunk(chunks[j]);
    }
    return sum;
  }

  double loop_bulk() {
    double sum =0;
    double [] vals = new double[chunks[0]._len];
    for (int j=0; j<cols; ++j) {
      sum += walkChunkBulk(chunks[j],vals);
    }
    return sum;
  }

  private static class ChunkSum extends ChunkVisitor {
    double sum;
    public void addZeros(int n){}
    public void addValue(double d){sum += d;}
    public void addValue(long l){sum += l;}
    public void addValue(int i){sum += i;}
  }
  double loop_visitor(){
    ChunkSum viz = new ChunkSum();
    for (int j=0; j<cols; ++j)
      chunks[j].processRows(viz,0,chunks[j].len());
    return viz.sum;
  }
  double loop_parts() {
    double sum =0;
    double [] vals = new double[16];
    for (int j=0; j<cols; ++j) {
      sum += walkChunkParts(chunks[j],vals);
    }
    return sum;
  }

  void chunksInline()
  {
    long start = 0;
    double sum = 0;
    for (int r = 0; r < rep; ++r) {
      if (r==rep/10)
        start = System.currentTimeMillis();
      for (int j=0; j<cols; ++j) {
        for (int i = 0; i < rows; ++i) {
          sum += chunks[j].atd(i);
        }
      }
    }
    long done = System.currentTimeMillis();
    Log.info("Sum: " + sum);
    long siz = 0;
    for (int j=0; j<cols; ++j) {
      siz += chunks[j].byteSize();
    }
    Log.info("Data size: " + PrettyPrint.bytes(siz));
    Log.info("Time for INLINE chunks atd(): " + PrettyPrint.msecs(done - start, true));
    Log.info("");
  }

  void chunks()
  {
    long start = 0;
    double sum = 0;
    for (int r = 0; r < rep; ++r) {
      if (r==rep/10)
        start = System.currentTimeMillis();
      sum += loop();
    }
    long done = System.currentTimeMillis();
    Log.info("Sum: " + sum);
    long siz = 0;
    for (int j=0; j<cols; ++j) {
      siz += chunks[j].byteSize();
    }
    Log.info("Data size: " + PrettyPrint.bytes(siz));
    Log.info("Time for METHODS chunks atd(): " + PrettyPrint.msecs(done - start, true));
    Log.info("");
  }

  void chunks_bulk()
  {
    long start = 0;
    double sum = 0;
    for (int r = 0; r < rep; ++r) {
      if (r==rep/10)
        start = System.currentTimeMillis();
      sum += loop_bulk();
    }
    long done = System.currentTimeMillis();
    Log.info("Sum: " + sum);
    long siz = 0;
    for (int j=0; j<cols; ++j) {
      siz += chunks[j].byteSize();
    }
    Log.info("Data size: " + PrettyPrint.bytes(siz));
    Log.info("Time for METHODS chunks getDoubles(): " + PrettyPrint.msecs(done - start, true));
    Log.info("");
  }
  void chunks_part()
  {
    long start = 0;
    double sum = 0;
    for (int r = 0; r < rep; ++r) {
      if (r==rep/10)
        start = System.currentTimeMillis();
      sum += loop_parts();
    }
    long done = System.currentTimeMillis();
    Log.info("Sum: " + sum);
    long siz = 0;
    for (int j=0; j<cols; ++j) {
      siz += chunks[j].byteSize();
    }
    Log.info("Data size: " + PrettyPrint.bytes(siz));
    Log.info("Time for METHODS chunks PARTS(): " + PrettyPrint.msecs(done - start, true));
    Log.info("");
  }
  void chunks_visitor()
  {
    long start = 0;
    double sum = 0;
    for (int r = 0; r < rep; ++r) {
      if (r==rep/10)
        start = System.currentTimeMillis();
      sum += loop_visitor();
    }
    long done = System.currentTimeMillis();
    Log.info("Sum: " + sum);
    long siz = 0;
    for (int j=0; j<cols; ++j) {
      siz += chunks[j].byteSize();
    }
    Log.info("Data size: " + PrettyPrint.bytes(siz));
    Log.info("Time for METHODS chunks Visitor(): " + PrettyPrint.msecs(done - start, true));
    Log.info("");
  }
  void chunksInverted()
  {
    long start = 0;
    double sum = 0;
    for (int r = 0; r < rep; ++r) {
      if (r==rep/10)
        start = System.currentTimeMillis();
      for (int i = 0; i < rows; ++i) {
        for (int j=0; j<cols; ++j) {
          sum += chunks[j].atd(i);
        }
      }
    }
    long done = System.currentTimeMillis();
    Log.info("Sum: " + sum);
    long siz = 0;
    for (int j=0; j<cols; ++j) {
      siz += chunks[j].byteSize();
    }
    Log.info("Data size: " + PrettyPrint.bytes(siz));
    Log.info("Time for INVERTED INLINE chunks atd(): " + PrettyPrint.msecs(done - start, true));
    Log.info("");
  }

  class FillTask extends MRTask<FillTask> {
    @Override
    public void map(Chunk[] cs) {
      for (int col=0; col<cs.length; ++col) {
        for (int row=0; row<cs[0]._len; ++row) {
          cs[col].set(row, raw[col][row]);
        }
      }
    }
  }

  static class SumTask extends MRTask<SumTask> {
    double _sum;
    @Override
    public void map(Chunk[] cs) {
      for (int col=0; col<cs.length; ++col) {
        for (int row=0; row<cs[0]._len; ++row) {
          _sum += cs[col].atd(row);
        }
      }
    }
    @Override
    public void reduce(SumTask other) {
      _sum += other._sum;
    }
  }

  void mrtask(boolean parallel)
  {
    long start = 0;
    double sum = 0;
    Frame fr = new Frame();
    for (int i=0; i<cols; ++i) {
      if (parallel)
        fr.add("C" + i, Vec.makeCon(0, rows)); //multi-chunk (based on #cores)
      else
        fr.add("C"+i, Vec.makeVec(raw[i], Vec.newKey())); //directly fill from raw double array (1 chunk)
    }
    if (parallel) new FillTask().doAll(fr);

    for (int r = 0; r < rep; ++r) {
      if (r==rep/10)
        start = System.currentTimeMillis();
      sum += new SumTask().doAll(fr)._sum;
    }
    long done = System.currentTimeMillis();
    Log.info("Sum: " + sum);
    long siz = 0;
    siz += fr.byteSize();
    Log.info("Data size: " + PrettyPrint.bytes(siz));
    Log.info("Time for " + (parallel ? "PARALLEL":"SERIAL") + " MRTask: " + PrettyPrint.msecs(done - start, true));
    Log.info("");
    fr.delete();
  }

  void rollups(boolean parallel)
  {
    Frame fr = new Frame();
//    Vec v = Vec.makeCon(Double.NaN, rows);
//    Log.info(v.mean());
//    Log.info(v.sigma());
//    Log.info(v.min());
//    Log.info(v.max());
//    Log.info(v.length());
//    Log.info(v.nzCnt());
//    Log.info(v.naCnt());
//    v.remove();
    for (int i=0; i<cols; ++i)
      fr.add("C" + i, Vec.makeCon(0, rows, parallel)); //multi-chunk (based on #cores)
    new FillTask().doAll(fr);

    long start = System.currentTimeMillis();
    for (int r = 0; r < rep; ++r) {
      for (int i=0; i<cols; ++i) {
        DKV.remove(fr.vec(i).rollupStatsKey());
        fr.vec(i).mean();
      }
    }
    long done = System.currentTimeMillis();
    long siz = 0;
    siz += fr.byteSize();
    Log.info("Data size: " + PrettyPrint.bytes(siz));
    Log.info("Time for " + (parallel ? "PARALLEL":"SERIAL") + " Rollups: " + PrettyPrint.msecs(done - start, true));
    Log.info("");
    fr.remove();
  }

  public static void main(String[] args) {
    setup();
    new ChunkSpeedTest().run();
  }
}

