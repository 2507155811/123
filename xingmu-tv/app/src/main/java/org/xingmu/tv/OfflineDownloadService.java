package org.xingmu.tv;
import android.app.*;import androidx.media3.exoplayer.offline.DownloadManager;import android.content.*;import androidx.media3.exoplayer.offline.*;import androidx.media3.exoplayer.scheduler.*;import java.util.*;
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public final class OfflineDownloadService extends DownloadService {
 public OfflineDownloadService(){super(42,1000,"offline-downloads",R.string.download_channel,R.string.download_channel_description);}
 @Override protected DownloadManager getDownloadManager(){return OfflineStore.manager(this);}
 @Override protected Scheduler getScheduler(){return new PlatformScheduler(this,43);}
 @Override protected Notification getForegroundNotification(List<Download> downloads,int requirements){PendingIntent open=PendingIntent.getActivity(this,42,new Intent(this,DownloadsActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);return new DownloadNotificationHelper(this,"offline-downloads").buildProgressNotification(this,R.drawable.ic_download,open,"星幕 TV · 下载中心",downloads,requirements);}
 @Override public void onTimeout(int startId,int type){getDownloadManager().pauseDownloads();stopSelf();}
}
