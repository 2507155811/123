package de.blinkt.openvpn.freevpn;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import javax.net.ssl.HttpsURLConnection;

public final class NodeRepository {
    public static final String SOURCE="https://www.vpngate.net/api/iphone/";
    private final File cache;
    public NodeRepository(File directory) {cache=new File(directory,"vpngate.csv");}
    public static final class Result {
        public final List<Node> nodes;public final boolean cached;public final long fetchedAt;
        public Result(List<Node> nodes,boolean cached,long fetchedAt){this.nodes=nodes;this.cached=cached;this.fetchedAt=fetchedAt;}
    }
    public Result load(boolean network) throws IOException {
        if(!network) return cached();
        HttpsURLConnection c=(HttpsURLConnection)new URL(SOURCE).openConnection();
        c.setConnectTimeout(8_000);c.setReadTimeout(25_000);c.setInstanceFollowRedirects(false);
        c.setRequestProperty("User-Agent","AeroVPN/1.0 Android");
        try {
            if(c.getResponseCode()!=200)throw new IOException("节点源响应 HTTP "+c.getResponseCode());
            String csv;
            try(InputStream in=c.getInputStream();ByteArrayOutputStream bytes=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[8192];int n;
                while((n=in.read(buffer))!=-1) {if(bytes.size()+n>12_000_000)throw new IOException("节点列表过大");bytes.write(buffer,0,n);}
                csv=bytes.toString("UTF-8");
            }
            return save(csv);
        } catch(IllegalArgumentException e) {throw new IOException(e.getMessage(),e);}
        finally {c.disconnect();}
    }
    public Result importList(InputStream in) throws IOException {
        if(in==null)throw new IOException("无法打开文件");
        try(InputStream input=in;ByteArrayOutputStream bytes=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192];int n;
            while((n=input.read(buffer))!=-1){if(bytes.size()+n>12_000_000)throw new IOException("节点文件超过 12 MB");bytes.write(buffer,0,n);}
            try{return save(bytes.toString("UTF-8"));}catch(IllegalArgumentException e){throw new IOException("请选择 VPN Gate 原始 CSV 节点列表",e);}
        }
    }
    private Result save(String csv) throws IOException {
        List<Node> nodes=NodeParser.parse(csv);
        if(nodes.isEmpty())throw new IOException("列表没有符合条件的境外 OpenVPN 节点");
        File temp=new File(cache.getParentFile(),"vpngate.tmp");
        Files.write(temp.toPath(),csv.getBytes(StandardCharsets.UTF_8));
        Files.move(temp.toPath(),cache.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return new Result(nodes,false,cache.lastModified());
    }
    public static String explain(IOException error) {
        if(error instanceof SocketTimeoutException)return "连接节点源超时，请切换 Wi-Fi 或导入节点列表";
        if(error instanceof UnknownHostException)return "无法解析节点源域名，请检查网络或 DNS";
        if(error instanceof javax.net.ssl.SSLException)return "安全连接失败，请检查手机日期和网络";
        if(error instanceof ConnectException)return "无法连接节点源，请切换网络或导入节点列表";
        return "获取失败："+error.getMessage();
    }
    private Result cached() throws IOException {
        if(!cache.exists() || System.currentTimeMillis()-cache.lastModified()>86_400_000)throw new IOException("没有 24 小时内的缓存");
        try {return new Result(NodeParser.parse(new String(Files.readAllBytes(cache.toPath()),StandardCharsets.UTF_8)),true,cache.lastModified());}
        catch(IllegalArgumentException e){throw new IOException("缓存损坏",e);}
    }
}
