package water;

import java.util.Arrays;
import water.init.JarHash;

/**
 * Struct holding H2ONode health info.
 * @author <a href="mailto:cliffc@h2o.ai"></a>
 */
public class HeartBeat extends Iced<HeartBeat> {
  char _hb_version;             // Incrementing counter for sorting timelines better.
  int _cloud_hash;              // Cloud-membership hash
  int _cloud_name_hash;         // Hash of this cloud's name
  boolean _common_knowledge;    // Cloud shares common knowledge
  char _cloud_size;             // Cloud-size this guy is reporting
  long _jvm_boot_msec;          // Boot time of JVM
  public long jvmBootTimeMsec(){return _jvm_boot_msec;}
  byte[] _jar_md5;              // JAR file digest

  public boolean _client;       // This is a client node: no keys homed here
  public boolean _watchdog_client = false; // Special client mode - kill cluster when client disappears


  public int _pid;              // Process ID

  // Static cpus & threads
  public short _num_cpus;        // Number of CPUs on this Node
  public short _cpus_allowed;    // Number of CPUs allowed by process
  public short _nthreads;        // Number of threads allowed by cmd line

  // Dynamic resource usage: ticks, files
  public float _system_load_average;
  public long _system_idle_ticks;
  public long _system_total_ticks;
  public long _process_total_ticks;
  public int _process_num_open_fds;

  // Memory & Disk scaled by K or by M setters & getters.

  // Sum of KV + POJO + FREE == MEM_MAX (heap set at JVM launch)
  private long _kv_mem;          // Memory used by K/V as of last FullGC
  private long _pojo_mem;        // POJO used as of last FullGC
  private long _free_mem;        // Free memory as of last FullGC
  private long _swap_mem;        // Swapped K/V as of last FullGC
  void   set_kv_mem (long n) { _kv_mem = n; }
  void set_pojo_mem (long n) { _pojo_mem = n; }
  void set_free_mem (long n) { _free_mem = n; }
  void set_swap_mem (long n) { _swap_mem = n; }
  public long get_kv_mem  () { return _kv_mem; }
  public long get_pojo_mem() { return _pojo_mem; }
  public long get_free_mem() { return _free_mem; }
  public long get_swap_mem() { return _swap_mem; }

  public int _keys;       // Number of LOCAL keys in this node, cached or homed

  int _free_disk;        // Free disk (internally stored in megabyte precision)
  void set_free_disk(long n) { _free_disk = (int)(n>>20); }
  public long get_free_disk()  { return ((long)_free_disk)<<20 ; }
  int _max_disk;         // Disk size (internally stored in megabyte precision)
  void set_max_disk (long n) {  _max_disk = (int)(n>>20); }
  public long get_max_disk ()  { return  ((long)_max_disk)<<20 ; }

  boolean check_jar_md5() {
    return H2O.ARGS.md5skip || Arrays.equals(JarHash.JARHASH, _jar_md5);
  }

  // Internal profiling
  public float _gflops = Float.NaN;         // Number of GFlops for this node
  public float _membw;          // Memory bandwidth in GB/s

  // Number of elements & threads in high FJ work queues
  public char _rpcs;            // Outstanding RemoteProcedureCalls
  public short _fjthrds[];      // Number of threads (not all are runnable)
  public short _fjqueue[];      // Number of elements in FJ work queue
  public char _tcps_active;     // Threads trying do a TCP send
}
