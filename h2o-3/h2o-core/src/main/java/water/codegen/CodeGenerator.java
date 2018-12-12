package water.codegen;

import water.exceptions.JCodeSB;

/**
 * Interface for code generator.
 */
public interface CodeGenerator {

  /** Generate code to given output.
   *
   * @param out  code generation output.
   */
  void generate(JCodeSB out);
}
