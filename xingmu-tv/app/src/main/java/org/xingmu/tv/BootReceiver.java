package org.xingmu.tv;
import android.content.*;import android.os.Build;import android.app.*;import android.Manifest;import android.content.pm.PackageManager;
public final class BootReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context context,Intent intent){if(!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())||!context.getSharedPreferences("settings",0).getBoolean("boot",false))return;Intent open=new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
 notifyReady(context,open);
 try{context.startActivity(open);}catch(RuntimeException ignored){}
 }
 private static void notifyReady(Context context,Intent open){
 NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(new NotificationChannel("boot","开机观看提醒",NotificationManager.IMPORTANCE_DEFAULT));if(Build.VERSION.SDK_INT>=33&&context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;PendingIntent tap=PendingIntent.getActivity(context,20,open,PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(context,"boot"):new Notification.Builder(context);manager.notify(21,b.setSmallIcon(org.xingmu.tv.R.drawable.ic_tv).setContentTitle("星幕 TV 已准备好").setContentText("点击进入；系统限制后台自动打开时，可开启厂商自启动或选择默认桌面方案。").setContentIntent(tap).setAutoCancel(true).build());
 }
}
