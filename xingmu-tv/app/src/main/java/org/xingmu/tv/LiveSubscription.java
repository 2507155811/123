package org.xingmu.tv;
import java.io.*;
/** TXT and M3U versions share one normalized catalog, including identical channel IDs. */
public final class LiveSubscription {
 public static SubscriptionParser.Result parse(String text)throws IOException{text=text.replaceFirst("^\\uFEFF","").trim();if(text.length()>1_000_000)throw new IOException("列表超过 1 MB");if(text.trim().startsWith("#EXTM3U"))return SubscriptionParser.parse(text,"直播电视 · zbds","zbds_");StringBuilder m3u=new StringBuilder("#EXTM3U\n");String group="其他";for(String raw:text.split("\\r?\\n")){String row=raw.trim();int comma=row.indexOf(',');if(comma<0)continue;String name=row.substring(0,comma).trim(),url=row.substring(comma+1).trim();if(url.equals("#genre#")){group=name;continue;}if(url.startsWith("http://")||url.startsWith("https://"))m3u.append("#EXTINF:-1 group-title=\"").append(group.replace("\"","")).append("\",").append(name).append('\n').append(url).append('\n');}return SubscriptionParser.parse(m3u.toString(),"直播电视 · zbds","zbds_");}
}
