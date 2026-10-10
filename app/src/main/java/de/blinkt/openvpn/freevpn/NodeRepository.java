package de.blinkt.openvpn.freevpn;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.HttpsURLConnection;

public final class NodeRepository {
    public static final String SOURCE="https://www.vpngate.net/api/iphone/";
    private static final String[] SOURCES={SOURCE,
        "https://raw.githubusercontent.com/2507155811/123/main/nodes/vpngate.csv",
        "https://github.com/2507155811/123/raw/refs/heads/main/nodes/vpngate.csv"};
    private final File cache;
    private static final long MAX_AGE=7L*24*60*60*1000;
    public NodeRepository(File directory) {cache=new File(directory,"vpngate.csv");}
    public static final class Result {
        public final List<Node> nodes;public final boolean cached;public final long fetchedAt;public final String origin;
        public Result(List<Node> nodes,boolean cached,long fetchedAt){this(nodes,cached,fetchedAt,cached?"离线列表":"实时列表");}
        public Result(List<Node> nodes,boolean cached,long fetchedAt,String origin){this.nodes=nodes;this.cached=cached;this.fetchedAt=fetchedAt;this.origin=origin;}
    }
    private static final class Download {final String csv,origin;Download(String csv,String origin){this.csv=csv;this.origin=origin;}}
    public Result load(boolean network)throws IOException {
        ExecutorService sources=Executors.newFixedThreadPool(2);
        Future<Result> gate=sources.submit(()->{try{return loadGate(network);}catch(IOException e){return loadGate(false);}});
        Future<List<Node>> book=sources.submit(()->{VpnBookRepository provider=new VpnBookRepository(cache.getParentFile());try{return provider.load(network);}catch(IOException e){return provider.load(false);}});
        Map<String,Node> combined=new LinkedHashMap<>();Result gateResult=null;List<Node> bookResult=Collections.emptyList();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(40);
        try{
            try{gateResult=gate.get(Math.max(1,deadline-System.nanoTime()),TimeUnit.NANOSECONDS);for(Node n:gateResult.nodes)combined.put(n.ip,n);}catch(ExecutionException|TimeoutException ignored){}
            try{bookResult=book.get(Math.max(1,deadline-System.nanoTime()),TimeUnit.NANOSECONDS);for(Node n:bookResult)combined.putIfAbsent(n.ip,n);}catch(ExecutionException|TimeoutException ignored){}
            if(combined.isEmpty())throw new IOException("所有来源暂时不可达，且没有有效缓存");
            String origin=(gateResult==null?"":"VPN Gate "+gateResult.nodes.size()+" 个候选")+(bookResult.isEmpty()?"":" · VPNBook "+bookResult.size()+" 个候选（需官方共享密码）");
            return new Result(new ArrayList<>(combined.values()),gateResult!=null&&gateResult.cached,gateResult==null?System.currentTimeMillis():gateResult.fetchedAt,origin);
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new InterruptedIOException("搜索已取消");}
        finally{gate.cancel(true);book.cancel(true);sources.shutdownNow();}
    }
    private Result loadGate(boolean network) throws IOException {
        if(!network) return cached();
        ExecutorService workers=Executors.newFixedThreadPool(SOURCES.length);
        CompletionService<Download> jobs=new ExecutorCompletionService<>(workers);
        List<Future<Download>> pending=new ArrayList<>();
        for(int i=0;i<SOURCES.length;i++){final int index=i;pending.add(jobs.submit(()->new Download(download(SOURCES[index]),index==0?"VPN Gate 实时":"GitHub 备用")));}
        IOException failure=new IOException("所有节点获取渠道暂时不可达，请切换网络；可浏览离线列表");
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);
        try {
            for(int i=0;i<SOURCES.length;i++) {
                Future<Download> done=jobs.poll(Math.max(0,end-System.nanoTime()),TimeUnit.NANOSECONDS);
                if(done==null)break;
                try {Download d=done.get();return save(d.csv,d.origin);}
                catch(ExecutionException e){Throwable cause=e.getCause();failure=cause instanceof IOException?(IOException)cause:new IOException("节点渠道返回无效列表",cause);}
                catch(IOException | IllegalArgumentException e){failure=e instanceof IOException?(IOException)e:new IOException(e.getMessage(),e);}
            }
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("更新已取消",e);}
        finally {for(Future<Download> f:pending)f.cancel(true);workers.shutdownNow();}
        throw failure;
    }
    private String download(String address) throws IOException {
        URL url=new URL(address);
        for(int redirects=0;redirects<=3;redirects++) {
            HttpsURLConnection c=(HttpsURLConnection)url.openConnection();
            c.setConnectTimeout(8_000);c.setReadTimeout(8_000);c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent","AeroVPN/1.2 Android");
            try {
                int status=c.getResponseCode();
                if(status==301||status==302||status==307||status==308) {
                    String location=c.getHeaderField("Location");if(location==null)throw new IOException("备用渠道跳转无效");
                    URL next=new URL(url,location);
                    if(!next.getProtocol().equals("https")||!Arrays.asList("github.com","raw.githubusercontent.com","www.vpngate.net").contains(next.getHost()))throw new IOException("拒绝未知节点渠道跳转");
                    url=next;continue;
                }
                if(status!=200)throw new IOException("节点渠道响应 HTTP "+status);
                return read(c.getInputStream());
            }finally{c.disconnect();}
        }
        throw new IOException("节点渠道跳转次数过多");
    }
    private static String read(InputStream in) throws IOException {
        if(in==null)throw new IOException("无法打开文件");
        try(InputStream input=in;ByteArrayOutputStream bytes=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192];int n;
            while((n=input.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("更新已取消");if(bytes.size()+n>12_000_000)throw new IOException("节点文件超过 12 MB");bytes.write(buffer,0,n);}
            return bytes.toString("UTF-8");
        }
    }
    public Result importList(InputStream in) throws IOException {
        try{return save(read(in),"导入列表");}catch(IllegalArgumentException e){throw new IOException("请选择 VPN Gate 原始 CSV 节点列表",e);}
    }
    public Result seed(InputStream in) throws IOException {
        String csv=read(in);Result bundled=parse(csv,true,"安装包快照",0);
        try {Result local=cached();if(local.fetchedAt>=bundled.fetchedAt)return local;}catch(IOException ignored){}
        return save(csv,"安装包快照");
    }
    static long timestamp(String csv,long fallback) throws IOException {
        if(!csv.startsWith("#AeroFetchedAt="))return fallback;
        try {
            long at=Long.parseLong(csv.substring(15,csv.indexOf('\n')).trim());
            if(at<=0||at>System.currentTimeMillis()+300_000)throw new NumberFormatException();
            return at;
        }catch(RuntimeException e){throw new IOException("节点列表时间信息无效",e);}
    }
    private Result parse(String csv,boolean cached,String origin,long fallback) throws IOException {
        long at=timestamp(csv,fallback);
        if(at<=0||System.currentTimeMillis()-at>MAX_AGE)throw new IOException("节点列表已超过七天，请更新或导入新列表");
        List<Node> nodes=NodeParser.parse(csv);
        if(nodes.isEmpty())throw new IOException("列表没有符合条件的境外 OpenVPN 节点");
        return new Result(nodes,cached,at,origin);
    }
    private Result save(String csv,String origin) throws IOException {
        Result result=parse(csv,false,origin,System.currentTimeMillis());
        // A delayed mirror must not replace a newer locally saved list.
        try {Result local=cached();if(local.fetchedAt>result.fetchedAt)return local;}catch(IOException ignored){}
        if(!csv.startsWith("#AeroFetchedAt="))csv="#AeroFetchedAt="+result.fetchedAt+"\n"+csv;
        File temp=new File(cache.getParentFile(),"vpngate.tmp");
        Files.write(temp.toPath(),csv.getBytes(StandardCharsets.UTF_8));
        Files.move(temp.toPath(),cache.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return result;
    }
    public static String explain(IOException error) {
        if(error instanceof SocketTimeoutException)return "节点渠道连接超时，请切换 Wi-Fi；可使用离线列表";
        if(error instanceof UnknownHostException)return "无法解析节点渠道域名，请检查网络或 DNS";
        if(error instanceof javax.net.ssl.SSLException)return "安全连接失败，请检查手机日期和网络";
        if(error instanceof ConnectException)return "无法连接节点渠道，请切换网络或导入节点列表";
        return "获取失败："+error.getMessage();
    }
    private Result cached() throws IOException {
        if(!cache.exists())throw new IOException("尚未保存节点列表");
        try {return parse(new String(Files.readAllBytes(cache.toPath()),StandardCharsets.UTF_8),true,"离线列表",cache.lastModified());}
        catch(IllegalArgumentException e){throw new IOException("缓存损坏",e);}
    }
}
