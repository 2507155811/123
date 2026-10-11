package org.xingmu.tv;
import android.content.Context;
import androidx.media3.datasource.*;
import androidx.media3.datasource.cache.*;
import androidx.media3.database.StandaloneDatabaseProvider;
import java.io.File;
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class PlaybackCache {
 private static SimpleCache cache;
 static synchronized DataSource.Factory factory(Context context,DataSource.Factory upstream){try{if(cache==null)cache=new SimpleCache(new File(context.getApplicationContext().getCacheDir(),"video-buffer"),new LeastRecentlyUsedCacheEvictor(64L*1024*1024),new StandaloneDatabaseProvider(context.getApplicationContext()));return new CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(upstream).setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);}catch(RuntimeException e){return upstream;}}
}
