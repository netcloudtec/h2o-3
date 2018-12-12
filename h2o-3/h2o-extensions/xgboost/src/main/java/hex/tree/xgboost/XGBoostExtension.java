package hex.tree.xgboost;

import ml.dmlc.xgboost4j.java.INativeLibLoader;
import ml.dmlc.xgboost4j.java.NativeLibLoader;
import ml.dmlc.xgboost4j.java.NativeLibrary;
import ml.dmlc.xgboost4j.java.NativeLibraryLoaderChain;
import water.AbstractH2OExtension;
import water.util.Log;

import java.io.IOException;
import java.util.Arrays;

/**
 * XGBoost Extension
 *
 * This is responsible for early initialization of
 * XGBoost per cluster node. The registration
 * of XGBoost REST API requires thix extension
 * to be enabled.
 */
public class XGBoostExtension extends AbstractH2OExtension {

  private static String XGBOOST_MIN_REQUIREMENTS =
          "Xgboost (enabled GPUs) needs: \n"
                  + "  - CUDA 8.0\n"
                  + "XGboost (minimal version) needs: \n"
                  + "  - GCC 4.7+\n"
                  + "For more details, run in debug mode: `java -Dlog4j.configuration=file:///tmp/log4j.properties -jar h2o.jar`\n";

  // XGBoost initialization sequence was called flag
  private boolean isInitCalled = false;
  // XGBoost binary presence on the system
  private boolean isXgboostPresent = false;

  public static String NAME = "XGBoost";

  @Override
  public String getExtensionName() {
    return NAME;
  }

  @Override
  public boolean isEnabled() {
    // Check if some native library was loaded
    if (!isInitCalled) {
      synchronized (this) {
        if (!isInitCalled) {
          isXgboostPresent = initXgboost();
          isInitCalled = true;
        }
      }
    }
    return isXgboostPresent;
  }

  private boolean initXgboost() {
    try {
      INativeLibLoader loader = NativeLibLoader.getLoader();
      if (! (loader instanceof NativeLibraryLoaderChain)) {
        Log.warn("Unexpected XGBoost library loader found. Custom loaders are not supported in this version. " +
                "XGBoost extension will be disabled.");
        return false;
      }
      NativeLibraryLoaderChain chainLoader = (NativeLibraryLoaderChain) loader;
      NativeLibrary lib = chainLoader.getLoadedLibrary();
      Log.info("Found XGBoost backend with library: " + lib.getName());
      NativeLibrary.CompilationFlags[] flags = lib.getCompilationFlags();
      if (flags.length == 0) {
        Log.warn("Your system supports only minimal version of XGBoost (no GPUs, no multithreading)!");
      } else {
        Log.info("XGBoost supported backends: " + Arrays.toString(flags));
      }
      return true;
    } catch (IOException e) {
      // Ups no lib loaded or load failed
      Log.warn("Cannot initialize XGBoost backend! " + XGBOOST_MIN_REQUIREMENTS);
      return false;
    }
  }

  static boolean isGpuSupportEnabled() {
    try {
      INativeLibLoader loader = NativeLibLoader.getLoader();
      if (! (loader instanceof NativeLibraryLoaderChain))
        return false;
      NativeLibraryLoaderChain chainLoader = (NativeLibraryLoaderChain) loader;
      NativeLibrary lib = chainLoader.getLoadedLibrary();
      return lib.hasCompilationFlag(NativeLibrary.CompilationFlags.WITH_GPU);
    } catch (IOException e) {
      Log.debug(e);
      return false;
    }
  }

}