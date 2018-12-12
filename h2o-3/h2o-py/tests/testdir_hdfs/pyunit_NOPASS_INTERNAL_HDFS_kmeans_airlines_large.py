from __future__ import print_function
from builtins import range
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
#----------------------------------------------------------------------
# Purpose:  This test runs k-means on the full airlines dataset.
#----------------------------------------------------------------------





def hdfs_kmeans_airlines():
    

    # Check if we are running inside the H2O network by seeing if we can touch
    # the namenode.
    hadoop_namenode_is_accessible = pyunit_utils.hadoop_namenode_is_accessible()

    if hadoop_namenode_is_accessible:
        hdfs_name_node = pyunit_utils.hadoop_namenode()
        hdfs_file = "/datasets/airlines_all.csv"

        print("Import airlines_all.csv from HDFS")
        url = "hdfs://{0}{1}".format(hdfs_name_node, hdfs_file)
        airlines_h2o = h2o.import_file(url)
        n = airlines_h2o.nrow
        print("rows: {0}".format(n))

        print("Run k-means++ with k = 7 and max_iterations = 10")
        myX = list(range(8)) + list(range(11,16)) + list(range(18,21)) + list(range(24,29)) + [9]
        airlines_km = h2o.kmeans(training_frame = airlines_h2o, x = airlines_h2o[myX], k = 7, init = "Furthest",
                                 max_iterations = 10, standardize = True)
        print(airlines_km)
    else:
        raise EnvironmentError



if __name__ == "__main__":
    pyunit_utils.standalone_test(hdfs_kmeans_airlines)
else:
    hdfs_kmeans_airlines()
