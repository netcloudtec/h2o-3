package water.fvec;

import water.*;
import water.util.Log;
import water.util.MathUtils;
import water.util.UnsafeUtils;

public abstract class FileVec extends ByteVec {
  long _len;                    // File length
  final byte _be;

  // Returns String with path for given key.
  public static String getPathForKey(Key k) {
    final int off = k._kb[0]==Key.CHK   || k._kb[0]==Key.VEC ? Vec.KEY_PREFIX_LEN : 0;
    String p = new String(k._kb,off,k._kb.length-off);

    if(p.startsWith("nfs:/"))
      p = p.substring("nfs:/".length());
    else if (p.startsWith("nfs:\\"))
      p = p.substring("nfs:\\".length());

    return p;
  }
  /** Log-2 of Chunk size. */
  public static final int DFLT_LOG2_CHUNK_SIZE = 20/*1Meg*/+2/*4Meg*/;
  /** Default Chunk size in bytes, useful when breaking up large arrays into
   *  "bite-sized" chunks.  Bigger increases batch sizes, lowers overhead
   *  costs, lower increases fine-grained parallelism. */
  public static final int DFLT_CHUNK_SIZE = 1 << DFLT_LOG2_CHUNK_SIZE;
  public int _chunkSize = DFLT_CHUNK_SIZE;
  public int _nChunks = -1;

  protected FileVec(Key key, long len, byte be) {
    super(key,-1/*no rowLayout*/);
    _len = len;
    _be = be;
  }
  public void setNChunks(int n){
    _nChunks = n;
    setChunkSize((int)length()/n);
  }
  /**
   * Chunk size must be positive, 1G or less, and a power of two.
   * Any values that aren't a power of two will be reduced to the
   * first power of two lower than the provided chunkSize.
   * <p>
   * Since, optimal chunk size is not known during FileVec instantiation,
   * setter is required to both set it, and keep it in sync with
   * _log2ChkSize.
   * </p>
   * @param chunkSize requested chunk size to be used when parsing
   * @return actual _chunkSize setting
   */
  public int setChunkSize(int chunkSize) { return setChunkSize(null, chunkSize); }

  public int setChunkSize(Frame fr, int chunkSize) {
    // Clear cached chunks first
    // Peeking into a file before the chunkSize has been set
    // will load chunks of the file in DFLT_CHUNK_SIZE amounts.
    // If this side-effect is not reversed when _chunkSize differs
    // from the default value, parsing will either double read
    // sections (_chunkSize < DFLT_CHUNK_SIZE) or skip data
    // (_chunkSize > DFLT_CHUNK_SIZE). This reverses this side-effect.
    Futures fs = new Futures();
    Keyed.remove(_key, fs);
    fs.blockForPending();
    if (chunkSize <= 0) throw new IllegalArgumentException("Chunk sizes must be > 0.");
    if (chunkSize > (1<<30) ) throw new IllegalArgumentException("Chunk sizes must be < 1G.");
    _chunkSize = chunkSize;
    //Now reset the chunk size on each node
    fs = new Futures();
    DKV.put(_key, this, fs);
    // also update Frame to invalidate local caches
    if (fr != null ) {
      fr.reloadVecs();
      DKV.put(fr._key, fr, fs);
    }
    fs.blockForPending();
    return _chunkSize;
  }

  @Override public long length() { return _len; }


  @Override public int nChunks() {
    if(_nChunks != -1) // number of chunks can be set explicitly
      return _nChunks;
    return (int)Math.max(1,_len / _chunkSize + ((_len % _chunkSize != 0)?1:0));
  }
  @Override public int nonEmptyChunks() {
    return nChunks();
  }
  @Override public boolean writable() { return false; }

  /** Size of vector data. */
  @Override public long byteSize(){return length(); }

  // Convert a row# to a chunk#.  For constant-sized chunks this is a little
  // shift-and-add math.  For variable-sized chunks this is a binary search,
  // with a sane API (JDK has an insane API).
  @Override
  public int elem2ChunkIdx(long i) {
    assert 0 <= i && i <= _len : " "+i+" < "+_len;
    int cidx = (int)(i/_chunkSize);
    int nc = nChunks();
    if( i >= _len ) return nc;
    if( cidx >= nc ) cidx=nc-1; // Last chunk is larger
    assert 0 <= cidx && cidx < nc;
    return cidx;
  }
  // Convert a chunk-index into a starting row #. Constant sized chunks
  // (except for the last, which might be a little larger), and size-1 rows so
  // this is a little shift-n-add math.
  @Override long chunk2StartElem( int cidx ) { return (long)cidx*_chunkSize; }

  /** Convert a chunk-key to a file offset. Size 1-byte "rows", so this is a
   *  direct conversion.
   *  @return The file offset corresponding to this Chunk index */
  public static long chunkOffset ( Key ckey ) { return (long)chunkIdx(ckey)*((FileVec)Vec.getVecKey(ckey).get())._chunkSize; }
  // Reverse: convert a chunk-key into a cidx
  static int chunkIdx(Key ckey) { assert ckey._kb[0]==Key.CHK; return UnsafeUtils.get4(ckey._kb, 1 + 1 + 4); }

  // Convert a chunk# into a chunk - does lazy-chunk creation. As chunks are
  // asked-for the first time, we make the Key and an empty backing DVec.
  // Touching the DVec will force the file load.
  @Override public Value chunkIdx( int cidx ) {
    final long nchk = nChunks();
    assert 0 <= cidx && cidx < nchk;
    Key dkey = chunkKey(cidx);
    Value val1 = DKV.get(dkey);// Check for an existing one... will fetch data as needed
    if( val1 != null ) return val1; // Found an existing one?
    // Lazily create a DVec for this chunk
    int len = (int)(cidx < nchk-1 ? _chunkSize : (_len-chunk2StartElem(cidx)));
    // DVec is just the raw file data with a null-compression scheme
    Value val2 = new Value(dkey,len,null,TypeMap.C1NCHUNK,_be);
    val2.setDsk(); // It is already on disk.
    // If not-home, then block till the Key is everywhere.  Most calls here are
    // from the parser loading a text file, and the parser splits the work such
    // that most puts here are on home - so this is a simple speed optimization:
    // do not make a Futures nor block on it on home.
    Futures fs = dkey.home() ? null : new Futures();
    // Atomically insert: fails on a race, but then return the old version
    Value val3 = DKV.DputIfMatch(dkey,val2,null,fs);
    if( !dkey.home() && fs != null ) fs.blockForPending();
    return val3 == null ? val2 : val3;
  }

  /**
   * Calculates safe and hopefully optimal chunk sizes.  Four cases
   * exist.
   * <p>
   * very small data < 64K per core - uses default chunk size and
   * all data will be in one chunk
   * <p>
   * small data - data is partitioned into chunks that at least
   * 4 chunks per core to help keep all cores loaded
   * <p>
   * default - chunks are {@value #DFLT_CHUNK_SIZE}
   * <p>
   * large data - if the data would create more than 2M keys per
   * node, then chunk sizes larger than DFLT_CHUNK_SIZE are issued.
   * <p>
   * Too many keys can create enough overhead to blow out memory in
   * large data parsing. # keys = (parseSize / chunkSize) * numCols.
   * Key limit of 2M is a guessed "reasonable" number.
   *
   * @param totalSize - parse size in bytes (across all files to be parsed)
   * @param numCols - number of columns expected in dataset
   * @param cores - number of processing cores per node
   * @param cloudsize - number of compute nodes
   * @param verbose - print the parse heuristics
   * @return - optimal chunk size in bytes (always a power of 2).
   */
  public static int calcOptimalChunkSize(long totalSize, int numCols, long maxLineLength, int cores, int cloudsize,
                                         boolean oldHeuristic, boolean verbose) {
    long localParseSize = (long) (double) totalSize / cloudsize;

    if (oldHeuristic) {
      long chunkSize = (localParseSize / (cores * 4));
      // Super small data check - less than 64K/thread
      if (chunkSize <= (1 << 16)) {
        return DFLT_CHUNK_SIZE;
      }
      // Small data check
      chunkSize = 1L << MathUtils.log2(chunkSize); //closest power of 2
      if (chunkSize < DFLT_CHUNK_SIZE
              && (localParseSize/chunkSize)*numCols < (1 << 21)) { // ignore if col cnt is high
        return (int)chunkSize;
      }
      // Big data check
      long tmp = (localParseSize * numCols / (1 << 21)); // ~ 2M keys per node
      if (tmp > (1 << 30)) return (1 << 30); // Max limit is 1G
      if (tmp > DFLT_CHUNK_SIZE) {
        chunkSize = 1 << MathUtils.log2((int) tmp); //closest power of 2
        return (int)chunkSize;
      } else return DFLT_CHUNK_SIZE;
    }
    else {
      // New Heuristic
      int minNumberRows = 10; // need at least 10 rows (lines) per chunk (core)
      int perNodeChunkCountLimit = 1<<21; // don't create more than 2M Chunk POJOs per node
      int minParseChunkSize = 1<<12; // don't read less than this many bytes
      int maxParseChunkSize = (1<<28)-1; // don't read more than this many bytes per map() thread (needs to fit into a Value object)
      long chunkSize = Math.max((localParseSize / (4*cores))+1, minParseChunkSize); //lower hard limit
      if(chunkSize > 1024*1024)
        chunkSize = (chunkSize & 0xFFFFFE00) + 512; // align chunk size to 512B

      // Super small data check - file size is smaller than 64kB
      if (totalSize <= 1<<16) {
        chunkSize = Math.max(DFLT_CHUNK_SIZE, (int) (minNumberRows * maxLineLength));
      } else {

        //round down to closest power of 2
//        chunkSize = 1L << MathUtils.log2(chunkSize);

        // Small data check
        if (chunkSize < DFLT_CHUNK_SIZE && (localParseSize / chunkSize) * numCols < perNodeChunkCountLimit) {
          chunkSize = Math.max((int)chunkSize, (int) (minNumberRows * maxLineLength));
        } else {
          // Adjust chunkSize such that we don't create too many chunks
          int chunkCount = cores * 4 * numCols;
          if (chunkCount > perNodeChunkCountLimit) {
            double ratio = 1 << Math.max(2, MathUtils.log2((int) (double) chunkCount / perNodeChunkCountLimit)); //this times too many chunks globally on the cluster
            chunkSize *= ratio; //need to bite off larger chunks
          }
          chunkSize = Math.min(maxParseChunkSize, chunkSize); // hard upper limit
          // if we can read at least minNumberRows and we don't create too large Chunk POJOs, we're done
          // else, fix it with a catch-all heuristic
          if (chunkSize <= minNumberRows * maxLineLength) {
            // might be more than default, if the max line length needs it, but no more than the size limit(s)
            // also, don't ever create too large chunks
            chunkSize = (int) Math.max(
                DFLT_CHUNK_SIZE,  //default chunk size is a good lower limit for big data
                Math.min(maxParseChunkSize, minNumberRows * maxLineLength) //don't read more than 1GB, but enough to read the minimum number of rows
            );
          }
        }
      }
      assert(chunkSize >= minParseChunkSize);
      assert(chunkSize <= maxParseChunkSize);
      if (verbose)
        Log.info("ParseSetup heuristic: "
          + "cloudSize: " + cloudsize
          + ", cores: " + cores
          + ", numCols: " + numCols
          + ", maxLineLength: " + maxLineLength
          + ", totalSize: " + totalSize
          + ", localParseSize: " + localParseSize
          + ", chunkSize: " + chunkSize
          + ", numChunks: " + Math.max(1,totalSize/chunkSize)
          + ", numChunks * cols: " + (Math.max(1,totalSize/chunkSize) * numCols)
      );
      return (int)chunkSize;
    }
  }
}
