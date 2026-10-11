package org.xingmu.tv;
import java.util.*;
/** Monotonic-clock samples, excluding old bytes on resume or cache restoration. */
final class DownloadRates {
 private static final class Sample{long bytes,time;double speed;boolean active;Sample(long bytes,long time,boolean active){this.bytes=bytes;this.time=time;this.active=active;}}
 private final Map<String,Sample> samples=new HashMap<>();
 double update(String id,long bytes,long now,boolean active){Sample old=samples.get(id);if(old==null||!active||!old.active||bytes<old.bytes||now<=old.time){samples.put(id,new Sample(bytes,now,active));return 0;}long elapsed=now-old.time;if(elapsed<250)return old.speed;old.speed=(bytes-old.bytes)*1000d/elapsed;old.bytes=bytes;old.time=now;return old.speed;}
 double get(String id){Sample sample=samples.get(id);return sample==null?0:sample.speed;}
 void retain(Set<String> ids){samples.keySet().retainAll(ids);}void clear(){samples.clear();}
 static String text(double speed){if(Double.isNaN(speed)||Double.isInfinite(speed)||speed<0)speed=0;return speed>=1048576?String.format(Locale.ROOT,"%.2f MB/s",speed/1048576):speed>=1024?String.format(Locale.ROOT,"%.0f KB/s",speed/1024):String.format(Locale.ROOT,"%.0f B/s",speed);}
}
