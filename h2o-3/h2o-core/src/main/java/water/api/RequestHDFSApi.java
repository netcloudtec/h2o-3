
package water.api;

public class RequestHDFSApi extends AbstractRegister {

    @Override
    public void registerEndPoints(RestApiContext context) {
        RequestServer.registerEndpoint("hdfslist", "GET /3/HdfsOp/list", HdfsOpHandler.class, "list", "hdfs files list.");
        RequestServer.registerEndpoint("hdfsread", "GET /3/HdfsOp/read", HdfsOpHandler.class, "read", "hdfs file content.");
        RequestServer.registerEndpoint("hdfsput", "POST /3/HdfsOp/put", HdfsOpHandler.class, "put", "hdfs put file.");
        RequestServer.registerEndpoint("hdfsdel", "DELETE /3/HdfsOp/delete", HdfsOpHandler.class, "delete", "hdfs file delete.");
    }
    public String getName() {
        return "HDFS V3";
    }
}
