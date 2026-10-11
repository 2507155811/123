package org.xingmu.tv;
import java.io.*;import java.net.*;
/** Bounded, validated HTTP text fetch; no arbitrary headers or local addresses. */
final class PublicHttp {
 static String get(String address)throws IOException{for(int hop=0;hop<4;hop++){if(!CatalogParser.safeMediaUrl(address))throw new IOException("不是支持的公开 HTTP/HTTPS 地址");HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();c.setConnectTimeout(5000);c.setReadTimeout(5000);c.setInstanceFollowRedirects(false);c.setRequestProperty("User-Agent","XingmuTV/1.2");try{int status=c.getResponseCode();if(status>=300&&status<400){String location=c.getHeaderField("Location");if(location==null)throw new IOException("重定向缺少地址");address=new URL(new URL(address),location).toString();continue;}if(status!=200)throw new IOException("HTTP "+status);return CatalogRepository.read(c.getInputStream());}finally{c.disconnect();}}throw new IOException("重定向次数过多");}
}
