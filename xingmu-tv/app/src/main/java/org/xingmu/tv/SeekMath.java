package org.xingmu.tv;
public final class SeekMath {
 public static long target(long start,float distance,int width,long duration){if(duration<=0)return Math.max(0,start);long clamped=Math.max(0,Math.min(start,duration));if(width<=0||Float.isNaN(distance)||Float.isInfinite(distance))return clamped;double offset=distance/width*Math.min(duration,120000L);return (long)Math.max(0,Math.min(duration,clamped+offset));}
 public static long step(long start,long delta,long duration){if(duration<=0)return Math.max(0,start);return (long)Math.max(0,Math.min(duration,start+(double)delta));}
 public static String time(long position){long seconds=Math.max(0,position)/1000;return String.format(java.util.Locale.ROOT,"%02d:%02d:%02d",seconds/3600,(seconds/60)%60,seconds%60);}
}
