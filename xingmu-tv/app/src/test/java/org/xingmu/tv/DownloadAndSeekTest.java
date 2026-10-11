package org.xingmu.tv;
import org.junit.Test;import static org.junit.Assert.*;
public class DownloadAndSeekTest {
 @Test public void genreSearchUsesTypeMetadataAndAliases(){assertTrue(GenreSearch.matches("动作片","动作"));assertTrue(GenreSearch.matches("言情 恋爱","爱情"));assertTrue(GenreSearch.matches("科幻 悬疑","科幻 悬疑"));assertFalse(GenreSearch.matches("剧情","科幻"));}
 @Test public void filmTagsAreReadSeparatelyFromTitles()throws Exception{VodApi.Movie movie=VodApi.parse("{\"list\":[{\"vod_name\":\"某影片\",\"type_name\":\"剧情片\",\"vod_class\":\"动作,悬疑\"}]}").get(0);assertEquals("动作,悬疑",movie.tags);assertTrue(GenreSearch.matches(movie.tags,"悬疑"));}
 @Test public void downloadLabelsPreserveTitleAndHeaders()throws Exception{CatalogItem item=new CatalogItem("episode","影片 · 第2集","films","国产剧","中国","https://example.org/episode.m3u8","stream","来源","署名",false,java.util.Collections.singletonList("https://example.org/episode.m3u8"),"custom-agent");CatalogItem result=OfflineMetadata.decode(item.id,item.url,OfflineMetadata.encode(item));assertEquals(item.id,result.id);assertEquals(item.title,result.title);assertEquals(item.userAgent,result.userAgent);assertEquals(item.license,result.license);assertFalse(result.live());}
 @Test public void damagedMetadataDoesNotPreventListing(){CatalogItem item=OfflineMetadata.decode("download","https://example.org/a.mp4",new byte[]{1,2});assertEquals("已下载视频",item.title);assertEquals("download",item.id);}
 @Test public void swipeMovesBothDirections(){assertEquals(80000,SeekMath.target(50000,250,1000,600000));assertEquals(20000,SeekMath.target(50000,-250,1000,600000));}
 @Test public void swipeAndRemoteCannotLeaveMediaBounds(){assertEquals(0,SeekMath.target(1000,-1000,1000,600000));assertEquals(600000,SeekMath.target(590000,1000,1000,600000));assertEquals(0,SeekMath.step(1000,-10000,600000));assertEquals(600000,SeekMath.step(599000,10000,600000));}
 @Test public void unknownDurationAndInvalidTouchAreSafe(){assertEquals(5000,SeekMath.target(5000,100,1000,-1));assertEquals(5000,SeekMath.target(5000,Float.NaN,1000,600000));assertEquals(5000,SeekMath.target(5000,100,0,600000));assertEquals("01:02:03",SeekMath.time(3723000));}
}
