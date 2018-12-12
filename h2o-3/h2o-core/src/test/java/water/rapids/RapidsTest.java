package water.rapids;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.fvec.Frame;
import water.fvec.NFSFileVec;
import water.fvec.Vec;
import water.parser.ParseDataset;
import water.parser.ParseSetup;
import water.rapids.ast.AstRoot;
import water.rapids.ast.params.AstNumList;
import water.rapids.ast.params.AstStr;
import water.rapids.vals.ValFrame;
import water.util.ArrayUtils;
import water.util.FileUtils;
import water.util.Log;

import java.io.File;
import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.*;
import static water.rapids.Rapids.IllegalASTException;


public class RapidsTest extends TestUtil {
  @BeforeClass public static void setup() { stall_till_cloudsize(1); }

  @Test public void bigSlice() {
    // check that large slices do something sane
    String tree = "(rows a.hex [0:2147483647])";
    checkTree(tree);
  }

  @Test public void testParseString() {
    astStr_ok("'hello'", "hello");
    astStr_ok("\"one two three\"", "one two three");
    astStr_ok("\"  \\\"  \"", "  \"  ");
    astStr_ok("\"\\\\\"", "\\");
    astStr_ok("'test\"omg'", "test\"omg");
    astStr_ok("'sun\nmoon'", "sun\nmoon");
    astStr_ok("'a\\nb'", "a\nb");
    astStr_ok("'\\n\\r\\t\\b\\f\\'\\\"\\\\'", "\n\r\t\b\f\'\"\\");
    astStr_ok("'\\x00\\xa2\\xBC\\xDe\\xFF\\xcb'", "\u0000\u00A2\u00BC\u00DE\u00FF\u00CB");
    astStr_ok("\"\\uABCD\\u0000\\uffff\"", "\uABCD\u0000\uFFFF");
    astStr_ok("\"\\U0001F578\"", new String(Character.toChars(0x1F578)));

    parse_err("\"hello");
    parse_err("\"one\"two\"");
    parse_err("\"something\'");
    parse_err("'\\+'");
    parse_err("'\\0'");
    parse_err("'\\xA'");
    parse_err("'\\xHI");
    parse_err("'\\u123 spam'");
    parse_err("'\\U'");
    parse_err("'\\U12345678'");
    parse_err("'\\U1F578'");
    parse_err("'\\U+1F578'");
    parse_err("'\\u{1F578}'");
  }

  @Test public void testParseNumList() {
    astNumList_ok("[]", new double[0]);
    astNumList_ok("[1 2 3]", ard(1, 2, 3));
    astNumList_ok("[1, 2, 3]", ard(1, 2, 3));
    astNumList_ok("[1     , 2\t, 3,4]", ard(1, 2, 3, 4));
    astNumList_ok("[000.1 -3 17.003 2e+01 +11.1 1234567890]", ard(0.1, -3, 17.003, 20, 11.1, 1234567890));
    astNumList_ok("[NaN nan]", ard(Double.NaN, Double.NaN));
    astNumList_ok("[1 2:3 5:10:2]", ard(1, 2, 3, 4, 5, 7, 9, 11, 13, 15, 17, 19, 21, 23));
    astNumList_ok("[-0.5:10:0.1]", ard(-0.5, -0.4, -0.3, -0.2, -0.1, 0, 0.1, 0.2, 0.3, 0.4));
    parse_err("[21 11");
    parse_err("[1 0.00.0]");
    parse_err("[0 1 true false]");
    parse_err("[#1 #2 #3]");
    parse_err("[0 1 'hello']");
    parse_err("[1:0]");
    parse_err("[0:nan:2]");
    parse_err("[0:2:nan]");
    parse_err("[1:0:5]");
    parse_err("[1:-20]");
    parse_err("[1:20:-5]");
  }

  @Test public void test1() {
    // Checking `hex + 5`
    String tree = "(+ a.hex 5)";
    checkTree(tree);
  }

  @Test public void test2() {
    // Checking `hex + 5 + 10`
    String tree = "(+ a.hex (+ 5 10))";
    checkTree(tree);
  }

  @Test public void test3() {
    // Checking `hex + 5 - 1 * hex + 15 * (23 / hex)`
    String tree = "(+ (- (+ a.hex 5) (* 1 a.hex)) (* 15 (/ 23 a.hex)))";
    checkTree(tree);
  }

  @Test public void test4() {
    //Checking `hex == 5`, <=, >=, <, >, !=
    String tree = "(== a.hex 5)";
    checkTree(tree);
    tree = "(<= a.hex 5)";
    checkTree(tree);
    tree = "(>= a.hex 1.25132)";
    checkTree(tree);
    tree = "(< a.hex 112.341e-5)";
    checkTree(tree);
    tree = "(> a.hex 0.0123)";
    checkTree(tree);
    tree = "(!= a.hex 0)";
    checkTree(tree);
  }
  @Test public void test4_throws() {
    String tree = "(== a.hex (cols a.hex [1 2]))";
    checkTree(tree, "Frames must have same columns, found 4 columns and 2 columns.");
  }

  @Test public void test5() {
    // Checking `hex && hex`, ||, &, |
    String tree = "(&& a.hex a.hex)";
    checkTree(tree);
    tree = "(|| a.hex a.hex)";
    checkTree(tree);
    tree = "(& a.hex a.hex)";
    checkTree(tree);
    tree = "(| a.hex a.hex)";
    checkTree(tree);
  }

  @Test public void test6() {
    // Checking `hex[,1]`
    String tree = "(cols a.hex [0])";
    checkTree(tree);
    // Checking `hex[1,5]`
    tree = "(rows (cols a.hex [0]) [5])";
    checkTree(tree);
    // Checking `hex[c(1:5,7,9),6]`
    tree = "(cols (rows a.hex [0:4 6 7]) [0])";
    checkTree(tree);
    // Checking `hex[c(8,1,1,7),1]`
    // No longer handle dup or out-of-order rows
    tree = "(rows a.hex [2 7 8])";
    checkTree(tree);
  }

  @Test public void testRowAssign() {
    String tree;
    // Assign column 3 over column 0
    tree = "(:= a.hex (cols a.hex [3]) 0 [0:150])";
    checkTree(tree);

    // Assign 17 over column 0
    tree = "(:= a.hex 17 [0] [0:150])";
    checkTree(tree);

    // Assign 17 over column 0, row 5
    tree = "(:= a.hex 17 [0] [5])";
    checkTree(tree);

    // Append 17
    tree = "(append a.hex 17 \"nnn\")";
    checkTree(tree);
  }

  @Test public void testFun() {
    // Compute 3*3; single variable defined in function body
    String tree = "({var1 . (* var1 var1)} 3)";
    checkTree(tree);
    // Unknown var2
    tree = "({var1 . (* var1 var2)} 3)";
    checkTree(tree, "Name lookup of 'var2' failed");
    // Compute 3* a.hex[0,0]
    tree = "({var1 . (* var1 (rows a.hex [0]))} 3)";
    checkTree(tree);

    // Some more horrible functions.  Drop the passed function and return a 3
    tree = "({fun . 3} {y . (* y y)})";
    checkTree(tree);
    // Apply a 3 to the passed function
    tree = "({fun . (fun 3)} {y . (* y y)})";
    checkTree(tree);
    // Pass the squaring function thru the ID function
    tree = "({fun . fun} {y . (* y y)})";
    checkTree(tree);
    // Pass the squaring function thru the twice-apply-3 function
    tree = "({fun . (fun (fun 3))} {y . (* y y)})";
    checkTree(tree);
    // Pass the squaring function thru the twice-apply-x function
    tree = "({fun x . (fun (fun x))} {y . (* y y)} 3)";
    checkTree(tree);
    // Pass the squaring function thru the twice-apply function
    tree = " ({fun . {x . (fun (fun x))}} {y . (* y y)})   ";
    checkTree(tree);
    // Pass the squaring function thru the twice-apply function, and apply it
    tree = "(({fun . {x . (fun (fun x))}} {y . (* y y)}) 3)";
    checkTree(tree);
  }

  @Test public void testCBind() {
    String tree = "(cbind 1 2)";
    checkTree(tree);

    tree = "(cbind 1 a.hex 2)";
    checkTree(tree);

    tree = "(cbind a.hex (cols a.hex 0) 2)";
    checkTree(tree);
  }

  @Test public void testRBind() {
    String tree = "(rbind 1 2)";
    checkTree(tree);

    //tree = "(rbind a.hex 1 2)";
    //checkTree(tree);
  }

  @Test public void testApply() {
    // Sum, reduction.  1 row result
    String tree = "(apply a.hex 2 {x . (sum x)})";
    checkTree(tree);

    // Return ID column results.  Shared data result.
    tree = "(apply a.hex 2 {x . x})";
    checkTree(tree);

    // Return column results, new data result.
    tree = "(apply a.hex 2 abs)";
    checkTree(tree);

    // Return two results
    tree = "(apply a.hex 2 {x . (rbind (sumNA x) (sum x))})";
    checkTree(tree);
  }

  @Test public void testRowApply() {
    String tree = "(apply a.hex 1 sum)";
    checkTree(tree);

    tree = "(apply a.hex 1 max)";
    checkTree(tree);

    tree = "(apply a.hex 1 {x . (sum x)})";
    checkTree(tree);

    tree = "(apply a.hex 1 {x . (sum (* x x))})";
    checkTree(tree);

    // require lookup of 'y' outside the scope of the applied function.
    // doubles all values.
    tree = "({y . (apply a.hex 1 {x . (sum (* x y))})} 2)";
    checkTree(tree);
  }

  @Test public void testMath() {
    for( String s : new String[] {"abs", "cos", "sin", "acos", "ceiling", "floor", "cosh", "exp", "log", "sqrt", "tan", "tanh"} )
      checkTree("("+s+" a.hex)");
  }

  @Test public void testVariance() {
    // Checking variance: scalar
    String tree = "({x . (var x x \"everything\" FALSE)} (rows a.hex [0]))";
    checkTree(tree);

    tree = "({x . (var x x \"everything\" FALSE)} a.hex)";
    checkTree(tree);

    tree = "(table (trunc (cols a.hex 1)) FALSE)";
    checkTree(tree);

    tree = "(table (cols a.hex 1) FALSE)";
    checkTree(tree);

    tree = "(table (cols a.hex 1) (cols a.hex 2) FALSE)";
    checkTree(tree);
  }

  private void checkTree(String tree) { checkTree(tree, null); }
  private void checkTree(String tree, String thrownMessage) {
    //Frame r = frame(new double[][]{{-1},{1},{2},{3},{4},{5},{6},{254}});
    //Key ahex = Key.make("a.hex");
    //Frame fr = new Frame(ahex, null, new Vec[]{r.remove(0)});
    //r.delete();
    //DKV.put(ahex, fr);
    Frame fr = parse_test_file(Key.make("a.hex"),"smalldata/iris/iris_wheader.csv");
    fr.remove(4).remove();
    try {
      Val val = Rapids.exec(tree);
      Assert.assertNull(thrownMessage);
      System.out.println(val.toString());
      if (val instanceof ValFrame) {
        Frame fr2 = val.getFrame();
        System.out.println(fr2.vec(0));
        fr2.remove();
      }
    } catch( IllegalArgumentException iae ) {
      if (thrownMessage != null) {
        Assert.assertEquals(thrownMessage, iae.getMessage());
        Log.debug("Expected Exception suppressed", iae);
      } else
        throw iae;
    } finally {
      fr.delete();
    }
  }

  @Test public void testProstate_assign_frame_scalar() {
    Frame fr = parse_test_file(Key.make("prostate.hex"), "smalldata/logreg/prostate.csv");
    try {
      Val val = Rapids.exec("(tmp= py_1 (:= prostate.hex -1 1 (== (cols_py prostate.hex 1) 0)))");
      if (val instanceof ValFrame ) {
        Frame fr2 = val.getFrame();
        System.out.println(fr2.vec(0));
        fr2.remove();
      }
    } finally {
      fr.delete();
    }
  }

  @Test public void testCombo() {
    Frame fr = parse_test_file(Key.make("a.hex"),"smalldata/iris/iris_wheader.csv");
    String tree = "(tmp= py_2 (:= (tmp= py_1 (cbind a.hex (== (cols_py a.hex 4.0 ) \"Iris-setosa\" ) ) ) (as.factor (cols_py py_1 5.0 ) ) 5.0 [] ) )";
    //String tree = "(:= (tmp= py_1 a.hex) (h2o.runif a.hex -1) 4 [])";
    Val val = Rapids.exec(tree);
    if (val instanceof ValFrame ) {
      Frame fr2 = val.getFrame();
      System.out.println(fr2.vec(0));
      fr2.remove();
    }
    fr.delete();
  }

  @Test public void testMerge() {
    Frame l=null,r=null,f=null;
    try {
      l = ArrayUtils.frame("name" ,vec(ar("Cliff","Arno","Tomas","Spencer"),ari(0,1,2,3)));
      l.    add("age"  ,vec(ar(">dirt" ,"middle","middle","young'n"),ari(0,1,2,3)));
      l = new Frame(l);
      DKV.put(l);
      System.out.println(l);
      r = ArrayUtils.frame("name" ,vec(ar("Arno","Tomas","Michael","Cliff"),ari(0,1,2,3)));
      r.    add("skill",vec(ar("science","linearmath","sparkling","hacker"),ari(0,1,2,3)));
      r = new Frame(r);
      DKV.put(r);
      System.out.println(r);
      String x = String.format("(merge %s %s 1 0 [] [] \"hash\")",l._key,r._key);
      Val res = Rapids.exec(x);
      f = res.getFrame();
      System.out.println(f);
      Vec names = f.vec(0);
      Assert.assertEquals(names.factor(names.at8(0)),"Cliff");
      Vec ages  = f.vec(1);
      Assert.assertEquals(ages .factor(ages .at8(0)),">dirt");
      Vec skilz = f.vec(2);
      Assert.assertEquals(skilz.factor(skilz.at8(0)),"hacker");
    } finally {
      if( f != null ) f.delete();
      if( r != null ) r.delete();
      if( l != null ) l.delete();
    }
  }

  // test merge with strings with various settings.  Note, both frames contain String columns.
  // Some columns contains NA entries in the String columns.  There are any cases I considered here.
  // However, due to test timing, I choose one test to run randomly each time.
  @Test public void testMergeWithStrings() {
    String[] f1Names = new String[] {"smalldata/jira/PUBDEV_5266_merge_strings/PUBDEV_5266_f1_small_NAs.csv",
            "smalldata/jira/PUBDEV_5266_merge_strings/PUBDEV_5266_f1_small_NAs.csv",
            "smalldata/jira/PUBDEV_5266_merge_strings/PUBDEV_5266_f2_small_NAs.csv",
            "smalldata/jira/PUBDEV_5266_merge_strings/PUBDEV_5266_f1_small.csv",
            "smalldata/jira/PUBDEV_5266_merge_strings/PUBDEV_5266_f1_small.csv",
            "smalldata/jira/PUBDEV_5266_merge_strings/PUBDEV_5266_f2_small.csv"};
    String[] f2Names = new String[] {"smalldata/jira/PUBDEV_5266_merge_strings/PUBDEV_5266_f2_small_NAs.csv",
            "smalldata/jira/PUBDEV_5266_merge_strings/PUBDEV_5266_f2_small_NAs.csv",
            "smalldata/jira/PUBDEV_5266_merge_strings/PUBDEV_5266_f1_small_NAs.csv",
            "smalldata/jira/PUBDEV_5266_merge_strings/PUBDEV_5266_f2_small.csv",
            "smalldata/jira/PUBDEV_5266_merge_strings/PUBDEV_5266_f2_small.csv",
            "smalldata/jira/PUBDEV_5266_merge_strings/PUBDEV_5266_f1_small.csv"};
    String[] ansNames = new String[] {"smalldata/jira/PUBDEV_5266_merge_strings/mergedf1_f2_small_NAs.csv",
            "smalldata/jira/PUBDEV_5266_merge_strings/mergedf1_f2_x_T_small_NAs.csv",
            "smalldata/jira/PUBDEV_5266_merge_strings/mergedf2_f1_x_T_small_NAs.csv",
            "smalldata/jira/PUBDEV_5266_merge_strings/mergedf1_f2_small.csv",
            "smalldata/jira/PUBDEV_5266_merge_strings/mergedf1_f2_x_T_small.csv",
            "smalldata/jira/PUBDEV_5266_merge_strings/mergedf2_f1_x_T_small.csv"};
    String[] rapidStrings = new String[] {"(merge %s %s 0 0 [0] [0] \"radix\")",
            "(merge %s %s 1 0 [0] [0] \"radix\")",
            "(merge %s %s 1 0 [0] [0] \"radix\")",
            "(merge %s %s 0 0 [0] [0] \"radix\")",
            "(merge %s %s 1 0 [0] [0] \"radix\")",
            "(merge %s %s 1 0 [0] [0] \"radix\")"};
    int[][] stringColIndices = new int[][] {{1,2,4,5,6,10}, {1,2,4,5,6,10}, {1,2,3,7,8,9}, {1,2,4,5,6,10},
            {1,2,4,5,6,10}, {1,2,3,7,8,9}};
    //for (int index = 0; index < ansNames.length; index++) { // 0, 1 pass, 2,3 failed
    Random newRand = new Random();
    int index = newRand.nextInt(rapidStrings.length);
    testMergeStringOneSetting(f1Names[index], f2Names[index], ansNames[index], rapidStrings[index], stringColIndices[index]);
   // }
  }

  private void testMergeStringOneSetting(String f1Name, String f2Name, String ansName, String rapidString, int[] stringCols) {
    Scope.enter();
    Frame f1=null, f2=null, mergeRes=null, ans=null;

    try {
      f1 = parse_test_file(f1Name);
      f2 = parse_test_file(f2Name);
      if (f1.numCols() < f2.numCols()) {
        f1.setNames(new String[]{"int1", "stringf1", "stringf1-2", "intf1-2"});
        f2.setNames(new String[]{"int1", "stringf2", "stringf2-2", "stringf2-3", "intf2-5", "intf2-3", "intf2-4", "stringf2-4"});
      } else {
        f2.setNames(new String[]{"int1", "stringf1", "stringf1-2", "intf1-2"});
        f1.setNames(new String[]{"int1", "stringf2", "stringf2-2", "stringf2-3", "intf2-5", "intf2-3", "intf2-4", "stringf2-4"});
      }
      ans = parse_test_file(ansName);
      for (int col=0; col < stringCols.length; col++) // change enum column back to string columns
        ans.replace(stringCols[col], ans.vec(stringCols[col]).toStringVec()).remove();
      Scope.track(f1);
      Scope.track(f2);
      Scope.track(ans);
      String x = String.format(rapidString, f1._key, f2._key);
      Val res = Rapids.exec(x);
      mergeRes = res.getFrame();
      Scope.track(mergeRes);
      assertTrue(isBitIdentical(ans, mergeRes)); // compare our merge frame with answer from R
    } finally {
      Scope.exit();
    }

  }


  @Test public void testQuantile() {
    Frame f = null;
    try {
      Frame fr = ArrayUtils.frame(ard(ard(1.223292e-02),
              ard(1.635312e-25),
              ard(1.601522e-11),
              ard(8.452298e-10),
              ard(2.643733e-10),
              ard(2.671520e-06),
              ard(1.165381e-06),
              ard(7.193265e-10),
              ard(3.383532e-04),
              ard(2.561221e-05)));
      double[] probs = new double[]{0.001, 0.005, .01, .02, .05, .10, .50, .8883, .90, .99};
      String x = String.format("(quantile %s %s \"interpolate\" _)", fr._key, Arrays.toString(probs));
      Val val = Rapids.exec(x);
      fr.delete();
      f = val.getFrame();
      Assert.assertEquals(2,f.numCols());
      // Expected values computed as golden values from R's quantile call
      double[] exp = ard(1.4413698000016206E-13, 7.206849000001562E-13, 1.4413698000001489E-12, 2.882739600000134E-12, 7.20684900000009E-12,
              1.4413698000000017E-11, 5.831131148999999E-07, 3.3669567275300000E-04, 0.00152780988        , 0.011162408988      );
      for( int i=0; i<exp.length; i++ )
        Assert.assertTrue( "expected "+exp[i]+" got "+f.vec(1).at(i), water.util.MathUtils.compare(exp[i],f.vec(1).at(i),1e-6,1e-6) );
    } finally {
      if( f != null ) f.delete();
    }
  }

  static void exec_str( String str, Session ses ) {
    Val val = Rapids.exec(str,ses);
    switch( val.type() ) {
    case Val.FRM:
      Frame fr = val.getFrame();
      System.out.println(fr);
      checkSaneFrame();
      break;
    case Val.NUM:
      System.out.println("num= "+val.getNum());
      checkSaneFrame();
      break;
    case Val.STR:
      System.out.println("str= "+val.getStr());
      checkSaneFrame();
      break;
    default:
      throw water.H2O.fail();
    }
  }

  static void checkSaneFrame() {  assert checkSaneFrame_impl(); }
  static boolean checkSaneFrame_impl() {
    for( Key k : H2O.localKeySet() ) {
      Value val = Value.STORE_get(k);
      if( val != null && val.isFrame() ) {
        Frame fr = val.get();
        Vec vecs[] = fr.vecs();
        for( int i=0; i<vecs.length; i++ ) {
          Vec v = vecs[i];
          if( DKV.get(v._key) == null ) {
            System.err.println("Frame "+fr._key+" in the DKV, is missing Vec "+v._key+", name="+fr._names[i]);
            return false;
          }
        }
      }
    }
    return true;
  }

  /*
  This test will generate a random sorted array and test only the index method of AstNumList.java. It will check
  and make sure every index of the array is returned correctly by the index method.
   */
  @Test public void testAstNumListIndex() {
    Random rand = new Random();
    int numElement = 2000;
    int[] array2H2O = new int[numElement];  // store sorted integer array
    int arrayVal = rand.nextInt(Integer.SIZE-1);

    for (int val = 0; val < numElement; val++) {
      int randValue = rand.nextInt(100);
      arrayVal += randValue;
      if (randValue==0)   // avoid duplicated elements
        arrayVal++;
      array2H2O[val]=Math.abs(arrayVal);
    }
    AstNumList h2oArray = new AstNumList(array2H2O);
    h2oArray._isSort=true;  // array is sorted
    // check to make sure indext returned by method index is correct
    for (int index=0; index < numElement; index++) {
      int val = array2H2O[index];
      int h2oIndex = (int) h2oArray.index(val);
      assertEquals(index, h2oIndex);
    }
  }

  @Test public void testRowSlice() {
    Session ses = new Session();
    Frame fr = null;
    try {
      fr = parse_test_file(Key.make("a.hex"),"smalldata/airlines/AirlinesTrainMM.csv.zip");
      System.out.printf(fr.toString());
      Rapids.exec("(h2o.runif a.hex -1)->flow_1",ses);
      Rapids.exec("(tmp= f.25 (rows a.hex (<  flow_1 0.25) ) )",ses);
      Rapids.exec("(rows a.hex (>= flow_1 0.25) )->f.75",ses);
      Rapids.exec("(h2o.runif a.hex -1)->flow_2",ses);
      ses.end(null);
    } catch( Throwable ex ) {
      throw ses.endQuietly(ex);
    } finally {
      if (fr != null) fr.delete();
    }
  }

  @Test public void testChicago() {
    String oldtz = Rapids.exec("(getTimeZone)").getStr();
    Session ses = new Session();
    try {
      parse_test_file(Key.make("weather.hex"),"smalldata/chicago/chicagoAllWeather.csv");
      parse_test_file(Key.make( "crimes.hex"),"smalldata/chicago/chicagoCrimes10k.csv.zip");
      String fname = "smalldata/chicago/chicagoCensus.csv";
      File f = FileUtils.locateFile(fname);
      assert f != null && f.exists():" file not found: " + fname;
      NFSFileVec nfs = NFSFileVec.make(f);
      ParseSetup ps = ParseSetup.guessSetup(new Key[]{nfs._key}, false, 1);
      ps.getColumnTypes()[1] = Vec.T_CAT;
      ParseDataset.parse(Key.make( "census.hex"), new Key[]{nfs._key}, true, ps);
      
      exec_str("(assign census.hex (colnames= census.hex\t[0 1 2 3 4 5 6 7 8] \n" +
               "['Community.Area.Number' 'COMMUNITY.AREA.NAME' \"PERCENT.OF.HOUSING.CROWDED\" \r\n" +
               " \"PERCENT.HOUSEHOLDS.BELOW.POVERTY\" \"PERCENT.AGED.16..UNEMPLOYED\" " +
               " \"PERCENT.AGED.25..WITHOUT.HIGH.SCHOOL.DIPLOMA\" \"PERCENT.AGED.UNDER.18.OR.OVER.64\" " +
               " \"PER.CAPITA.INCOME.\" \"HARDSHIP.INDEX\"]))", ses);

      exec_str("(assign crimes.hex (colnames= crimes.hex [0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21] [\"ID\" \"Case.Number\" \"Date\" \"Block\" \"IUCR\" \"Primary.Type\" \"Description\" \"Location.Description\" \"Arrest\" \"Domestic\" \"Beat\" \"District\" \"Ward\" \"Community.Area\" \"FBI.Code\" \"X.Coordinate\" \"Y.Coordinate\" \"Year\" \"Updated.On\" \"Latitude\" \"Longitude\" \"Location\"]))", ses);

      exec_str("(setTimeZone \"Etc/UTC\")", ses);

      exec_str("(assign crimes.hex (append crimes.hex (tmp= unary_op_6 (day (tmp= nary_op_5 (cols crimes.hex [2])))) \"Day\"))", ses);

      checkSaneFrame();
      exec_str("(assign crimes.hex (append crimes.hex (tmp= binary_op_31 (+ (tmp= unary_op_7 (month nary_op_5)) 1)) \"Month\"))", ses);

      exec_str("(rm nary_op_30)",ses);

      exec_str("(assign crimes.hex (append crimes.hex (tmp= binary_op_32 (+ (tmp= binary_op_9 (- (tmp= unary_op_8 (year nary_op_5)) 1900)) 1900)) \"Year\"))", ses);

      exec_str("(assign crimes.hex (append crimes.hex (tmp= unary_op_10 (week nary_op_5)) \"WeekNum\"))", ses);

      exec_str("(rm binary_op_32)",ses);
      exec_str("(rm binary_op_31)",ses);
      exec_str("(rm unary_op_8)",ses);
      checkSaneFrame();

      exec_str("(assign crimes.hex (append crimes.hex (tmp= unary_op_11 (dayOfWeek nary_op_5)) \"WeekDay\"))", ses);
      exec_str("(rm 'nfs:\\\\C:\\\\Users\\\\cliffc\\\\Desktop\\\\h2o-3\\\\smalldata\\\\chicago\\\\chicagoCrimes10k.csv.zip')", ses);

      exec_str("(assign crimes.hex (append crimes.hex (tmp= unary_op_12 (hour nary_op_5)) \"HourOfDay\"))", ses);

      exec_str("(assign crimes.hex (append crimes.hex (tmp= nary_op_16 (ifelse (tmp= binary_op_15 (| (tmp= binary_op_13 (== unary_op_11 \"Sun\")) (tmp= binary_op_14 (== unary_op_11 \"Sat\")))) 1 0)) \"Weekend\"))", ses);

      // Season is incorrectly assigned in the original chicago demo; picks up the Weekend flag
      exec_str("(assign crimes.hex (append crimes.hex nary_op_16 \"Season\"))", ses);

      // Standard "head of 10 rows" pattern for printing
      exec_str("(tmp= subset_33 (rows crimes.hex [0:10]))", ses);
      exec_str("(rm subset_33)",ses);

      exec_str("(rm subset_33)",ses);
      exec_str("(rm unary_op_29)",ses);
      exec_str("(rm nary_op_28)",ses);
      exec_str("(rm nary_op_27)",ses);
      exec_str("(rm nary_op_26)",ses);
      exec_str("(rm binary_op_25)",ses);
      exec_str("(rm binary_op_24)",ses);
      exec_str("(rm binary_op_23)",ses);
      exec_str("(rm binary_op_22)",ses);
      exec_str("(rm binary_op_21)",ses);
      exec_str("(rm binary_op_20)",ses);
      exec_str("(rm binary_op_19)",ses);
      exec_str("(rm binary_op_18)",ses);
      exec_str("(rm binary_op_17)",ses);
      exec_str("(rm nary_op_16)",ses);
      exec_str("(rm binary_op_15)",ses);
      exec_str("(rm binary_op_14)",ses);
      exec_str("(rm binary_op_13)",ses);
      exec_str("(rm unary_op_12)",ses);
      exec_str("(rm unary_op_11)",ses);
      exec_str("(rm unary_op_10)",ses);
      exec_str("(rm binary_op_9)",ses);
      exec_str("(rm unary_op_8)",ses);
      exec_str("(rm unary_op_7)",ses);
      exec_str("(rm unary_op_6)",ses);
      exec_str("(rm nary_op_5)",ses);
      checkSaneFrame();

      // Standard "head of 10 rows" pattern for printing
      exec_str("(tmp= subset_34 (rows crimes.hex [0:10]))", ses);
      exec_str("(rm subset_34)",ses);

      exec_str("(assign census.hex (colnames= census.hex [0 1 2 3 4 5 6 7 8] [\"Community.Area\" \"COMMUNITY.AREA.NAME\" \"PERCENT.OF.HOUSING.CROWDED\" \"PERCENT.HOUSEHOLDS.BELOW.POVERTY\" \"PERCENT.AGED.16..UNEMPLOYED\" \"PERCENT.AGED.25..WITHOUT.HIGH.SCHOOL.DIPLOMA\" \"PERCENT.AGED.UNDER.18.OR.OVER.64\" \"PER.CAPITA.INCOME.\" \"HARDSHIP.INDEX\"]))", ses);
      exec_str("(rm subset_34)",ses);

      exec_str("(tmp= subset_35 (cols  crimes.hex [-3]))", ses);
      exec_str("(tmp= subset_36 (cols weather.hex [-1]))", ses);

      exec_str("(tmp= subset_36_2 (colnames= subset_36 [0 1 2 3 4 5] [\"Month\" \"Day\" \"Year\" \"maxTemp\" \"meanTemp\" \"minTemp\"]))", ses);

      exec_str("(rm crimes.hex)",ses);
      exec_str("(rm weather.hex)",ses);

      // nary_op_37 = merge( X Y ); Vecs in X & nary_op_37 shared
      exec_str("(tmp= nary_op_37 (merge subset_35 census.hex TRUE FALSE [] [] \"hash\"))", ses);

      // nary_op_38 = merge( nary_op_37 subset_36_2); Vecs in nary_op_38 and nary_pop_37 and X shared
      exec_str("(tmp= subset_41 (rows (tmp= nary_op_38 (merge nary_op_37 subset_36_2 TRUE FALSE [] [] \"hash\")) (tmp= binary_op_40 (<= (tmp= nary_op_39 (h2o.runif nary_op_38 30792152736.5179)) 0.8))))", ses);

      // Standard "head of 10 rows" pattern for printing
      exec_str("(tmp= subset_44 (rows subset_41 [0:10]))", ses);
      exec_str("(rm subset_44)",ses);
      exec_str("(rm subset_44)",ses);
      exec_str("(rm binary_op_40)",ses);
      exec_str("(rm nary_op_37)",ses);

      exec_str("(tmp= subset_43 (rows nary_op_38 (tmp= binary_op_42 (> nary_op_39 0.8))))", ses);

      // Chicago demo continues on past, but this is all I've captured for now

      checkSaneFrame();
      ses.end(null);

    } catch( Throwable ex ) {
      throw ses.endQuietly(ex);
    } finally {
      Rapids.exec("(setTimeZone \""+oldtz+"\")"); // Restore time zone (which is global, and will affect following tests)

      for( String s : new String[]{"weather.hex","crimes.hex","census.hex",
                                   "nary_op_5", "unary_op_6", "unary_op_7", "unary_op_8", "binary_op_9",
                                   "unary_op_10", "unary_op_11", "unary_op_12", "binary_op_13",
                                   "binary_op_14", "binary_op_15", "nary_op_16", "binary_op_17",
                                   "binary_op_18", "binary_op_19", "binary_op_20", "binary_op_21",
                                   "binary_op_22", "binary_op_23", "binary_op_24", "binary_op_25",
                                   "nary_op_26", "nary_op_27", "nary_op_28", "unary_op_29", "binary_op_30",
                                   "binary_op_31", "binary_op_32", "subset_33", "subset_34", "subset_35",
                                   "subset_36", "subset_36_2", "nary_op_37", "nary_op_38", "nary_op_39", "binary_op_40",
                                   "subset_41", "binary_op_42", "subset_43", "subset_44", } )
        Keyed.remove(Key.make(s));
    }
  }

  @Test public void test_frameKeyStartsWithNumber() {
    Frame fr = parse_test_file(Key.make("123STARTSWITHDIGITS"), "smalldata/logreg/prostate.csv");
    try {
      Val val = Rapids.exec("(cols_py 123STARTSWITHDIGITS 'ID')");
      Assert.assertNotNull(val);
      val.getFrame().delete();
    } finally {
      fr.delete();
    }
  }

  private static void astNumList_ok(String expr, double[] expected) {
    AstRoot res = Rapids.parse(expr);
    assertTrue(res instanceof AstNumList);
    if (expected != null)
      assertArrayEquals(expected, ((AstNumList)res).expand(), 1e-10);
  }

  private static void astStr_ok(String expr, String expected) {
    AstRoot res = Rapids.parse(expr);
    assertTrue(res instanceof AstStr);
    assertEquals(expected, ((AstStr)res).getStr());
  }

  private static void parse_err(String expr) {
    try {
      Rapids.parse(expr);
      fail("Expression " + expr + " expected to fail, however it did not.");
    } catch (IllegalASTException ignored) {}
  }
}
