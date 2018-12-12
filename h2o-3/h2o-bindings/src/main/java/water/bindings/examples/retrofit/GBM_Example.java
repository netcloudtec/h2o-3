package water.bindings.examples.retrofit;

import water.bindings.H2oApi;
import water.bindings.pojos.*;

import java.io.IOException;
import java.util.UUID;

public class GBM_Example {

    public static void gbmExampleFlow(String url) throws IOException {
        H2oApi h2o = url != null ? new H2oApi(url) : new H2oApi();

        // Utility var:
        JobV3 job = null;

        // STEP 0: init a session
        String sessionId = h2o.newSession().sessionKey;


        // STEP 1: import raw file
        ImportFilesV3 importBody = h2o.importFiles(
            "http://s3.amazonaws.com/h2o-public-test-data/smalldata/flow_examples/arrhythmia.csv.gz", null
          );
        System.out.println("import: " + importBody);


        // STEP 2: parse setup
        ParseSetupV3 parseSetupBody = h2o.guessParseSetup(H2oApi.stringArrayToKeyArray(importBody.destinationFrames, FrameKeyV3.class));
        System.out.println("parseSetupBody: " + parseSetupBody);


        // STEP 3: parse into columnar Frame
        ParseV3 parseParms = new ParseV3();
        H2oApi.copyFields(parseParms, parseSetupBody);
        parseParms.destinationFrame = H2oApi.stringToFrameKey("arrhythmia.hex");
        parseParms.blocking = true;  // alternately, call h2o.waitForJobCompletion(parseSetupBody.job)

        ParseV3 parseBody = h2o.parse(parseParms);
        System.out.println("parseBody: " + parseBody);


        // STEP 4: Split into test and train datasets
        String tmpVec = "tmp_" + UUID.randomUUID().toString();
        String splitExpr =
          "(, " +
          "  (tmp= " + tmpVec + " (h2o.runif arrhythmia.hex 906317))" +
          "  (assign train " +
          "    (rows arrhythmia.hex (<= " + tmpVec + " 0.75)))" +
          "  (assign test " +
          "    (rows arrhythmia.hex (> " + tmpVec + " 0.75)))" +
          "  (rm " + tmpVec + "))";
        RapidsSchemaV3 rapidsParms = new RapidsSchemaV3();
        rapidsParms.sessionId = sessionId;
        rapidsParms.ast = splitExpr;
        h2o.rapidsExec(rapidsParms);


        // STEP 5: Train the model (NOTE: step 4 is polling, which we don't require because we specified blocking for the parse above)
        GBMParametersV3 gbmParms = new GBMParametersV3();

        // gbmParms.trainingFrame = H2oApi.stringToFrameKey("arrhythmia.hex");

        gbmParms.trainingFrame = H2oApi.stringToFrameKey("train");
        gbmParms.validationFrame = H2oApi.stringToFrameKey("test");

        ColSpecifierV3 responseColumn = new ColSpecifierV3();
        responseColumn.columnName = "C1";
        gbmParms.responseColumn = responseColumn;

        System.out.println("About to train GBM. . .");
        GBMV3 gbmBody = h2o.train_gbm(gbmParms);
        System.out.println("gbmBody: " + gbmBody);


        // STEP 6: poll for completion
        job = h2o.waitForJobCompletion(gbmBody.job.key);
        System.out.println("GBM build done.");


        // STEP 7: fetch the model
        ModelKeyV3 model_key = (ModelKeyV3)job.dest;
        ModelsV3 models = h2o.model(model_key);
        System.out.println("models: " + models);
        GBMModelV3 model = (GBMModelV3)models.models[0];
        System.out.println("new GBM model: " + model);
        // System.out.println("new GBM model: " + models.models[0]);
        assert model.getClass() == GBMModelV3.class;
        assert model.output.getClass() == GBMModelOutputV3.class;
        assert model.parameters.getClass() == GBMParametersV3.class;


        // STEP 8: predict!
        ModelMetricsListSchemaV3 predict_params = new ModelMetricsListSchemaV3();
        predict_params.model = model_key;
        predict_params.frame = gbmParms.trainingFrame;
        predict_params.predictionsFrame = H2oApi.stringToFrameKey("predictions");

        ModelMetricsListSchemaV3 predictions = h2o.predict(predict_params);
        System.out.println("predictions: " + predictions);

        // STEP 99: end the session
        h2o.endSession();
    }

    public static void gbmExampleFlow() throws IOException {
        gbmExampleFlow(null);
    }

    public static void main (String[] args) throws IOException {
        gbmExampleFlow();
    }
}

