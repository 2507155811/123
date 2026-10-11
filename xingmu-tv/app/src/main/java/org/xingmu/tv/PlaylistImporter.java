package org.xingmu.tv;
import org.json.*;import java.io.*;import java.util.*;import java.util.regex.*;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;import javax.xml.parsers.*;import org.w3c.dom.*;import org.xml.sax.InputSource;
/** Data-only playlist importer. Never executes playlist scripts or resolver plugins. */
public final class PlaylistImporter {
 public static String convert(String input,String section,String country)throws IOException{
  if(!Arrays.asList("domestic","foreign","films").contains(section))throw new IOException("请选择导入分类");
  String text=input.replaceFirst("^\\uFEFF","").trim();if(text.length()>1_000_000)throw new IOException("目录超过 1 MB");
  if(text.startsWith("{")){try{JSONObject root=new JSONObject(text);if(root.has("sites")||root.has("spider")||root.has("parses"))throw new IOException("这是 TVBox 插件/解析配置，不能作为直接播放列表导入；请导出你有权访问的 M3U/TXT 或媒体 JSON 目录");}catch(JSONException e){throw new IOException("JSON 格式错误",e);}CatalogParser.parse(text);return text;}
  LinkedHashMap<String,JSONObject> items=new LinkedHashMap<>();
  try{
   if(text.startsWith("<")){DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);factory.setFeature("http://xml.org/sax/features/external-general-entities",false);factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);factory.setExpandEntityReferences(false);factory.setNamespaceAware(true);Document document=factory.newDocumentBuilder().parse(new InputSource(new StringReader(text)));NodeList tracks=document.getElementsByTagNameNS("*","track");for(int n=0;n<tracks.getLength();n++){Element track=(Element)tracks.item(n);String title=value(track,"title","自定义内容 "+(n+1)),group=value(track,"album","其他");NodeList urls=track.getElementsByTagNameNS("*","location");for(int u=0;u<urls.getLength();u++)add(items,title,urls.item(u).getTextContent().trim(),section,group,country);}}
   else {String title=null,group="其他",itemCountry=country;boolean manifest=text.contains("#EXT-X-TARGETDURATION")||text.contains("#EXT-X-STREAM-INF");if(manifest)throw new IOException("这是 HLS 视频清单，请导入它的 HTTPS 地址，不要导入分片文件");
    for(String raw:text.split("\\r?\\n")){String line=raw.trim();if(line.isEmpty())continue;if(line.startsWith("#EXTINF:")){int comma=line.indexOf(',');title=comma>=0?line.substring(comma+1).trim():"自定义频道";group=attribute(line,"group-title","其他");itemCountry=attribute(line,"tvg-country",country);continue;}if(line.startsWith("#"))continue;
     if(line.startsWith("https://")||line.startsWith("http://")){add(items,title==null?"自定义内容":title,line,section,group,itemCountry);title=null;continue;}
     int separator=line.indexOf(',');if(separator<0)separator=line.indexOf('$');if(separator>=0){String name=line.substring(0,separator).trim(),url=line.substring(separator+1).trim();if(url.equals("#genre#")){group=name;continue;}add(items,name,url,section,group,country);}
    }
   }
   if(items.isEmpty())throw new IOException("未识别到播放地址；支持 JSON、M3U、TXT、XSPF 和 HTTPS 地址");JSONArray rows=new JSONArray();for(JSONObject item:items.values())rows.put(item);String result=new JSONObject().put("authorized",true).put("items",rows).toString();CatalogParser.parse(result);return result;
  }catch(IOException e){throw e;}catch(Exception e){throw new IOException("播放列表格式错误",e);}
 }
 private static String attribute(String line,String key,String fallback){Matcher m=Pattern.compile(Pattern.quote(key)+"=\"([^\"]*)\"").matcher(line);return m.find()&&!m.group(1).isEmpty()?m.group(1):fallback;}
 private static String value(Element parent,String name,String fallback){NodeList nodes=parent.getElementsByTagNameNS("*",name);return nodes.getLength()==0?fallback:nodes.item(0).getTextContent().trim();}
 private static void add(Map<String,JSONObject> items,String title,String url,String section,String group,String country)throws Exception{
  if(!CatalogParser.safeMediaUrl(url))throw new IOException("播放地址需使用公开 HTTP/HTTPS；不支持内网、UDP 或插件地址");if(title.isEmpty())title="自定义内容";String key=section+"\n"+country+"\n"+group+"\n"+title;JSONObject item=items.get(key);
  if(item==null){if(items.size()>=1000)throw new IOException("最多支持 1000 条内容");byte[] hash=MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));StringBuilder id=new StringBuilder("import_");for(int n=0;n<12;n++)id.append(String.format(Locale.ROOT,"%02x",hash[n]&255));item=new JSONObject().put("id",id.toString()).put("title",title).put("section",section).put("category",group).put("country",country).put("url",url).put("mode","stream").put("source","我的播放列表").put("license","用户提供的自有或已授权媒体");items.put(key,item);}
  else if(!item.getString("url").equals(url)){JSONArray backups=item.optJSONArray("backupUrls");if(backups==null){backups=new JSONArray();item.put("backupUrls",backups);}for(int n=0;n<backups.length();n++)if(backups.getString(n).equals(url))return;if(backups.length()>=7)throw new IOException("同一内容最多支持 8 个地址");backups.put(url);}
 }
}
