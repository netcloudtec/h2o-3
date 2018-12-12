import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator

def plot_test():
    air = h2o.import_file(pyunit_utils.locate("smalldata/airlines/AirlinesTrain.csv.zip"))

    # Constructing test and train sets by sampling (20/80)
    s = air[0].runif()
    air_train = air[s <= 0.8]
    air_valid = air[s > 0.8]

    myX = ["Origin", "Dest", "Distance", "UniqueCarrier", "fMonth", "fDayofMonth", "fDayOfWeek"]
    myY = "IsDepDelayed"

    air_gbm = H2OGradientBoostingEstimator(distribution="bernoulli", ntrees=100, max_depth=3, learn_rate=0.01)
    air_gbm.train(x=myX, y=myY, training_frame=air_train, validation_frame=air_valid)

    # Plot ROC for training and validation sets
    air_gbm.plot(type="roc", train=True, server=True)
    air_gbm.plot(type="roc", valid=True, server=True)

    air_test = h2o.import_file(pyunit_utils.locate("smalldata/airlines/AirlinesTest.csv.zip"))
    perf = air_gbm.model_performance(air_test)

    # Plot ROC for test set
    perf.plot(type="roc", server=True)



if __name__ == "__main__":
    pyunit_utils.standalone_test(plot_test)
else:
    plot_test()
