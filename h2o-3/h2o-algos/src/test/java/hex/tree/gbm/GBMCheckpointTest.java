package hex.tree.gbm;

import hex.tree.CompressedTree;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.TestUtil;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.VecUtils;

import static water.ModelSerializationTest.assertTreeEquals;
import static water.ModelSerializationTest.getTrees;

public class GBMCheckpointTest extends TestUtil {

  @BeforeClass public static void stall() { stall_till_cloudsize(1); }

  @Test
  public void testCheckpointReconstruction4Multinomial() {
    testCheckPointReconstruction("smalldata/iris/iris.csv", 4, true, 5, 3);
  }

  @Test
  public void testCheckpointReconstruction4Multinomial2() {
    testCheckPointReconstruction("smalldata/junit/cars_20mpg.csv", 1, true, 5, 3);
  }

  @Test
  public void testCheckpointReconstruction4Binomial() {
    testCheckPointReconstruction("smalldata/logreg/prostate.csv", 1, true, 5, 3);
  }

  @Test
  public void testCheckpointReconstruction4Binomial2() {
    testCheckPointReconstruction("smalldata/junit/cars_20mpg.csv", 7, true, 2, 2);
  }

  /** Test throwing the right exception if non-modifiable parameter is specified.
   */
  @Test(expected = H2OIllegalArgumentException.class)
  @Ignore
  public void testCheckpointWrongParams() {
    testCheckPointReconstruction("smalldata/iris/iris.csv", 4, true, 5, 3, 0.2f, 0.67f);
  }

  @Test
  public void testCheckpointReconstruction4Regression() {
    testCheckPointReconstruction("smalldata/logreg/prostate.csv", 8, false, 5, 3);
  }

  @Test
  public void testCheckpointReconstruction4Regression2() {
    testCheckPointReconstruction("smalldata/junit/cars_20mpg.csv", 1, false, 5, 3);
  }

  private void testCheckPointReconstruction(String dataset,
                                            int responseIdx,
                                            boolean classification,
                                            int ntreesInPriorModel, int ntreesInNewModel) {
    testCheckPointReconstruction(dataset, responseIdx, classification, ntreesInPriorModel, ntreesInNewModel, 0.632f, 0.632f);
  }

  private void testCheckPointReconstruction(String dataset,
                                            int responseIdx,
                                            boolean classification,
                                            int ntreesInPriorModel, int ntreesInNewModel,
                                            float sampleRateInPriorModel, float sampleRateInNewModel) {
    Frame f = parse_test_file(dataset);
    Vec v = f.remove("economy"); if (v!=null) v.remove(); //avoid overfitting for binomial case for cars dataset
    DKV.put(f);
    // If classification turn response into categorical
    if (classification) {
      Vec respVec = f.vec(responseIdx);
      f.replace(responseIdx, VecUtils.toCategoricalVec(respVec)).remove();
      DKV.put(f._key, f);
    }
    GBMModel model = null;
    GBMModel modelFromCheckpoint = null;
    GBMModel modelFinal = null;
    try {
      GBMModel.GBMParameters gbmParams = new GBMModel.GBMParameters();
      gbmParams._train = f._key;
      gbmParams._response_column = f.name(responseIdx);
      gbmParams._ntrees = ntreesInPriorModel;
      gbmParams._seed = 42;
      gbmParams._max_depth = 5;
      gbmParams._learn_rate_annealing = 0.9;
      gbmParams._score_each_iteration = true;
      model = new GBM(gbmParams, Key.<GBMModel>make("Initial model") ).trainModel().get();

      GBMModel.GBMParameters gbmFromCheckpointParams = new GBMModel.GBMParameters();
      gbmFromCheckpointParams._train = f._key;
      gbmFromCheckpointParams._response_column = f.name(responseIdx);
      gbmFromCheckpointParams._ntrees = ntreesInPriorModel + ntreesInNewModel;
      gbmFromCheckpointParams._seed = 42;
      gbmFromCheckpointParams._checkpoint = model._key;
      gbmFromCheckpointParams._score_each_iteration = true;
      gbmFromCheckpointParams._max_depth = 5;
      gbmFromCheckpointParams._learn_rate_annealing = 0.9;
      modelFromCheckpoint = new GBM(gbmFromCheckpointParams,Key.<GBMModel>make("Model from checkpoint")).trainModel().get();

      // Compute a separated model containing the same numnber of trees as a model built from checkpoint
      GBMModel.GBMParameters gbmFinalParams = new GBMModel.GBMParameters();
      gbmFinalParams._train = f._key;
      gbmFinalParams._response_column = f.name(responseIdx);
      gbmFinalParams._ntrees = ntreesInPriorModel + ntreesInNewModel;
      gbmFinalParams._seed = 42;
      gbmFinalParams._score_each_iteration = true;
      gbmFinalParams._max_depth = 5;
      gbmFinalParams._learn_rate_annealing = 0.9;
      modelFinal = new GBM(gbmFinalParams,Key.<GBMModel>make("Validation model")).trainModel().get();

//      System.err.println(modelFromCheckpoint.toJava(false,true));
//      System.err.println(modelFinal.toJava(false,true));

      CompressedTree[][] treesFromCheckpoint = getTrees(modelFromCheckpoint);
      CompressedTree[][] treesFromFinalModel = getTrees(modelFinal);
      assertTreeEquals("The model created from checkpoint and corresponding model created from scratch should have the same trees!",
              treesFromCheckpoint, treesFromFinalModel, true);

      // Make sure we are not re-using trees
      for (int tree = 0; tree < treesFromCheckpoint.length; tree++) {
        for (int clazz = 0; clazz < treesFromCheckpoint[tree].length; clazz++) {
          if (treesFromCheckpoint[tree][clazz] !=null) { // We already verify equality of models
            CompressedTree a = treesFromCheckpoint[tree][clazz];
            CompressedTree b = treesFromFinalModel[tree][clazz];
            Assert.assertNotEquals(a._key, b._key);
          }
        }
      }
    } finally {
      if (f!=null) f.delete();
      if (model!=null) model.delete();
      if (modelFromCheckpoint!=null) modelFromCheckpoint.delete();
      if (modelFinal!=null) modelFinal.delete();
    }
  }

  @Ignore("PUBDEV-1829")
  public void testCheckpointReconstruction4BinomialPUBDEV1829() {
    Frame tr = parse_test_file("smalldata/jira/gbm_checkpoint_train.csv");
    Frame val = parse_test_file("smalldata/jira/gbm_checkpoint_valid.csv");

    Vec old = null;

    tr.remove("name").remove();
    tr.remove("economy").remove();
    val.remove("name").remove();
    val.remove("economy").remove();

    old = tr.remove("economy_20mpg");
    tr.add("economy_20mpg", old);
    DKV.put(tr);

    old = val.remove("economy_20mpg");
    val.add("economy_20mpg", old);
    DKV.put(val);

    GBMModel model = null;
    GBMModel modelFromCheckpoint = null;
    GBMModel modelFinal = null;

    try {
      GBMModel.GBMParameters gbmParams = new GBMModel.GBMParameters();
      gbmParams._train = tr._key;
      gbmParams._valid = val._key;
      gbmParams._response_column = "economy_20mpg";
      gbmParams._ntrees = 5;
      gbmParams._max_depth = 5;
      gbmParams._min_rows = 10;
      gbmParams._score_each_iteration = true;
      gbmParams._seed = 42;
      model = new GBM(gbmParams,Key.<GBMModel>make("Initial model")).trainModel().get();

      GBMModel.GBMParameters gbmFromCheckpointParams = new GBMModel.GBMParameters();
      gbmFromCheckpointParams._train = tr._key;
      gbmFromCheckpointParams._valid = val._key;
      gbmFromCheckpointParams._response_column = "economy_20mpg";
      gbmFromCheckpointParams._ntrees = 10;
      gbmFromCheckpointParams._checkpoint = model._key;
      gbmFromCheckpointParams._score_each_iteration = true;
      gbmFromCheckpointParams._max_depth = 5;
      gbmFromCheckpointParams._min_rows = 10;
      gbmFromCheckpointParams._seed = 42;
      modelFromCheckpoint = new GBM(gbmFromCheckpointParams,Key.<GBMModel>make("Model from checkpoint")).trainModel().get();

      // Compute a separated model containing the same number of trees as a model built from checkpoint
      GBMModel.GBMParameters gbmFinalParams = new GBMModel.GBMParameters();
      gbmFinalParams._train = tr._key;
      gbmFinalParams._valid = val._key;
      gbmFinalParams._response_column = "economy_20mpg";
      gbmFinalParams._ntrees = 10;
      gbmFinalParams._score_each_iteration = true;
      gbmFinalParams._max_depth = 5;
      gbmFinalParams._min_rows = 10;
      gbmFinalParams._seed = 42;
      modelFinal = new GBM(gbmFinalParams,Key.<GBMModel>make("Validation model")).trainModel().get();

      CompressedTree[][] treesFromCheckpoint = getTrees(modelFromCheckpoint);
      CompressedTree[][] treesFromFinalModel = getTrees(modelFinal);
      assertTreeEquals("The model created from checkpoint and corresponding model created from scratch should have the same trees!",
              treesFromCheckpoint, treesFromFinalModel, true);
    } finally {
      if (tr!=null) tr.delete();
      if (val!=null) val.delete();
      if (old != null) old.remove();
      if (model!=null) model.delete();
      if (modelFromCheckpoint!=null) modelFromCheckpoint.delete();
      if (modelFinal!=null) modelFinal.delete();
    }
  }
}
