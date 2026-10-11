package org.xingmu.tv;
import java.io.IOException;import androidx.media3.exoplayer.offline.Downloader;
/** Prevent Media3 from marking an empty response as a completed video. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class VerifiedDownloader implements Downloader {
 private final Downloader delegate;VerifiedDownloader(Downloader delegate){this.delegate=delegate;}
 public void download(ProgressListener listener)throws IOException,InterruptedException{final long[] bytes={0};delegate.download((length,downloaded,percent)->{bytes[0]=downloaded;if(listener!=null)listener.onProgress(length,downloaded,percent);});if(bytes[0]<=0)throw new IOException("没有下载到视频数据，请更换线路");}
 public void cancel(){delegate.cancel();}public void remove(){delegate.remove();}
}
