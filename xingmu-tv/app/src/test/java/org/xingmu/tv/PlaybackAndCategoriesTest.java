package org.xingmu.tv;
import org.junit.Test;import static org.junit.Assert.*;
public class PlaybackAndCategoriesTest {
 @Test public void hiddenHlsAndIncorrectContentTypeAreRecognised(){assertEquals("application/x-mpegURL",MediaFormat.infer("text/plain","\ufeff#EXTM3U\n#EXT-X-TARGETDURATION:6"));assertEquals("application/x-mpegURL",MediaFormat.infer("application/vnd.apple.mpegurl",""));}
 @Test public void dashAndTransportStreamAreRecognised(){assertEquals("application/dash+xml",MediaFormat.infer("text/xml","<?xml version='1.0'?><MPD xmlns='a'>"));assertEquals("video/mp2t",MediaFormat.infer("video/MP2T",""));assertNull(MediaFormat.infer("text/html","<html>error</html>"));}
 @Test public void mainstreamFilmCategoriesAreStable(){assertEquals("电影",FilmCategory.of("动作片"));assertEquals("电视剧",FilmCategory.of("国产剧"));assertEquals("纪录片",FilmCategory.of("纪录片"));assertEquals("动漫",FilmCategory.of("动画片"));assertEquals("综艺",FilmCategory.of("大陆综艺"));assertEquals("戏曲",FilmCategory.of("京剧"));assertTrue(FilmCategory.matches("喜剧片","电影"));assertFalse(FilmCategory.matches("欧美剧","电影"));}
 @Test public void categoryBrowsingUsesSourceTypeIds()throws Exception{assertEquals(java.util.Arrays.asList("6","7"),VodApi.categoryIds("{\"class\":[{\"type_id\":6,\"type_name\":\"动作片\"},{\"type_id\":7,\"type_name\":\"喜剧片\"},{\"type_id\":13,\"type_name\":\"国产剧\"}]}","电影"));}
}
