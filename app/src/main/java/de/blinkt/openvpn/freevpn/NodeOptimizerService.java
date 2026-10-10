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

/** Runs manually requested tunnel searches and speed tests; never schedules node selection. */
public final class NodeOptimizerService extends Service implements VpnStatus.StateListener {
    public static final String SCAN="scan",CANCEL="cancel",SELECT="select",STOP="stop",SPEED="speed";
    private static final long SCAN_BUDGET=90_000L;
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
    private volatile boolean busy,destroyed,stopping,intentionalStop;
    private volatile String message="尚未搜索：点击搜索可用节点";
    private volatile Future<?> work;
    private ConnectivityManager connectivity;
    private PowerManager.WakeLock wakeLock;
    private volatile boolean bound;
    private boolean started;
    private final AtomicInteger networkEpoch=new AtomicInteger();
    private final ConnectivityManager.NetworkCallback networkChanges=new ConnectivityManager.NetworkCallback(){
        @Override public void onAvailable(Network network){invalidateNetwork();}
        @Override public void onLost(Network network){invalidateNetwork();}
    };
    private void invalidateNetwork(){networkEpoch.incrementAndGet();verified.clear();main.post(()->{if(destroyed)return;if(!busy){message="网络已变化，需要重新验证节点";}publish();});}
    private volatile boolean searching;
    public boolean isSearching(){return searching;}
    public boolean isStopping(){return stopping;}
    public boolean hasTunnel(){return current!=null&&level!=ConnectionStatus.LEVEL_NOTCONNECTED&&level!=ConnectionStatus.LEVEL_AUTH_FAILED&&level!=ConnectionStatus.UNKNOWN_LEVEL;}
    private final Map<String,Integer> failures=new ConcurrentHashMap<>();
    private final List<String> recentFailures=new CopyOnWriteArrayList<>();
    public String diagnostics(){
        StringBuilder text=new StringBuilder("只记录本轮搜索或连接结果，不包含账号或配置密钥。\n可达节点（端口探测不证明联网）："+available().size()+"\n");
        for(String stage:Arrays.asList("需要来源密码","端口不可达","隧道未建立","隧道内联网失败"))text.append(stage).append("：").append(failures.getOrDefault(stage,0)).append('\n');
        for(String reason:recentFailures)text.append('\n').append(reason);
        return text.toString();
    }
    private void failed(Node node,String stage,String reason){failures.merge(stage,1,Integer::sum);if(recentFailures.size()<8)recentFailures.add(node.displayCountry()+" · "+node.ip+"："+reason);}

    private final ServiceConnection engineConnection=new ServiceConnection(){
        public void onServiceConnected(ComponentName name,IBinder b){engine=IOpenVPNServiceInternal.Stub.asInterface(b);synchronized(stateLock){stateLock.notifyAll();}}
        public void onServiceDisconnected(ComponentName name){engine=null;if(current!=null)verified.remove(current.ip);level=ConnectionStatus.LEVEL_NOTCONNECTED;synchronized(stateLock){stateLock.notifyAll();}}
    };
    private final Runnable tick=new Runnable(){public void run(){
        if(destroyed)return;
        long now=SystemClock.elapsedRealtime();
        verified.entrySet().removeIf(e->!e.getValue().fresh(now));
        publish();main.postDelayed(this,15_000);
    }};
    public List<NodeHealth> available(){
        long now=SystemClock.elapsedRealtime();List<NodeHealth> result=new ArrayList<>();
        for(NodeHealth h:verified.values())if(h.fresh(now))result.add(h);
        result.sort(Comparator.comparingDouble(NodeHealth::quality).reversed());return result;
    }
    public NodeHealth health(String ip){NodeHealth h=verified.get(ip);return h!=null&&h.fresh(SystemClock.elapsedRealtime())?h:null;}
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
        manager.createNotificationChannel(new NotificationChannel("aero_optimizer","节点搜索与测速",NotificationManager.IMPORTANCE_LOW));
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
            case SCAN:if(!busy)submitScan();break;
            case SPEED:
                if(!busy&&current!=null&&level==ConnectionStatus.LEVEL_CONNECTED){Node testing=current;submit(()->{int token=generation.get();message="正在通过当前 VPN 实测下载速度…";publish();try{NodeHealth h=measure(testing,token);verified.put(h.node.ip,h);message=h.bytesPerSecond>0?String.format(Locale.ROOT,"实测下载 %.2f Mbps · %s",h.bytesPerSecond*8/1_000_000.0,h.speedSource):"当前 VPN 联网正常，测速地址不可达，未测得速度";}catch(Exception e){verified.remove(testing.ip);if(token==generation.get())message="当前隧道联网验证失败，请重新检测";}finally{if(token==generation.get()&&!stopping)busy=false;publish();}});}
                else {message="请先连接通过验证的 VPN 节点";publish();}break;
            case SELECT:
                String ip=intent.getStringExtra("ip");NodeHealth health=health(ip==null?"":ip);
                if(health==null){message="节点验证已过期，请重新检测";publish();}else submit(()->{int token=generation.get();message="正在重新验证所选节点…";publish();try{check(token);stopTunnel();NodeHealth result=trial(health.node,token);verified.put(result.node.ip,result);message="已验证并连接："+result.node.displayCountry();}catch(Exception e){verified.remove(health.node.ip);safeStop();if(token==generation.get())message="连接失败："+(e.getMessage()==null?"请重试或换节点":e.getMessage());}finally{if(token==generation.get()&&!stopping)busy=false;publish();}});
                break;
            case CANCEL:
                if(searching){generation.incrementAndGet();if(work!=null)work.cancel(true);searching=false;busy=false;message="搜索已停止，当前 VPN 连接保留";publish();}break;
            case STOP:
                stopping=true;searching=false;busy=true;message="正在断开系统 VPN…";int stopToken=generation.incrementAndGet();if(work!=null)work.cancel(true);publish();
                worker.execute(()->{try{stopTunnel();main.post(()->{if(stopToken!=generation.get()||destroyed)return;releaseEngine();level=ConnectionStatus.LEVEL_NOTCONNECTED;engineUuid="";stopping=false;busy=false;message="已断开，可重新连接";started=false;stopForeground(STOP_FOREGROUND_REMOVE);stopSelfResult(id);publish();});}
                catch(Exception e){busy=false;stopping=false;message="尚未确认断开，请重试或在手机 VPN 设置中断开";publish();}});break;
            default:break;
        }
        return START_NOT_STICKY;
    }
    private Notification notification(){
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,NodeOptimizerService.class).setAction(STOP),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new NotificationCompat.Builder(this,"aero_optimizer").setSmallIcon(de.blinkt.openvpn.R.drawable.ic_aero).setContentTitle("Aero VPN · 搜索可用节点").setContentText(message).setContentIntent(open).setOngoing(true).addAction(0,"停止并断开",stop).build();
    }
    private void publish(){main.post(()->{if(destroyed)return;for(Runnable r:observers)r.run();if(started&&!busy&&current==null&&!stopping){started=false;stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}if(started)((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(3401,notification());});}
    private void submit(Runnable task){generation.incrementAndGet();if(work!=null)work.cancel(true);busy=true;work=worker.submit(task);publish();}
    private void submitScan(){searching=true;submit(()->scan(generation.get()));}
    private void check(int token)throws InterruptedException {if(destroyed||Thread.currentThread().isInterrupted()||token!=generation.get())throw new InterruptedException();}
    private void scan(int token){
        searching=true;failures.clear();recentFailures.clear();
        ExecutorService probes=Executors.newFixedThreadPool(24);List<Future<NodeHealth>> pending=new ArrayList<>();
        int epoch=networkEpoch.get();wakeLock.acquire(SCAN_BUDGET+10_000);
        try{
            message="正在读取候选列表并快速探测…搜索不会切换 VPN";publish();
            NodeRepository repository=new NodeRepository(getCacheDir());NodeRepository.Result directory;
            try{directory=repository.load(false);}catch(IOException e){try{directory=repository.load(true);}catch(IOException ignored){directory=repository.seed(getAssets().open("vpngate.csv"));}}
            check(token);Network network=physical();if(network==null)throw new IOException("没有可用的手机网络");
            epoch=networkEpoch.get();final int searchEpoch=epoch;int udp=0;
            CompletionService<NodeHealth> results=new ExecutorCompletionService<>(probes);
            for(Node node:directory.nodes){
                int port=TcpEndpoint.port(node.config);if(port<0){udp++;continue;}
                pending.add(results.submit(()->{
                    check(token);long start=SystemClock.elapsedRealtime();
                    try(Socket socket=network.getSocketFactory().createSocket()){
                        socket.connect(new InetSocketAddress(node.ip,port),1800);check(token);
                        if(networkEpoch.get()!=searchEpoch)throw new IOException("网络已变化");
                        return NodeHealth.reachable(node,SystemClock.elapsedRealtime(),Math.max(1,SystemClock.elapsedRealtime()-start));
                    }catch(IOException e){NodeHealth old=health(node.ip);if(old!=null&&!old.tunnelVerified)verified.remove(node.ip,old);failed(node,"端口不可达","当前网络无法连接此节点端口");return null;}
                }));
            }
            long deadline=SystemClock.elapsedRealtime()+30_000;int finished=0;
            while(finished<pending.size()){
                check(token);if(networkEpoch.get()!=epoch)throw new IOException("网络变化，请重新搜索");
                long left=deadline-SystemClock.elapsedRealtime();if(left<=0)break;
                Future<NodeHealth> result=results.poll(Math.min(left,500),TimeUnit.MILLISECONDS);if(result==null)continue;
                finished++;try{NodeHealth h=result.get();check(token);if(networkEpoch.get()!=epoch)throw new IOException("网络变化，请重新搜索");if(h!=null){NodeHealth old=health(h.node.ip);if(old==null||!old.tunnelVerified)verified.put(h.node.ip,h);}}catch(ExecutionException ignored){}
                message="并发搜索 "+finished+"/"+pending.size()+" · 已发现 "+available().size()+" 个可达节点";publish();
            }
            message="搜索完成 · "+available().size()+" 个可达节点；连接后验证联网和测速"+(udp>0?" · "+udp+" 个 UDP 配置无法用 TCP 探测，未列入结果":"");
        }catch(InterruptedException e){Thread.currentThread().interrupt();}
        catch(Exception e){message="搜索未完成："+(e.getMessage()==null?"请检查网络":e.getMessage());}
        finally{for(Future<NodeHealth> f:pending)f.cancel(true);probes.shutdownNow();if(wakeLock.isHeld())wakeLock.release();if(token==generation.get()){searching=false;if(!stopping)busy=false;publish();}}
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
    private NodeHealth trial(Node node,int token)throws IOException,InterruptedException {
        check(token);if(VpnService.prepare(this)!=null)throw new IOException("需要重新授权 VPN，请回到应用点击检测");
        awaitEngine(token);
        try{
            if(!NodeParser.safeConfig(node.config,node.ip))throw new IOException("节点配置校验失败");
            ConfigParser parser=new ConfigParser();parser.parseConfig(new StringReader(node.config));VpnProfile profile=parser.convertProfile();
            profile.mExpectTLSCert=true;if(node.provider().equals("VPNBook")){profile.mUsername="vpnbook";profile.mPassword=ProviderPassword.read(this);if(profile.mPassword.isEmpty())throw new IOException("请先设置 VPNBook 官网共享密码");}profile.mName="Aero · "+node.displayCountry()+" · "+node.ip;profile.mConnectRetryMax="1";
            for(Connection connection:profile.mConnections)connection.mConnectTimeout=10;
            if(profile.checkProfile(this)!=de.blinkt.openvpn.R.string.no_error_found)throw new IOException("节点配置不支持当前连接内核");
            String expected=profile.getUUIDString();level=ConnectionStatus.LEVEL_START;engineUuid="";
            check(token);current=node;ProfileManager.setTemporaryProfile(this,profile);VPNLaunchHelper.startOpenVpn(profile,this,"Aero verified node trial",true);
            long end=SystemClock.elapsedRealtime()+30_000;
            synchronized(stateLock){while(!(level==ConnectionStatus.LEVEL_CONNECTED&&expected.equals(engineUuid))){check(token);if(level==ConnectionStatus.LEVEL_AUTH_FAILED)throw new IOException("认证失败");if(expected.equals(engineUuid)&&level==ConnectionStatus.LEVEL_NOTCONNECTED)throw new IOException("节点已拒绝连接或退出");if(SystemClock.elapsedRealtime()>=end)throw new IOException("隧道建立超时");stateLock.wait(300);}}
            NodeHealth result=measure(node,token);getSharedPreferences("aero",MODE_PRIVATE).edit().putString("connected_label",node.displayCountry()+" · "+node.ip).apply();return result;
        }catch(InterruptedException|IOException e){throw e;}catch(Exception e){throw new IOException("无法建立节点隧道",e);}
    }
    private void releaseEngine(){
        engine=null;if(bound){unbindService(engineConnection);bound=false;}
        stopService(new Intent(this,OpenVPNService.class));
    }
    private void awaitEngine(int token)throws IOException,InterruptedException {
        if(!bound)main.post(()->{if(destroyed||stopping||bound)return;Intent intent=new Intent(this,OpenVPNService.class).setAction(OpenVPNService.START_SERVICE);bound=bindService(intent,engineConnection,Context.BIND_AUTO_CREATE);});
        long end=SystemClock.elapsedRealtime()+8_000;
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
        },current!=null);current=null;}finally{intentionalStop=false;}
    }
    private void safeStop(){boolean interrupted=Thread.interrupted();try{stopTunnel();}catch(Exception ignored){}finally{if(interrupted)Thread.currentThread().interrupt();}}
    @Override public void updateState(String state,String log,int resource,ConnectionStatus value,Intent intent){level=value;synchronized(stateLock){stateLock.notifyAll();}if(current!=null&&(value==ConnectionStatus.LEVEL_AUTH_FAILED||(value==ConnectionStatus.LEVEL_NOTCONNECTED&&!intentionalStop)))verified.remove(current.ip);publish();}
    @Override public void setConnectedVPN(String uuid){engineUuid=uuid==null?"":uuid;synchronized(stateLock){stateLock.notifyAll();}}
    @Override public void onDestroy(){destroyed=true;generation.incrementAndGet();if(work!=null)work.cancel(true);main.removeCallbacksAndMessages(null);connectivity.unregisterNetworkCallback(networkChanges);VpnStatus.removeStateListener(this);if(bound)unbindService(engineConnection);if(wakeLock.isHeld())wakeLock.release();worker.shutdownNow();observers.clear();super.onDestroy();}
}
