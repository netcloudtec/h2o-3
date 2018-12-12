package water.parser;

import java.io.File;
import org.junit.*;

import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.NFSFileVec;
import water.util.FileUtils;

public class ParseFolderTest extends TestUtil {
  @BeforeClass static public void setup() { stall_till_cloudsize(5); }

  @Test public void testProstate() {
    Frame k1 = null, k2 = null;
    try {
      k2 = parse_test_folder("smalldata/junit/parse_folder" );
      k1 = parse_test_file  ("smalldata/junit/parse_folder_gold.csv");
      Assert.assertTrue("parsed values do not match!", TestUtil.isBitIdentical(k1,k2));
    } finally {
      if( k1 != null ) k1.delete();
      if( k2 != null ) k2.delete();
    }
  }

  @Test public void testSameFile() {
    File f = FileUtils.locateFile("smalldata/iris/iris_wheader.csv");
    NFSFileVec nfs1 = NFSFileVec.make(f);
    NFSFileVec nfs2 = NFSFileVec.make(f);
    Frame fr = null;
    try {
      fr = ParseDataset.parse(Key.make(), new Key[]{nfs1._key, nfs2._key}, false/*delete on done*/, false, ParseSetup.GUESS_HEADER);
    } finally {
      if( fr != null ) fr.delete();
      if( nfs1 != null ) nfs1.remove();
    }
  }
}
