Saving and Loading a Model
==========================

This section describes how to save and load models using R, Python, and Flow. 

**Note**: When saving an H2O binary model with ``h2o.saveModel`` (R), ``h2o.save_model`` (Python), or in Flow, you will only be able to load and use that saved binary model with the same version of H2O that you used to train your model. H2O binary models are not compatible across H2O versions. If you update your H2O version, then you will need to retrain your model. For production, you can save your model as a :ref:`POJO/MOJO <about-pojo-mojo>`. These artifacts are not tied to a particular version of H2O because they are just plain Java code and do not require an H2O cluster to be running.

In R and Python
---------------

In R and Python, you can save a model locally or to HDFS using the ``h2o.saveModel`` (R) or ``h2o.save_model`` (Python) function . This function accepts the model object and the file path. If no path is specified, then the model will be saved to the current working directory. After the model is saved, you can load it using the ``h2o.loadModel`` (R) or ``h2o.load_model`` (Python) function.

.. example-code::
   .. code-block:: r

    # build the model
    model <- h2o.deeplearning(params)

    # save the model
    model_path <- h2o.saveModel(object=model, path=getwd(), force=TRUE)

    print(model_path)
    /tmp/mymodel/DeepLearning_model_R_1441838096933

    # load the model
    saved_model <- h2o.loadModel(model_path)

   .. code-block:: python

	# build the model
	model = H2ODeepLearningEstimator(params)
	model.train(params)

	# save the model
	model_path = h2o.save_model(model=model, path="/tmp/mymodel", force=True)

	print model_path
	/tmp/mymodel/DeepLearning_model_python_1441838096933

	# load the model
	saved_model = h2o.load_model(model_path)
 

**Note**: When saving to HDFS, you must prepend the save directory with ``hdfs://``. For example:

.. example-code::
   .. code-block:: r

    # build the model
    model <- h2o.glm(model params)

    # save the model to HDFS
    hdfs_name_node <- "node-1"
    hdfs_tmp_dir <- "/tmp/runit"
    model_path <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_tmp_dir)
    h2o.saveModel(model, dir=model_path, name="mymodel")

   .. code-block:: python

	# build the model
	h2o_glm = H2OGeneralizedLinearEstimator(model params)
	h2o_glm.train(training params)

	# save the model to HDFS
	hdfs_name_node = "node-1"
	hdfs_model_path = sprintf("hdfs://%s%s", hdfs_name_node, hdfs_tmp_dir)
	new_model_path = h2o.save_model(h2o_glm, "hdfs://" + hdfs_name_node + "/" + hdfs_model_path)

In Flow
-------

The steps for saving and loading models in Flow are described in the **Using Flow - H2O's Web UI** section. Specifically, refer to :ref:`Exporting and Importing Models <export-import-models-flow>` for information about loading models into Flow. 
