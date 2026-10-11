package org.xingmu.tv;
import org.junit.Test;import static org.junit.Assert.*;import java.io.*;import java.util.*;
public class VodApiTest {
 @Test public void queryReplacesOldActionAndEncodesTitle()throws Exception{String url=VodApi.query("https://example.org/api?ac=list&token=abc","wd","京剧 空格");assertTrue(url.contains("ac=detail"));assertFalse(url.contains("ac=list"));assertTrue(url.contains("token=abc"));assertTrue(url.contains("wd=%E4%BA%AC%E5%89%A7+%E7%A9%BA%E6%A0%BC"));}
 @Test public void jsonSearchParsesMovieMetadata()throws Exception{List<VodApi.Movie> m=VodApi.parse("{\"list\":[{\"vod_id\":4,\"vod_name\":\"电影\",\"type_name\":\"动画\",\"vod_year\":\"2024\"}]}");assertEquals("4",m.get(0).id);assertEquals("动画",m.get(0).category);}
 @Test public void xmlDetailParsesEpisodes()throws Exception{List<VodApi.Movie> m=VodApi.parse("<rss><list><video><id>4</id><name>戏曲</name><type>京剧</type><dl><dd>正片$https://example.org/a.mp4</dd></dl></video></list></rss>");assertTrue(CatalogParser.parse(VodApi.episodes(m.get(0),"test","来源")).get(0).opera());}
 @Test public void episodeGroupsMergeAsBackupSources()throws Exception{VodApi.Movie m=new VodApi.Movie("id","剧","电视剧","2024","第1集$https://example.org/a#第2集$https://example.org/b$$$第1集$https://backup.example.org/a");List<CatalogItem> items=CatalogParser.parse(VodApi.episodes(m,"test","来源"));assertEquals(2,items.size());assertEquals(2,items.get(0).urls.size());}
 @Test(expected=IOException.class) public void parserOnlyPlayAddressIsNotTreatedAsStream()throws Exception{VodApi.episodes(new VodApi.Movie("id","剧","其他","","正片$proxy://resolve"),"site","来源");}
 @Test(expected=IOException.class) public void badApiResponseGivesError()throws Exception{VodApi.parse("<html>Access denied</html>");}
}
