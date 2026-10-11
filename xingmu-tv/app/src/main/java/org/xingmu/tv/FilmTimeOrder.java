package org.xingmu.tv;
import java.util.regex.*;
/** Order release years with unknown values last in either direction. */
public final class FilmTimeOrder {
 public static int year(String value){Matcher m=Pattern.compile("(?<![0-9])(?:18|19|20|21)[0-9]{2}(?![0-9])").matcher(value==null?"":value);return m.find()?Integer.parseInt(m.group()):-1;}
 public static int compare(String left,String right,boolean newest){int a=year(left),b=year(right);if(a<0)return b<0?0:1;if(b<0)return -1;return newest?Integer.compare(b,a):Integer.compare(a,b);}
}
