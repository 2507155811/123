package org.xingmu.tv;
import org.json.*;import java.nio.charset.StandardCharsets;import java.io.*;
/** Persistent labels travel with the download; URLs remain in the Media3 request. */
public final class OfflineMetadata {
 public static byte[] encode(CatalogItem item)throws IOException{try{return new JSONObject().put("title",item.title).put("category",item.category).put("source",item.source).put("license",item.license).put("userAgent",item.userAgent).toString().getBytes(StandardCharsets.UTF_8);}catch(JSONException e){throw new IOException("无法保存下载信息",e);}}
 public static CatalogItem decode(String id,String url,byte[] data){try{JSONObject m=new JSONObject(new String(data,StandardCharsets.UTF_8));return new CatalogItem(id,m.optString("title","已下载视频"),"films",m.optString("category","其他"),"未分类",url,"stream",m.optString("source","下载中心"),m.optString("license","离线视频"),false,java.util.Collections.singletonList(url),m.optString("userAgent","XingmuTV/2.1"));}catch(Exception e){return new CatalogItem(id,"已下载视频","films","其他","未分类",url,"stream","下载中心","离线视频");}}
}
