package hex.genmodel.algos.glm;

import java.util.Arrays;

public class GlmOrdinalMojoModel extends GlmMojoModelBase {

  private int P;
  private int noff;
  private int lastClass;
  private int[] icptIndices;

  GlmOrdinalMojoModel(String[] columns, String[][] domains, String responseColumn) {
    super(columns, domains, responseColumn);
  }

  @Override
  void init() {
    P = _beta.length / _nclasses;
    lastClass = _nclasses-1;
    icptIndices = new int[lastClass];
    for (int c = 0; c < lastClass; c++) {
      icptIndices[c] = P-1+c*P;
    }
    if (P * _nclasses != _beta.length)
      throw new IllegalStateException("Incorrect coding of Beta.");
    noff = _catOffsets[_cats];
  }

  @Override
  double[] glmScore0(double[] data, double[] preds) {
    Arrays.fill(preds, 0);
    preds[0]=lastClass;

    for (int c = 0; c < lastClass; ++c) { // preds contains the etas for each class
      if (_cats > 0) {
        if (! _useAllFactorLevels) { // skip level 0 of all factors
          for (int i = 0; i < _catOffsets.length-1; ++i) if(data[i] != 0) {
            int ival = (int) data[i] - 1;
            if (ival != data[i] - 1) throw new IllegalArgumentException("categorical value out of range");
            ival += _catOffsets[i];
            if (ival < _catOffsets[i + 1])
              preds[c + 1] += _beta[ival + c*P];
          }
        } else { // do not skip any levels
          for(int i = 0; i < _catOffsets.length-1; ++i) {
            int ival = (int) data[i];
            if (ival != data[i]) throw new IllegalArgumentException("categorical value out of range");
            ival += _catOffsets[i];
            if(ival < _catOffsets[i + 1])
              preds[c + 1] += _beta[ival + c*P];
          }
        }
      }

      for (int i = 0; i < _nums; ++i) {
        preds[c + 1] += _beta[i+noff + c * P] * data[i+_cats];
      }
      preds[c+1] += _beta[icptIndices[c]];
    }

    double previousCDF = 0.0;
    for (int cInd = 0; cInd < lastClass; cInd++) { // classify row and calculate PDF of each class
      double eta = preds[cInd + 1];
      double currCDF = 1.0 / (1 + Math.exp(-eta));
      preds[cInd + 1] = currCDF - previousCDF;
      previousCDF = currCDF;

      if (eta > 0) { // found the correct class
        preds[0] = cInd;
        break;
      }
    }
    for (int cInd = (int) preds[0] + 1; cInd < lastClass; cInd++) {  // continue PDF calculation
      double currCDF = 1.0 / (1 + Math.exp(-preds[cInd + 1]));
      preds[cInd + 1] = currCDF - previousCDF;
      previousCDF = currCDF;

    }
    preds[_nclasses] = 1-previousCDF;
    return preds;
  }
}