package org.xingmu.tv;
import android.content.Context;import java.io.*;import java.nio.charset.StandardCharsets;
final class ZbdsRepository {
 static final String M3U="https://live.zbds.top/tv/iptv4.m3u",TXT="https://live.zbds.top/tv/iptv4.txt";
 private static final java.util.concurrent.atomic.AtomicBoolean busy=new java.util.concurrent.atomic.AtomicBoolean();
 private final Context context;ZbdsRepository(Context c){context=c.getApplicationContext();}
 static void refreshInBackground(Context c){Context app=c.getApplicationContext();long last=app.getSharedPreferences("sources",0).getLong("zbdsUpdated",0);if(System.currentTimeMillis()-last<6*60*60*1000L||!busy.compareAndSet(false,true))return;new Thread(()->{try{new ZbdsRepository(app).update();}catch(IOException ignored){}finally{busy.set(false);}},"zbds-refresh").start();}
 String update()throws IOException{synchronized(ZbdsRepository.class){SubscriptionParser.Result result;try{result=LiveSubscription.parse(PublicHttp.get(M3U));}catch(IOException e){result=LiveSubscription.parse(PublicHttp.get(TXT));}File temp=new File(context.getFilesDir(),"zbds-catalog.tmp"),target=new File(context.getFilesDir(),"zbds-catalog.json");try(FileOutputStream out=new FileOutputStream(temp)){out.write(result.json.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}if(!temp.renameTo(target))throw new IOException("直播目录保存失败");CatalogRepository.invalidate();context.getSharedPreferences("sources",0).edit().putLong("zbdsUpdated",System.currentTimeMillis()).apply();return "zbds 已更新："+result.entries+" 个频道，跳过 "+result.skipped+" 个不支持地址";}}
}
