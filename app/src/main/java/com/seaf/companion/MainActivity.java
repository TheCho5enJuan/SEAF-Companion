package com.seaf.companion;

import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.util.Locale;

public class MainActivity extends Activity {
    private WebView web;
    private GameViewport viewport;
    private FrameLayout root;
    private LinearLayout panel;
    private ImageView ambientView;
    private boolean ambientEnabled;
    private int ambientMode; // 0=Off, 1=Balanced, 2=Smooth
    // Live mirror backing the letterbox blur. The frame shown by ImageView
    // must never be modified: render-thread uploads are asynchronous.
    private boolean ambientForeground=false;
    // PixelCopy samples GPU-composited gameplay asynchronously.
    // The previous 12 fps WebView.draw() loop blocked the game UI thread.
    private android.graphics.Bitmap ambientLastFrame;
    private final android.graphics.Bitmap[] ambientBuffers=new android.graphics.Bitmap[3];
    private int ambientBufferSlot=0;
    private boolean ambientCopyBusy=false;
    private boolean ambientGeometryPending=false;
    private android.graphics.Rect ambientStageRect;
    private long ambientGeometryUpdatedAt=0L;
    private int ambientPixelCopyFailures=0;
    private int ambientWebBackground=Integer.MIN_VALUE;
    private boolean exiting=false;
    private final Runnable ambientTick=new Runnable(){public void run(){
        if(!ambientEnabled||!ambientForeground||isFinishing()||exiting)return;
        if(!ambientCopyBusy)updateAmbient();
    }};
    private TextView countdown, timerButton, bellBadge;
    private final CompanionChrome.Icon[] viewButtons=new CompanionChrome.Icon[4];
    private boolean adjustingPanel=false;
    private boolean expanded,original,fullscreen,landscape;
    private static final String REMEMBER_HUD="rememberHudPosition";
    private static final String EDGE_SNAP="hudEdgeSnap";
    private static final String AUTO_HIDE="hudAutoHideSeconds";
    private static final String HUD_OPACITY="hudOpacityPercent";
    private View collapsedBadge;
    private boolean autoHidden=false;
    private float hudOpacity=1f;
    private int dialogDepth=0;
    private final Runnable autoHideTask=()->{
        if(panel!=null&&!original&&!autoHidden&&dialogDepth==0&&ambientForeground)
            setAutoHidden(true);
    };
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable clock=new Runnable(){public void run(){updateTimer();handler.postDelayed(this,1000);}};
    private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(0xFF07182C);getWindow().setNavigationBarColor(0xFF07182C);
        expanded=getPreferences(0).getBoolean("expanded",false);
        original=getPreferences(0).getBoolean("originalMode",false);
        fullscreen=getPreferences(0).getBoolean("fullscreenMode",false);
        ambientMode=getPreferences(0).getInt("ambientBlurMode",
            getPreferences(0).getBoolean("ambientBlurEnabled",false)?1:0);
        ambientEnabled=ambientMode!=0;
        hudOpacity=Math.max(.2f,Math.min(1f,getPreferences(0).getInt(HUD_OPACITY,100)/100f));
        web=new WebView(this);
        WebSettings s=web.getSettings();
        s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);
        s.setLoadsImagesAutomatically(true);s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient(){
            @Override public void onPageStarted(WebView v,String url,android.graphics.Bitmap icon){
                if(viewport!=null)viewport.onPageStarted();
                ambientStageRect=null;ambientGeometryUpdatedAt=0;
                // Retain the last good blur until the new page paints a real frame.
                handler.removeCallbacks(ambientTick);
            }
            @Override public void onPageFinished(WebView v,String url){
                if(viewport!=null)viewport.onPageFinished();
                if(ambientEnabled)handler.postDelayed(()->applyAmbient(),600);
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){
                Uri u=r.getUrl();String scheme=u.getScheme();
                if("https".equals(scheme)||"http".equals(scheme))return false;
                try{startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception ignored){}
                return true;
            }
        });
        viewport=new GameViewport(this,web);
        viewport.setZoomListener(z->updateZoom());
        int mode=getPreferences(0).getInt("orientationMode",0);
        applyOrientation(mode);
        TimerReceiver.ensureChannel(this);
        ReminderScheduler.list(this);
        build();
        setFullscreen(fullscreen);
        handler.post(clock);
        web.loadUrl("https://seaf.se/");
    }
    private TextView label(String text,int size,boolean bold){
        TextView t=new TextView(this);t.setText(text);t.setTextColor(Color.WHITE);
        t.setTextSize(size);t.setGravity(Gravity.CENTER_VERTICAL);
        if(bold)t.setTypeface(null,1);return t;
    }
    private TextView button(String name,Runnable run){
        TextView t=label(name,13,true);t.setGravity(Gravity.CENTER);
        t.setBackgroundColor(0xFF243650);t.setOnClickListener(v->run.run());return t;
    }
    private CompanionChrome.Icon iconButton(String name,String description,Runnable click){
        CompanionChrome.Icon icon=new CompanionChrome.Icon(this,name);
        icon.setContentDescription(description);
        icon.setBackground(CompanionChrome.glass(getResources().getDisplayMetrics().density,false));
        icon.setOnClickListener(v->click.run());
        return icon;
    }
    private void addIcon(LinearLayout bar,CompanionChrome.Icon button,int weight){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(46),weight);
        lp.setMargins(dp(2),0,dp(2),0);
        bar.addView(button,lp);
    }
    /** Long-press a control or the panel background to drag the HUD. */
    private void makeDraggable(View control) {
        final float[] start=new float[4];
        final boolean[] dragging={false};
        final Runnable[] activate={null};
        control.setOnTouchListener((v,e)->{
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    hudInteracted();
                    start[0]=e.getRawX();start[1]=e.getRawY();
                    start[2]=panel.getTranslationX();start[3]=panel.getTranslationY();
                    dragging[0]=false;
                    activate[0]=()->{
                        dragging[0]=true;
                        v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                    };
                    handler.postDelayed(activate[0],420);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if(!dragging[0]&&Math.hypot(e.getRawX()-start[0],
                        e.getRawY()-start[1])>dp(12))
                        handler.removeCallbacks(activate[0]);
                    if(dragging[0]){
                        float bx=panelBaseX(),by=panelBaseY();
                        float x=bx+start[2]+e.getRawX()-start[0];
                        float y=by+start[3]+e.getRawY()-start[1];
                        panel.setTranslationX(limit(x,dp(8),
                            root.getWidth()-panel.getWidth()-dp(8))-bx);
                        panel.setTranslationY(limit(y,dp(12),
                            root.getHeight()-panel.getHeight()-dp(12))-by);
                        updateHudReservation();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    handler.removeCallbacks(activate[0]);
                    if(!dragging[0])v.performClick();
                    else {
                        if(getPreferences(0).getBoolean(EDGE_SNAP,false))snapToEdge();
                        rememberPosition();
                    }
                    dragging[0]=false;
                    hudInteracted();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacks(activate[0]);
                    dragging[0]=false;
                    hudInteracted();
                    return true;
            }
            return false;
        });
    }
    private float limit(float value,float min,float max){
        return Math.max(min,Math.min(Math.max(min,max),value));
    }
    private float panelBaseX(){
        return landscape?root.getWidth()-panel.getWidth()-dp(9):
            (root.getWidth()-panel.getWidth())/2f;
    }
    private float panelBaseY(){
        return landscape?root.getHeight()/2f-panel.getHeight()/2f:
            root.getHeight()-panel.getHeight()-dp(10);
    }
    private void rememberPosition(){
        if(panel==null||!getPreferences(0).getBoolean(REMEMBER_HUD,true))return;
        String key=landscape?"landscape":"portrait";
        float density=getResources().getDisplayMetrics().density;
        getPreferences(0).edit()
            .putFloat(key+"BarOffsetXDp",panel.getTranslationX()/density)
            .putFloat(key+"BarOffsetDp",panel.getTranslationY()/density).apply();
    }
    private void snapToEdge(){
        if(panel==null||root.getWidth()<1||root.getHeight()<1)return;
        float minX=dp(8),minY=dp(12);
        float maxX=Math.max(minX,root.getWidth()-panel.getWidth()-dp(8));
        float maxY=Math.max(minY,root.getHeight()-panel.getHeight()-dp(12));
        float x=limit(panelBaseX()+panel.getTranslationX(),minX,maxX);
        float y=limit(panelBaseY()+panel.getTranslationY(),minY,maxY);
        float[] distance={x-minX,maxX-x,y-minY,maxY-y};
        int nearest=0;
        for(int i=1;i<4;i++)if(distance[i]<distance[nearest])nearest=i;
        if(nearest==0)x=minX;
        else if(nearest==1)x=maxX;
        else if(nearest==2)y=minY;
        else y=maxY;
        panel.setTranslationX(x-panelBaseX());
        panel.setTranslationY(y-panelBaseY());
        updateHudReservation();
    }
    private void scheduleAutoHide(){
        handler.removeCallbacks(autoHideTask);
        if(panel==null||original||autoHidden||dialogDepth>0||!ambientForeground)return;
        int seconds=getPreferences(0).getInt(AUTO_HIDE,0);
        if(seconds>0)handler.postDelayed(autoHideTask,seconds*1000L);
    }
    private void hudInteracted(){
        if(autoHidden)setAutoHidden(false);
        scheduleAutoHide();
    }
    private void dialogOpened(){dialogDepth++;handler.removeCallbacks(autoHideTask);}
    private void dialogClosed(){dialogDepth=Math.max(0,dialogDepth-1);scheduleAutoHide();}
    private void setHudOpacity(float value){
        hudOpacity=limit(value,.2f,1f);
        if(panel!=null)panel.setAlpha(hudOpacity);
        if(collapsedBadge!=null)collapsedBadge.setAlpha(hudOpacity);
    }
    private void locateBadge(){
        if(collapsedBadge==null||panel==null||root==null||root.getWidth()<1)return;
        collapsedBadge.setX(limit(panel.getX(),dp(8),
            root.getWidth()-collapsedBadge.getWidth()-dp(8)));
        collapsedBadge.setY(limit(panel.getY(),dp(12),
            root.getHeight()-collapsedBadge.getHeight()-dp(12)));
    }
    private void setAutoHidden(boolean hidden){
        if(panel==null||collapsedBadge==null||original)return;
        autoHidden=hidden;
        if(hidden){
            handler.removeCallbacks(autoHideTask);
            locateBadge();
            panel.setVisibility(View.INVISIBLE);
            collapsedBadge.setVisibility(View.VISIBLE);
        }else{
            collapsedBadge.setVisibility(View.GONE);
            panel.setVisibility(View.VISIBLE);
        }
        updateHudReservation();
    }
    private void updateHudReservation(){
        if(landscape||original||viewport==null||panel==null)return;
        if(autoHidden){viewport.setReservedBottom(0);return;}
        boolean docked=Math.abs(panel.getTranslationY())<dp(24);
        viewport.setReservedBottom(docked?
            Math.round((panel.getHeight()+dp(16))/getResources().getDisplayMetrics().density):0);
    }
    private void updatePanelPosition(){
        if(panel==null||root==null)return;
        String key=landscape?"landscape":"portrait";
        boolean remember=getPreferences(0).getBoolean(REMEMBER_HUD,true);
        float x=remember?getPreferences(0).getFloat(key+"BarOffsetXDp",0):0;
        float y=remember?getPreferences(0).getFloat(key+"BarOffsetDp",0):0;
        float baseX=panelBaseX(),baseY=panelBaseY();
        panel.setTranslationX(Math.max(dp(8),Math.min(root.getWidth()-panel.getWidth()-dp(8),
            baseX+dp(Math.round(x))))-baseX);
        panel.setTranslationY(Math.max(dp(12),Math.min(root.getHeight()-panel.getHeight()-dp(12),
            baseY+dp(Math.round(y))))-baseY);
        updateHudReservation();
        if(autoHidden)locateBadge();
    }
    private static final String SUSPEND_GAME_AUDIO = """
        (function(){
          try{
            document.querySelectorAll('audio,video').forEach(function(m){
              try{m.pause();}catch(e){}
            });
            if(window.Howler && typeof window.Howler.mute==='function')
              window.Howler.mute(true);
            if(window.createjs && createjs.Sound && createjs.Sound.muted!==undefined)
              createjs.Sound.muted=true;
            if(window.__seafCompanionAudioContexts)
              window.__seafCompanionAudioContexts.forEach(function(ctx){
                if(ctx.state==='running')ctx.suspend().catch(function(){});
              });
          }catch(e){}
        })();
        """;
    private static final String RESUME_GAME_AUDIO = """
        (function(){
          try{
            if(window.Howler && typeof window.Howler.mute==='function')
              window.Howler.mute(false);
            if(window.createjs && createjs.Sound && createjs.Sound.muted!==undefined)
              createjs.Sound.muted=false;
            if(window.__seafCompanionAudioContexts)
              window.__seafCompanionAudioContexts.forEach(function(ctx){
                if(ctx.state==='suspended')ctx.resume().catch(function(){});
              });
          }catch(e){}
        })();
        """;
    private void pauseGameAudio(){
        if(web==null)return;
        try{web.evaluateJavascript(SUSPEND_GAME_AUDIO,null);}catch(Exception ignored){}
        try{web.onPause();}catch(Exception ignored){}
        try{web.pauseTimers();}catch(Exception ignored){}
    }
    private void resumeGameAudio(){
        if(web==null||isFinishing())return;
        try{web.onResume();web.resumeTimers();}catch(Exception ignored){}
        try{web.evaluateJavascript(RESUME_GAME_AUDIO,null);}catch(Exception ignored){}
    }
    private void closeCompanion(){
        new AlertDialog.Builder(this)
            .setTitle("Close SEAF Companion?")
            .setMessage("The game will close and audio will stop. Your scheduled mission reminder stays active.")
            .setNegativeButton("Keep Playing",null)
            .setPositiveButton("Close Game",(dialog,which)->{
                if(exiting)return;
                exiting=true;
                ambientForeground=false;
                handler.removeCallbacks(ambientTick);
                handler.removeCallbacks(autoHideTask);
                handler.removeCallbacks(clock);
                pauseGameAudio();
                try{web.stopLoading();CookieManager.getInstance().flush();}
                catch(Exception ignored){}
                if(root!=null)root.animate().alpha(0f).setDuration(180)
                    .withEndAction(()->{
                        try{web.loadUrl("about:blank");}catch(Exception ignored){}
                        finishAndRemoveTask();
                    }).start();
                else finishAndRemoveTask();
            }).show();
    }
    private void build(){
        handler.removeCallbacks(autoHideTask);
        autoHidden=false;
        collapsedBadge=null;
        panel=null;
        landscape=getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;
        countdown=null;timerButton=null;bellBadge=null;
        java.util.Arrays.fill(viewButtons,null);
        if(viewport.getParent()!=null)((android.view.ViewGroup)viewport.getParent()).removeView(viewport);
        root=new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        ambientStageRect=null;ambientGeometryUpdatedAt=0;
        ambientView=new ImageView(this);
        ambientView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ambientView.setVisibility(ambientEnabled?View.VISIBLE:View.GONE);
        if(ambientLastFrame!=null)ambientView.setImageBitmap(ambientLastFrame);
        root.addView(ambientView,new FrameLayout.LayoutParams(-1,-1));
        if(android.os.Build.VERSION.SDK_INT>=31){
            ambientView.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(
                ambientMode==2?38f:29f,ambientMode==2?38f:29f,
                android.graphics.Shader.TileMode.CLAMP));
        }
        root.addView(viewport,new FrameLayout.LayoutParams(-1,-1));
        viewport.setModes(landscape,original,(!landscape&&!original)?(expanded?162:66):0);
        if(!original){
            panel=new LinearLayout(this);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setPadding(dp(6),dp(6),dp(6),dp(6));
            panel.setBackground(CompanionChrome.panel(getResources().getDisplayMetrics().density));
            panel.setElevation(dp(10));
            panel.setAlpha(hudOpacity);
            makeDraggable(panel);
            FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(
                landscape?dp(expanded?255:76):Math.min(dp(375),getResources().getDisplayMetrics().widthPixels-dp(20)),
                FrameLayout.LayoutParams.WRAP_CONTENT,
                landscape?Gravity.RIGHT|Gravity.CENTER_VERTICAL:Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
            params.setMargins(dp(9),0,dp(9),dp(10));
            root.addView(panel,params);
            panel.addOnLayoutChangeListener((v,l,t,r,bottom,ol,ot,or_,ob)->{
                if(!original&&(r-l!=or_-ol||bottom-t!=ob-ot))
                    root.post(this::updatePanelPosition);
            });
            LinearLayout bar=new LinearLayout(this);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            if(!landscape){
                String[] kinds={"left","center","right","overview"};
                String[] names={"Left view","Center view","Right view","Overview"};
                for(int i=0;i<4;i++){
                    final int selection=i;
                    CompanionChrome.Icon item=iconButton(kinds[i],names[i],()->selectView(selection));
                    makeDraggable(item);addIcon(bar,item,1);
                    viewButtons[i]=item;
                }
                View separator=new View(this);separator.setBackgroundColor(0x8875B5DB);
                LinearLayout.LayoutParams divider=new LinearLayout.LayoutParams(dp(1),dp(30));
                divider.setMargins(dp(2),0,dp(2),0);bar.addView(separator,divider);
                CompanionChrome.Icon close=iconButton("close","Close SEAF Companion",
                    this::closeCompanion);
                makeDraggable(close);addIcon(bar,close,1);
                FrameLayout bell=new FrameLayout(this);
                CompanionChrome.Icon bellIcon=iconButton("bell","Mission reminder",this::showTimer);
                bell.addView(bellIcon,new FrameLayout.LayoutParams(-1,-1));
                makeDraggable(bellIcon);
                bellBadge=label("",10,true);bellBadge.setTextColor(Color.BLACK);
                bellBadge.setGravity(Gravity.CENTER);bellBadge.setBackgroundColor(CompanionChrome.GOLD);
                bell.addView(bellBadge,new FrameLayout.LayoutParams(dp(17),dp(17),
                    Gravity.RIGHT|Gravity.TOP));
                LinearLayout.LayoutParams bellLp=new LinearLayout.LayoutParams(0,dp(46),1);
                bellLp.setMargins(dp(2),0,dp(2),0);bar.addView(bell,bellLp);
                CompanionChrome.Icon exp=iconButton(expanded?"collapse":"expand",
                    expanded?"Collapse":"Expand",()->{
                        expanded=!expanded;getPreferences(0).edit()
                            .putBoolean("expanded",expanded).apply();build();
                    });
                makeDraggable(exp);addIcon(bar,exp,1);
            }else{
                CompanionChrome.Icon expand=iconButton(expanded?"collapse":"expand",
                    expanded?"Collapse":"Expand",()->{
                        expanded=!expanded;getPreferences(0).edit()
                            .putBoolean("expanded",expanded).apply();build();
                    });
                makeDraggable(expand);addIcon(bar,expand,1);
                CompanionChrome.Icon close=iconButton("close","Close SEAF Companion",
                    this::closeCompanion);
                makeDraggable(close);addIcon(bar,close,1);
            }
            panel.addView(bar);
            if(expanded){
                View line=new View(this);line.setBackgroundColor(0x776B9CC0);
                LinearLayout.LayoutParams lineParams=new LinearLayout.LayoutParams(-1,dp(1));
                lineParams.setMargins(dp(9),dp(8),dp(9),dp(9));panel.addView(line,lineParams);
                countdown=label("No active reminder",23,true);
                countdown.setPadding(dp(10),0,dp(10),0);
                panel.addView(countdown,new LinearLayout.LayoutParams(-1,dp(40)));
                TextView note=label("Manual reminder  •  SEAF not synchronized",11,false);
                note.setTextColor(CompanionChrome.WHITE);
                note.setPadding(dp(10),0,0,dp(7));
                panel.addView(note);
                LinearLayout actions=new LinearLayout(this);
                timerButton=button("Edit Reminder",this::showTimer);
                timerButton.setBackground(CompanionChrome.glass(getResources().getDisplayMetrics().density,false));
                actions.addView(timerButton,new LinearLayout.LayoutParams(0,dp(43),1));
                TextView fullscreenButton=button(fullscreen?"Exit Fullscreen":"Fullscreen",()->setFullscreen(!fullscreen));
                fullscreenButton.setBackground(CompanionChrome.glass(getResources().getDisplayMetrics().density,false));
                actions.addView(fullscreenButton,new LinearLayout.LayoutParams(0,dp(43),1));
                makeDraggable(timerButton);
                makeDraggable(fullscreenButton);
                panel.addView(actions);
                LinearLayout more=new LinearLayout(this);
                TextView settings=button("Settings",this::showSettings);
                TextView rotate=button("Rotate",this::toggleOrientation);
                settings.setBackground(CompanionChrome.glass(getResources().getDisplayMetrics().density,false));
                rotate.setBackground(CompanionChrome.glass(getResources().getDisplayMetrics().density,false));
                more.addView(settings,new LinearLayout.LayoutParams(0,dp(43),1));
                more.addView(rotate,new LinearLayout.LayoutParams(0,dp(43),1));
                makeDraggable(settings);
                makeDraggable(rotate);
                panel.addView(more);
            }
            TextView badge=label("SEAF",12,true);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(CompanionChrome.panel(getResources().getDisplayMetrics().density));
            badge.setElevation(dp(12));
            badge.setAlpha(hudOpacity);
            badge.setContentDescription("Restore Companion panel");
            badge.setOnClickListener(v->hudInteracted());
            collapsedBadge=badge;
            root.addView(badge,new FrameLayout.LayoutParams(dp(70),dp(38),
                Gravity.TOP|Gravity.LEFT));
            badge.setVisibility(View.GONE);
        }else{
            TextView restore=button("Companion",()->setOriginal(false));
            restore.setBackground(CompanionChrome.glass(getResources().getDisplayMetrics().density,false));
            FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(dp(116),dp(43),Gravity.RIGHT|Gravity.BOTTOM);
            p.setMargins(0,0,dp(12),dp(20));root.addView(restore,p);
        }
        setContentView(root);
        if(!original)root.post(this::updatePanelPosition);
        updateViewButtons();updateTimer();
        scheduleAutoHide();
    }
    private void selectView(int index){
        if(viewport==null||landscape||original)return;
        if(index==3)viewport.showOverview();
        else viewport.selectRegion(index);
        updateViewButtons();
    }
    private void updateViewButtons(){
        if(viewport==null)return;
        int selected=viewport.isHeightFit()?viewport.getFocusRegion():3;
        for(int i=0;i<viewButtons.length;i++){
            CompanionChrome.Icon b=viewButtons[i];
            if(b!=null)b.setSelectedState(i==selected);
        }
    }
    private void updateZoom(){updateViewButtons();}
    private void updateTimer(){
        java.util.ArrayList<ReminderScheduler.Reminder> reminders=ReminderScheduler.list(this);
        if(timerButton!=null)timerButton.setText(reminders.isEmpty()?"Set Reminder":"Edit Reminder");
        if(bellBadge!=null){
            bellBadge.setText(reminders.isEmpty()?"":"1");
            bellBadge.setVisibility(reminders.isEmpty()?View.GONE:View.VISIBLE);
        }
        if(countdown==null)return;
        if(reminders.isEmpty()){countdown.setText("No active reminders");return;}
        long remaining=Math.max(0,reminders.get(0).due-System.currentTimeMillis())/1000;
        String time=String.format(Locale.US,"%02d:%02d:%02d",
            remaining/3600,remaining/60%60,remaining%60);
        countdown.setText(remaining==0?"Due now":time);
    }
    private TextView dialogLabel(String text,int size){
        TextView t=new TextView(this);
        t.setText(text);t.setTextSize(size);t.setTextColor(0xFF25354A);
        t.setPadding(dp(3),dp(5),dp(3),dp(5));
        return t;
    }
    private void startQuickTimer(String category,int minutes,AlertDialog dialog){
        if(minutes<1||minutes>10080){
            Toast.makeText(this,"Enter 1–10080 minutes",Toast.LENGTH_LONG).show();
            return;
        }
        ReminderScheduler.Reminder reminder=ReminderScheduler.schedule(this,
            System.currentTimeMillis()+minutes*60000L,
            category+" ("+minutes+"m)");
        if(reminder==null){
            Toast.makeText(this,"Unable to set reminder",Toast.LENGTH_LONG).show();
            return;
        }
        if(Build.VERSION.SDK_INT>=33 &&
            checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=
               android.content.pm.PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},7);
        }
        updateTimer();
        Toast.makeText(this,category+" reminder set for "+minutes+" minutes",Toast.LENGTH_SHORT).show();
        if(dialog!=null)dialog.dismiss();
    }
    private TextView reminderAction(String name,boolean primary,boolean danger,Runnable click){
        TextView t=label(name,14,true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(CompanionChrome.glass(getResources().getDisplayMetrics().density,primary));
        if(danger){
            android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{0xCC65294C,0xD8422440});
            bg.setCornerRadius(dp(14));bg.setStroke(dp(1),0xAAE58BAC);
            t.setBackground(bg);
            t.setTextColor(0xFFFFA7B7);
        }
        t.setOnClickListener(v->click.run());
        return t;
    }
    private void showTimer(){
        final java.util.ArrayList<ReminderScheduler.Reminder> current=ReminderScheduler.list(this);
        final ReminderScheduler.Reminder active=current.isEmpty()?null:current.get(0);
        final AlertDialog dialog=new AlertDialog.Builder(this).create();
        LinearLayout body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18),dp(14),dp(18),dp(17));
        body.setBackground(CompanionChrome.panel(getResources().getDisplayMetrics().density));
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(body);
        TextView title=label("MISSION REMINDER",17,true);
        title.setTextColor(CompanionChrome.WHITE);
        body.addView(title);
        TextView detail=label("One manual timer  •  Not synced to SEAF",12,false);
        detail.setTextColor(CompanionChrome.WHITE);
        LinearLayout.LayoutParams infoLp=new LinearLayout.LayoutParams(-1,dp(29));
        body.addView(detail,infoLp);
        final TextView clock=label(active==null?"No timer running":"",32,true);
        clock.setGravity(Gravity.CENTER);
        clock.setTextColor(CompanionChrome.WHITE);
        body.addView(clock,new LinearLayout.LayoutParams(-1,dp(60)));
        if(active!=null){
            long remaining=Math.max(0,active.due-System.currentTimeMillis())/1000;
            clock.setText(String.format(Locale.US,"%02d:%02d:%02d",
                remaining/3600,remaining/60%60,remaining%60));
        }
        TextView durationDetail=label(active==null?
            "Select a duration to start":"Active timer · "+active.label.replace("Mission ",""),12,false);
        durationDetail.setGravity(Gravity.CENTER);
        durationDetail.setTextColor(0xFFAAC5E2);
        body.addView(durationDetail,new LinearLayout.LayoutParams(-1,dp(24)));
        final Runnable[] redrawTime={null};
        redrawTime[0]=()->{
            if(!dialog.isShowing()||active==null)return;
            long left=Math.max(0,(active.due-System.currentTimeMillis())/1000);
            clock.setText(String.format(Locale.US,"%02d:%02d:%02d",
                left/3600,left/60%60,left%60));
            handler.postDelayed(redrawTime[0],1000);
        };
        dialog.setOnDismissListener(d->{handler.removeCallbacks(redrawTime[0]);dialogClosed();});
        View separator=new View(this);
        separator.setBackgroundColor(0x7089B9DE);
        LinearLayout.LayoutParams split=new LinearLayout.LayoutParams(-1,dp(1));
        split.setMargins(0,dp(9),0,dp(15));body.addView(separator,split);
        TextView select=label("Choose a duration",13,true);
        select.setTextColor(CompanionChrome.WHITE);
        body.addView(select,new LinearLayout.LayoutParams(-1,dp(29)));

        final int[] chosen={0};
        final TextView[] presets=new TextView[4];
        final EditText custom=new EditText(this);
        final int[] values={5,15,30,60};
        LinearLayout options=new LinearLayout(this);
        for(int i=0;i<4;i++){
            final int which=i;
            TextView button=label(new String[]{"5m","15m","30m","1h"}[i],15,true);
            button.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(49),1);
            lp.setMargins(dp(2),0,dp(2),0);
            options.addView(button,lp);
            presets[i]=button;
            button.setOnClickListener(v->{
                chosen[0]=values[which];
                for(int j=0;j<4;j++)
                    presets[j].setBackground(CompanionChrome.glass(
                        getResources().getDisplayMetrics().density,j==which));
                custom.setText("");
            });
        }
        body.addView(options);
        custom.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        custom.setSingleLine(true);
        custom.setTextColor(CompanionChrome.WHITE);
        custom.setHintTextColor(0xFF8BA8C9);
        custom.setHint("Or enter custom minutes");
        custom.setTextSize(14);
        custom.setPadding(dp(12),dp(5),dp(12),dp(5));
        custom.setBackground(CompanionChrome.glass(getResources().getDisplayMetrics().density,false));
        LinearLayout.LayoutParams customLp=new LinearLayout.LayoutParams(-1,dp(49));
        customLp.setMargins(0,dp(12),0,dp(17));
        body.addView(custom,customLp);
        custom.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){
                if(s.length()>0){
                    chosen[0]=0;
                    for(TextView b:presets)
                        b.setBackground(CompanionChrome.glass(
                            getResources().getDisplayMetrics().density,false));
                }
            }
            public void afterTextChanged(android.text.Editable e){}
        });
        // Reflect the actual saved duration, not the mockup's 15-minute choice.
        // Preserve exact configured minutes in the reminder label even as time elapses.
        int savedMinutes=0;
        if(active!=null){
            java.util.regex.Matcher matcher=java.util.regex.Pattern
                .compile("\\((\\d+)m\\)").matcher(active.label);
            if(matcher.find()){
                try{savedMinutes=Integer.parseInt(matcher.group(1));}
                catch(NumberFormatException ignored){}
            }
        }
        int matched=-1;
        for(int j=0;j<values.length;j++)if(values[j]==savedMinutes)matched=j;
        chosen[0]=matched>=0?savedMinutes:0;
        if(active!=null&&savedMinutes>0&&matched<0)
            custom.setText(Integer.toString(savedMinutes));
        for(int j=0;j<4;j++)
            presets[j].setBackground(CompanionChrome.glass(
                getResources().getDisplayMetrics().density,j==matched));

        LinearLayout actions=new LinearLayout(this);
        if(active!=null){
            TextView delete=reminderAction("Delete",false,true,()->{
                ReminderScheduler.cancel(this);
                updateTimer();
                dialog.dismiss();
                Toast.makeText(this,"Reminder deleted",Toast.LENGTH_SHORT).show();
            });
            LinearLayout.LayoutParams del=new LinearLayout.LayoutParams(0,dp(48),1);
            del.setMargins(0,0,dp(5),0);actions.addView(delete,del);
        }
        TextView set=reminderAction(active==null?"Set Reminder":"Update Reminder",
            true,false,()->{
                int minutes=chosen[0];
                if(custom.getText().length()>0){
                    try{minutes=Integer.parseInt(custom.getText().toString());}
                    catch(NumberFormatException ignored){minutes=0;}
                }
                if(minutes<1||minutes>10080){
                    custom.setError("Enter 1 to 10080 minutes");
                    return;
                }
                ReminderScheduler.Reminder next=ReminderScheduler.schedule(this,
                    System.currentTimeMillis()+minutes*60000L,"Mission ("+minutes+"m)");
                if(next==null){
                    Toast.makeText(this,"Unable to set reminder",Toast.LENGTH_LONG).show();
                    return;
                }
                if(Build.VERSION.SDK_INT>=33 &&
                   checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=
                       android.content.pm.PackageManager.PERMISSION_GRANTED)
                    requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},7);
                updateTimer();
                dialog.dismiss();
                Toast.makeText(this,"Mission reminder "+(active==null?"set":"updated"),
                    Toast.LENGTH_SHORT).show();
            });
        actions.addView(set,new LinearLayout.LayoutParams(0,dp(48),1.2f));
        body.addView(actions);
        TextView close=label("Close",12,false);
        close.setGravity(Gravity.CENTER);
        close.setTextColor(CompanionChrome.WHITE);
        close.setOnClickListener(v->dialog.dismiss());
        LinearLayout.LayoutParams closeLp=new LinearLayout.LayoutParams(-1,dp(34));
        closeLp.setMargins(0,dp(10),0,0);
        body.addView(close,closeLp);
        dialog.setView(scroll);
        dialog.setOnShowListener(d->{
            android.view.Window window=dialog.getWindow();
            if(window!=null){
                window.setBackgroundDrawableResource(android.R.color.transparent);
                window.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels-dp(24),
                    dp(450)),-2);
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                WindowManager.LayoutParams attrs=window.getAttributes();
                attrs.dimAmount=.58f;window.setAttributes(attrs);
            }
        });
        dialogOpened();dialog.show();
        if(active!=null)handler.postDelayed(redrawTime[0],1000);
    }
    private void applyAmbient(){
        handler.removeCallbacks(ambientTick);
        if(ambientView!=null)
            ambientView.setVisibility(ambientEnabled?View.VISIBLE:View.GONE);
        // Do not repeatedly invalidate the WebView compositor on every resume.
        int color=ambientEnabled?Color.TRANSPARENT:Color.BLACK;
        if(ambientWebBackground!=color){
            web.setBackgroundColor(color);
            ambientWebBackground=color;
        }
        // Transparent page chrome is necessary to expose the blurred backdrop
        // behind letterboxed content. The game stage itself is not modified.
        web.evaluateJavascript(ambientEnabled?
            "(function(){var s=document.getElementById('seaf-ambient-transparent');"+
            "if(!s){s=document.createElement('style');s.id='seaf-ambient-transparent';"+
            "s.textContent='html,body,#page-home{background-color:transparent!important}';"+
            "document.head.appendChild(s);}})();":
            "(function(){var s=document.getElementById('seaf-ambient-transparent');"+
            "if(s)s.remove();})()",null);
        if(ambientEnabled&&ambientForeground)handler.post(ambientTick);
    }
    private boolean usableAmbientFrame(android.graphics.Bitmap image){
        // Reject black/white intermediate WebView frames when Chromium is
        // waiting for GPU tiles; never publish these as a new blur frame.
        int lit=0,white=0,total=0;
        for(int y=5;y<image.getHeight();y+=8)
            for(int x=5;x<image.getWidth();x+=8){
                int rgb=image.getPixel(x,y);
                int r=(rgb>>16)&255,g=(rgb>>8)&255,b=rgb&255;
                if(Math.max(r,Math.max(g,b))>28)lit++;
                if(r>248&&g>248&&b>248)white++;
                total++;
            }
        return lit>Math.max(5,total/80)&&white<total*0.92f;
    }
    private void scheduleNextAmbient(long milliseconds){
        handler.removeCallbacks(ambientTick);
        if(ambientEnabled&&ambientForeground&&!isFinishing()&&!exiting)
            handler.postDelayed(ambientTick,milliseconds);
    }
    /** Obtain the rendered SEAF stage bounds, excluding HUD and letterbox. */
    private void refreshAmbientStage(){
        if(ambientGeometryPending||web==null||!ambientForeground)return;
        ambientGeometryPending=true;
        web.evaluateJavascript("""
            (function(){
              var el=document.querySelector('#page-home > .scale-me.pos-abs-c') ||
                document.querySelector('#page-home .scale-me.pos-abs-c');
              if(!el)return null;
              var r=el.getBoundingClientRect();
              var v=window.visualViewport;
              var sx=window.innerWidth/(v?v.width:window.innerWidth);
              if(!Number.isFinite(sx)||sx<0.5||sx>5)sx=1;
              return [r.left,r.top,r.right,r.bottom,window.innerWidth,window.innerHeight];
            })();
            """,value->{
            ambientGeometryPending=false;
            try{
                org.json.JSONArray data=new org.json.JSONArray(value);
                if(data.length()!=6)return;
                double cssW=data.getDouble(4),cssH=data.getDouble(5);
                if(cssW<100||cssH<100)return;
                double xScale=web.getWidth()/cssW,yScale=web.getHeight()/cssH;
                int l=(int)Math.round(data.getDouble(0)*xScale);
                int t=(int)Math.round(data.getDouble(1)*yScale);
                int r=(int)Math.round(data.getDouble(2)*xScale);
                int b=(int)Math.round(data.getDouble(3)*yScale);
                // Intersect with actual viewport. Reject tiny cropped fragments.
                l=Math.max(0,l);t=Math.max(0,t);
                r=Math.min(web.getWidth(),r);b=Math.min(web.getHeight(),b);
                if(r-l<web.getWidth()*.30f||b-t<web.getHeight()*.10f)return;
                ambientStageRect=new android.graphics.Rect(l,t,r,b);
                ambientGeometryUpdatedAt=android.os.SystemClock.uptimeMillis();
            }catch(Exception ignored){}
        });
    }
    private void updateAmbient(){
        if(!ambientEnabled||!ambientForeground||web==null||ambientView==null||
           ambientView.getVisibility()!=View.VISIBLE||web.getWidth()<1||
           web.getHeight()<1||ambientCopyBusy)return;
        if(Build.VERSION.SDK_INT<26){scheduleNextAmbient(1800);return;}
        long now=android.os.SystemClock.uptimeMillis();
        if(ambientStageRect==null||now-ambientGeometryUpdatedAt>3500){
            refreshAmbientStage();
            if(ambientStageRect==null){scheduleNextAmbient(900);return;}
        }
        final android.graphics.Rect stage=new android.graphics.Rect(ambientStageRect);
        int[] pos=new int[2];web.getLocationInWindow(pos);
        stage.offset(pos[0],pos[1]);
        // Capture only the actual game, never the HUD, blur, or letterbox.
        // If HUD overlaps the game, avoid a recursive contaminated frame.
        if(panel!=null&&panel.getVisibility()==View.VISIBLE &&
            android.graphics.Rect.intersects(stage,new android.graphics.Rect(
                Math.round(panel.getX()),Math.round(panel.getY()),
                Math.round(panel.getX()+panel.getWidth()),
                Math.round(panel.getY()+panel.getHeight())))){
            scheduleNextAmbient(ambientMode==2?500:1100);return;
        }
        android.view.Window window=getWindow();
        android.view.View decor=window.getDecorView();
        android.graphics.Rect bounds=new android.graphics.Rect(0,0,decor.getWidth(),decor.getHeight());
        if(!stage.intersect(bounds)||stage.width()<80||stage.height()<60){
            scheduleNextAmbient(900);return;
        }
        final int width=256,height=144;
        try{
            android.graphics.Bitmap bitmap=ambientBuffers[ambientBufferSlot];
            if(bitmap==null||bitmap.isRecycled()){
                bitmap=android.graphics.Bitmap.createBitmap(
                    width,height,android.graphics.Bitmap.Config.RGB_565);
                ambientBuffers[ambientBufferSlot]=bitmap;
            }
            final android.graphics.Bitmap frame=bitmap;
            final int slot=ambientBufferSlot;
            ambientCopyBusy=true;
            long started=android.os.SystemClock.uptimeMillis();
            android.view.PixelCopy.request(window,stage,frame,result->{
                ambientCopyBusy=false;
                if(!ambientEnabled||!ambientForeground||exiting||isFinishing())return;
                if(result==android.view.PixelCopy.SUCCESS&&usableAmbientFrame(frame)){
                    ambientLastFrame=frame;
                    if(ambientView!=null)ambientView.setImageBitmap(frame);
                    ambientBufferSlot=(slot+1)%ambientBuffers.length;
                    ambientPixelCopyFailures=0;
                }else ambientPixelCopyFailures++;
                long duration=android.os.SystemClock.uptimeMillis()-started;
                long base=ambientMode==2?220:750;
                long delay=duration>120?Math.max(1200,base):duration>60?
                    Math.max(800,base):base;
                if(ambientPixelCopyFailures>3)delay=1800;
                scheduleNextAmbient(delay);
            },handler);
        }catch(Exception ignored){
            ambientCopyBusy=false;
            scheduleNextAmbient(1800);
        }
    }
    private void chooseAutoHide(){
        final int[] values={0,5,10,15,30};
        String[] choices={"Off","5 seconds","10 seconds","15 seconds","30 seconds"};
        int saved=getPreferences(0).getInt(AUTO_HIDE,0),selected=0;
        for(int i=0;i<values.length;i++)if(values[i]==saved)selected=i;
        AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle("Auto-hide after inactivity")
            .setSingleChoiceItems(choices,selected,(d,index)->{
                getPreferences(0).edit().putInt(AUTO_HIDE,values[index]).apply();
                d.dismiss();scheduleAutoHide();
            }).setNegativeButton("Cancel",null).create();
        dialog.setOnDismissListener(d->dialogClosed());
        dialogOpened();dialog.show();
    }
    private void chooseOpacity(){
        int old=Math.round(hudOpacity*100);
        LinearLayout content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24),dp(10),dp(24),dp(8));
        TextView amount=dialogLabel(old+"%",18);
        amount.setGravity(Gravity.CENTER);
        content.addView(amount);
        SeekBar slider=new SeekBar(this);
        slider.setMax(80);slider.setProgress(old-20);
        content.addView(slider);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int progress,boolean user){
                int value=progress+20;
                amount.setText(value+"%");
                setHudOpacity(value/100f);
            }
            public void onStartTrackingTouch(SeekBar bar){}
            public void onStopTrackingTouch(SeekBar bar){}
        });
        AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle("Overlay opacity").setView(content)
            .setPositiveButton("Save",(d,which)->getPreferences(0).edit()
                .putInt(HUD_OPACITY,slider.getProgress()+20).apply())
            .setNegativeButton("Cancel",(d,which)->setHudOpacity(old/100f)).create();
        dialog.setOnCancelListener(d->setHudOpacity(old/100f));
        dialog.setOnDismissListener(d->dialogClosed());
        dialogOpened();dialog.show();
    }
    private void chooseAmbientMode(){
        final String[] options={
            "Off — original black background",
            "Balanced — less GPU use, adaptive updates",
            "Smooth — quicker live background updates"
        };
        new AlertDialog.Builder(this).setTitle("Ambient background blur")
            .setSingleChoiceItems(options,ambientMode,(d,chosen)->{
                ambientMode=chosen;
                ambientEnabled=chosen!=0;
                getPreferences(0).edit()
                    .putInt("ambientBlurMode",chosen)
                    .putBoolean("ambientBlurEnabled",ambientEnabled).apply();
                if(ambientView!=null&&Build.VERSION.SDK_INT>=31){
                    float radius=chosen==2?38f:29f;
                    ambientView.setRenderEffect(
                        android.graphics.RenderEffect.createBlurEffect(
                            radius,radius,android.graphics.Shader.TileMode.CLAMP));
                }
                ambientStageRect=null;
                ambientGeometryUpdatedAt=0;
                applyAmbient();d.dismiss();
            }).setNegativeButton("Cancel",null).show();
    }
    private void showSettings(){
        SharedPreferences prefs=getPreferences(0);
        int auto=prefs.getInt(AUTO_HIDE,0);
        String[] opts={"Original game mode","Auto rotate","Lock portrait","Lock landscape",
            "Toggle fullscreen","Remember zoom: "+(prefs.getBoolean("rememberZoom",true)?"On":"Off"),
            "Auto center: "+(prefs.getBoolean("autoCenter",true)?"On":"Off"),
            "Mission notification sound","Android battery settings","Refresh game","Open game in browser",
            "Ambient blur: "+(ambientMode==0?"Off":ambientMode==2?"Smooth":"Balanced"),
            "Remember position: "+(prefs.getBoolean(REMEMBER_HUD,true)?"On":"Off"),
            "Edge snapping: "+(prefs.getBoolean(EDGE_SNAP,false)?"On":"Off"),
            "Auto-hide: "+(auto==0?"Off":auto+" seconds"),
            "Overlay opacity: "+Math.round(hudOpacity*100)+"%"};
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("SEAF Companion Settings")
          .setItems(opts,(d,which)->{
              switch(which){
                case 0:setOriginal(true);break;
                case 1:applyOrientation(0);break;
                case 2:applyOrientation(1);break;
                case 3:applyOrientation(2);break;
                case 4:setFullscreen(!fullscreen);break;
                case 5:viewport.setRememberZoom(!prefs.getBoolean("rememberZoom",true));break;
                case 6:viewport.setAutoCenter(!prefs.getBoolean("autoCenter",true));break;
                case 7:try{
                    Intent i=new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
                    i.putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName());
                    i.putExtra(Settings.EXTRA_CHANNEL_ID,TimerReceiver.CHANNEL_ID);startActivity(i);
                }catch(Exception ex){openAppSettings();}break;
                case 8:openAppSettings();break;
                case 9:web.reload();break;
                case 10:startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(
                    web.getUrl()==null?"https://seaf.se/":web.getUrl())));break;
                case 11:chooseAmbientMode();break;
                case 12:
                    boolean remember=!prefs.getBoolean(REMEMBER_HUD,true);
                    SharedPreferences.Editor editor=prefs.edit().putBoolean(REMEMBER_HUD,remember);
                    if(!remember)editor.remove("portraitBarOffsetXDp").remove("portraitBarOffsetDp")
                        .remove("landscapeBarOffsetXDp").remove("landscapeBarOffsetDp");
                    editor.apply();
                    if(panel!=null)updatePanelPosition();
                    break;
                case 13:prefs.edit().putBoolean(EDGE_SNAP,
                    !prefs.getBoolean(EDGE_SNAP,false)).apply();break;
                case 14:chooseAutoHide();break;
                case 15:chooseOpacity();break;
              }
          }).setNegativeButton("Close",null).create();
        dialog.setOnDismissListener(d->dialogClosed());
        dialogOpened();dialog.show();
    }
    private void openAppSettings(){
        Intent i=new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        i.setData(Uri.parse("package:"+getPackageName()));startActivity(i);
    }
    private void setOriginal(boolean value){
        original=value;getPreferences(0).edit().putBoolean("originalMode",value).apply();build();
    }
    private void applyOrientation(int mode){
        getPreferences(0).edit().putInt("orientationMode",mode).apply();
        setRequestedOrientation(mode==1?ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:
            mode==2?ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }
    private void toggleOrientation(){
        applyOrientation(landscape?1:2);
        handler.postDelayed(()->{
            boolean actualLandscape=getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;
            if(actualLandscape!=landscape)build();
        },450);
    }
    private void setFullscreen(boolean value){
        fullscreen=value;getPreferences(0).edit().putBoolean("fullscreenMode",value).apply();
        if(Build.VERSION.SDK_INT>=30){
            WindowInsetsController ctr=getWindow().getInsetsController();
            if(ctr!=null){if(value)ctr.hide(WindowInsets.Type.statusBars()|WindowInsets.Type.navigationBars());
            else ctr.show(WindowInsets.Type.statusBars()|WindowInsets.Type.navigationBars());
            ctr.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);}
        }else getWindow().getDecorView().setSystemUiVisibility(value?5894:0);
    }
    @Override public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);build();}
    @Override public void onBackPressed(){if(web.canGoBack())web.goBack();else if(original)setOriginal(false);else super.onBackPressed();}
    @Override protected void onResume(){
        super.onResume();
        resumeGameAudio();
        ambientForeground=true;
        if(ambientEnabled)handler.post(this::applyAmbient);
        scheduleAutoHide();
    }
    @Override protected void onPause(){
        pauseGameAudio();
        ambientForeground=false;
        handler.removeCallbacks(ambientTick);
        handler.removeCallbacks(autoHideTask);
        CookieManager.getInstance().flush();
        super.onPause();
    }
    @Override protected void onDestroy(){
        handler.removeCallbacks(clock);
        handler.removeCallbacks(ambientTick);
        handler.removeCallbacks(autoHideTask);
        try{pauseGameAudio();web.stopLoading();web.loadUrl("about:blank");web.destroy();}
        catch(Exception ignored){}
        super.onDestroy();
    }
}
