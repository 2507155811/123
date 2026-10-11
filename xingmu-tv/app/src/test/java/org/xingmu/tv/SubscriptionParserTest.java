package org.xingmu.tv;
import org.junit.Test;import static org.junit.Assert.*;import java.util.*;
public class SubscriptionParserTest {
 @Test public void invalidProtocolDoesNotDiscardWorkingChannels()throws Exception{SubscriptionParser.Result result=SubscriptionParser.parse("#EXTM3U\n#EXTINF:-1 group-title=\"央视\",CCTV1\nhttp://example.org:8080/live.m3u8\n#EXTINF:-1,bad\nproxy://bad");assertEquals(1,result.entries);assertEquals(1,result.skipped);assertEquals("央视",CatalogParser.parse(result.json).get(0).category);}
 @Test public void sameGroupNameUsesBackupSources()throws Exception{SubscriptionParser.Result r=SubscriptionParser.parse("#EXTINF:-1,频道\nhttps://example.org/a\n#EXTINF:-1,频道\nhttps://backup.example.org/a");assertEquals(1,r.entries);assertEquals(2,CatalogParser.parse(r.json).get(0).urls.size());}
 @Test public void privateOrCredentialAddressesRemainBlocked(){assertFalse(CatalogParser.safeMediaUrl("http://127.0.0.1:8080/a"));assertFalse(CatalogParser.safeMediaUrl("https://user:password@example.org:8443/a"));assertTrue(CatalogParser.safeMediaUrl("https://example.org:8443/a"));}
 @Test public void largeSubscriptionIsSupported()throws Exception{StringBuilder input=new StringBuilder();for(int n=0;n<1200;n++)input.append("#EXTINF:-1,频道").append(n).append("\nhttps://example.org/").append(n).append("\n");assertEquals(1200,SubscriptionParser.parse(input.toString()).entries);}
 @Test public void userAgentMetadataIsPreserved()throws Exception{SubscriptionParser.Result r=SubscriptionParser.parse("#EXTINF:-1 http-user-agent=\"AptvPlayer-UA\",频道\nhttps://example.org/live");assertEquals("AptvPlayer-UA",CatalogParser.parse(r.json).get(0).userAgent);}
 @Test public void vodGroupsGoIntoFilmLibrary()throws Exception{SubscriptionParser.Result r=SubscriptionParser.parse("#EXTINF:-1 group-title=\"点播电影\",电影\nhttps://example.org/movie.mp4");assertFalse(CatalogParser.parse(r.json).get(0).live());}
}
