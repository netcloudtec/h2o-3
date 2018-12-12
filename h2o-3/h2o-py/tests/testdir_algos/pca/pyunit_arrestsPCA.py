from __future__ import print_function
from builtins import str
from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.pca import H2OPrincipalComponentAnalysisEstimator as H2OPCA



def pca_arrests():


  print("Importing USArrests.csv data...")
  arrestsH2O = h2o.upload_file(pyunit_utils.locate("smalldata/pca_test/USArrests.csv"))
  arrestsH2O.describe()



  for i in range(4):
    print("H2O PCA with " + str(i) + " dimensions:\n")
    print("Using these columns: {0}".format(arrestsH2O.names))
    pca_h2o = H2OPCA(k = i+1)
    pca_h2o.train(x=list(range(4)), training_frame=arrestsH2O)
    # TODO: pca_h2o.show()



if __name__ == "__main__":
  pyunit_utils.standalone_test(pca_arrests)
else:
  pca_arrests()
