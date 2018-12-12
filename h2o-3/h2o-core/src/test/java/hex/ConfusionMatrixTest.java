package hex;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Key;
import water.MRTask;
import water.Scope;
import water.TestUtil;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.FileUtils;
import water.util.VecUtils;

import static water.util.FrameUtils.parseFrame;

import java.io.IOException;
import java.util.Arrays;

public class ConfusionMatrixTest extends TestUtil {
  @BeforeClass
  public static void stall() { stall_till_cloudsize(5); }

  final boolean debug = false;

  @Test
  public void testIdenticalVectors() {
    try {
      Scope.enter();

      simpleCMTest(
              "smalldata/junit/cm/v1.csv",
              "smalldata/junit/cm/v1.csv",
              ar("A", "B", "C"),
              ar("A", "B", "C"),
              ar("A", "B", "C"),
              ard(ard(2, 0, 0),
                      ard(0, 2, 0),
                      ard(0, 0, 1)),
              debug);

    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testVectorAlignment() {
    simpleCMTest(
            "smalldata/junit/cm/v1.csv",
            "smalldata/junit/cm/v2.csv",
            ar("A", "B", "C"),
            ar("A", "B", "C"),
            ar("A", "B", "C"),
            ard( ard(1, 1, 0),
                    ard(0, 1, 1),
                    ard(0, 0, 1)
            ),
            debug);
  }

  /** Negative test testing expected exception if two vectors
   * of different lengths are provided.
   */
  @Test(expected = IllegalArgumentException.class)
  public void testDifferentLengthVectors() {
    simpleCMTest(
            "smalldata/junit/cm/v1.csv",
            "smalldata/junit/cm/v3.csv",
            ar("A", "B", "C"),
            ar("A", "B", "C"),
            ar("A", "B", "C"),
            ard( ard(1, 1, 0),
                    ard(0, 1, 1),
                    ard(0, 0, 1)
            ),
            debug);
  }

  @Test
  public void testDifferentDomains() {

    simpleCMTest(
            "smalldata/junit/cm/v1.csv",
            "smalldata/junit/cm/v4.csv",
            ar("A", "B", "C"),
            ar("B", "C"),
            ar("A", "B", "C"),
            ard( ard(0, 2, 0),
                    ard(0, 0, 2),
                    ard(0, 0, 1)
            ),
            debug);

    simpleCMTest(
            "smalldata/junit/cm/v2.csv",
            "smalldata/junit/cm/v4.csv",
            ar("A", "B", "C"),
            ar("B", "C"),
            ar("A", "B", "C"),
            ard( ard(0, 1, 0),
                    ard(0, 1, 1),
                    ard(0, 0, 2)
            ),
            debug);
  }

  @Test
  public void testSimpleNumericVectors() {
    simpleCMTest(
            "smalldata/junit/cm/v1n.csv",
            "smalldata/junit/cm/v1n.csv",
            ar("0", "1", "2"),
            ar("0", "1", "2"),
            ar("0", "1", "2"),
            ard( ard(2, 0, 0),
                    ard(0, 2, 0),
                    ard(0, 0, 1)
            ),
            debug);

    simpleCMTest(
            "smalldata/junit/cm/v1n.csv",
            "smalldata/junit/cm/v2n.csv",
            ar("0", "1", "2"),
            ar("0", "1", "2"),
            ar("0", "1", "2"),
            ard( ard(1, 1, 0),
                    ard(0, 1, 1),
                    ard(0, 0, 1)
            ),
            debug);
  }

  @Test
  public void testDifferentDomainsNumericVectors() {

    simpleCMTest(
            "smalldata/junit/cm/v1n.csv",
            "smalldata/junit/cm/v4n.csv",
            ar("0", "1", "2"),
            ar("1", "2"),
            ar("0", "1", "2"),
            ard( ard(0, 2, 0),
                    ard(0, 0, 2),
                    ard(0, 0, 1)
            ),
            debug);

    simpleCMTest(
            "smalldata/junit/cm/v2n.csv",
            "smalldata/junit/cm/v4n.csv",
            ar("0", "1", "2"),
            ar("1", "2"),
            ar("0", "1", "2"),
            ard( ard(0, 1, 0),
                    ard(0, 1, 1),
                    ard(0, 0, 2)
            ),
            debug);

  }

  /** Test for PUB-216:
   * The case when vector domain is set to a value (0~A, 1~B, 2~C), but actual values stored in
   * vector references only a subset of domain (1~B, 2~C). The TransfVec was using minimum from
   * vector (i.e., value 1) to compute transformation but minimum was wrong since it should be 0. */
  @Test public void testBadModelPrect() {

    simpleCMTest(
            ArrayUtils.frame("v1", vec(ar("A", "B", "C"), ari(0, 0, 1, 1, 2))),
            ArrayUtils.frame("v1", vec(ar("A", "B", "C"), ari(1, 1, 2, 2, 2))),
            ar("A","B","C"),
            ar("A","B","C"),
            ar("A","B","C"),
            ard( ard(0, 2, 0),
                    ard(0, 0, 2),
                    ard(0, 0, 1)
            ),
            debug);

  }

  @Test public void testBadModelPrect2() {
    simpleCMTest(
            ArrayUtils.frame("v1", vec(ar("-1", "0", "1"), ari(0, 0, 1, 1, 2))),
            ArrayUtils.frame("v1", vec(ar("0", "1"), ari(0, 0, 1, 1, 1))),
            ar("-1", "0", "1"),
            ar("0", "1"),
            ar("-1", "0", "1"),
            ard(ard(0, 2, 0),
                    ard(0, 0, 2),
                    ard(0, 0, 1)
            ),
            debug);

  }

  private void simpleCMTest(String f1, String f2, String[] expectedActualDomain, String[] expectedPredictDomain, String[] expectedDomain, double[][] expectedCM, boolean debug) {
    try {
      Frame v1 = parseFrame(Key.make("v1.hex"), FileUtils.getFile(f1));
      Frame v2 = parseFrame(Key.make("v2.hex"), FileUtils.getFile(f2));
      if (!v1.isCompatible(v2)) {
        Frame old = null;
        v2 = new Frame(v1.makeCompatible(old = v2));
        old.delete();
      }
      simpleCMTest(v1, v2, expectedActualDomain, expectedPredictDomain, expectedDomain, expectedCM, debug);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /** Delete v1, v2 after potential modifying operations during processing: categoricals and/or train/test adaptation. */
  private void simpleCMTest(Frame v1, Frame v2, String[] actualDomain, String[] predictedDomain, String[] expectedDomain, double[][] expectedCM, boolean debug) {
    Scope.enter();
    try {
      ConfusionMatrix cm = buildCM(VecUtils.toCategoricalVec(v1.vecs()[0]), VecUtils.toCategoricalVec(v2.vecs()[0]));

      // -- DEBUG --
      if (debug) {
        System.err.println("actual            : " + Arrays.toString(actualDomain));
        System.err.println("predicted         : " + Arrays.toString(predictedDomain));
        System.err.println("CM domain         : " + Arrays.toString(cm._domain));
        System.err.println("expected CM domain: " + Arrays.toString(expectedDomain) + "\n");
        for (int i=0; i<cm._cm.length; i++)
          System.err.println(Arrays.toString(cm._cm[i]));
        System.err.println("");
        System.err.println(cm.toASCII());
      }
      // -- -- --
      assertCMEqual(expectedDomain, expectedCM, cm);
    } finally {
      if (v1 != null) v1.delete();
      if (v2 != null) v2.delete();
      Scope.exit();
    }
  }

  private void assertCMEqual(String[] expectedDomain, double[][] expectedCM, ConfusionMatrix actualCM) {
    Assert.assertArrayEquals("Expected domain differs",     expectedDomain,        actualCM._domain);
    double[][] acm = actualCM._cm;
    Assert.assertEquals("CM dimension differs", expectedCM.length, acm.length);
    for (int i=0; i < acm.length; i++) Assert.assertArrayEquals("CM row " +i+" differs!", expectedCM[i], acm[i],1e-10);
  }

  /** Build the CM data from the actuals and predictions, using the default
   *  threshold.  Print to Log.info if the number of classes is below the
   *  print_threshold.  Actuals might have extra levels not trained on (hence
   *  never predicted).  Actuals with NAs are not scored, and their predictions
   *  ignored. */
  public static ConfusionMatrix buildCM(Vec actuals, Vec predictions) {
    if (!actuals.isCategorical()) throw new IllegalArgumentException("actuals must be categorical.");
    if (!predictions.isCategorical()) throw new IllegalArgumentException("predictions must be categorical.");
    Scope.enter();
    try {
      Vec adapted = predictions.adaptTo(actuals.domain());
      int len = actuals.domain().length;
      CMBuilder cm = new CMBuilder(len).doAll(actuals, adapted);
      return new ConfusionMatrix(cm._arr, actuals.domain());
    } finally {
      Scope.exit();
    }
  }

  private static class CMBuilder extends MRTask<CMBuilder> {
    final int _len;
    double _arr[/*actuals*/][/*predicted*/];
    CMBuilder(int len) { _len = len; }
    @Override public void map(Chunk ca, Chunk cp ) {
      // After adapting frames, the Actuals have all the levels in the
      // prediction results, plus any extras the model was never trained on.
      // i.e., Actual levels are at least as big as the predicted levels.
      _arr = new double[_len][_len];
      for( int i=0; i < ca._len; i++ )
        if( !ca.isNA(i) )
          _arr[(int)ca.at8(i)][(int)cp.at8(i)]++;
    }
    @Override public void reduce( CMBuilder cm ) {
      ArrayUtils.add(_arr,cm._arr);
    }
  }


}
