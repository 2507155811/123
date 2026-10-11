package org.xingmu.tv;
import org.json.*;import org.junit.Test;import java.io.*;import static org.junit.Assert.*;
public class CatalogParserTest {
 private JSONObject entry(String url)throws Exception{return new JSONObject().put("id","test").put("title","授权电影").put("section","films").put("url",url).put("license","CC BY 3.0");}
 private String data(JSONObject entry)throws Exception{return new JSONObject().put("authorized",true).put("items",new JSONArray().put(entry)).toString();}
 @Test public void authorizedHttpsVideoAccepted()throws Exception{assertEquals(1,CatalogParser.parse(data(entry("https://example.org/movie.mp4"))).size());}
 @Test(expected=IOException.class) public void authorizationDeclarationRequired()throws Exception{CatalogParser.parse("{\"items\":[]}");}
 @Test public void rejectsLocalPrivateAndCredentialUrls(){for(String u:new String[]{"http://example.org/a","file:///a","javascript:alert(1)","https://localhost/a","https://192.168.1.1/a","https://10.1.1.1/a","https://127.0.0.1/a","https://user:password@example.org/a","https://example.org:8443/a"})assertFalse(u,CatalogParser.safeUrl(u));}
 @Test(expected=IOException.class) public void rejectsUnknownOfficialPortal()throws Exception{CatalogParser.parse(data(entry("https://example.org/a").put("mode","official")));}
 @Test public void officialCctvPortalAccepted()throws Exception{assertEquals(1,CatalogParser.parse(data(entry("https://tv.cctv.com/live/cctv1/").put("mode","official").put("section","domestic"))).size());}
 @Test(expected=IOException.class) public void missingLicenseRejected()throws Exception{JSONObject item=entry("https://example.org/a");item.remove("license");CatalogParser.parse(data(item));}
 @Test public void duplicateIdsDoNotDuplicateChannels()throws Exception{JSONObject item=entry("https://example.org/a");String data=new JSONObject().put("authorized",true).put("items",new JSONArray().put(item).put(item)).toString();assertEquals(1,CatalogParser.parse(data).size());}
 @Test public void channelSwitchingWrapsInBothDirections(){assertEquals(0,CatalogParser.wrappedIndex(2,1,3));assertEquals(2,CatalogParser.wrappedIndex(0,-1,3));assertEquals(-1,CatalogParser.wrappedIndex(0,1,0));}
 @Test(expected=IOException.class) public void unknownCategoryRejected()throws Exception{CatalogParser.parse(data(entry("https://example.org/a").put("section","unapproved")));}
 @Test public void operaVodAggregatesAcrossSources()throws Exception{CatalogItem i=CatalogParser.parse(data(entry("https://example.org/opera.mp4").put("category","京剧"))).get(0);assertTrue(i.opera());}
 @Test public void mixedLiveChannelDoesNotClaimPureOpera()throws Exception{CatalogItem i=CatalogParser.parse(data(entry("https://example.org/live.m3u8").put("category","戏曲").put("section","domestic"))).get(0);assertFalse(i.opera());}
 @Test public void declaredPureOperaLiveSourceIsIncluded()throws Exception{CatalogItem i=CatalogParser.parse(data(entry("https://example.org/live.m3u8").put("category","越剧").put("section","domestic").put("operaOnly",true))).get(0);assertTrue(i.opera());}
 @Test public void nonOperaContentExcludedEvenWhenFlagIsSet()throws Exception{CatalogItem i=CatalogParser.parse(data(entry("https://example.org/movie.mp4").put("category","科幻").put("operaOnly",true))).get(0);assertFalse(i.opera());}
 @Test public void backupsAreDeduplicated()throws Exception{CatalogItem i=CatalogParser.parse(data(entry("https://example.org/live.m3u8").put("backupUrls",new JSONArray().put("https://example.org/live.m3u8").put("https://backup.example.org/live.m3u8")))).get(0);assertEquals(2,i.urls.size());}
 @Test(expected=IOException.class) public void privateBackupIsRejected()throws Exception{CatalogParser.parse(data(entry("https://example.org/live.m3u8").put("backupUrls",new JSONArray().put("https://127.0.0.1/live"))));}
 @Test(expected=IOException.class) public void backupCannotBeAttachedToOfficialWebPage()throws Exception{CatalogParser.parse(data(entry("https://tv.cctv.com/live/cctv1/").put("mode","official").put("backupUrls",new JSONArray().put("https://example.org/live.m3u8"))));}
 @Test(expected=IOException.class) public void tooManyBackupsRejected()throws Exception{JSONArray urls=new JSONArray();for(int n=0;n<8;n++)urls.put("https://example.org/"+n);CatalogParser.parse(data(entry("https://example.org/live.m3u8").put("backupUrls",urls)));}
}
