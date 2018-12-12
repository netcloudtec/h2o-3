package water.rapids.ast.prims.mungers;

import water.fvec.Frame;
import water.rapids.Env;
import water.rapids.Val;
import water.rapids.vals.ValNum;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;

/**
 *
 */
public class AstNrow extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary"};
  }

  @Override
  public int nargs() {
    return 1 + 1;
  }

  @Override
  public String str() {
    return "nrow";
  }

  @Override
  public String example() {
    return "(nrow frame)";
  }

  @Override
  public String description() {
    return "Return the number of rows in the frame.";
  }

  @Override
  public ValNum apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    return new ValNum(fr.numRows());
  }
}
