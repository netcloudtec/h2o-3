//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package water.api.schemas3;

import water.Iced;
import water.api.API;
import water.api.API.Direction;

public class FilePropertiesSchema extends SchemaV3<Iced, FilePropertiesSchema> {
    @API(
            help = "name",
            direction = Direction.OUTPUT
    )
    public String name;
    @API(
            help = "size",
            direction = Direction.OUTPUT
    )
    public long size;
    @API(
            help = "user",
            direction = Direction.OUTPUT
    )
    public String user;
    @API(
            help = "time",
            direction = Direction.OUTPUT
    )
    public long time;
    @API(
            help = "group",
            direction = Direction.OUTPUT
    )
    public String group;
    @API(
            help = "power",
            direction = Direction.OUTPUT
    )
    public String power;
    @API(
            help = "isfile",
            direction = Direction.OUTPUT
    )
    public String isfile;

    public FilePropertiesSchema() {
    }
}
