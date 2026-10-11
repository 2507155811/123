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
import de.blinkt.openvpn.core.*;

public final class MainActivity extends Activity implements VpnStatus.StateListener {
    private static final int BLUE=0xff245cec, INK=0xff15253c, MUTED=0xff7c889d, BG=0xfff5f7fb;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final ExecutorService vpnIo=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final List<Node> nodes=new ArrayList<>(), visible=new ArrayList<>();
    private Set<String> favorites; private SharedPreferences prefs;
    private NodeRepository repository; private Node selected;
    private IOpenVPNServiceInternal vpnService;
    private boolean bound, refreshing, favoriteOnly, connected, tunnelEstablished, active, initiating, expired;
    private int sort=0; private String country="全部地区", query="", restoredIp="";
    private long fetchedAt;private String loadError;
    private TextView status, selectedLabel, source, count, empty, traffic, allTab, favoriteTab;
    private Button connect, refresh, detect, speedButton;
    private TextView healthStatus;
    private NodeOptimizerService optimizer;
    private boolean optimizerBound;
    private String pendingOptimizerAction;
    private String pendingOptimizerIp;
    private final Runnable optimizerObserver=()->{if(isDestroyed())return;initiating=optimizer!=null&&optimizer.isBusy();detect.setEnabled(!initiating);speedButton.setEnabled(!initiating&&tunnelEstablished&&optimizer!=null&&optimizer.current()!=null);refresh.setEnabled(!refreshing&&!initiating);healthStatus.setText(optimizer==null?"搜索显示可达节点，连接后验证联网和测速":optimizer.message());if(optimizer!=null&&optimizer.current()!=null)selected=optimizer.current();else if(selected!=null&&(optimizer==null||optimizer.health(selected.ip)==null))selected=null;connected=tunnelEstablished&&optimizer!=null&&optimizer.current()!=null;if(tunnelEstablished)status.setText(connected?"VPN 已连接":"系统隧道已建立 · 待联网验证");filter();updateConnection();};
    private final ServiceConnection optimizerConnection=new ServiceConnection(){
        public void onServiceConnected(ComponentName name,IBinder binder){optimizer=((NodeOptimizerService.LocalBinder)binder).service();optimizer.observe(optimizerObserver);}
        public void onServiceDisconnected(ComponentName name){optimizer=null;filter();}
    };
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
        if(saved!=null){query=saved.getString("query","");sort=saved.getInt("sort",0);country=saved.getString("country","全部地区");favoriteOnly=saved.getBoolean("favorites",false);pendingOptimizerAction=saved.getString("optimizer_action");pendingOptimizerIp=saved.getString("optimizer_ip");}
        buildUi();search.setText(query);sorts.setSelection(sort);
        if(!prefs.getBoolean("notice_seen",false))new AlertDialog.Builder(this).setTitle("欢迎使用 Aero VPN")
                .setMessage("节点来自 VPN Gate 志愿者网络及 VPNBook 官方免费服务，动态变化。VPNBook 需要在“节点来源”填写官网当前共享密码。节点运营者可能记录连接信息，请勿用于传输敏感资料。国家信息由节点源提供，已排除 CN、HK、MO。搜索通过并发 TCP 端口检查显示可达节点，不建立 VPN，也不能证明认证或互联网访问成功。选中连接时才申请安卓 VPN 权限、验证联网并实测速度。UDP 配置无法用 TCP 探测，搜索结果不包含这类配置。不会定时自动选择节点。")
                .setPositiveButton("知道了",(d,w)->prefs.edit().putBoolean("notice_seen",true).apply()).show();
        io.execute(()->{
            NodeRepository.Result initial=null;
            try{initial=repository.seed(getAssets().open("vpngate.csv"));}catch(IOException ignored){try{initial=repository.load(false);}catch(IOException ignoredCache){}}
            NodeRepository.Result snapshot=initial;
            handler.post(()->{if(isDestroyed())return;if(snapshot!=null)showNodes(snapshot);loadNodes();});
        });
    }
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putString("query",query);out.putString("country",country);out.putInt("sort",sort);out.putBoolean("favorites",favoriteOnly);out.putString("optimizer_action",pendingOptimizerAction);out.putString("optimizer_ip",pendingOptimizerIp);}
    @Override protected void onStart(){super.onStart();
        VpnStatus.addStateListener(this);VpnStatus.addByteCountListener(bytes);
        Intent intent=new Intent(this,OpenVPNService.class);intent.setAction(OpenVPNService.START_SERVICE);
        // Connection ownership belongs to NodeOptimizerService; avoid retaining an idle engine.
        optimizerBound=bindService(new Intent(this,NodeOptimizerService.class),optimizerConnection,Context.BIND_AUTO_CREATE);
        handler.post(checkExpiry);
    }
    @Override protected void onStop(){
        VpnStatus.removeStateListener(this);VpnStatus.removeByteCountListener(bytes);handler.removeCallbacks(checkExpiry);
        if(optimizer!=null)optimizer.removeObserver(optimizerObserver);
        if(optimizerBound){unbindService(optimizerConnection);optimizerBound=false;}optimizer=null;
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
        space(hero,12);connect=button("选择节点后连接",true);connect.setEnabled(false);connect.setAlpha(.5f);hero.addView(connect,new LinearLayout.LayoutParams(-1,dp(48)));connect.setOnClickListener(v->{if(optimizer!=null&&optimizer.isStopping())return;if(active||initiating||(optimizer!=null&&optimizer.isBusy()))disconnect();else beginConnect();});
        space(hero,10);traffic=label("内置 OpenVPN · 无需额外客户端",11,MUTED);traffic.setGravity(Gravity.CENTER);hero.addView(traffic);root.addView(hero);space(root,16);
        LinearLayout searchRow=row();search=new EditText(this);search.setSingleLine(true);search.setTextSize(14);search.setTextColor(INK);search.setHintTextColor(MUTED);search.setHint("搜索国家 / IP / 节点名称");search.setPadding(dp(14),0,dp(14),0);search.setBackground(background(Color.WHITE,14));search.setInputType(android.text.InputType.TYPE_CLASS_TEXT);search.setContentDescription("搜索境外 VPN 节点");searchRow.addView(search,new LinearLayout.LayoutParams(0,dp(48),1));
        refresh=button("刷新",false);refresh.setSingleLine(true);refresh.setPadding(dp(8),0,dp(8),0);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(dp(84),dp(48));rp.leftMargin=dp(10);searchRow.addView(refresh,rp);refresh.setOnClickListener(v->loadNodes());root.addView(searchRow);
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){query=s.toString();filter();}public void afterTextChanged(Editable e){}});
        healthStatus=label("搜索显示可达节点，连接后验证联网和测速",11,MUTED);root.addView(healthStatus);healthStatus.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("搜索诊断").setMessage(optimizer==null?"请先搜索节点，再查看失败原因":optimizer.diagnostics()).setPositiveButton("关闭",null).show());
        space(root,6);LinearLayout actions=row();detect=button("搜索可用节点",false);actions.addView(detect,new LinearLayout.LayoutParams(0,dp(40),1));speedButton=button("当前 VPN 测速",false);speedButton.setEnabled(false);LinearLayout.LayoutParams speedLayout=new LinearLayout.LayoutParams(0,dp(40),1);speedLayout.leftMargin=dp(8);actions.addView(speedButton,speedLayout);root.addView(actions);speedButton.setOnClickListener(v->startOptimizer(NodeOptimizerService.SPEED,true));
        detect.setOnClickListener(v->requestOptimizer(NodeOptimizerService.SCAN));
        space(root,8);LinearLayout sourceActions=row();Button importButton=button("导入 CSV",false);sourceActions.addView(importButton,new LinearLayout.LayoutParams(0,dp(40),1));Button providers=button("节点来源",false);LinearLayout.LayoutParams providerLayout=new LinearLayout.LayoutParams(0,dp(40),1);providerLayout.leftMargin=dp(8);sourceActions.addView(providers,providerLayout);root.addView(sourceActions);providers.setOnClickListener(v->showSources());
        importButton.setOnClickListener(v->{if(refreshing){toast("请等待当前更新完成");return;}Intent file=new Intent(Intent.ACTION_OPEN_DOCUMENT);file.addCategory(Intent.CATEGORY_OPENABLE);file.setType("*/*");startActivityForResult(file,8);});
        space(root,10);LinearLayout filters=row();countries=new Spinner(this);countries.setContentDescription("按地区筛选");sorts=new Spinner(this);sorts.setContentDescription("节点排序");filters.addView(countries,new LinearLayout.LayoutParams(0,dp(44),1));filters.addView(sorts,new LinearLayout.LayoutParams(0,dp(44),1));root.addView(filters);
        setSpinner(sorts,Arrays.asList("推荐排序","低延迟优先","高速度优先"));sorts.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int pos,long id){sort=pos;filter();}});
        setSpinner(countries,Collections.singletonList("全部地区"));countries.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int pos,long id){country=p.getItemAtPosition(pos).toString();filter();}});
        LinearLayout tabs=row();allTab=label("可达节点",15,BLUE);bold(allTab);allTab.setPadding(0,dp(12),dp(24),dp(12));favoriteTab=label("我的收藏",15,MUTED);favoriteTab.setPadding(0,dp(12),dp(16),dp(12));tabs.addView(allTab);tabs.addView(favoriteTab);count=label("0",12,MUTED);count.setGravity(Gravity.RIGHT);tabs.addView(count,new LinearLayout.LayoutParams(0,-2,1));root.addView(tabs);
        allTab.setOnClickListener(v->{favoriteOnly=false;filter();});favoriteTab.setOnClickListener(v->{favoriteOnly=true;filter();});
        source=label("正在获取实时境外节点…",10,MUTED);source.setPadding(0,0,0,dp(8));root.addView(source);
        FrameLayout frame=new FrameLayout(this);list=new ListView(this);list.setDivider(null);list.setDividerHeight(dp(10));list.setClipToPadding(false);list.setPadding(0,0,0,dp(12));adapter=new NodeAdapter();list.setAdapter(adapter);list.setOnItemClickListener((p,v,pos,id)->chooseNode(visible.get(pos)));frame.addView(list,new FrameLayout.LayoutParams(-1,-1));
        empty=label("正在搜索可用节点…",14,MUTED);empty.setGravity(Gravity.CENTER);empty.setPadding(dp(16),dp(16),dp(16),dp(16));frame.addView(empty,new FrameLayout.LayoutParams(-1,-1));root.addView(frame,new LinearLayout.LayoutParams(-1,0,1));
    }
    private void showSources(){
        LinearLayout content=column();content.setPadding(dp(20),dp(8),dp(20),0);
        content.addView(label("VPN Gate：免账号，直接参与搜索。\nRiseupVPN：开源 LEAP 官方免费来源，免账号；需 API 可达并成功取得匿名证书。\nVPNBook：10 台境外服务器，需要官网当前共享密码。官网获取密码需完成人机验证。用户名固定为 vpnbook。",13,INK));
        EditText password=new EditText(this);password.setSingleLine(true);password.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);password.setHint("VPNBook 共享密码（留空清除）");content.addView(password);
        content.addView(label(ProviderPassword.read(this).isEmpty()?"尚未设置 VPNBook 密码":"已保存 VPNBook 密码，更新时重新输入",11,MUTED));
        new AlertDialog.Builder(this).setTitle("免费节点来源").setView(content).setNegativeButton("取消",null)
            .setNeutralButton("获取官网密码",(d,w)->{try{startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(VpnBookRepository.WEBSITE)));}catch(ActivityNotFoundException e){toast("请在浏览器打开 www.vpnbook.com/freevpn/openvpn");}})
            .setPositiveButton("保存",(d,w)->{try{ProviderPassword.save(this,password.getText().toString().trim());toast("来源设置已保存，请点击搜索可用节点");}catch(Exception e){toast("密码保存失败，请重新输入");}}).show();
    }
    private void setSpinner(Spinner s,List<String> values){ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,values);a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);s.setAdapter(a);}
    private void showAbout(){new AlertDialog.Builder(this).setTitle("Aero VPN · 自由探索")
            .setMessage("免费节点：VPN Gate（日本筑波大学志愿者项目）、VPNBook 与 RiseupVPN\n来源：www.vpngate.net、www.vpnbook.com、api.black.riseup.net\n备用分发：本项目 GitHub 目录，每小时计划更新（更新可能延迟）\n安装包内置带采集时间的候选快照，最多使用七天。搜索通过并发 TCP 端口检查显示十分钟内的可达节点，不建立或切换 VPN。端口可达不保证认证与联网成功；选中连接后才验证隧道内 HTTPS 联网并测速。UDP 配置无法用 TCP 探测，未列入搜索结果。搜索完成后由你选择连接，不定时切换。\n\n仅显示地区代码不属于 CN / HK / MO 的节点；国家位置未经独立验证。连接前显示 TCP 连接耗时；连接验证后显示 HTTPS 联网耗时与最多 1 MB 下载样本速度。推荐排序只代表本轮样本结果。\n\n节点可能随时离线或记录访问，请勿传输敏感资料。应用无账号、广告或分析 SDK，收藏与最长七天的离线列表保存在本机。\n\n内置 OpenVPN for Android 0.7.62，遵循 GPLv2 及其附加条款。源码和许可证随项目提供。")
            .setNeutralButton("手机 VPN 设置",(d,w)->{try{startActivity(new Intent(android.provider.Settings.ACTION_VPN_SETTINGS));}catch(ActivityNotFoundException e){startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));}}).setPositiveButton("关闭",null).show();}
    private void loadNodes(){
        if(refreshing)return;loadError=null;refreshing=true;refresh.setEnabled(false);refresh.setText("…");
        source.setText("正在查询 VPN Gate 和 VPNBook…");
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
        Map<String,NodeHealth> evidenceByIp=new HashMap<>();
        visible.clear();if(optimizer!=null)for(NodeHealth evidence:optimizer.available()){Node n=evidence.node;evidenceByIp.put(n.ip,evidence);if(n.matches(query)&&(country.equals("全部地区")||country.equals(n.displayCountry()))&&(!favoriteOnly||favorites.contains(n.ip)))visible.add(n);}
        Comparator<Node> comparator=sort==1?Comparator.comparingLong(n->evidenceByIp.get(n.ip).latencyMs):sort==2?Comparator.comparingLong((Node n)->evidenceByIp.get(n.ip).bytesPerSecond).reversed():Comparator.comparingDouble((Node n)->evidenceByIp.get(n.ip).quality()).reversed();
        visible.sort(comparator);if(adapter==null)return;adapter.notifyDataSetChanged();count.setText(visible.size()+" 个");allTab.setTextColor(favoriteOnly?MUTED:BLUE);favoriteTab.setTextColor(favoriteOnly?BLUE:MUTED);
        empty.setVisibility(visible.isEmpty()?View.VISIBLE:View.GONE);
        if(visible.isEmpty())empty.setText(!refreshing && nodes.isEmpty() && loadError!=null?"暂时无法获取节点\n"+loadError+"\n\n切换 Wi-Fi 后刷新，或导入 VPN Gate CSV 列表":refreshing?"正在搜索可用节点…":favoriteOnly?"还没有收藏的匹配节点\n点击节点右侧 ☆ 收藏":"尚未发现端口可达的匹配节点\n点击「搜索可用节点」；点击上方搜索状态文字查看诊断");
    }
    private void chooseNode(Node node){
        if(optimizer!=null&&optimizer.isBusy()){toast("正在搜索，请等待完成或停止搜索");return;}
        search.clearFocus();((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(),0);
        selected=node;prefs.edit().putString("selected_ip",node.ip).apply();adapter.notifyDataSetChanged();updateConnection();
    }
    private void updateConnection(){
        if(status==null)return;radar.on=connected;radar.invalidate();
        selectedLabel.setText(connected?prefs.getString("connected_label","VPN 隧道已建立"):selected==null?"选择一个境外节点开始":selected.displayCountry()+" · "+selected.ip);
        connect.setText(optimizer!=null&&optimizer.isStopping()?"正在断开…":optimizer!=null&&optimizer.isSearching()?"停止搜索":active||initiating||(optimizer!=null&&optimizer.isBusy())?connected?"断开连接":"停止搜索 / 取消连接":selected==null?"搜索后选择节点":"连接并验证联网");
        boolean enabled=(optimizer==null||!optimizer.isStopping())&&(active||initiating||(optimizer!=null&&optimizer.isBusy())||(selected!=null&&optimizer!=null&&optimizer.health(selected.ip)!=null));connect.setEnabled(enabled);connect.setAlpha(enabled?1f:.5f);
        if(optimizer!=null&&optimizer.isStopping())status.setText("正在断开…");
        if(!active&&!initiating){status.setText("尚未连接");traffic.setText("内置 OpenVPN · 无需额外客户端");}
    }
    private void beginConnect(){
        if(selected!=null&&selected.provider().equals("VPNBook")&&ProviderPassword.read(this).isEmpty()){showSources();toast("请先填写 VPNBook 官网共享密码");return;}
        if(selected==null||initiating||optimizer==null||optimizer.health(selected.ip)==null)return;
        requestOptimizer(NodeOptimizerService.SELECT);
    }
    private void requestOptimizer(String action){
        if(optimizer!=null&&optimizer.isBusy()){toast("搜索正在进行，可点击取消连接停止");return;}
        pendingOptimizerAction=action;pendingOptimizerIp=selected==null?null:selected.ip;
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},9);else authorizeOptimizer();
    }
    private void authorizeOptimizer(){
        if(NodeOptimizerService.SCAN.equals(pendingOptimizerAction)){startOptimizer(pendingOptimizerAction,true);return;}
        Intent permission=VpnService.prepare(this);if(permission!=null)startActivityForResult(permission,9);else startOptimizer(pendingOptimizerAction,true);
    }
    private void startOptimizer(String action,boolean enabled){
        Intent intent=new Intent(this,NodeOptimizerService.class).setAction(action).putExtra("enabled",enabled);
        if(NodeOptimizerService.SELECT.equals(action))intent.putExtra("ip",pendingOptimizerIp);
        if(NodeOptimizerService.STOP.equals(action)||NodeOptimizerService.SCAN.equals(action)||NodeOptimizerService.SELECT.equals(action)){initiating=true;updateConnection();}
        try{startForegroundService(intent);}catch(Exception e){initiating=false;updateConnection();toast("无法启动服务，请重新打开应用");}
        pendingOptimizerAction=null;pendingOptimizerIp=null;
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==9&&pendingOptimizerAction!=null)authorizeOptimizer();}
    @Override public void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
        if(request==9){if(result==RESULT_OK)startOptimizer(pendingOptimizerAction,true);else{pendingOptimizerAction=null;toast("未授权 VPN，无法验证节点");}}
        if(request==8&&result==RESULT_OK&&data!=null&&data.getData()!=null){android.net.Uri uri=data.getData();refreshing=true;refresh.setEnabled(false);
            io.execute(()->{try{NodeRepository.Result imported=repository.importList(getContentResolver().openInputStream(uri));handler.post(()->{if(isDestroyed())return;loadError=null;refreshing=false;refresh.setEnabled(true);country="全部地区";search.setText("");favoriteOnly=false;showNodes(imported);});}
            catch(IOException|SecurityException e){handler.post(()->{if(isDestroyed())return;refreshing=false;refresh.setEnabled(true);toast("导入失败："+e.getMessage());});}});
        }
    }
    private void disconnect(){startOptimizer(optimizer!=null&&optimizer.isSearching()?NodeOptimizerService.CANCEL:NodeOptimizerService.STOP,false);}
    @Override public void updateState(String state,String log,int resource,ConnectionStatus level,Intent intent){handler.post(()->{
        if(isDestroyed())return;
        tunnelEstablished=level==ConnectionStatus.LEVEL_CONNECTED;connected=tunnelEstablished&&optimizer!=null&&optimizer.current()!=null;
        active=level!=ConnectionStatus.LEVEL_NOTCONNECTED && level!=ConnectionStatus.LEVEL_AUTH_FAILED && level!=ConnectionStatus.UNKNOWN_LEVEL;
        switch(level){
            case LEVEL_CONNECTED:status.setText(connected?"VPN 已连接":"系统隧道已建立 · 待联网验证");break;
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
            LinearLayout description=column();description.setPadding(dp(12),0,0,0);TextView name=label(n.displayCountry(),16,INK);bold(name);description.addView(name);description.addView(label(n.ip+" · "+n.provider(),11,MUTED));NodeHealth evidence=optimizer==null?null:optimizer.health(n.ip);
            TextView stats=label(evidence==null?"验证已过期，请重新搜索":evidence.tunnelVerified?"实测 HTTPS "+evidence.latencyMs+" ms · 联网验证通过":"TCP 可达 "+evidence.latencyMs+" ms · 待连接验证",10,MUTED);stats.setPadding(0,dp(4),0,0);description.addView(stats);
            String speed=evidence==null||evidence.bytesPerSecond<=0?"连接后可实测下载速度":String.format(Locale.ROOT,"下载 %.2f Mbps · %.2f MB 样本",evidence.bytesPerSecond*8/1_000_000.0,evidence.sampleBytes/1_000_000.0);
            TextView measured=label(speed,12,evidence!=null&&evidence.bytesPerSecond>0?BLUE:MUTED);description.addView(measured);
            if(evidence!=null)description.addView(label("验证于 "+Math.max(0,(SystemClock.elapsedRealtime()-evidence.checkedAt)/1000)+" 秒前",10,MUTED));
            card.addView(description,new LinearLayout.LayoutParams(0,-2,1));
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
