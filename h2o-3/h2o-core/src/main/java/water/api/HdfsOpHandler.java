//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package water.api;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

import water.H2O;
import water.api.schemas3.FilePropertiesSchema;
import water.api.schemas3.HdfsOpV3;
import water.persist.Persist.PersistEntry;

public class HdfsOpHandler extends Handler {
    public HdfsOpHandler() {
    }

    public HdfsOpV3 list(int version, HdfsOpV3 t) {
        PersistEntry[] arr = H2O.getPM().list(t.src);
        FilePropertiesSchema[] filePropertySchemas = new FilePropertiesSchema[arr.length];

        for (int i = 0; i < arr.length; ++i) {
            FilePropertiesSchema file = new FilePropertiesSchema();
            PersistEntry persistEntry = arr[i];
            file.name = persistEntry._name;
            file.size = persistEntry._size;
            file.time = persistEntry._timestamp_millis;
            if (persistEntry instanceof PersistEntryExtension) {
                PersistEntryExtension persistEntry_tmp = (PersistEntryExtension) persistEntry;
                file.isfile = persistEntry_tmp._isFile + "";
                file.group = persistEntry_tmp._group;
                file.user = persistEntry_tmp._user;
                file.power = persistEntry_tmp._power;
            } else {
                String path = persistEntry._name;
                if (!t.src.endsWith("/") && !t.src.endsWith("\\")) {
                    path = File.separator + persistEntry._name;
                }

                file.isfile = (new File(t.src + path)).isFile() ? "1" : "0";
            }

            filePropertySchemas[i] = file;
        }

        t.files = filePropertySchemas;
        return t;
    }

    public HdfsOpV3 read(int version, HdfsOpV3 t) {
        try {
            InputStream is = H2O.getPM().open(t.src);
            if (is != null) {
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                StringBuilder sb = new StringBuilder();
                br.skip((long) ((t.page - 1) * t.size));
                char[] cs = new char[t.size];
                br.read(cs);
                sb.append(cs);
                t.content = sb.toString();
            }
        } catch (IOException var8) {

            ;
        }

        return t;
    }

    public HdfsOpV3 delete(int version, HdfsOpV3 t) {
        boolean f = H2O.getPM().delete(t.src);
        t.is_delete = f ? 0 : 1;
        return t;
    }

    public HdfsOpV3 put(int version, HdfsOpV3 t) throws IOException {
        boolean b = H2O.getPM().exists(t.src);
        if (b) {
            t.flag = false;
            t.msg = "文件已经存在，请修改名称！";
            return t;
        } else {
            boolean lb = H2O.getPM().exists(t.local);
            if (!lb) {
                t.flag = false;
                t.msg = "上传的数据源文件不存在，请检查后再上传！";
                return t;
            } else {
                boolean isd = H2O.getPM().isDirectory(t.local);
                if (isd) {
                    t.flag = false;
                    t.msg = "只能上传文件！";
                    return t;
                } else {
                    OutputStream out = H2O.getPM().create(t.src, false);
                    InputStream in = H2O.getPM().open(t.local);
                    byte[] buf = new byte[10240];
                    boolean var9 = false;

                    int len;
                    while ((len = in.read(buf)) != -1) {
                        out.write(buf, 0, len);
                        out.flush();
                    }

                    in.close();
                    out.close();
                    t.flag = true;
                    t.msg = "文件上传成功！";
                    return t;
                }
            }
        }
    }
}
