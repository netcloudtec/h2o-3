package hex.genmodel.easy.prediction;

/**
 * Regression model prediction.
 */
public class RegressionModelPrediction extends AbstractPrediction {
  /**
   * This value may be Double.NaN, which means NA (this will happen with GLM, for example,
   * if one of the input values for a new data point is NA).
   */
  public double value;
  public String[] leafNodeAssignments;  // only valid for GBM or DRF, null for all other mojo models
  public int[] leafNodeAssignmentIds;   // ditto, available in MOJO 1.3 and newer
}
