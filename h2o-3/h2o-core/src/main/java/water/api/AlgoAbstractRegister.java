package water.api;

import hex.ModelBuilder;

/**
 * Abstract base class for registering Rest API for algorithms
 */
public abstract class AlgoAbstractRegister extends AbstractRegister {

  /**
   * Register algorithm common REST interface.
   *
   * @param mbProto  prototype instance of algorithm model builder
   * @param version  registration version
   */
  protected final void registerModelBuilder(RestApiContext context, ModelBuilder mbProto, int version) {
    String base = mbProto.getClass().getSimpleName();
    String lbase = base.toLowerCase();
    // This is common model builder handler
    Class<? extends water.api.Handler> handlerClass = water.api.ModelBuilderHandler.class;

    context.registerEndpoint("train_" + lbase, "POST /" + version + "/ModelBuilders/" + lbase, handlerClass, "train",
            "Train a " + base + " model.");

    context.registerEndpoint("validate_" + lbase, "POST /"+version+"/ModelBuilders/"+lbase+"/parameters", handlerClass, "validate_parameters",
            "Validate a set of " + base + " model builder parameters.");

    // Grid search is experimental feature
    context.registerEndpoint("grid_search_" + lbase, "POST /99/Grid/"+lbase, GridSearchHandler.class, "train",
            "Run grid search for "+base+" model.");
  }
}
