from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
import time
from tests import pyunit_utils
#----------------------------------------------------------------------
# PUBDEV-3359: verify that we can parse thousands of files correctly or
# identify if not.  Split the airlines_all datasets into 2000 files
# each with 50000 lines of data.  Total data frame should contain 99999999
# rows. One row is header. Check and make sure our parser can handle this.
#
#----------------------------------------------------------------------


def hdfs_pubdev_3359_parser():

    # Check if we are running inside the H2O network by seeing if we can touch
    # the namenode.
    hadoop_namenode_is_accessible = pyunit_utils.hadoop_namenode_is_accessible()

    if hadoop_namenode_is_accessible:
        hdfs_name_node = pyunit_utils.hadoop_namenode()

        hdfs_csv_file = "/datasets/PUBDEV-3359"
        url_csv = "hdfs://{0}{1}".format(hdfs_name_node, hdfs_csv_file)

        h2oframe_csv = h2o.import_file(url_csv)

        # compare the two frames

        # compare the two frames
        assert h2oframe_csv.nrow == 99998000, "Data should contain 99998000 rows but we parsed: {0} " \
                                            "rows!".format(h2oframe_csv.nrow)
    else:
        raise EnvironmentError


if __name__ == "__main__":
    pyunit_utils.standalone_test(hdfs_pubdev_3359_parser)
else:
    hdfs_pubdev_3359_parser()