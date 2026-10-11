package org.xingmu.tv;
import java.util.*;
/** Small process-local detail cache avoids a second request after searching. */
final class VodCache {
 private static final Map<String,CacheEntry> entries=new LinkedHashMap<String,CacheEntry>(64,.75f,true){@Override protected boolean removeEldestEntry(Map.Entry<String,CacheEntry> eldest){return size()>64;}};
 private static final class CacheEntry{final VodApi.Movie movie;final long time=android.os.SystemClock.elapsedRealtime();CacheEntry(VodApi.Movie m){movie=m;}}
 static synchronized void put(String api,VodApi.Movie movie){if(!movie.play.isEmpty())entries.put(api+"\n"+movie.id,new CacheEntry(movie));}
 static synchronized VodApi.Movie get(String api,String id){CacheEntry entry=entries.get(api+"\n"+id);return entry!=null&&android.os.SystemClock.elapsedRealtime()-entry.time<600000?entry.movie:null;}
}
