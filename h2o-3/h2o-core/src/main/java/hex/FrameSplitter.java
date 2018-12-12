package hex;

import java.util.Arrays;

import jsr166y.CountedCompleter;
import water.*;
import water.H2O.H2OCountedCompleter;
import water.fvec.*;

/**
 * Frame splitter function to divide given frame into
 * multiple partitions based on given ratios.
 *
 * <p>The task creates <code>ratios.length+1</code> output frame each containing a
 * demanded fraction of rows from source dataset</p>
 *
 * <p>The tasks internally extract data from source chunks and create output chunks in preserving order of parts.
 * I.e., the 1st partition contains the first P1-rows, the 2nd partition contains following P2-rows, ...
 * </p>
 *
 * <p>Assumptions and invariants</p>
 * <ul>
 * <li>number of demanding split parts is reasonable number, i.e., &lt;10. The task is not designed to split into many small parts.</li>
 * <li>the worker DOES NOT preserves distribution of new chunks over the cloud according to source dataset chunks.</li>
 * <li>rows inside one output chunk are not shuffled, they are extracted deterministically in the same order as they appear in source chunk.</li>
 * <li>workers can enforce data transfers if they need to obtain data from remote chunks.</li>
 * </ul>
 *
 * <p>NOTE: the implementation is data-transfer expensive and in some cases it would be beneficial to use original
 * implementation from <a href="https://github.com/0xdata/h2o/commits/9af3f4e">9af3f4e</a>.</p>.
 */
public class FrameSplitter extends H2OCountedCompleter<FrameSplitter> {
  /** Dataset to split */
  final Frame   dataset;
  /** Split ratios - resulting number of split is ratios.length+1 */
  final double[] ratios;
  /** Destination keys for each output frame split. */
  final Key<Frame>[]   destKeys;
  /** Optional job key */
  final Key<Job>     jobKey;

  /** Output frames for each output split part */
  private Frame[] splits;

  public FrameSplitter(Frame dataset, double[] ratios, Key<Frame>[] destKeys, Key<Job> jobKey) {
    this(null, dataset, ratios,destKeys,jobKey);
  }
  public FrameSplitter(H2OCountedCompleter cc, Frame dataset, double[] ratios, Key<Frame>[] destKeys, Key<Job> jobKey) {
    super(cc);
    assert ratios.length > 0 : "No ratio specified!";
    assert ratios.length < 100 : "Too many frame splits demanded!";
    assert destKeys!=null : "Destination keys are not specified!";
    assert destKeys.length == ratios.length+1 : "Unexpected number of destination keys.";
    this.dataset  = dataset;
    this.ratios   = ratios;
    this.jobKey   = jobKey;
    this.destKeys = destKeys;
  }

  @Override public void compute2() {
    // Lock all possible data
    dataset.read_lock(jobKey);

    // Create a template vector for each segment
    final Vec[][] templates = makeTemplates(dataset, ratios);

    final int nsplits = templates.length;
    assert nsplits == ratios.length+1 : "Unexpected number of split templates!";
    // Launch number of distributed FJ for each split part
    final Vec[] datasetVecs = dataset.vecs();
    splits = new Frame[nsplits];
    for (int s=0; s<nsplits; s++) {
      Frame split = new Frame(destKeys[s], dataset.names(), templates[s] );
      split.delete_and_lock(jobKey);
      splits[s] = split;
    }
    setPendingCount(nsplits);
    for (int s=0; s<nsplits; s++)
      new FrameSplitTask(this,datasetVecs, ratios, s).dfork(splits[s]);
    tryComplete(); // complete the computation of thrown tasks
  }

  /** Blocking call to obtain a result of computation. */
  public Frame[] getResult() {
    join();
    return splits;
  }

  @Override public void onCompletion(CountedCompleter caller) {
    dataset.unlock(jobKey);
    if (splits!=null)
      for (Frame s : splits)
        if (s!=null)
          s.update(jobKey).unlock(jobKey);
  }
  @Override public boolean onExceptionalCompletion(Throwable ex, CountedCompleter caller) {
    dataset.unlock(jobKey);
    Futures fs = new Futures();
    if (splits!=null)
      for (Frame s : splits)
        if (s!=null)
          s.unlock(jobKey).delete(jobKey,fs);
    fs.blockForPending();
    return true;
  }

  // Make vector templates for all output frame vectors
  private Vec[][] makeTemplates(Frame dataset, double[] ratios) {
    Vec anyVec = dataset.anyVec();
    final long[][] espcPerSplit = computeEspcPerSplit(anyVec.espc(), anyVec.length(), ratios);
    final int num = dataset.numCols(); // number of columns in input frame
    final int nsplits = espcPerSplit.length; // number of splits
    final String[][] domains = dataset.domains(); // domains
    final byte[] types = new byte[num];
    int j=0;
    for (Vec v : dataset.vecs()) types[j++] = v.get_type();

    Vec[][] t = new Vec[nsplits][/*num*/]; // resulting vectors for all
    for (int i=0; i<nsplits; i++) {
      // vectors for j-th split
      Key vkey = Vec.newKey();
      int rowLayout = Vec.ESPC.rowLayout(vkey,espcPerSplit[i]);
      t[i] = new Vec(vkey,rowLayout).makeCons(num, 0, domains, types);
    }
    return t;
  }

  // The task computes ESPC per split
  static long[/*nsplits*/][/*nchunks*/] computeEspcPerSplit(long[] espc, long len, double[] ratios) {
    assert espc.length>0 && espc[0] == 0;
    assert espc[espc.length-1] == len;
    long[] partSizes = partitione(len, ratios); // Split of whole vector
    int nparts = ratios.length+1;
    long[][] r = new long[nparts][espc.length]; // espc for each partition
    long nrows = 0;
    long start = 0;
    for (int p=0,c=0; p<nparts; p++) {
      int nc = 0; // number of chunks for this partition
      for(;c<espc.length-1 && (espc[c+1]-start) <= partSizes[p];c++) r[p][++nc] = espc[c+1]-start;
      if (r[p][nc] < partSizes[p]) r[p][++nc] = partSizes[p]; // last item in espc contains number of rows
      r[p] = Arrays.copyOf(r[p], nc+1);
      // Transfer rest of lines to the next part
      nrows = nrows-partSizes[p];
      start += partSizes[p];
    }
    return r;
  }


  /** MR task extract specified part of <code>_srcVecs</code>
   * into output chunk.*/
  private static class FrameSplitTask extends MRTask<FrameSplitTask> {
    final Vec  [] _srcVecs; // a source frame given by list of its columns
    final double[] _ratios;  // split ratios
    final int     _partIdx; // part index

    transient int _pcidx; // Start chunk index for this partition
    transient int _psrow; // Start row in chunk for this partition

    public FrameSplitTask(H2OCountedCompleter completer, Vec[] srcVecs, double[] ratios, int partIdx) {
      super(completer);
      _srcVecs = srcVecs;
      _ratios  = ratios;
      _partIdx = partIdx;
    }
    @Override protected void setupLocal() {
      // Precompute the first input chunk index and start row inside that chunk for this partition
      Vec anyInVec = _srcVecs[0];
      long[] partSizes = partitione(anyInVec.length(), _ratios);
      long pnrows = 0;
      for (int p=0; p<_partIdx; p++) pnrows += partSizes[p];
      long[] espc = anyInVec.espc();
      while (_pcidx < espc.length-1 && (pnrows -= (espc[_pcidx+1]-espc[_pcidx])) >= 0 ) _pcidx++;
      assert pnrows <= 0;
      _psrow = (int) (pnrows + espc[_pcidx+1]-espc[_pcidx]);
    }
    @Override public void map(Chunk[] cs) { // Output chunks
      int coutidx = cs[0].cidx(); // Index of output Chunk
      int cinidx = _pcidx + coutidx;
      int startRow = coutidx > 0 ? 0 : _psrow; // where to start extracting
      int nrows = cs[0]._len;
      // For each output chunk extract appropriate rows for partIdx-th part
      for (int i=0; i<cs.length; i++) {
        // WARNING: this implementation does not preserve co-location of chunks so we are forcing here network transfer!
        ChunkSplitter.extractChunkPart(_srcVecs[i].chunkForChunkIdx(cinidx), cs[i], startRow, nrows, _fs);
      }
    }
  }
  static final long[] partitione(long len, double[] ratio) {
    long[] r = new long[ratio.length+1];
    long sum = 0;
    int i = 0;
    float sr = 0;
    for (i=0; i<ratio.length; i++) {
      r[i] = (int) (ratio[i]*len);
      sum += r[i];
      sr  += ratio[i];
    }
    if (sr<1f) r[i] = len - sum;
    else r[i-1] += (len-sum);
    return r;
  }
}
