from __future__ import print_function
from builtins import zip
from builtins import range
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils




def javapredict_dl_xlarge():

    hdfs_name_node = pyunit_utils.hadoop_namenode()
    hdfs_file_name = "/datasets/z_repro.csv"
    url = "hdfs://{0}{1}".format(hdfs_name_node, hdfs_file_name)

    params = {'hidden':[3500, 3500], 'epochs':0.0001} # 436MB pojo
    print("Parameter list:")
    for k,v in zip(list(params.keys()), list(params.values())): print("{0}, {1}".format(k,v))

    train =  h2o.import_file(url)
    test = train[list(range(0,10)),:]
    x = list(range(1,train.ncol))
    y = 0

    pyunit_utils.javapredict("deeplearning", "numeric", train, test, x, y, **params)



if __name__ == "__main__":
    pyunit_utils.standalone_test(javapredict_dl_xlarge)
else:
    javapredict_dl_xlarge()
