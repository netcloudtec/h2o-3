import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils


def cars_checkpoint():

    cars = h2o.upload_file(pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
    predictors = ["displacement","power","weight","acceleration","year"]
    response_col = "economy"
    distribution = "gaussian"

    # build first model

    from h2o.estimators.gbm import H2OGradientBoostingEstimator
    model1 = H2OGradientBoostingEstimator(ntrees=10,max_depth=2, min_rows=10, distribution=distribution)
    model1.train(x=predictors,y=response_col,training_frame=cars)

    # model1 = h2o.gbm(x=cars[predictors],y=cars[response_col],ntrees=10,max_depth=2, min_rows=10,
    #                  distribution=distribution)

    # continue building the model
    model2 = H2OGradientBoostingEstimator(ntrees=11,max_depth=3, min_rows=9,r2_stopping=0.8,
                                          distribution=distribution,checkpoint=model1._id)
    model2.train(x=predictors,y=response_col,training_frame=cars)

    # model2 = h2o.gbm(x=cars[predictors],y=cars[response_col],ntrees=11,max_depth=3, min_rows=9,r2_stopping=0.8,
    #                  distribution=distribution,checkpoint=model1._id)

    #   erroneous, not MODIFIABLE_BY_CHECKPOINT_FIELDS
    # PUBDEV-1833
    #   learn_rate
    try:
        model = H2OGradientBoostingEstimator(learn_rate=0.00001,distribution=distribution,
                                             checkpoint=model1._id)
        model.train(x=predictors,y=response_col,training_frame=cars)

        # model = h2o.gbm(y=cars[response_col], x=cars[predictors],learn_rate=0.00001,distribution=distribution,
        #                 checkpoint=model1._id)
        assert False, "Expected model-build to fail because learn_rate not modifiable by checkpoint"
    except EnvironmentError:
        assert True

    #   nbins_cats
    try:

        model = H2OGradientBoostingEstimator(nbins_cats=99,distribution=distribution,
                                             checkpoint=model1._id)
        model.train(x=predictors,y=response_col,training_frame=cars)

        # model = h2o.gbm(y=cars[response_col], x=cars[predictors],nbins_cats=99,distribution=distribution,
        #                 checkpoint=model1._id)
        assert False, "Expected model-build to fail because nbins_cats not modifiable by checkpoint"
    except EnvironmentError:
        assert True

    #   balance_classes
    try:
        model = H2OGradientBoostingEstimator(balance_classes=True,distribution=distribution,
                                             checkpoint=model1._id)
        model.train(x=predictors,y=response_col,training_frame=cars)

        # model = h2o.gbm(y=cars[response_col], x=cars[predictors],balance_classes=True,distribution=distribution,
        #                 checkpoint=model1._id)
        assert False, "Expected model-build to fail because balance_classes not modifiable by checkpoint"
    except EnvironmentError:
        assert True

    #   nbins
    try:
        model = H2OGradientBoostingEstimator(nbins=99,distribution=distribution,
                                             checkpoint=model1._id)
        model.train(x=predictors,y=response_col,training_frame=cars)
        # model = h2o.gbm(y=cars[response_col], x=cars[predictors],nbins=99,distribution=distribution,
        #                 checkpoint=model1._id)
        assert False, "Expected model-build to fail because nbins not modifiable by checkpoint"
    except EnvironmentError:
        assert True

    #   nfolds
    try:
        model = H2OGradientBoostingEstimator(nfolds=3,distribution=distribution,
                                             checkpoint=model1._id)
        model.train(x=predictors,y=response_col,training_frame=cars)
        # model = h2o.gbm(y=cars[response_col], x=cars[predictors],nfolds=3,distribution=distribution,
        #                 checkpoint=model1._id)
        assert False, "Expected model-build to fail because nfolds not modifiable by checkpoint"
    except EnvironmentError:
        assert True




if __name__ == "__main__":
    pyunit_utils.standalone_test(cars_checkpoint)
else:
    cars_checkpoint()
