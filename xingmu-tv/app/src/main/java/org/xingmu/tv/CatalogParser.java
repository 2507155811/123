package org.xingmu.tv;
import org.json.*;import java.io.*;import java.net.URI;import java.util.*;
public final class CatalogParser {
 public static final Set<String> OFFICIAL_HOSTS=new HashSet<>(Arrays.asList("tv.cctv.com","www3.nhk.or.jp","www.nhk.or.jp","www.dw.com","www.france24.com","www.aljazeera.com","plus.nasa.gov","studio.blender.org","www.blender.org"));
 public static boolean safeUrl(String value){try{URI u=new URI(value);String h=u.getHost();if(!"https".equals(u.getScheme())||h==null||u.getUserInfo()!=null||u.getPort()!=-1&&u.getPort()!=443)return false;h=h.toLowerCase(Locale.ROOT);if(h.equals("localhost")||h.endsWith(".local")||h.contains(":"))return false;if(h.matches("[0-9.]+")){String[] p=h.split("\\.");if(p.length!=4)return false;int a=Integer.parseInt(p[0]),b=Integer.parseInt(p[1]);for(String v:p)if(Integer.parseInt(v)>255)return false;if(a==0||a==10||a==127||a>=224||a==169&&b==254||a==172&&b>=16&&b<=31||a==192&&b==168||a==100&&b>=64&&b<=127)return false;}return true;}catch(Exception e){return false;}}
 public static List<CatalogItem> parse(String value)throws IOException{
  if(value.length()>1_000_000)throw new IOException("目录超过 1 MB");
  try{JSONObject root=new JSONObject(value);if(!root.optBoolean("authorized",false))throw new IOException("目录必须声明 authorized: true，并具有观看授权");JSONArray rows=root.getJSONArray("items");if(rows.length()>1000)throw new IOException("最多支持 1000 条内容");List<CatalogItem> out=new ArrayList<>();Set<String> seen=new HashSet<>();
   for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);String id=r.getString("id"),title=r.getString("title"),section=r.getString("section"),url=r.getString("url"),mode=r.optString("mode","stream"),license=r.getString("license");
    if(id.isEmpty()||title.isEmpty()||license.isEmpty()||!Arrays.asList("domestic","foreign","films").contains(section)||!Arrays.asList("stream","official").contains(mode)||!safeUrl(url))throw new IOException("内容字段或 HTTPS 地址无效");
    if(mode.equals("official")&&!OFFICIAL_HOSTS.contains(new URI(url).getHost().toLowerCase(Locale.ROOT)))throw new IOException("官方入口不在支持的来源名单中");
    if(!seen.add(id))continue;out.add(new CatalogItem(id,title,section,r.optString("category","其他"),r.optString("country","未分类"),url,mode,r.optString("source","已授权自定义来源"),license,r.optBoolean("operaOnly",false)));
   }return out;
  }catch(JSONException|java.net.URISyntaxException e){throw new IOException("目录格式不正确",e);}
 }
 public static int wrappedIndex(int index,int delta,int size){if(size<=0)return -1;return (int)(((long)index+delta)%size+size)%size;}
}
