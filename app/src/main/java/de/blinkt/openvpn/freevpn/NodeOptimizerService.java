package de.blinkt.openvpn.freevpn;

import android.app.*;
import android.content.*;
import android.net.*;
import android.os.*;
import androidx.core.app.NotificationCompat;
import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.core.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.HttpsURLConnection;

/** Owns sequential tunnel trials and the 10 minute foreground optimization schedule. */
public final class NodeOptimizerService extends Service implements VpnStatus.StateListener {
    public static final String SCAN="scan",SELECT="select",STOP="stop",AUTO="auto",SPEED="speed";
    private static final long PERIOD=10*60*1000L,SCAN_BUDGET=8*60*1000L;
    public final class LocalBinder extends Binder {public NodeOptimizerService service(){return NodeOptimizerService.this;}}
    private final LocalBinder binder=new LocalBinder();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicInteger generation=new AtomicInteger();
    private final Map<String,NodeHealth> verified=new ConcurrentHashMap<>();
    private final List<Runnable> observers=new CopyOnWriteArrayList<>();
    private final Object stateLock=new Object();
    private volatile IOpenVPNServiceInternal engine;
    private volatile ConnectionStatus level=ConnectionStatus.LEVEL_NOTCONNECTED;
    private volatile String engineUuid="";
    private volatile Node current;
    private volatile boolean automatic,busy,destroyed,stopping,intentionalStop;
    private volatile String message="尚未检测：点击检测可用节点";
    private volatile Future<?> work;
    private ConnectivityManager connectivity;
    private PowerManager.WakeLock wakeLock;
    private boolean bound,started;
    private final AtomicInteger networkEpoch=new AtomicInteger();
    private final ConnectivityManager.NetworkCallback networkChanges=new ConnectivityManager.NetworkCallback(){
        @Override public void onAvailable(Network network){invalidateNetwork();}
        @Override public void onLost(Network network){invalidateNetwork();}
    };
    private void invalidateNetwork(){networkEpoch.incrementAndGet();verified.clear();main.post(()->{if(destroyed)return;if(!busy){message="网络已变化，需要重新验证节点";if(automatic)nextCheck=0;}publish();});}
    private int cursor;
    private final Map<String,Integer> failures=new ConcurrentHashMap<>();
    private final List<String> recentFailures=new CopyOnWriteArrayList<>();
    public String diagnostics(){
        StringBuilder text=new StringBuilder("只记录本轮检测结果，不包含账号或配置密钥。\n可用节点："+available().size()+"\n");
        for(String stage:Arrays.asList("端口不可达","隧道未建立","隧道内联网失败"))text.append(stage).append("：").append(failures.getOrDefault(stage,0)).append('\n');
        for(String reason:recentFailures)text.append('\n').append(reason);
        return text.toString();
    }
    private void failed(Node node,String stage,String reason){failures.merge(stage,1,Integer::sum);if(recentFailures.size()<8)recentFailures.add(node.displayCountry()+" · "+node.ip+"："+reason);}

    private long nextCheck;
    private final ServiceConnection engineConnection=new ServiceConnection(){
        public void onServiceConnected(ComponentName name,IBinder b){engine=IOpenVPNServiceInternal.Stub.asInterface(b);synchronized(stateLock){stateLock.notifyAll();}}
        public void onServiceDisconnected(ComponentName name){engine=null;if(current!=null)verified.remove(current.ip);level=ConnectionStatus.LEVEL_NOTCONNECTED;synchronized(stateLock){stateLock.notifyAll();}}
    };
    private final Runnable tick=new Runnable(){public void run(){
        if(destroyed)return;
        long now=SystemClock.elapsedRealtime();
        verified.entrySet().removeIf(e->!e.getValue().fresh(now));
        if(automatic && !busy && now>=nextCheck){nextCheck=now+PERIOD;submitScan();}
        publish();main.postDelayed(this,15_000);
    }};
    public List<NodeHealth> available(){
        long now=SystemClock.elapsedRealtime();List<NodeHealth> result=new ArrayList<>();
        for(NodeHealth h:verified.values())if(h.fresh(now))result.add(h);
        result.sort(Comparator.comparingDouble(NodeHealth::quality).reversed());return result;
    }
    public NodeHealth health(String ip){NodeHealth h=verified.get(ip);return h!=null&&h.fresh(SystemClock.elapsedRealtime())?h:null;}
    public boolean isAutomatic(){return automatic;}
    public boolean isBusy(){return busy;}
    public String message(){return message;}
    public Node current(){return current;}
    public void observe(Runnable r){observers.add(r);r.run();}
    public void removeObserver(Runnable r){observers.remove(r);}
    @Override public IBinder onBind(Intent intent){return binder;}
    @Override public void onCreate(){
        super.onCreate();connectivity=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        wakeLock=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"AeroVPN:NodeTrial");
        wakeLock.setReferenceCounted(false);
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        manager.createNotificationChannel(new NotificationChannel("aero_optimizer","节点验证与自动选择",NotificationManager.IMPORTANCE_LOW));
        VpnStatus.addStateListener(this);
        Intent intent=new Intent(this,OpenVPNService.class);intent.setAction(OpenVPNService.START_SERVICE);
        bound=bindService(intent,engineConnection,Context.BIND_AUTO_CREATE);
        connectivity.registerNetworkCallback(new NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(),networkChanges);
        main.post(tick);
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){
        started=true;startForeground(3401,notification());
        if(intent==null)return START_NOT_STICKY;
        if(stopping)return START_NOT_STICKY;
        switch(String.valueOf(intent.getAction())) {
            case AUTO:
                automatic=intent.getBooleanExtra("enabled",false);
                if(automatic){nextCheck=SystemClock.elapsedRealtime()+PERIOD;if(!busy)submitScan();}
                else message=busy?"自动选择已关闭；完成本轮检测后不再定时检测":"自动选择已关闭";
                publish();break;
            case SCAN:if(!busy)submitScan();break;
            case SPEED:
                if(!busy&&current!=null&&level==ConnectionStatus.LEVEL_CONNECTED){Node testing=current;submit(()->{int token=generation.get();message="正在通过当前 VPN 实测下载速度…";publish();try{NodeHealth h=measure(testing,token);verified.put(h.node.ip,h);message=h.bytesPerSecond>0?String.format(Locale.ROOT,"实测下载 %.2f Mbps · %s",h.bytesPerSecond*8/1_000_000.0,h.speedSource):"当前 VPN 联网正常，测速地址不可达，未测得速度";}catch(Exception e){verified.remove(testing.ip);if(token==generation.get())message="当前隧道联网验证失败，请重新检测";}finally{if(token==generation.get()&&!stopping)busy=false;publish();}});}
                else {message="请先连接通过验证的 VPN 节点";publish();}break;
            case SELECT:
                String ip=intent.getStringExtra("ip");NodeHealth health=health(ip==null?"":ip);
                if(health==null){message="节点验证已过期，请重新检测";publish();}else submit(()->{int token=generation.get();message="正在重新验证所选节点…";publish();try{check(token);stopTunnel();NodeHealth result=trial(health.node,token);verified.put(result.node.ip,result);message="已验证并连接："+result.node.displayCountry();}catch(Exception e){verified.remove(health.node.ip);safeStop();if(token==generation.get())message="所选节点当前不可用，已隐藏";}finally{if(token==generation.get()&&!stopping)busy=false;publish();}});
                break;
            case STOP:
                automatic=false;stopping=true;busy=true;message="正在停止检测并关闭系统 VPN…";generation.incrementAndGet();if(work!=null)work.cancel(true);publish();
                worker.execute(()->{try{stopTunnel();busy=false;message="已确认停止并断开系统 VPN";publish();main.post(()->{started=false;stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();});}
                catch(Exception e){busy=false;stopping=false;message="尚未确认断开，请重试或在手机 VPN 设置中断开";publish();}});break;
            default:break;
        }
        return START_NOT_STICKY;
    }
    private Notification notification(){
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,NodeOptimizerService.class).setAction(STOP),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new NotificationCompat.Builder(this,"aero_optimizer").setSmallIcon(de.blinkt.openvpn.R.drawable.ic_aero).setContentTitle("Aero VPN · "+(automatic?"每 10 分钟自动检测":"节点可用性检测")).setContentText(message).setContentIntent(open).setOngoing(true).addAction(0,"停止并断开",stop).build();
    }
    private void publish(){main.post(()->{if(destroyed)return;for(Runnable r:observers)r.run();if(started&&!busy&&!automatic&&current==null&&!stopping){started=false;stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}if(started)((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(3401,notification());});}
    private void submit(Runnable task){generation.incrementAndGet();if(work!=null)work.cancel(true);busy=true;work=worker.submit(task);publish();}
    private void submitScan(){submit(()->scan(generation.get()));}
    private void check(int token)throws InterruptedException {if(destroyed||Thread.currentThread().isInterrupted()||token!=generation.get())throw new InterruptedException();}
    private void scan(int token){
        failures.clear();recentFailures.clear();Node original=current;NodeHealth best=null;
        wakeLock.acquire(SCAN_BUDGET+60_000);
        long deadline=SystemClock.elapsedRealtime()+SCAN_BUDGET;
        try {
            message="更新目录并检测真实 VPN 联网能力…";publish();
            NodeRepository repository=new NodeRepository(getCacheDir());NodeRepository.Result directory;
            try{directory=repository.load(true);}catch(IOException e){try{directory=repository.load(false);}catch(IOException ignored){directory=repository.seed(getAssets().open("vpngate.csv"));}}
            check(token);
            if(original!=null && level==ConnectionStatus.LEVEL_CONNECTED){
                try{NodeHealth existing=measure(original,token);verified.put(original.ip,existing);best=existing;}catch(IOException e){verified.remove(original.ip);}
            }
            List<Node> candidates=new ArrayList<>(directory.nodes);
            // Rotate the directory so difficult-to-connect nodes do not monopolize every round.
            candidates.sort(Comparator.comparingLong((Node n)->n.score).reversed());
            if(!candidates.isEmpty())Collections.rotate(candidates,-(cursor%candidates.size()));
            int tried=0,inspected=0;
            for(Node node:candidates){
                check(token);if(SystemClock.elapsedRealtime()>deadline-45_000||tried>=12)break;
                inspected++;if(original!=null && node.ip.equals(original.ip) && best!=null)continue;
                message="检测端口 "+inspected+"/"+candidates.size()+" · 已验证 "+available().size()+" 个";publish();
                if(!reachable(node,token)){verified.remove(node.ip);failed(node,"端口不可达","当前网络无法连接此节点端口");continue;}
                tried++;stopTunnel();check(token);
                message="验证隧道 "+tried+"/12 · "+node.displayCountry()+" · "+node.ip;publish();
                try{NodeHealth h=trial(node,token);verified.put(node.ip,h);if(h.substantiallyBetterThan(best))best=h;}
                catch(IOException e){verified.remove(node.ip);String reason=e.getMessage()==null?"连接失败":e.getMessage();failed(node,reason.contains("HTTPS")||reason.contains("路由")?"隧道内联网失败":"隧道未建立",reason);stopTunnel();}
                publish();
            }
            cursor+=inspected;check(token);
            Node target=automatic&&best!=null?best.node:original;
            if(target!=null){
                try{if(current==null||!current.ip.equals(target.ip)||level!=ConnectionStatus.LEVEL_CONNECTED){stopTunnel();NodeHealth h=trial(target,token);verified.put(target.ip,h);}else{NodeHealth h=measure(target,token);verified.put(target.ip,h);}}
                catch(IOException e){verified.remove(target.ip);stopTunnel();target=null;}
            }else stopTunnel();
            if(automatic&&target==null){
                for(NodeHealth fallback:available()){
                    check(token);if(SystemClock.elapsedRealtime()>deadline)break;
                    try{stopTunnel();NodeHealth h=trial(fallback.node,token);verified.put(h.node.ip,h);target=h.node;break;}
                    catch(IOException e){verified.remove(fallback.node.ip);stopTunnel();}
                }
            }
            message=available().isEmpty()?"本轮未发现通过隧道联网验证的节点，请更换网络后检测":automatic&&target!=null?"已自动选择本轮较优节点 · 下次检测约 10 分钟后":"检测完成 · "+available().size()+" 个近期验证可用节点";
        }catch(InterruptedException e){Thread.currentThread().interrupt();safeStop();}
        catch(Exception e){safeStop();message="检测未完成："+(e.getMessage()==null?"请检查网络或 VPN 授权":e.getMessage());}
        finally {if(wakeLock.isHeld())wakeLock.release();if(token==generation.get()&&!stopping)busy=false;publish();}
    }
    private Network physical(){
        Network active=connectivity.getActiveNetwork();NetworkCapabilities caps=active==null?null:connectivity.getNetworkCapabilities(active);
        if(caps!=null&&!caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))return active;
        for(Network n:connectivity.getAllNetworks()){NetworkCapabilities c=connectivity.getNetworkCapabilities(n);if(c!=null&&c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)&&c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))return n;}
        for(Network n:connectivity.getAllNetworks()){NetworkCapabilities c=connectivity.getNetworkCapabilities(n);if(c!=null&&!c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)&&c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))return n;}return null;
    }
    private Network tunnel()throws IOException {
        for(Network n:connectivity.getAllNetworks()){NetworkCapabilities c=connectivity.getNetworkCapabilities(n);if(c!=null&&c.hasTransport(NetworkCapabilities.TRANSPORT_VPN))return n;}throw new IOException("VPN 路由尚未建立");
    }
    private boolean reachable(Node node,int token)throws InterruptedException {
        check(token);String proto="udp",remoteProto="";int port=0;
        for(String line:node.config.split("\\r?\\n")){String[] words=line.trim().split("\\s+");if(words.length>=2&&words[0].equals("proto"))proto=words[1];if(words.length>=3&&words[0].equals("remote")){try{port=Integer.parseInt(words[2]);}catch(NumberFormatException e){return false;}if(words.length>=4)remoteProto=words[3];break;}}
        if(!(remoteProto.isEmpty()?proto:remoteProto).startsWith("tcp"))return true; // UDP requires the actual handshake; a UDP send proves nothing.
        Network network=physical();if(network==null)return false;
        try(Socket socket=network.getSocketFactory().createSocket()){long start=SystemClock.elapsedRealtime();socket.connect(new InetSocketAddress(node.ip,port),1500);return SystemClock.elapsedRealtime()-start<1500;}catch(IOException e){return false;}
    }
    private NodeHealth trial(Node node,int token)throws IOException,InterruptedException {
        check(token);if(VpnService.prepare(this)!=null)throw new IOException("需要重新授权 VPN，请回到应用点击检测");
        awaitEngine(token);
        try{
            if(!NodeParser.safeConfig(node.config,node.ip))throw new IOException("节点配置校验失败");
            ConfigParser parser=new ConfigParser();parser.parseConfig(new StringReader(node.config));VpnProfile profile=parser.convertProfile();
            profile.mExpectTLSCert=true;profile.mName="Aero · "+node.displayCountry()+" · "+node.ip;profile.mConnectRetryMax="1";
            for(Connection connection:profile.mConnections)connection.mConnectTimeout=10;
            if(profile.checkProfile(this)!=de.blinkt.openvpn.R.string.no_error_found)throw new IOException("节点配置不支持当前连接内核");
            String expected=profile.getUUIDString();level=ConnectionStatus.LEVEL_START;engineUuid="";
            current=node;ProfileManager.setTemporaryProfile(this,profile);VPNLaunchHelper.startOpenVpn(profile,this,"Aero verified node trial",true);
            long end=SystemClock.elapsedRealtime()+30_000;
            synchronized(stateLock){while(!(level==ConnectionStatus.LEVEL_CONNECTED&&expected.equals(engineUuid))){check(token);if(level==ConnectionStatus.LEVEL_AUTH_FAILED)throw new IOException("认证失败");if(expected.equals(engineUuid)&&level==ConnectionStatus.LEVEL_NOTCONNECTED)throw new IOException("节点已拒绝连接或退出");if(SystemClock.elapsedRealtime()>=end)throw new IOException("隧道建立超时");stateLock.wait(300);}}
            NodeHealth result=measure(node,token);getSharedPreferences("aero",MODE_PRIVATE).edit().putString("connected_label",node.displayCountry()+" · "+node.ip).apply();return result;
        }catch(InterruptedException|IOException e){throw e;}catch(Exception e){throw new IOException("无法建立节点隧道",e);}
    }
    private void awaitEngine(int token)throws IOException,InterruptedException {
        long end=SystemClock.elapsedRealtime()+5_000;
        synchronized(stateLock){while(engine==null){check(token);if(SystemClock.elapsedRealtime()>=end)throw new IOException("连接服务尚未就绪");stateLock.wait(200);}}
    }
    private NodeHealth measure(Node node,int token)throws IOException,InterruptedException {
        check(token);int epoch=networkEpoch.get();Network vpn=tunnel();long start=SystemClock.elapsedRealtime();boolean online=false;
        for(String url:Arrays.asList("https://www.cloudflare.com/cdn-cgi/trace","https://www.gstatic.com/generate_204")){
            check(token);HttpsURLConnection c=(HttpsURLConnection)vpn.openConnection(new URL(url));c.setConnectTimeout(4000);c.setReadTimeout(4000);c.setInstanceFollowRedirects(false);
            try {int code=c.getResponseCode();if(url.endsWith("generate_204")){if(code==204){online=true;break;}}else if(code==200){try(InputStream in=c.getInputStream()){ByteArrayOutputStream response=new ByteArrayOutputStream();byte[] data=new byte[512];int n;while(response.size()<4096&&(n=in.read(data,0,Math.min(data.length,4096-response.size())))!=-1){check(token);response.write(data,0,n);}String body=response.toString("UTF-8");if(body.contains("\nip=")&&body.contains("\nh=")){online=true;break;}}}}
            catch(IOException ignored){}finally{c.disconnect();}
        }
        if(!online)throw new IOException("隧道内 HTTPS 联网验证失败");
        long latency=Math.max(1,SystemClock.elapsedRealtime()-start),rate=0;check(token);
        long sampleBytes=0;String speedSource="";
        String[] speedUrls={"https://speed.cloudflare.com/__down?bytes=1048576","https://raw.githubusercontent.com/2507155811/123/main/artifacts/AeroVPN-debug.apk"};
        for(int i=0;i<speedUrls.length;i++){
            check(token);message="VPN 隧道测速 · "+(i==0?"Cloudflare":"GitHub 备用");publish();
            HttpsURLConnection download=(HttpsURLConnection)vpn.openConnection(new URL(speedUrls[i]));
            download.setConnectTimeout(4000);download.setReadTimeout(4000);download.setInstanceFollowRedirects(false);
            download.setRequestProperty("Accept-Encoding","identity");download.setRequestProperty("Cache-Control","no-cache");download.setRequestProperty("Range","bytes=0-1048575");
            try{int status=download.getResponseCode();if(status==200||status==206){String encoding=download.getHeaderField("Content-Encoding");if(encoding!=null&&!encoding.equalsIgnoreCase("identity"))continue;
                try(InputStream in=download.getInputStream()){DownloadSample.Result sample=DownloadSample.measure(in,SystemClock::elapsedRealtime,()->check(token));rate=sample.bytesPerSecond;sampleBytes=sample.bytes;speedSource=i==0?"Cloudflare":"GitHub";break;}
            }}catch(IOException ignored){}finally{download.disconnect();}
        }
        if(networkEpoch.get()!=epoch)throw new IOException("网络变化，验证结果已作废");
        if(level!=ConnectionStatus.LEVEL_CONNECTED)throw new IOException("节点连接已中断");
        return new NodeHealth(node,SystemClock.elapsedRealtime(),latency,rate,sampleBytes,speedSource);
    }
    private void stopTunnel()throws IOException,InterruptedException {
        keepVPNAlive.unscheduleKeepVPNAliveJobService(this);
        intentionalStop=true;try{TunnelStopper.await(new TunnelStopper.Transport(){
            public long elapsed(){return SystemClock.elapsedRealtime();}
            public void requestStop()throws IOException{IOpenVPNServiceInternal service=engine;if(service!=null){try{service.stopVPN(false);}catch(RemoteException e){throw new IOException("无法停止前一条隧道",e);}}}
            public boolean inactive(){return level==ConnectionStatus.LEVEL_NOTCONNECTED||level==ConnectionStatus.LEVEL_AUTH_FAILED||level==ConnectionStatus.UNKNOWN_LEVEL;}
            public void pause()throws InterruptedException{synchronized(stateLock){stateLock.wait(200);}}
        });current=null;}finally{intentionalStop=false;}
    }
    private void safeStop(){boolean interrupted=Thread.interrupted();try{stopTunnel();}catch(Exception ignored){}finally{if(interrupted)Thread.currentThread().interrupt();}}
    @Override public void updateState(String state,String log,int resource,ConnectionStatus value,Intent intent){level=value;synchronized(stateLock){stateLock.notifyAll();}if(current!=null&&(value==ConnectionStatus.LEVEL_AUTH_FAILED||(value==ConnectionStatus.LEVEL_NOTCONNECTED&&!intentionalStop)))verified.remove(current.ip);publish();}
    @Override public void setConnectedVPN(String uuid){engineUuid=uuid==null?"":uuid;synchronized(stateLock){stateLock.notifyAll();}}
    @Override public void onDestroy(){destroyed=true;automatic=false;generation.incrementAndGet();if(work!=null)work.cancel(true);main.removeCallbacksAndMessages(null);connectivity.unregisterNetworkCallback(networkChanges);VpnStatus.removeStateListener(this);if(bound)unbindService(engineConnection);if(wakeLock.isHeld())wakeLock.release();worker.shutdownNow();observers.clear();super.onDestroy();}
}
