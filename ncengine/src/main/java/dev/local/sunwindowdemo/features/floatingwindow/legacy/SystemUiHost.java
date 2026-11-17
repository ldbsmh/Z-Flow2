package dev.local.sunwindowdemo;


import android.app.KeyguardManager;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Color;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.os.Handler;
import android.os.Looper;
import android.view.*;

import android.widget.*;
import java.util.ArrayList;
import java.util.List;

public final class SystemUiHost extends ContextWrapper implements ComponentCallbacks {
    static final int OVERLAY_TYPE=2017;
    // Called only inside hooked SystemUI (INTERNAL_SYSTEM_WINDOW), with exact API37 preflight.
    @android.annotation.SuppressLint("BlockedPrivateApi")
    static void trust(WindowManager.LayoutParams p){
        try{WindowManager.LayoutParams.class.getDeclaredMethod("setTrustedOverlay").invoke(p);}
        catch(Exception e){throw new IllegalStateException("trusted overlay preflight",e);}
    }
    static volatile SystemUiHost live;
    private boolean settingsReady;
    boolean ready(){return settingsReady&&!cleaned;}
    private final java.util.concurrent.ExecutorService settingsWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
    private SystemUiHost(Context c){super(c);}
    // The target framework defines TYPE_STATUS_BAR_SUB_PANEL (2017); the public SDK IntDef omits it.
    @android.annotation.SuppressLint("WrongConstant")
    private static Context overlayContext(Context display){return display.createWindowContext(OVERLAY_TYPE,null);}
    static void start(Context c){
        if(live!=null)return;
        trust(new WindowManager.LayoutParams());
        Context display=c.createDisplayContext(c.getSystemService(android.hardware.display.DisplayManager.class).getDisplay(0));
        Context window=overlayContext(display);
        SystemUiHost host=new SystemUiHost(new android.view.ContextThemeWrapper(window,android.R.style.Theme_DeviceDefault_DayNight));
        live=host;
        try{host.initialize();}catch(Throwable failure){
            try{host.cleanup();}catch(Throwable cleanupFailure){failure.addSuppressed(cleanupFailure);}
            finally{if(live==host)live=null;connected=false;}
            android.util.Log.e("NativeComplements","HOST_START_FAILED",failure);
        }
    }
    private final BroadcastReceiver settingsReceiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){
        try{if(getSentFromUid()==getPackageManager().getApplicationInfo(Wire.PKG,0).uid){
            loadSettings();
        }}catch(Exception ignored){}
    }};
    private int settingsFailures;
    private void loadSettings(){settingsWorker.execute(()->{
        try{android.os.Bundle b=getContentResolver().call(android.net.Uri.parse("content://"+ConfigProvider.AUTHORITY),"read",null,null);
            if(b==null)throw new IllegalStateException("empty settings");
            ConfigProvider.copy(b,config.prefs);
            if(settingsFailures>0)android.util.Log.i("NativeComplements","SETTINGS_READ_RECOVERED");
            settingsFailures=0;
            main.post(()->{settingsReady=true;reloadSettings();});
        }catch(Exception e){
            // The module process may be stopped (force-stop/stay-stopped, task cleaners). The host keeps the
            // last complete snapshot already copied into its own preferences.
            boolean cached;
            try{cached=!config.prefs.getAll().isEmpty();}catch(RuntimeException locked){cached=false;}
            if(settingsFailures++==0)android.util.Log.w("NativeComplements","SETTINGS_READ_FAILED cached="+cached,e);
            boolean usable=cached;long delay=Math.min(60_000L,3000L<<Math.min(settingsFailures-1,5));
            main.post(()->{if(!cleaned&&!settingsReady&&usable){settingsReady=true;reloadSettings();}});
            main.postDelayed(()->{if(!cleaned)loadSettings();},delay);
        }
    });}
    /* STATUS carries foreground/IME/overlay state used by overlay occlusion rules,
     * so it polls at 750ms while interactive. Screen off stops until SCREEN_ON/USER_PRESENT. */
    private boolean polling;
    private final Runnable poll=new Runnable(){public void run(){
        if(cleaned||!getSystemService(android.os.PowerManager.class).isInteractive()){polling=false;return;}
        Wire.send(SystemUiHost.this,"STATUS");main.postDelayed(this,750);
    }};
    private void resumePoll(){if(cleaned||polling)return;polling=true;main.post(poll);}
    private void reloadSettings(){
        if(cleaned)return;
        // 快照刚更新：露出比例等只影响摆位的设置要就地重排，不必等引擎再发一帧。
        if(controls!=null)controls.relayout();
        for(WindowControls c:minis.values())c.relayout();
        refresh();Wire.send(this,"STATUS");
    }
    private final Runnable reload=this::reloadSettings;
    private boolean screenshotVisible,systemFocus,immersive;
    private String systemOverlayTitle="";
    private android.window.OnBackInvokedDispatcher openWaitDispatcher;
    private final android.window.OnBackInvokedCallback openWaitBack=this::cancelOpenWait;
    private void syncControlsOcclusion(){
        if(controls==null)return;
        for(WindowControls c:minis.values())c.normalOcclusion(controls.contentOcclusion());
    }
    void controlsChanged(){main.post(()->{syncControlsOcclusion();publish(false);});}
    /** 迷你窗帧列表：新协议是 pinnedFrames，旧引擎只发 pinnedFrame，两者都认。 */
    private java.util.ArrayList<Intent> framesOf(Intent i){
        java.util.ArrayList<Intent> frames="active".equals(backendState)
            ?i.getParcelableArrayListExtra("pinnedFrames",Intent.class):null;
        if(frames!=null)return frames;
        java.util.ArrayList<Intent> one=new java.util.ArrayList<>(1);
        if("active".equals(backendState)){
            Intent single=i.getParcelableExtra("pinnedFrame",Intent.class);
            if(single!=null)one.add(single);
        }
        return one;
    }
    private boolean publishedImmersive;
    private int activeTask=-1;
    /** 所有挂着的迷你窗任务号，用于跳过浮动窗口找真正的前台应用。 */
    private final java.util.Set<Integer> pinnedTasks=new java.util.HashSet<>();
    private final android.os.Binder dropChannel=new android.os.Binder(){
        @Override protected boolean onTransact(int code,android.os.Parcel data,android.os.Parcel reply,int flags) throws android.os.RemoteException {
            if(code<android.os.IBinder.FIRST_CALL_TRANSACTION||code>android.os.IBinder.FIRST_CALL_TRANSACTION+3)return super.onTransact(code,data,reply,flags);
            try{if(android.os.Binder.getCallingUid()!=getPackageManager().getApplicationInfo(BackBridge.PACKAGE,0).uid)return false;}
            catch(Exception e){return false;}
            data.enforceInterface(BackBridge.CHANNEL);
            if(code==android.os.IBinder.FIRST_CALL_TRANSACTION+3){
                boolean shown=data.readBoolean();data.enforceNoDataAvail();
                main.post(()->{screenshotVisible=shown;updateOverlayPriority();if(!shown)refresh();});return true;
            }
            return false; // Retired gesture Binder operations never open or admit a task.
        }
    };
    static volatile boolean connected;
    private final Handler main=new Handler(Looper.getMainLooper());
    private Config config;
    private WindowManager wm;
    private View openWait;
    private ImageView openWaitIcon;
    private final OpenWaitState openWaitState=new OpenWaitState();
    private WindowControls controls;
    /** 每个迷你窗一个控制条，按 taskId 复用；引擎允许多个迷你窗并存。 */
    private final java.util.Map<Integer,WindowControls> minis=new java.util.LinkedHashMap<>();
    private WindowControls miniFor(int taskId){
        WindowControls c=minis.get(taskId);
        if(c==null){c=new WindowControls(this);minis.put(taskId,c);}
        return c;
    }
    private void hideControls(){
        if(controls!=null)controls.hide();
        for(WindowControls c:minis.values())c.hide();
    }
    /**
     * 报文里没有的迷你窗要收起，实例留着复用（下一个迷你窗常常还是同一个应用）。
     *
     * 注意不能调 close()：那是“关闭小窗”指令，会把用户窗口真关掉。
     * 窗口本身已经被 hide() 移除，这里只是控制实例数量，避免 taskId 不断变化时无限增长。
     */
    private static final int MAX_MINI_CONTROLS=8;
    private void retainMinis(java.util.Set<Integer> live){
        for(java.util.Map.Entry<Integer,WindowControls> e:new java.util.ArrayList<>(minis.entrySet()))
            if(!live.contains(e.getKey()))e.getValue().hide();
        while(minis.size()>MAX_MINI_CONTROLS){
            Integer drop=null;
            for(Integer id:minis.keySet())if(!live.contains(id)){drop=id;break;}
            if(drop==null)break;
            minis.remove(drop);
        }
    }
    private int suggestionImeTop=Integer.MAX_VALUE;
    private volatile String backendState="";
    private final BroadcastReceiver backendReceiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){
        if(getSentFromUid()!=1000||i.getIntExtra("protocol",0)!=Wire.PROTOCOL)return;
        Wire.acceptBackend(i);receiveFrame(i);
    }};
    private void receiveFrame(Intent i){
        if(cleaned||controls==null)return;
        backendState=i.getStringExtra("state");
        updateOpenWait(i);
        if(i.hasExtra("foreground")){foreground=i.getStringExtra("foreground");keyboard=i.getBooleanExtra("imeVisible",false);systemFocus=i.getBooleanExtra("systemOverlay",false);
            systemOverlayTitle=i.getStringExtra("systemOverlayTitle");
            suggestionImeTop=i.getIntExtra("imeTop",Integer.MAX_VALUE);
            updateOverlayPriority();controls.imeTop(suggestionImeTop);
            for(WindowControls c:minis.values())c.imeTop(suggestionImeTop);refresh();}
        if(i.hasExtra("immersive"))immersive=i.getBooleanExtra("immersive",false);
        activeTask="active".equals(backendState)?i.getIntExtra("taskId",-1):-1;
        java.util.ArrayList<Intent> pinnedFrames=framesOf(i);
        pinnedTasks.clear();
        for(Intent frame:pinnedFrames){int id=frame.getIntExtra("taskId",-1);if(id>0)pinnedTasks.add(id);}
        publish(false);
        try{if(!getSystemService(android.os.PowerManager.class).isInteractive()||getSystemService(KeyguardManager.class).isKeyguardLocked())hideControls();else {controls.update(i);
            syncControlsOcclusion();
            java.util.Set<Integer> live=new java.util.HashSet<>();
            for(Intent frame:pinnedFrames){
                int id=frame.getIntExtra("taskId",-1);
                if(id<=0)continue;
                live.add(id);miniFor(id).update(frame);
            }
            retainMinis(live);
            syncControlsOcclusion();
        }}catch(RuntimeException e){hideControls();android.util.Log.e("NativeComplements","overlay controls failed",e);}
        if(i.getStringExtra("event")!=null&&(i.getStringExtra("event").endsWith("FAILED")||"BLOCKED".equals(i.getStringExtra("event"))))Toast.makeText(this,WindowPolicy.failureMessage(i.getStringExtra("report")),Toast.LENGTH_SHORT).show();
    }
    private List<AppCatalog.App> all=List.of();
    private String foreground="";
    private boolean keyboard,cleaned;
    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener=(p,k)->{main.removeCallbacks(reload);main.post(reload);};
    private final BroadcastReceiver screenReceiver=new BroadcastReceiver(){
        public void onReceive(Context c,Intent i){
            if(Intent.ACTION_SCREEN_ON.equals(i.getAction())){resumePoll();return;}
            cancelOpenWait();if(controls!=null)hideControls();refresh();Wire.send(SystemUiHost.this,"STATUS");
            if(Intent.ACTION_SCREEN_OFF.equals(i.getAction())){main.removeCallbacks(poll);polling=false;}else resumePoll();
        }
    };
    private final BroadcastReceiver packageReceiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){loadApps();}};
    private int catalogGeneration;
    private final Runnable iconsReloaded=()->main.post(()->{if(!cleaned)loadApps();});
    private void initialize() {
        cleaned=false;connected=true;live=this;
        Config.hostPreferences=getSharedPreferences(Config.HOST_PREFERENCES,Context.MODE_PRIVATE);
        config=new Config(this);wm=getSystemService(WindowManager.class);
        controls=new WindowControls(this);
        Wire.listen(this::receiveFrame);
        registerReceiver(backendReceiver,new IntentFilter(Wire.RESULT),null,main,Context.RECEIVER_EXPORTED);
        Wire.send(this,"STATUS");
        config.prefs.registerOnSharedPreferenceChangeListener(prefListener);
        IntentFilter screen=new IntentFilter(Intent.ACTION_SCREEN_OFF);screen.addAction(Intent.ACTION_USER_PRESENT);screen.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screenReceiver,screen,Context.RECEIVER_NOT_EXPORTED);
        IntentFilter pkgs=new IntentFilter(Intent.ACTION_PACKAGE_ADDED);pkgs.addAction(Intent.ACTION_PACKAGE_REMOVED);pkgs.addAction(Intent.ACTION_PACKAGE_CHANGED);pkgs.addDataScheme("package");
        registerReceiver(packageReceiver,pkgs,Context.RECEIVER_NOT_EXPORTED);
        registerReceiver(settingsReceiver,settingsFilter(),null,main,Context.RECEIVER_EXPORTED);
        getApplicationContext().registerComponentCallbacks(this);
        dev.local.sunwindowdemo.features.iconlayers.IconAssets.onReload(iconsReloaded);
        dev.local.sunwindowdemo.features.iconlayers.IconAssets.connect(getApplicationContext());
        loadApps();loadSettings();resumePoll();
    }
    private void loadApps() {
        int generation=++catalogGeneration;
        new Thread(()->{
            List<AppCatalog.App> apps=AppCatalog.load(this);
            main.post(()->{
                if(!connected||cleaned||generation!=catalogGeneration)return;all=apps;
                if(openWaitIcon!=null)for(AppCatalog.App app:apps)if(app.pkg.equals(openWaitState.packageName())){openWaitIcon.setImageDrawable(app.icon);break;}
                refresh();
            });
        },"window-app-catalog").start();
    }
    private void updateOverlayPriority(){
        for(View overlay:new View[]{openWait})if(overlay!=null&&overlay.getLayoutParams() instanceof WindowManager.LayoutParams p){
            int mask=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            int next=screenshotVisible||systemFocus?p.flags|mask:p.flags&~mask;
            if(next!=p.flags){p.flags=next;try{wm.updateViewLayout(overlay,p);}catch(RuntimeException ignored){}}
        }
        if(controls!=null)controls.systemOverlay(screenshotVisible||systemFocus);
        for(WindowControls c:minis.values())c.systemOverlay(screenshotVisible||systemFocus);
    }
    private int dp(float value){return (int)(value*getResources().getDisplayMetrics().density+.5f);}
    private WindowManager.LayoutParams params(int width,int height,int flags) {
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(width,height,OVERLAY_TYPE,flags,PixelFormat.TRANSLUCENT);
        p.setFitInsetsTypes(0);p.setTitle("NativeComplements-window-menu");trust(p);return p;
    }
    /** 宿主状态广播：截图优先级通道（dropChannel binder）与沉浸标记，只发给 SystemUI 自己的接收器。 */
    static final String STATE=Wire.PKG+".window.STATE";
    private void refresh() {
        publish(false);
    }
    private void remove(View v){try{if(wm!=null)wm.removeViewImmediate(v);}catch(RuntimeException ignored){}}
    void publish(boolean force){
        if(!force&&publishedImmersive==immersive)return;
        publishedImmersive=immersive;
        Intent state=new Intent(STATE).setPackage(BackBridge.PACKAGE).putExtra("immersive",immersive);
        android.os.Bundle extras=new android.os.Bundle();extras.putBinder("dropChannel",dropChannel);state.putExtras(extras);
        sendBroadcast(state,null,android.app.BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle());
    }
    private IntentFilter settingsFilter(){IntentFilter filter=new IntentFilter(ConfigProvider.CHANGED);return filter;}
    private void updateOpenWait(Intent frame){
        if(!frame.hasExtra("waitingOpen"))return;
        String token=frame.getStringExtra("waitingToken"),pkg=frame.getStringExtra("waitingPackage");
        boolean waiting=frame.getBooleanExtra("waitingOpen",false);
        boolean valid=Wire.validToken(token)&&Wire.allowedPackage(pkg);
        boolean permitted=waiting&&valid&&ready()&&getSystemService(android.os.PowerManager.class).isInteractive()
            &&!getSystemService(KeyguardManager.class).isKeyguardLocked();
        OpenWaitState.Change change=openWaitState.update(true,waiting,valid,permitted,token,pkg);
        if(change==OpenWaitState.Change.KEEP)return;
        removeOpenWait();
        if(change==OpenWaitState.Change.HIDE)return;
        FrameLayout outside=new FrameLayout(this){
            @Override public boolean dispatchKeyEvent(KeyEvent event){
                if(event.getKeyCode()==KeyEvent.KEYCODE_BACK){if(event.getAction()==KeyEvent.ACTION_UP)cancelOpenWait();return true;}
                return super.dispatchKeyEvent(event);
            }
        };
        outside.setOnClickListener(v->cancelOpenWait());
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setGravity(Gravity.CENTER);card.setPadding(dp(32),dp(28),dp(32),dp(28));
        boolean dark=(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        android.graphics.drawable.GradientDrawable background=new android.graphics.drawable.GradientDrawable();
        background.setColor(dark?0xff171717:0xfff0f0f0);background.setCornerRadius(dp(24));card.setBackground(background);card.setOnClickListener(v->{});
        ImageView icon=new ImageView(this);openWaitIcon=icon;String label="";
        for(AppCatalog.App app:all)if(pkg.equals(app.pkg)){icon.setImageDrawable(app.icon);label=app.label;break;}
        icon.setContentDescription(label);card.addView(icon,new LinearLayout.LayoutParams(dp(64),dp(64)));
        ProgressBar progress=new ProgressBar(this);LinearLayout.LayoutParams indicator=new LinearLayout.LayoutParams(dp(28),dp(28));indicator.topMargin=dp(20);card.addView(progress,indicator);
        TextView text=new TextView(this);text.setText("正在打开…");text.setTextSize(14);text.setTextColor(dark?Color.WHITE:Color.BLACK);
        LinearLayout.LayoutParams caption=new LinearLayout.LayoutParams(-2,-2);caption.topMargin=dp(12);card.addView(text,caption);
        outside.addView(card,new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER));
        outside.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
            public void onViewAttachedToWindow(View view){openWaitDispatcher=view.findOnBackInvokedDispatcher();if(openWaitDispatcher!=null)openWaitDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,openWaitBack);}
            public void onViewDetachedFromWindow(View view){if(openWaitDispatcher!=null){openWaitDispatcher.unregisterOnBackInvokedCallback(openWaitBack);openWaitDispatcher=null;}}
        });
        WindowManager.LayoutParams params=params(-1,-1,WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        params.setTitle("NativeComplements-opening");params.windowAnimations=0;openWait=outside;
        try{wm.addView(outside,params);updateOverlayPriority();}
        catch(RuntimeException failure){hideOpenWait();}
        main.postDelayed(()->{if(openWaitState.timeoutMatches(token))cancelOpenWait();},13000);
    }
    private void removeOpenWait(){
        if(openWait!=null){remove(openWait);openWait=null;}
        openWaitIcon=null;updateOverlayPriority();
    }
    private void hideOpenWait(){openWaitState.hide();removeOpenWait();}
    private void cancelOpenWait(){
        OpenWaitState.Request request=openWaitState.cancel();removeOpenWait();
        if(request!=null)Wire.send(this,"CANCEL_OPEN",request.pkg(),-1,request.token(),false);
    }
    @Override public void onConfigurationChanged(Configuration c){cancelOpenWait();refresh();Wire.send(this,"STATUS");}
    @Override public void onLowMemory(){}
    private void cleanup(){if(cleaned)return;dev.local.sunwindowdemo.features.iconlayers.IconAssets.removeOnReload(iconsReloaded);cancelOpenWait();settingsWorker.shutdownNow();try{getApplicationContext().unregisterComponentCallbacks(this);}catch(RuntimeException ignored){}for(BroadcastReceiver receiver:new BroadcastReceiver[]{settingsReceiver,backendReceiver,screenReceiver,packageReceiver})try{unregisterReceiver(receiver);}catch(RuntimeException ignored){}if(controls!=null){controls.close();}for(WindowControls c:minis.values())c.close();minis.clear();Wire.listen(null);cleaned=true;connected=false;if(live==this)live=null;main.removeCallbacksAndMessages(null);publish(true);if(config!=null)config.prefs.unregisterOnSharedPreferenceChangeListener(prefListener);}
}
