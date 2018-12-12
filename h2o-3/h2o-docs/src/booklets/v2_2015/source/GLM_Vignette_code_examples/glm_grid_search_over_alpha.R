library(h2o)
h2o.init()
path = system.file("extdata", "prostate.csv", package = "h2o")
h2o_df = h2o.importFile(path)
h2o_df$CAPSULE = as.factor(h2o_df$CAPSULE)
alpha_opts = list(list(0), list(.25), list(.5), list(.75), list(1))
hyper_parameters = list(alpha = alpha_opts)
grid <- h2o.grid("glm", hyper_params = hyper_parameters,
                 y = "CAPSULE", x = c("AGE", "RACE", "PSA", "GLEASON"), training_frame = h2o_df, family = "binomial")
grid_models <- lapply(grid@model_ids, function(model_id) { model = h2o.getModel(model_id) })
for (i in 1:length(grid_models)) {
    print(sprintf("regularization: %-50s  auc: %f", grid_models[[i]]@model$model_summary$regularization, h2o.auc(grid_models[[i]])))
}
