package water.util;

import org.joda.time.DurationFieldType;
import org.joda.time.Period;
import org.joda.time.PeriodType;
import org.joda.time.format.PeriodFormat;
import org.joda.time.format.PeriodFormatter;

import water.fvec.C1SChunk;
import water.fvec.C2SChunk;
import water.fvec.C4SChunk;
import water.fvec.Chunk;

import static java.lang.Double.isNaN;

import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class PrettyPrint {
  public static String msecs(long msecs, boolean truncate) {
    final long hr  = TimeUnit.MILLISECONDS.toHours  (msecs); msecs -= TimeUnit.HOURS  .toMillis(hr);
    final long min = TimeUnit.MILLISECONDS.toMinutes(msecs); msecs -= TimeUnit.MINUTES.toMillis(min);
    final long sec = TimeUnit.MILLISECONDS.toSeconds(msecs); msecs -= TimeUnit.SECONDS.toMillis(sec);
    final long ms  = TimeUnit.MILLISECONDS.toMillis (msecs);
    if( !truncate ) return String.format("%02d:%02d:%02d.%03d", hr, min, sec, ms);
    if( hr != 0 ) return String.format("%2d:%02d:%02d.%03d", hr, min, sec, ms);
    if( min != 0 ) return String.format("%2d min %2d.%03d sec", min, sec, ms);
    return String.format("%2d.%03d sec", sec, ms);
  }
  public static String usecs(long usecs) {
    final long hr = TimeUnit.MICROSECONDS.toHours (usecs); usecs -= TimeUnit.HOURS .toMicros(hr);
    final long min = TimeUnit.MICROSECONDS.toMinutes(usecs); usecs -= TimeUnit.MINUTES.toMicros(min);
    final long sec = TimeUnit.MICROSECONDS.toSeconds(usecs); usecs -= TimeUnit.SECONDS.toMicros(sec);
    final long ms = TimeUnit.MICROSECONDS.toMillis(usecs); usecs -= TimeUnit.MILLISECONDS.toMicros(ms);
    if( hr != 0 ) return String.format("%2d:%02d:%02d.%03d", hr, min, sec, ms);
    if( min != 0 ) return String.format("%2d min %2d.%03d sec", min, sec, ms);
    if( sec != 0 ) return String.format("%2d.%03d sec", sec, ms);
    if( ms != 0 ) return String.format("%3d.%03d msec", ms, usecs);
    return String.format("%3d usec", usecs);
  }

  public static String toAge(Date from, Date to) {
    if (from == null || to == null) return "N/A";
    final Period period = new Period(from.getTime(), to.getTime());
    DurationFieldType[] dtf = new ArrayList<DurationFieldType>() {{
      add(DurationFieldType.years()); add(DurationFieldType.months());
      add(DurationFieldType.days());
      if (period.getYears() == 0 && period.getMonths() == 0 && period.getDays() == 0) {
        add(DurationFieldType.hours());
        add(DurationFieldType.minutes());
      }

    }}.toArray(new DurationFieldType[0]);

    PeriodFormatter pf = PeriodFormat.getDefault();
    return pf.print(period.normalizedStandard(PeriodType.forFields(dtf)));
  }

  // Return X such that (bytes < 1L<<(X*10))
  static int byteScale(long bytes) {
    if (bytes<0) return -1;
    for( int i=0; i<6; i++ )
      if( bytes < 1L<<(i*10) )
        return i;
    return 6;
  }
  static double bytesScaled(long bytes, int scale) {
    if( scale <= 0 ) return bytes;
    return bytes / (double)(1L<<((scale-1)*10));
  }
  static final String[] SCALE = new String[] {"N/A (-ve)","Zero  ","%4.0f  B","%.1f KB","%.1f MB","%.2f GB","%.3f TB","%.3f PB"};
  public static String bytes(long bytes) { return bytes(bytes,byteScale(bytes)); }
  static String bytes(long bytes, int scale) { return String.format(SCALE[scale+1],bytesScaled(bytes,scale)); }
  public static String bytesPerSecond(long bytes) {
    if( bytes < 0 ) return "N/A";
    return bytes(bytes)+"/S";
  }

  static double [] powers10 = new double[]{
    0.0000000001,
    0.000000001,
    0.00000001,
    0.0000001,
    0.000001,
    0.00001,
    0.0001,
    0.001,
    0.01,
    0.1,
    1.0,
    10.0,
    100.0,
    1000.0,
    10000.0,
    100000.0,
    1000000.0,
    10000000.0,
    100000000.0,
    1000000000.0,
    10000000000.0,
  };

  static public long [] powers10i = new long[]{
    1l,
    10l,
    100l,
    1000l,
    10000l,
    100000l,
    1000000l,
    10000000l,
    100000000l,
    1000000000l,
    10000000000l,
    100000000000l,
    1000000000000l,
    10000000000000l,
    100000000000000l,
    1000000000000000l,
    10000000000000000l,
    100000000000000000l,
    1000000000000000000l,
  };

  public static double pow10(long m, int e){
    return e < 0?m/pow10(-e):m*pow10(e);
  }
  private static double pow10(int exp){ return ((exp >= -10 && exp <= 10)?powers10[exp+10]:Math.pow(10, exp)); }
  public static long pow10i(int exp){ return ((exp > -1 && exp < 19)?powers10i[exp]:(long)Math.pow(10, exp)); }
  public static boolean fitsIntoInt(double d) { return Math.abs((int)d - d) < 1e-8; }


  // About as clumsy and random as a blaster...
  public static String UUID( long lo, long hi ) {
    long lo0 = (lo>>32)&0xFFFFFFFFL;
    long lo1 = (lo>>16)&0xFFFFL;
    long lo2 = (lo>> 0)&0xFFFFL;
    long hi0 = (hi>>48)&0xFFFFL;
    long hi1 = (hi>> 0)&0xFFFFFFFFFFFFL;
    return String.format("%08X-%04X-%04X-%04X-%012X",lo0,lo1,lo2,hi0,hi1);
  }

  public static String uuid(java.util.UUID uuid) {
    return uuid == null ? "(N/A)" : UUID(uuid.getLeastSignificantBits(), uuid.getMostSignificantBits());
  }

  public static String number(Chunk chk, double d, int precision) {
    long l = (long)d;
    if( (double)l == d ) return Long.toString(l);
    if( precision > 0 ) return x2(d,PrettyPrint.pow10(-precision));
    Class chunkClass = chk.getClass();
    if( chunkClass == C1SChunk.class ) return x2(d,((C1SChunk)chk).scale());
    if( chunkClass == C2SChunk.class ) return x2(d,((C2SChunk)chk).scale());
    if( chunkClass == C4SChunk.class ) return x2(d,((C4SChunk)chk).scale());
    return Double.toString(d);
  }

  private static String x2( double d, double scale ) {
    String s = Double.toString(d);
    // Double math roundoff error means sometimes we get very long trailing
    // strings of junk 0's with 1 digit at the end... when we *know* the data
    // has only "scale" digits.  Chop back to actual digits
    int ex = (int)Math.log10(scale);
    int x = s.indexOf('.');
    int y = x+1+(-ex);
    if( x != -1 && y < s.length() ) s = s.substring(0,x+1+(-ex));
    while( s.charAt(s.length()-1)=='0' )
      s = s.substring(0,s.length()-1);
    return s;
  }

  public static String formatPct(double pct) {
    String s = "N/A";
    if( !isNaN(pct) )
      s = String.format("%5.2f %%", 100 * pct);
    return s;
  }

  /**
   * This method takes a number, and returns the
   * string form of the number with the proper
   * ordinal indicator attached (e.g. 1->1st, and 22->22nd)
   * @param i - number to have ordinal indicator attached
   * @return string form of number along with ordinal indicator as a suffix
   */
  public static String withOrdinalIndicator(long i) {
    String ord;
    // Grab second to last digit
    int d = (int) (Math.abs(i) / Math.pow(10, 1)) % 10;
    if (d == 1) ord = "th"; //teen values all end in "th"
    else { // not a weird teen number
      d = (int) (Math.abs(i) / Math.pow(10, 0)) % 10;
      switch (d) {
        case 1: ord = "st"; break;
        case 2: ord = "nd"; break;
        case 3: ord = "rd"; break;
        default: ord = "th";
      }
    }
    return i+ord;
  }
}
