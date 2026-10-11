package org.xingmu.tv;
import org.junit.Test;import static org.junit.Assert.*;import java.io.*;
public class TvBoxConfigTest {
 @Test public void standardApiAcceptedAndPluginCounted()throws Exception{TvBoxConfig c=TvBoxConfig.parse("{\"spider\":\"plugin.jar\",\"sites\":[{\"type\":1,\"name\":\"示例\",\"api\":\" https://example.org/api.php \"},{\"type\":3,\"api\":\"csp_Plugin\"}]}",null);assertEquals(1,c.sites.size());assertEquals(1,c.unsupported);assertEquals("https://example.org/api.php",c.sites.get(0).api);}
 @Test public void localAndProxyApiNotLoaded()throws Exception{TvBoxConfig c=TvBoxConfig.parse("{\"sites\":[{\"type\":1,\"api\":\"http://127.0.0.1/api\"},{\"type\":1,\"api\":\"proxy://api\"}]}",null);assertEquals(0,c.sites.size());assertEquals(2,c.unsupported);}
 @Test public void remoteRelativeLiveUrlResolves()throws Exception{TvBoxConfig c=TvBoxConfig.parse("{\"lives\":[{\"url\":\"./live.txt\"}]}","https://example.org/config/main.json");assertEquals("https://example.org/config/live.txt",c.liveUrls.get(0));}
 @Test public void inlineChannelBackupsPreserved()throws Exception{TvBoxConfig c=TvBoxConfig.parse("{\"lives\":[{\"group\":\"新闻\",\"channels\":[{\"name\":\"频道\",\"urls\":[\"https://example.org/a\",\"https://example.org/b\",\"proxy://bad\"]}]}]}",null);assertEquals(1,c.inline.size());assertEquals(1,c.inline.get(0).getJSONArray("backupUrls").length());assertEquals(1,c.issues.size());}
 @Test public void duplicateStandardApisAreDeduplicated()throws Exception{assertEquals(1,TvBoxConfig.parse("{\"sites\":[{\"type\":1,\"api\":\"https://example.org/api\"},{\"type\":1,\"api\":\"https://example.org/api\"}]}",null).sites.size());}
}
