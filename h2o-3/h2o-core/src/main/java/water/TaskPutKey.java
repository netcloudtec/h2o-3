package water;

/** Push the given key to the remote node
 *  @author <a href="mailto:cliffc@h2o.ai"></a>
 *  @version 1.0
 */
public class TaskPutKey extends DTask<TaskPutKey> {
  Key _key;
  Value _val;
  boolean _dontCache; // delete cached value on the sender's side?
  transient Value _xval;
  transient Key _xkey;

  static void put( H2ONode h2o, Key key, Value val, Futures fs, boolean dontCache) {
    fs.add(RPC.call(h2o,new TaskPutKey(key,val,dontCache)));
  }

  protected TaskPutKey( Key key, Value val ) { this(key,val,false);}
  protected TaskPutKey( Key key, Value val, boolean removeCache ) { super(H2O.PUT_KEY_PRIORITY); _xkey = _key = key; _xval = _val = val; _dontCache = removeCache;}
  protected TaskPutKey( Key key ) { super(H2O.INVALIDATE_PRIORITY); _xkey = _key = key; _xval = _val = null; _dontCache = false;}

  @Override public void dinvoke( H2ONode sender ) {
    assert _key.home() || _val==null; // Only PUT to home for keys, or remote invalidation from home
    Paxos.lockCloud(_key);
    // Initialize Value for having a single known replica (the sender)
    if( _val != null ) _val.initReplicaHome(sender,_key);
    else if( _key.home() ) _val = Value.makeNull(_key);
    // Spin, until we update something.
    Value old = H2O.STORE.get(_key); // Raw-get: do not lazy-manifest if overwriting
    while( H2O.putIfMatch(_key,_val,old) != old )
      old = H2O.STORE.get(_key);  // Repeat until we update something.
    // Invalidate remote caches.  Block, so that all invalidates are done
    // before we return to the remote caller.  This is conservative, but
    // otherwise we have to send the invalidate-completion message to the
    // remote caller; i.e. the caller would have to handle a 2-step Put
    // completion ("I started your Put request" and "I completed your Put
    // request").
    if( _key.home() ) {
      if( old != null ) old.lockAndInvalidate(sender,_val,new Futures()).blockForPending();
      else _val.lowerActiveGetCount(null);  // Remove initial read-lock, accounting for pending inv counts
    }
    // No return result
    _key = null;
    _val = null;
    tryComplete();
  }
  @Override public void compute2() { throw H2O.fail(); }

  // Received an ACK
  @Override public void onAck() {
    // remove local cache but NOT in case it is already on disk
    // (ie memory can be reclaimed and we assume we have plenty of disk space)
    if( _dontCache && !_xval.isPersisted() ) H2O.putIfMatch(_xkey, null, _xval);
    if( _xval != null ) _xval.completeRemotePut();
  }
}
