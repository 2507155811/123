package de.blinkt.openvpn.freevpn;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.VpnService;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.core.*;

public final class MainActivity extends Activity implements VpnStatus.StateListener {
    private static final int BLUE=0xff245cec, INK=0xff15253c, MUTED=0xff7c889d, BG=0xfff5f7fb;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final ExecutorService vpnIo=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final List<Node> nodes=new ArrayList<>(), visible=new ArrayList<>();
    private Set<String> favorites; private SharedPreferences prefs;
    private NodeRepository repository; private Node selected;
    private VpnProfile pendingProfile; private IOpenVPNServiceInternal vpnService;
    private boolean bound, refreshing, favoriteOnly, connected, active, initiating, expired;
    private int connectAttempt=0;
    private int sort=0; private String country="全部地区", query="", restoredIp="";
    private long fetchedAt;private String loadError;
    private TextView status, selectedLabel, source, count, empty, traffic, allTab, favoriteTab;
    private Button connect, refresh;
    private EditText search;private Spinner countries, sorts;
    private ListView list; private NodeAdapter adapter; private Radar radar;
    private final ServiceConnection serviceConnection=new ServiceConnection() {
        public void onServiceConnected(ComponentName name,IBinder binder){vpnService=IOpenVPNServiceInternal.Stub.asInterface(binder);}
        public void onServiceDisconnected(ComponentName name){vpnService=null;}
    };
    private final VpnStatus.ByteCountListener bytes=(in,out,diffIn,diffOut)->handler.post(()->{
        if(traffic!=null)traffic.setText(connected?"↓ "+formatBytes(in)+"    ↑ "+formatBytes(out):"内置 OpenVPN · 无需额外客户端");
    });
    private final Runnable checkExpiry=new Runnable(){public void run(){
        expired=fetchedAt>0 && System.currentTimeMillis()-fetchedAt>3_600_000;
        updateConnection();handler.postDelayed(this,30_000);
    }};

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        prefs=getSharedPreferences("aero",MODE_PRIVATE);
        favorites=new HashSet<>(prefs.getStringSet("favorites",Collections.emptySet()));
        restoredIp=prefs.getString("selected_ip","");
        repository=new NodeRepository(getCacheDir());
        if(saved!=null){query=saved.getString("query","");sort=saved.getInt("sort",0);country=saved.getString("country","全部地区");favoriteOnly=saved.getBoolean("favorites",false);}
        buildUi();search.setText(query);sorts.setSelection(sort);
        if(!prefs.getBoolean("notice_seen",false))new AlertDialog.Builder(this).setTitle("欢迎使用 Aero VPN")
                .setMessage("节点来自日本筑波大学 VPN Gate 志愿者网络，免费且动态变化。节点运营者可能记录连接信息，请勿用于传输敏感资料。国家信息由节点源提供，已排除 CN、HK、MO。首次连接需授权安卓 VPN 权限。")
                .setPositiveButton("知道了",(d,w)->prefs.edit().putBoolean("notice_seen",true).apply()).show();
        io.execute(()->{
            NodeRepository.Result initial=null;
            try{initial=repository.seed(getAssets().open("vpngate.csv"));}catch(IOException ignored){try{initial=repository.load(false);}catch(IOException ignoredCache){}}
            NodeRepository.Result snapshot=initial;
            handler.post(()->{if(isDestroyed())return;if(snapshot!=null)showNodes(snapshot);loadNodes();});
        });
    }
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putString("query",query);out.putString("country",country);out.putInt("sort",sort);out.putBoolean("favorites",favoriteOnly);}
    @Override protected void onStart(){super.onStart();
        VpnStatus.addStateListener(this);VpnStatus.addByteCountListener(bytes);
        Intent intent=new Intent(this,OpenVPNService.class);intent.setAction(OpenVPNService.START_SERVICE);
        bound=bindService(intent,serviceConnection,Context.BIND_AUTO_CREATE);
        handler.post(checkExpiry);
    }
    @Override protected void onStop(){
        VpnStatus.removeStateListener(this);VpnStatus.removeByteCountListener(bytes);handler.removeCallbacks(checkExpiry);
        if(bound){unbindService(serviceConnection);bound=false;}vpnService=null;super.onStop();
    }
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);io.shutdownNow();vpnIo.shutdownNow();super.onDestroy();}
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private GradientDrawable background(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private TextView label(String text,int size,int color){TextView t=new TextView(this);t.setText(text);t.setTextSize(size);t.setTextColor(color);return t;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private void bold(TextView t){t.setTypeface(null,Typeface.BOLD);}
    private void space(LinearLayout l,int height){l.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height)));}
    private Button button(String text,boolean primary){Button b=new Button(this);b.setText(text);b.setTextSize(14);b.setAllCaps(false);b.setTextColor(primary?Color.WHITE:BLUE);b.setBackground(background(primary?BLUE:0xffeaf0ff,14));b.setPadding(dp(18),0,dp(18),0);return b;}
    private void buildUi(){
        LinearLayout root=column();root.setBackgroundColor(BG);root.setPadding(dp(22),dp(16),dp(22),0);setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(dp(22),dp(16)+insets.getSystemWindowInsetTop(),dp(22),insets.getSystemWindowInsetBottom());return insets.consumeSystemWindowInsets();});
        LinearLayout header=row();
        TextView mark=label("A",24,Color.WHITE);bold(mark);mark.setGravity(Gravity.CENTER);mark.setBackground(background(BLUE,13));header.addView(mark,new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout title=column();title.setPadding(dp(12),0,0,0);TextView name=label("Aero VPN",23,INK);bold(name);title.addView(name);title.addView(label("探索世界，轻松连接",11,MUTED));header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        TextView info=label("ⓘ",25,MUTED);info.setGravity(Gravity.CENTER);info.setContentDescription("关于与隐私说明");header.addView(info,new LinearLayout.LayoutParams(dp(48),dp(48)));info.setOnClickListener(v->showAbout());root.addView(header);space(root,20);
        LinearLayout hero=column();hero.setPadding(dp(18),dp(18),dp(18),dp(16));hero.setBackground(background(Color.WHITE,22));
        LinearLayout top=row();LinearLayout text=column();status=label("尚未连接",23,INK);bold(status);text.addView(status);selectedLabel=label("选择一个境外节点开始",12,MUTED);text.addView(selectedLabel);top.addView(text,new LinearLayout.LayoutParams(0,-2,1));radar=new Radar();top.addView(radar,new LinearLayout.LayoutParams(dp(76),dp(76)));hero.addView(top);
        space(hero,12);connect=button("选择节点后连接",true);connect.setEnabled(false);connect.setAlpha(.5f);hero.addView(connect,new LinearLayout.LayoutParams(-1,dp(48)));connect.setOnClickListener(v->{if(active||initiating)disconnect();else beginConnect();});
        space(hero,10);traffic=label("内置 OpenVPN · 无需额外客户端",11,MUTED);traffic.setGravity(Gravity.CENTER);hero.addView(traffic);root.addView(hero);space(root,16);
        LinearLayout searchRow=row();search=new EditText(this);search.setSingleLine(true);search.setTextSize(14);search.setTextColor(INK);search.setHintTextColor(MUTED);search.setHint("搜索国家 / IP / 节点名称");search.setPadding(dp(14),0,dp(14),0);search.setBackground(background(Color.WHITE,14));search.setInputType(android.text.InputType.TYPE_CLASS_TEXT);search.setContentDescription("搜索境外 VPN 节点");searchRow.addView(search,new LinearLayout.LayoutParams(0,dp(48),1));
        refresh=button("刷新",false);refresh.setSingleLine(true);refresh.setPadding(dp(8),0,dp(8),0);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(dp(84),dp(48));rp.leftMargin=dp(10);searchRow.addView(refresh,rp);refresh.setOnClickListener(v->loadNodes());root.addView(searchRow);
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){query=s.toString();filter();}public void afterTextChanged(Editable e){}});
        space(root,8);Button importButton=button("导入节点列表（CSV）",false);root.addView(importButton,new LinearLayout.LayoutParams(-1,dp(40)));
        importButton.setOnClickListener(v->{if(refreshing){toast("请等待当前更新完成");return;}Intent file=new Intent(Intent.ACTION_OPEN_DOCUMENT);file.addCategory(Intent.CATEGORY_OPENABLE);file.setType("*/*");startActivityForResult(file,8);});
        space(root,10);LinearLayout filters=row();countries=new Spinner(this);countries.setContentDescription("按地区筛选");sorts=new Spinner(this);sorts.setContentDescription("节点排序");filters.addView(countries,new LinearLayout.LayoutParams(0,dp(44),1));filters.addView(sorts,new LinearLayout.LayoutParams(0,dp(44),1));root.addView(filters);
        setSpinner(sorts,Arrays.asList("推荐排序","低延迟优先","高速度优先"));sorts.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int pos,long id){sort=pos;filter();}});
        setSpinner(countries,Collections.singletonList("全部地区"));countries.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int pos,long id){country=p.getItemAtPosition(pos).toString();filter();}});
        LinearLayout tabs=row();allTab=label("全部节点",15,BLUE);bold(allTab);allTab.setPadding(0,dp(12),dp(24),dp(12));favoriteTab=label("我的收藏",15,MUTED);favoriteTab.setPadding(0,dp(12),dp(16),dp(12));tabs.addView(allTab);tabs.addView(favoriteTab);count=label("0",12,MUTED);count.setGravity(Gravity.RIGHT);tabs.addView(count,new LinearLayout.LayoutParams(0,-2,1));root.addView(tabs);
        allTab.setOnClickListener(v->{favoriteOnly=false;filter();});favoriteTab.setOnClickListener(v->{favoriteOnly=true;filter();});
        source=label("正在获取实时境外节点…",10,MUTED);source.setPadding(0,0,0,dp(8));root.addView(source);
        FrameLayout frame=new FrameLayout(this);list=new ListView(this);list.setDivider(null);list.setDividerHeight(dp(10));list.setClipToPadding(false);list.setPadding(0,0,0,dp(12));adapter=new NodeAdapter();list.setAdapter(adapter);list.setOnItemClickListener((p,v,pos,id)->chooseNode(visible.get(pos)));frame.addView(list,new FrameLayout.LayoutParams(-1,-1));
        empty=label("正在搜索可用节点…",14,MUTED);empty.setGravity(Gravity.CENTER);empty.setPadding(dp(16),dp(16),dp(16),dp(16));frame.addView(empty,new FrameLayout.LayoutParams(-1,-1));root.addView(frame,new LinearLayout.LayoutParams(-1,0,1));
    }
    private void setSpinner(Spinner s,List<String> values){ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,values);a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);s.setAdapter(a);}
    private void showAbout(){new AlertDialog.Builder(this).setTitle("Aero VPN · 自由探索")
            .setMessage("免费节点：VPN Gate（日本筑波大学志愿者项目）\n来源：www.vpngate.net\n备用分发：本项目 GitHub 目录，每小时计划更新（更新可能延迟）\n安装包内置带采集时间的真实节点快照，最多使用七天，不保证在线\n\n仅显示地区代码不属于 CN / HK / MO 的节点；国家位置未经独立验证。速度与延迟为节点源报告值，不代表本机实测。\n\n节点可能随时离线或记录访问，请勿传输敏感资料。应用无账号、广告或分析 SDK，收藏与最长七天的离线列表保存在本机。\n\n内置 OpenVPN for Android 0.7.62，遵循 GPLv2 及其附加条款。源码和许可证随项目提供。")
            .setPositiveButton("关闭",null).show();}
    private void loadNodes(){
        if(refreshing)return;loadError=null;refreshing=true;refresh.setEnabled(false);refresh.setText("…");
        source.setText("正在查询 3 个 HTTPS 获取渠道…");
        io.execute(()->{
            NodeRepository.Result result;String problem=null;
            try{result=repository.load(true);}catch(IOException e){problem=NodeRepository.explain(e);try{result=repository.load(false);}catch(IOException cacheError){result=null;}}
            NodeRepository.Result r=result;String error=problem;
            handler.post(()->{
                if(isDestroyed())return;refreshing=false;refresh.setEnabled(true);refresh.setText("刷新");
                if(r==null){loadError=error==null?"节点源暂时不可用":error;source.setText("更新失败 · 检查网络后重试");filter();empty.setText("暂时无法获取节点\n"+(error==null?"节点源暂时不可用":error)+"\n\n切换 Wi-Fi 后刷新，或导入 VPN Gate CSV 列表");updateConnection();return;}
                showNodes(r);
                if(r.cached)toast("实时更新不可用，保留离线列表；节点可能已经离线");

            });
        });
    }
    private void showNodes(NodeRepository.Result r){
        nodes.clear();nodes.addAll(r.nodes);fetchedAt=r.fetchedAt;expired=System.currentTimeMillis()-fetchedAt>3_600_000;
        String selectedIp=selected!=null?selected.ip:restoredIp;selected=null;
        for(Node n:nodes)if(n.ip.equals(selectedIp)){selected=n;break;}
        TreeSet<String> regions=new TreeSet<>();for(Node n:nodes)regions.add(n.displayCountry());
        List<String> options=new ArrayList<>();options.add("全部地区");options.addAll(regions);String oldCountry=country;
        setSpinner(countries,options);countries.setSelection(Math.max(0,options.indexOf(oldCountry)));
        source.setText(r.origin+" · "+new SimpleDateFormat("MM-dd HH:mm",Locale.getDefault()).format(new Date(r.fetchedAt))+" · "+(expired?"列表较旧，连接前请更新":"排除 CN/HK/MO"));
        filter();updateConnection();
    }
    private void filter(){
        visible.clear();for(Node n:nodes)if(n.matches(query) && (country.equals("全部地区")||country.equals(n.displayCountry())) && (!favoriteOnly||favorites.contains(n.ip)))visible.add(n);
        Comparator<Node> comparator=sort==1?Comparator.comparingInt(n->n.ping):sort==2?Comparator.comparingLong((Node n)->n.speed).reversed():Comparator.comparingLong((Node n)->n.score).reversed();
        visible.sort(comparator);if(adapter==null)return;adapter.notifyDataSetChanged();count.setText(visible.size()+" 个");allTab.setTextColor(favoriteOnly?MUTED:BLUE);favoriteTab.setTextColor(favoriteOnly?BLUE:MUTED);
        empty.setVisibility(visible.isEmpty()?View.VISIBLE:View.GONE);
        if(visible.isEmpty())empty.setText(!refreshing && nodes.isEmpty() && loadError!=null?"暂时无法获取节点\n"+loadError+"\n\n切换 Wi-Fi 后刷新，或导入 VPN Gate CSV 列表":refreshing?"正在搜索可用节点…":favoriteOnly?"还没有收藏的匹配节点\n点击节点右侧 ☆ 收藏":"没有匹配的节点\n尝试其他关键词或刷新列表");
    }
    private void chooseNode(Node node){
        if(active||initiating){toast("请先断开当前连接，再切换节点");return;}
        search.clearFocus();((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(),0);
        selected=node;prefs.edit().putString("selected_ip",node.ip).apply();adapter.notifyDataSetChanged();updateConnection();
    }
    private void updateConnection(){
        if(status==null)return;radar.on=connected;radar.invalidate();
        selectedLabel.setText(connected?prefs.getString("connected_label","VPN 隧道已建立"):selected==null?"选择一个境外节点开始":selected.displayCountry()+" · "+selected.ip);
        connect.setText(active||initiating?connected?"断开连接":"取消连接":selected==null?"选择节点后连接":expired?"尝试连接（旧列表）":"连接此节点");
        boolean enabled=active||initiating||selected!=null;connect.setEnabled(enabled);connect.setAlpha(enabled?1f:.5f);
        if(!active&&!initiating){status.setText("尚未连接");traffic.setText("内置 OpenVPN · 无需额外客户端");}
    }
    private void beginConnect(){
        if(selected==null||initiating)return;
        Node node=selected;
        new AlertDialog.Builder(this).setTitle("连接 "+node.displayCountry())
                .setMessage(node.ip+(expired?"\n\n该节点来自超过一小时的旧列表，可能已失效。建议刷新；继续仅尝试连接，不代表节点可用。":"")+"\n\n此免费节点由第三方志愿者提供，可能记录连接信息。继续后应用会通过内置 OpenVPN 建立隧道。")
                .setNegativeButton("取消",null).setPositiveButton("连接",(d,w)->prepareProfile(node)).show();
    }
    private void prepareProfile(Node node){
        initiating=true;int attempt=++connectAttempt;status.setText("准备连接…");updateConnection();
        vpnIo.execute(()->{
            try {
                if(!NodeParser.safeConfig(node.config,node.ip))throw new IOException("节点配置未通过校验");
                ConfigParser parser=new ConfigParser();parser.parseConfig(new StringReader(node.config));
                VpnProfile profile=parser.convertProfile();profile.mExpectTLSCert=true;profile.mName="Aero · "+node.displayCountry()+" · "+node.ip;
                int error=profile.checkProfile(getApplicationContext());if(error!=de.blinkt.openvpn.R.string.no_error_found)throw new IOException(getString(error));
                ProfileManager.setTemporaryProfile(getApplicationContext(),profile);
                try(FileOutputStream file=openFileOutput("pending.ovpn",MODE_PRIVATE)){file.write(node.config.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
                prefs.edit().putString("pending_ip",node.ip).putString("pending_name",profile.mName).putString("connected_label",node.displayCountry()+" · "+node.ip).apply();
                handler.post(()->{if(isDestroyed() || attempt!=connectAttempt)return;pendingProfile=profile;
                    if(Build.VERSION.SDK_INT>=33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},8);
                    else authorizeVpn();
                });
            } catch(Exception e){handler.post(()->{if(isDestroyed() || attempt!=connectAttempt)return;initiating=false;updateConnection();toast("无法使用此节点："+e.getMessage());});}
        });
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==8 && pendingProfile!=null)authorizeVpn();}
    private void authorizeVpn(){
        Intent request=VpnService.prepare(this);
        if(request!=null)startActivityForResult(request,7);else startTunnel();
    }
    @Override public void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
        if(request==8 && result==RESULT_OK && data!=null && data.getData()!=null){
            android.net.Uri uri=data.getData();refreshing=true;refresh.setEnabled(false);
            io.execute(()->{try{
                NodeRepository.Result imported=repository.importList(getContentResolver().openInputStream(uri));
                handler.post(()->{if(isDestroyed())return;loadError=null;refreshing=false;refresh.setEnabled(true);country="全部地区";search.setText("");favoriteOnly=false;showNodes(imported);});
            }catch(IOException | SecurityException e){handler.post(()->{if(isDestroyed())return;refreshing=false;refresh.setEnabled(true);toast("导入失败："+e.getMessage());});}});
        }
        if(request==7){if(result==RESULT_OK)startTunnel();else{initiating=false;pendingProfile=null;updateConnection();toast("VPN 授权已取消");}}
    }
    private void startTunnel(){
        if(pendingProfile==null){
            try{String config=new String(java.nio.file.Files.readAllBytes(new File(getFilesDir(),"pending.ovpn").toPath()),java.nio.charset.StandardCharsets.UTF_8);
                if(!NodeParser.safeConfig(config,prefs.getString("pending_ip","")))throw new IOException("配置无效");
                ConfigParser p=new ConfigParser();p.parseConfig(new StringReader(config));pendingProfile=p.convertProfile();pendingProfile.mExpectTLSCert=true;pendingProfile.mName=prefs.getString("pending_name","Aero VPN");
            }catch(Exception e){initiating=false;updateConnection();toast("请重新选择节点连接");return;}
        }
        try {ProfileManager.setTemporaryProfile(this,pendingProfile);VPNLaunchHelper.startOpenVpn(pendingProfile,this,"Aero user selection",true);initiating=false;active=true;status.setText("正在连接…");updateConnection();}
        catch(Exception e){initiating=false;active=false;updateConnection();toast("启动失败："+e.getMessage());}
        pendingProfile=null;
    }
    private void disconnect(){
        connectAttempt++;initiating=false;pendingProfile=null;updateConnection();
        if(vpnService==null){toast("服务尚未就绪，请稍后重试");updateConnection();return;}
        IOpenVPNServiceInternal service=vpnService;
        vpnIo.execute(()->{try{service.stopVPN(false);}catch(RemoteException e){handler.post(()->toast("断开失败，请在安卓 VPN 设置中断开"));}});
    }
    @Override public void updateState(String state,String log,int resource,ConnectionStatus level,Intent intent){handler.post(()->{
        if(isDestroyed())return;
        connected=level==ConnectionStatus.LEVEL_CONNECTED;
        active=level!=ConnectionStatus.LEVEL_NOTCONNECTED && level!=ConnectionStatus.LEVEL_AUTH_FAILED && level!=ConnectionStatus.UNKNOWN_LEVEL;
        switch(level){
            case LEVEL_CONNECTED:status.setText("已连接");break;
            case LEVEL_AUTH_FAILED:initiating=false;status.setText("认证失败");toast("节点认证失败，请换一个节点");break;
            case LEVEL_NONETWORK:status.setText("等待网络…");break;
            case LEVEL_VPNPAUSED:status.setText("连接已暂停");break;
            case LEVEL_NOTCONNECTED:initiating=false;status.setText("尚未连接");break;
            case LEVEL_WAITING_FOR_USER_INPUT:status.setText("等待授权…");break;
            default:if(active)status.setText("正在连接…");break;
        }
        updateConnection();
    });}
    @Override public void setConnectedVPN(String uuid){}
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    private String formatBytes(long bytes){return String.format(Locale.ROOT,"%.1f MB",bytes/1048576.0);}
    private final class NodeAdapter extends BaseAdapter {
        public int getCount(){return visible.size();}public Object getItem(int p){return visible.get(p);}public long getItemId(int p){return visible.get(p).ip.hashCode();}
        public View getView(int position,View recycled,android.view.ViewGroup parent){
            Node n=visible.get(position);LinearLayout card=row();card.setPadding(dp(14),dp(13),dp(12),dp(13));
            GradientDrawable bg=background(Color.WHITE,16);if(selected!=null&&selected.ip.equals(n.ip))bg.setStroke(dp(2),BLUE);card.setBackground(bg);
            TextView flag=label(n.flag(),27,INK);flag.setGravity(Gravity.CENTER);flag.setBackground(background(0xfff3f6fc,12));card.addView(flag,new LinearLayout.LayoutParams(dp(44),dp(44)));
            LinearLayout description=column();description.setPadding(dp(12),0,0,0);TextView name=label(n.displayCountry(),16,INK);bold(name);description.addView(name);description.addView(label(n.ip,11,MUTED));TextView stats=label((n.ping==Integer.MAX_VALUE?"延迟未知":n.ping+" ms")+"  ·  "+n.speedLabel()+"  ·  "+n.sessions+" 人",10,MUTED);stats.setPadding(0,dp(4),0,0);description.addView(stats);card.addView(description,new LinearLayout.LayoutParams(0,-2,1));
            TextView star=label(favorites.contains(n.ip)?"★":"☆",26,favorites.contains(n.ip)?0xffe6ac36:MUTED);star.setGravity(Gravity.CENTER);star.setContentDescription(favorites.contains(n.ip)?"取消收藏 "+n.ip:"收藏 "+n.ip);card.addView(star,new LinearLayout.LayoutParams(dp(48),dp(48)));star.setOnClickListener(v->{if(!favorites.add(n.ip))favorites.remove(n.ip);prefs.edit().putStringSet("favorites",new HashSet<>(favorites)).apply();filter();});return card;
        }
    }
    private final class Radar extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);boolean on;
        Radar(){super(MainActivity.this);setContentDescription("VPN 连接状态");}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float x=getWidth()/2f,y=getHeight()/2f,r=getWidth()*.44f;
            paint.setColor(on?0xffe0f6ec:0xffeef3ff);paint.setStyle(Paint.Style.FILL);c.drawCircle(x,y,r,paint);
            paint.setColor(on?0xffb4e5cd:0xffdce6ff);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));c.drawCircle(x,y,r*.8f,paint);
            paint.setColor(on?0xff26a574:BLUE);paint.setStrokeWidth(dp(2.5f));Path shield=new Path();shield.moveTo(x,y-dp(18));shield.lineTo(x+dp(14),y-dp(12));shield.lineTo(x+dp(12),y+dp(7));shield.quadTo(x+dp(7),y+dp(15),x,y+dp(20));shield.quadTo(x-dp(7),y+dp(15),x-dp(12),y+dp(7));shield.lineTo(x-dp(14),y-dp(12));shield.close();c.drawPath(shield,paint);
            Path check=new Path();check.moveTo(x-dp(6),y);check.lineTo(x-dp(1),y+dp(5));check.lineTo(x+dp(7),y-dp(5));c.drawPath(check,paint);
        }
    }
}
