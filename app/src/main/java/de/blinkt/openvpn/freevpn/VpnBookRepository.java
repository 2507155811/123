package de.blinkt.openvpn.freevpn;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.HttpsURLConnection;

/** Reads the official public server directory and single-file OpenVPN downloads. */
public final class VpnBookRepository {
    public static final String WEBSITE="https://www.vpnbook.com/freevpn/openvpn";
    private final File cache;
    public VpnBookRepository(File directory){cache=new File(directory,"vpnbook.json");}
    static JSONArray servers(String html)throws IOException{
        String marker="self.__next_f.push(";StringBuilder flight=new StringBuilder();int cursor=0;
        try{
            while((cursor=html.indexOf(marker,cursor))>=0){int begin=cursor+marker.length(),end=html.indexOf(")</script>",begin);if(end<0)break;JSONArray record=new JSONArray(html.substring(begin,end));if(record.length()>1&&record.opt(1) instanceof String)flight.append(record.getString(1));cursor=end+1;}
            int at=flight.indexOf("{\"servers\":");if(at<0)throw new IOException("VPNBook 页面格式已变化");
            JSONObject object=(JSONObject)new JSONTokener(flight.substring(at)).nextValue();JSONArray result=object.getJSONArray("servers");
            if(result.length()==0||result.length()>30)throw new IOException("VPNBook 服务器列表无效");return result;
        }catch(JSONException|ClassCastException e){throw new IOException("VPNBook 页面格式无效",e);}
    }
    static Node node(JSONObject server,String original)throws IOException{
        try{
            String ip=server.getString("ipAddress"),code=server.getString("countryCode").toUpperCase(Locale.ROOT),hostname=server.getString("hostname");
            if(!NodeParser.publicIpv4(ip)||!code.matches("[A-Z]{2}")||Arrays.asList("CN","HK","MO").contains(code)||!hostname.matches("[a-z0-9-]+\\.vpnbook\\.com"))throw new IOException("VPNBook 节点信息无效");
            String config=original.replaceAll("(?m)^dev\\s+tun[0-9]*\\s*$","dev tun");
            if(!config.contains("auth-user-pass"))throw new IOException("VPNBook 配置缺少登录要求");
            // Preserve the actual official endpoint. No extra, unverified ports are invented.
            if(!NodeParser.safeConfig(config,ip))throw new IOException("VPNBook 配置未通过安全校验");
            return new Node("vpnbook-"+hostname,ip,server.getString("countryName"),code,Integer.MAX_VALUE,0,0,0,config);
        }catch(JSONException e){throw new IOException("VPNBook 字段不完整",e);}
    }
    public List<Node> load(boolean network)throws IOException {
        if(!network)return cached();
        JSONArray servers=servers(fetch(WEBSITE,2_000_000));ExecutorService pool=Executors.newFixedThreadPool(4);List<Future<Node>> tasks=new ArrayList<>();
        try{
            for(int i=0;i<servers.length();i++){
                final JSONObject server=servers.getJSONObject(i);
                tasks.add(pool.submit(()->{String hostname=server.getString("hostname"),ip=server.getString("ipAddress");
                    if(!hostname.matches("[a-z0-9-]+\\.vpnbook\\.com")||!NodeParser.publicIpv4(ip))throw new IOException("VPNBook 地址无效");
                    String url="https://www.vpnbook.com/api/openvpn?hostname="+URLEncoder.encode(hostname,"UTF-8")+"&protocol=tcp443&ip="+URLEncoder.encode(ip,"UTF-8");
                    return node(server,fetch(url,400_000));}));
            }
            List<Node> nodes=new ArrayList<>();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(25);
            for(Future<Node> task:tasks){try{nodes.add(task.get(Math.max(1,deadline-System.nanoTime()),TimeUnit.NANOSECONDS));}catch(ExecutionException|TimeoutException ignored){}}
            if(nodes.isEmpty())throw new IOException("VPNBook 配置下载失败");
            JSONObject stored=new JSONObject();stored.put("fetchedAt",System.currentTimeMillis());JSONArray entries=new JSONArray();
            for(Node n:nodes)entries.put(new JSONObject().put("hostname",n.id.substring("vpnbook-".length())).put("ipAddress",n.ip).put("countryCode",n.code).put("countryName",n.country).put("config",n.config));
            stored.put("nodes",entries);Files.write(cache.toPath(),stored.toString().getBytes(StandardCharsets.UTF_8));return nodes;
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new InterruptedIOException("搜索已取消");}catch(JSONException e){throw new IOException("VPNBook 数据无效",e);}
        finally{for(Future<Node> task:tasks)task.cancel(true);pool.shutdownNow();}
    }
    private List<Node> cached()throws IOException{
        try{
            JSONObject data=new JSONObject(new String(Files.readAllBytes(cache.toPath()),StandardCharsets.UTF_8));long at=data.getLong("fetchedAt");if(at>System.currentTimeMillis()+300_000||System.currentTimeMillis()-at>86_400_000)throw new IOException("VPNBook 缓存已过期");
            List<Node> result=new ArrayList<>();JSONArray entries=data.getJSONArray("nodes");for(int i=0;i<entries.length();i++){JSONObject item=entries.getJSONObject(i);result.add(node(item,item.getString("config")));}return result;
        }catch(JSONException e){throw new IOException("VPNBook 缓存无效",e);}
    }
    private static String fetch(String address,int limit)throws IOException{
        HttpsURLConnection c=(HttpsURLConnection)new URL(address).openConnection();c.setConnectTimeout(8000);c.setReadTimeout(8000);c.setInstanceFollowRedirects(false);c.setRequestProperty("User-Agent","Mozilla/5.0 AeroVPN/1.5");
        try{if(c.getResponseCode()!=200)throw new IOException("VPNBook HTTP "+c.getResponseCode());try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();if(out.size()+n>limit)throw new IOException("VPNBook 响应过大");out.write(buffer,0,n);}return out.toString("UTF-8");}}finally{c.disconnect();}
    }
}
