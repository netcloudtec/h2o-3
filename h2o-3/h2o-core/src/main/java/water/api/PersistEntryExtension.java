//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package water.api;

import water.persist.Persist.PersistEntry;

public class PersistEntryExtension extends PersistEntry {
    public final String _power;
    public final String _group;
    public final String _user;
    public final Integer _isFile;

    public PersistEntryExtension(String name, long size, long timestamp) {
        super(name, size, timestamp);
        this._power = null;
        this._group = null;
        this._user = null;
        this._isFile = null;
    }

    public PersistEntryExtension(String name, long size, long timestamp, String user, String group, String power, boolean isFile) {
        super(name, size, timestamp);
        this._power = power;
        this._group = group;
        this._user = user;
        this._isFile = isFile ? 1 : 0;
    }
}
