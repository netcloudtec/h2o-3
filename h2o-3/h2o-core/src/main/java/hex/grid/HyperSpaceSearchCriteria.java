package hex.grid;

import hex.ScoreKeeper;
import water.Iced;
import water.fvec.Frame;

/**
 * Search criteria for a hyperparameter search including directives for how to search and
 * when to stop the search.
 */
public class HyperSpaceSearchCriteria extends Iced {
  public enum Strategy { Unknown, Cartesian, RandomDiscrete } // search strategy

  public final Strategy _strategy;
  public final Strategy strategy() { return _strategy; }

  public ScoreKeeper.StoppingMetric stopping_metric() { return ScoreKeeper.StoppingMetric.AUTO; }


// TODO: add a factory which accepts a Strategy and calls the right constructor

  public HyperSpaceSearchCriteria(Strategy strategy) {
    this._strategy = strategy;
  }

  /**
   * Search criteria for an exhaustive Cartesian hyperparameter search.
   */
  public static final class CartesianSearchCriteria extends HyperSpaceSearchCriteria {
    public CartesianSearchCriteria() {
      super(Strategy.Cartesian);
    }
  }

  /**
   * Search criteria for a hyperparameter search including directives for how to search and
   * when to stop the search.
   * <p>
   * NOTE: client ought to call set_default_stopping_tolerance_for_frame(Frame) to get a reasonable stopping tolerance, especially for small N.
   */
  public static final class RandomDiscreteValueSearchCriteria extends HyperSpaceSearchCriteria {
    private long _seed = -1; // -1 means true random

    /////////////////////
    // stopping criteria:
    private int _max_models = 0;
    private double _max_runtime_secs = 0;
    private int _stopping_rounds = 0;
    private ScoreKeeper.StoppingMetric _stopping_metric = ScoreKeeper.StoppingMetric.AUTO;
    public double _stopping_tolerance = 0.001;


    /** Seed for the random choices of hyperparameter values.  Set to a value other than -1 to get a repeatable pseudorandom sequence. */
    public long seed() { return _seed; }

    /** Max number of models to build. */
    public int max_models() { return _max_models; }

    /**
     * Max runtime for the entire grid, in seconds. Set to 0 to disable. Can be combined with <i>max_runtime_secs</i> in the model parameters. If
     * <i>max_runtime_secs</i> is not set in the model parameters then each model build is launched with a limit equal to
     * the remainder of the grid time.  If <i>max_runtime_secs</i> <b>is</b> set in the mode parameters each build is launched
     * with a limit equal to the minimum of the model time limit and the remaining time for the grid.
     */
    public double max_runtime_secs() { return _max_runtime_secs; }

    /**
     * Early stopping based on convergence of stopping_metric.
     * Stop if simple moving average of the stopping_metric does not improve by stopping_tolerance for
     * k scoring events.
     * Can only trigger after at least 2k scoring events. Use 0 to disable.
     */
    public int stopping_rounds() { return _stopping_rounds; }

    /** Metric to use for convergence checking; only for _stopping_rounds > 0 */
    public ScoreKeeper.StoppingMetric stopping_metric() { return _stopping_metric; }

    /** Relative tolerance for metric-based stopping criterion: stop if relative improvement is not at least this much. */
    public double stopping_tolerance() { return _stopping_tolerance; }

    /** Calculate a reasonable stopping tolerance for the Frame.
     * Currently uses only the NA percentage and nrows, but later
     * can take into account the response distribution, response variance, etc.
     * <p>
     * <pre>1/Math.sqrt(frame.naFraction() * frame.numRows())</pre>
     */
    public static double default_stopping_tolerance_for_frame(Frame frame) {
      return Math.min(0.05, Math.max(0.001, 1/Math.sqrt((1 - frame.naFraction()) * frame.numRows())));
    }

    public void set_default_stopping_tolerance_for_frame(Frame frame) {
      _stopping_tolerance = default_stopping_tolerance_for_frame(frame);
    }


    public RandomDiscreteValueSearchCriteria() {
      super(Strategy.RandomDiscrete);
    }

    public void set_seed(long _seed) {
      this._seed = _seed;
    }

    public void set_max_models(int _max_models) {
      this._max_models = _max_models;
    }

    public void set_max_runtime_secs(double _max_runtime_secs) {
      this._max_runtime_secs = _max_runtime_secs;
    }

    public void set_stopping_rounds(int _stopping_rounds) {
      this._stopping_rounds = _stopping_rounds;
    }

    public void set_stopping_metric(ScoreKeeper.StoppingMetric _stopping_metric) {
      this._stopping_metric = _stopping_metric;
    }

    public void set_stopping_tolerance(double _stopping_tolerance) {
      this._stopping_tolerance = _stopping_tolerance;
    }
  }
}
