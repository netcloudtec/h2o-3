package water.rapids.ast;

import water.rapids.Env;
import water.rapids.Val;
import water.rapids.vals.ValFun;
import water.util.SB;

import java.util.ArrayList;

/**
 * Apply A Function.  Basic function execution.
 */
public class AstExec extends AstRoot {
  public final AstRoot[] _asts;

  public AstExec() {
    this((AstRoot[])null);
  }

  public AstExec(AstRoot[] asts) {
    _asts = asts;
  }

  public AstExec(ArrayList<AstRoot> asts) {
    _asts = asts.toArray(new AstRoot[asts.size()]);
  }

  @Override
  public String str() {
    SB sb = new SB().p('(');
    for (AstRoot ast : _asts)
      sb.p(ast.toString()).p(' ');
    return sb.p(')').toString();
  }

  @Override
  public String example() {
    return "(func ...args)";
  }

  @Override
  public String description() {
    return "List of whitespace-separated tokens within parenthesis is interpreted as a function application. The " +
        "first argument must be a function name (or an expression returning a function), all other tokens are passed " +
        "to the function as arguments. For example: `(sqrt 16)`, `(+ 2 3)`, `(getTimeZone)`, etc.";
  }

  // Function application.  Execute the first AstRoot and verify that it is a
  // function.  Then call that function's apply method.  Do not evaluate other
  // arguments; e.g. short-circuit logicals' apply calls may choose to not ever
  // evalute some arguments.
  @Override
  public Val exec(Env env) {
    Val fun = _asts[0].exec(env);
    if (!fun.isFun())
      throw new IllegalArgumentException("Expected a function but found " + fun.getClass());
    AstPrimitive ast = fun.getFun();
    int nargs = ast.nargs();
    if (nargs != -1 && nargs != _asts.length)
      throw new IllegalArgumentException(
          "Incorrect number of arguments; '" + ast + "' expects " + (nargs - 1) + " but was passed " + (_asts.length - 1));
    try (Env.StackHelp stk = env.stk()) {
      return env.returning(ast.apply(env, stk, _asts));
    }
  }

  public String[] getArgs() {
    return ((ValFun) _asts[0].exec(new Env(null))).getArgs();
  }
}
