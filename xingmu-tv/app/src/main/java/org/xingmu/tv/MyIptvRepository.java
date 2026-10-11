package org.xingmu.tv;
import android.content.Context;import java.io.*;import java.nio.charset.StandardCharsets;
final class MyIptvRepository {
 static final String URL="https://raw.githubusercontent.com/suxuang/myIPTV/main/ipv4.m3u";
 static final String MIRROR="https://gh-proxy.org/raw.githubusercontent.com/suxuang/myIPTV/main/ipv4.m3u";
 private static final java.util.concurrent.atomic.AtomicBoolean updating=new java.util.concurrent.atomic.AtomicBoolean();
 static void refreshInBackground(Context c){Context app=c.getApplicationContext();long last=app.getSharedPreferences("sources",0).getLong("myiptvUpdated",0);if(System.currentTimeMillis()-last<6*60*60*1000L||!updating.compareAndSet(false,true))return;new Thread(()->{try{new MyIptvRepository(app).update();}catch(IOException ignored){}finally{updating.set(false);}},"myiptv-refresh").start();}
 private final Context context;MyIptvRepository(Context c){context=c.getApplicationContext();}
 String update()throws IOException{synchronized(MyIptvRepository.class){String text;try{text=PublicHttp.get(MIRROR);}catch(IOException e){text=PublicHttp.get(URL);}SubscriptionParser.Result parsed=SubscriptionParser.parse(text);File target=new File(context.getFilesDir(),"myiptv-catalog.json"),temp=new File(context.getFilesDir(),"myiptv-catalog.tmp");try(FileOutputStream out=new FileOutputStream(temp)){out.write(parsed.json.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}if(!temp.renameTo(target))throw new IOException("订阅保存失败");CatalogRepository.invalidate();context.getSharedPreferences("sources",0).edit().putLong("myiptvUpdated",System.currentTimeMillis()).apply();return "myIPTV 已更新："+parsed.entries+" 项频道，跳过 "+parsed.skipped+" 项不支持地址";}}
 String status(){long time=context.getSharedPreferences("sources",0).getLong("myiptvUpdated",0);return time==0?"已内置 myIPTV 目录，可随时刷新":"最近更新："+android.text.format.DateFormat.format("MM-dd HH:mm",time);}
}
