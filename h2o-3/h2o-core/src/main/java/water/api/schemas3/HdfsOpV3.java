//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package water.api.schemas3;

import water.Iced;
import water.api.API;
import water.api.API.Direction;

public class HdfsOpV3 extends SchemaV3<Iced, HdfsOpV3> {
    @API(
            help = "src",
            required = true
    )
    public String src;
    @API(
            help = "local"
    )
    public String local;
    @API(
            help = "flag"
    )
    public boolean flag;
    @API(
            help = "msg"
    )
    public String msg;
    @API(
            help = "limit"
    )
    public int limit;
    @API(
            help = "matches",
            direction = Direction.OUTPUT
    )
    public String[] matches;
    @API(
            help = "files",
            direction = Direction.OUTPUT
    )
    public FilePropertiesSchema[] files;
    @API(
            help = "is_delete",
            direction = Direction.OUTPUT
    )
    public int is_delete;
    @API(
            help = "page",
            direction = Direction.INPUT
    )
    public int page;
    @API(
            help = "size",
            direction = Direction.INPUT
    )
    public int size;
    @API(
            help = "content",
            direction = Direction.OUTPUT
    )
    public String content;

    public HdfsOpV3() {
    }
}
