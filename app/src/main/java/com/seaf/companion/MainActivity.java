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
    private TextView countdown, timerButton, bellBadge;
    private final CompanionChrome.Icon[] viewButtons=new CompanionChrome.Icon[4];
    private boolean adjustingPanel=false;
    private boolean expanded,original,fullscreen,landscape;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable clock=new Runnable(){public void run(){updateTimer();handler.postDelayed(this,1000);}};
    private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(0xFF07182C);getWindow().setNavigationBarColor(0xFF07182C);
        expanded=getPreferences(0).getBoolean("expanded",false);
        original=getPreferences(0).getBoolean("originalMode",false);
        fullscreen=getPreferences(0).getBoolean("fullscreenMode",false);
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
            }
            @Override public void onPageFinished(WebView v,String url){
                if(viewport!=null)viewport.onPageFinished();
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
    private void installDragHandle(LinearLayout parent){
        CompanionChrome.Icon handle=new CompanionChrome.Icon(this,"handle");
        handle.setContentDescription("Drag to move Companion");
        handle.setBackground(CompanionChrome.glass(getResources().getDisplayMetrics().density,false));
        parent.addView(handle,new LinearLayout.LayoutParams(dp(30),dp(46)));
        final float[] initial={0,0};
        final float[] translation={0,0};
        handle.setOnTouchListener((v,e)->{
            if(landscape)return false;
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    initial[0]=e.getRawX();initial[1]=e.getRawY();
                    translation[0]=panel.getTranslationX();translation[1]=panel.getTranslationY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx=e.getRawX()-initial[0],dy=e.getRawY()-initial[1];
                    // Full-width portrait bar: restrict movement to the vertical axis.
                    panel.setTranslationY(Math.max(-availablePanelTravel(),Math.min(0,translation[1]+dy)));
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    getPreferences(0).edit().putFloat("portraitBarOffsetDp",
                        panel.getTranslationY()/getResources().getDisplayMetrics().density).apply();
                    return true;
            }
            return false;
        });
    }
    private float availablePanelTravel(){
        if(root==null||panel==null)return 0;
        // Leave the bar below the system status area and inside the screen.
        return Math.max(0,root.getHeight()-panel.getHeight()-dp(48));
    }
    private void updatePanelPosition(){
        if(landscape||panel==null||root==null)return;
        float saved=getPreferences(0).getFloat("portraitBarOffsetDp",0);
        panel.setTranslationY(Math.max(-availablePanelTravel(),Math.min(0,dp(Math.round(saved)))));
    }
    private void build(){
        landscape=getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;
        countdown=null;timerButton=null;bellBadge=null;
        java.util.Arrays.fill(viewButtons,null);
        if(viewport.getParent()!=null)((android.view.ViewGroup)viewport.getParent()).removeView(viewport);
        root=new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.addView(viewport,new FrameLayout.LayoutParams(-1,-1));
        viewport.setModes(landscape,original,(!landscape&&!original)?(expanded?162:66):0);
        if(!original){
            panel=new LinearLayout(this);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setPadding(dp(6),dp(6),dp(6),dp(6));
            panel.setBackground(CompanionChrome.panel(getResources().getDisplayMetrics().density));
            panel.setElevation(dp(10));
            FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(
                landscape?dp(expanded?270:88):-1,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                landscape?Gravity.RIGHT|Gravity.CENTER_VERTICAL:Gravity.BOTTOM);
            params.setMargins(dp(9),0,dp(9),dp(10));
            root.addView(panel,params);
            panel.addOnLayoutChangeListener((v,l,t,r,bottom,ol,ot,or_,ob)->{
                if(!landscape&&!original){
                    root.post(()->{
                        updatePanelPosition();
                        // Floating HUD does not permanently shrink the game canvas.
                        // Docked HUD leaves enough space for its actual height.
                        boolean docked=Math.abs(panel.getTranslationY())<dp(24);
                        viewport.setReservedBottom(docked?
                            Math.round((panel.getHeight()+dp(16))/getResources().getDisplayMetrics().density):0);
                    });
                }
            });
            LinearLayout bar=new LinearLayout(this);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            if(!landscape){
                String[] kinds={"left","center","right","overview"};
                String[] names={"Left view","Center view","Right view","Full overview"};
                for(int i=0;i<4;i++){
                    final int selection=i;
                    CompanionChrome.Icon icon=iconButton(kinds[i],names[i],()->selectView(selection));
                    addIcon(bar,icon,1);
                    viewButtons[i]=icon;
                }
                View divider=new View(this);divider.setBackgroundColor(0x6682C7E8);
                LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(dp(1),dp(29));
                dl.setMargins(dp(4),0,dp(4),0);bar.addView(divider,dl);
                FrameLayout bell=new FrameLayout(this);
                CompanionChrome.Icon bellIcon=iconButton("bell","Mission reminder",this::showTimer);
                bell.addView(bellIcon,new FrameLayout.LayoutParams(-1,-1));
                bellBadge=label("",10,true);bellBadge.setTextColor(Color.BLACK);
                bellBadge.setGravity(Gravity.CENTER);bellBadge.setBackgroundColor(CompanionChrome.GOLD);
                FrameLayout.LayoutParams badgeParams=new FrameLayout.LayoutParams(dp(17),dp(17),Gravity.RIGHT|Gravity.TOP);
                bell.addView(bellBadge,badgeParams);
                LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,dp(46),1);
                bp.setMargins(dp(2),0,dp(2),0);bar.addView(bell,bp);
                addIcon(bar,iconButton("settings","Settings",this::showSettings),1);
                addIcon(bar,iconButton("rotate","Rotate screen",this::toggleOrientation),1);
                View separator=new View(this);separator.setBackgroundColor(0x6682C7E8);
                LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(1),dp(29));
                sp.setMargins(dp(2),0,dp(2),0);bar.addView(separator,sp);
                addIcon(bar,iconButton(expanded?"collapse":"expand",
                    expanded?"Collapse Companion":"Expand Companion",()->{
                        expanded=!expanded;
                        getPreferences(0).edit().putBoolean("expanded",expanded).apply();build();
                    }),1);
                installDragHandle(bar);
            }else{
                TextView name=label(expanded?"SEAF COMPANION":"SEAF",13,true);
                bar.addView(name,new LinearLayout.LayoutParams(0,dp(46),1));
                addIcon(bar,iconButton("rotate","Switch to portrait",()->applyOrientation(1)),1);
                addIcon(bar,iconButton("settings","Settings",this::showSettings),1);
                addIcon(bar,iconButton(expanded?"collapse":"expand",
                    expanded?"Collapse":"Expand",()->{
                        expanded=!expanded;getPreferences(0).edit().putBoolean("expanded",expanded).apply();build();
                    }),1);
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
                panel.addView(actions);
            }
            if(landscape&&!expanded){
                // Always-available orientation escape for any landscape lock.
                CompanionChrome.Icon back=iconButton("rotate","Return to portrait",()->applyOrientation(1));
                FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(dp(45),dp(45),Gravity.RIGHT|Gravity.TOP);
                p.setMargins(0,dp(8),dp(8),0);root.addView(back,p);
            }
        }else{
            TextView restore=button("Companion",()->setOriginal(false));
            restore.setBackground(CompanionChrome.glass(getResources().getDisplayMetrics().density,false));
            FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(dp(116),dp(43),Gravity.RIGHT|Gravity.BOTTOM);
            p.setMargins(0,0,dp(12),dp(20));root.addView(restore,p);
        }
        setContentView(root);
        if(!landscape&&!original)root.post(this::updatePanelPosition);
        updateViewButtons();updateTimer();
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
    private void showTimer(){
        ScrollView scroll=new ScrollView(this);
        LinearLayout body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18),dp(10),dp(18),dp(6));
        scroll.addView(body);
        body.addView(dialogLabel("Quick reminders (manual, not synced to SEAF)",13));
        RadioGroup type=new RadioGroup(this);
        type.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton mission=new RadioButton(this);mission.setText("Mission");
        mission.setTextColor(0xFF25354A);mission.setId(View.generateViewId());
        RadioButton care=new RadioButton(this);care.setText("Care / Minigame");
        care.setTextColor(0xFF25354A);care.setId(View.generateViewId());
        type.addView(mission);type.addView(care);
        type.check(mission.getId());
        body.addView(type);
        final AlertDialog[] current={null};
        int[][] durations={{5,15},{30,60}};
        for(int[] group:durations){
            LinearLayout row=new LinearLayout(this);
            row.setPadding(0,dp(3),0,dp(3));
            for(int minutes:group){
                TextView quick=button(minutes+" min",()->startQuickTimer(
                    care.isChecked()?"Care":"Mission",minutes,current[0]));
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(42),1);
                p.setMargins(dp(2),0,dp(2),0);
                row.addView(quick,p);
            }
            body.addView(row);
        }
        LinearLayout custom=new LinearLayout(this);
        custom.setGravity(Gravity.CENTER_VERTICAL);
        EditText minutes=new EditText(this);
        minutes.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        minutes.setSingleLine(true);
        minutes.setHint("Custom minutes");
        minutes.setTextColor(0xFF25354A);
        custom.addView(minutes,new LinearLayout.LayoutParams(0,dp(54),1));
        TextView add=button("Start",()->{
            int value=0;
            try{value=Integer.parseInt(minutes.getText().toString().trim());}
            catch(NumberFormatException ignored){}
            startQuickTimer(care.isChecked()?"Care":"Mission",value,current[0]);
        });
        custom.addView(add,new LinearLayout.LayoutParams(dp(86),dp(42)));
        body.addView(custom);
        body.addView(dialogLabel("Current reminder (selecting a preset replaces it)",17));
        java.util.ArrayList<ReminderScheduler.Reminder> active=ReminderScheduler.list(this);
        if(active.isEmpty())body.addView(dialogLabel("None yet",13));
        for(ReminderScheduler.Reminder r:active){
            LinearLayout row=new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            long remaining=Math.max(0,r.due-System.currentTimeMillis())/1000;
            String duration=String.format(Locale.US,"%dh %02dm",remaining/3600,(remaining/60)%60);
            TextView name=dialogLabel(r.label+"   ·   "+duration+" left",13);
            row.addView(name,new LinearLayout.LayoutParams(0,dp(46),1));
            TextView cancel=button("Cancel",()->{
                ReminderScheduler.cancel(this,r.id);
                updateTimer();
                if(current[0]!=null)current[0].dismiss();
                showTimer();
            });
            row.addView(cancel,new LinearLayout.LayoutParams(dp(84),dp(38)));
            body.addView(row);
        }
        body.addView(dialogLabel("Android may delay alerts when exact alarms are restricted. "+
            "You can change the alert sound in Companion Settings.",12));
        current[0]=new AlertDialog.Builder(this)
            .setTitle("SEAF Reminder")
            .setView(scroll)
            .setNegativeButton("Close",null)
            .create();
        current[0].show();
    }
    private void showSettings(){
        String[] opts={"Original game mode","Auto rotate","Lock portrait","Lock landscape",
            "Toggle fullscreen","Remember zoom: "+(getPreferences(0).getBoolean("rememberZoom",true)?"On":"Off"),
            "Auto center: "+(getPreferences(0).getBoolean("autoCenter",true)?"On":"Off"),
            "Mission notification sound","Android battery settings","Refresh game","Open game in browser"};
        new AlertDialog.Builder(this).setTitle("SEAF Companion Settings")
          .setItems(opts,(d,which)->{
              switch(which){
                case 0:setOriginal(true);break;
                case 1:applyOrientation(0);break;
                case 2:applyOrientation(1);break;
                case 3:applyOrientation(2);break;
                case 4:setFullscreen(!fullscreen);break;
                case 5:viewport.setRememberZoom(!getPreferences(0).getBoolean("rememberZoom",true));break;
                case 6:viewport.setAutoCenter(!getPreferences(0).getBoolean("autoCenter",true));break;
                case 7:try{
                    Intent i=new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
                    i.putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName());
                    i.putExtra(Settings.EXTRA_CHANNEL_ID,TimerReceiver.CHANNEL_ID);startActivity(i);
                }catch(Exception ex){openAppSettings();}break;
                case 8:openAppSettings();break;
                case 9:web.reload();break;
                case 10:startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(web.getUrl()==null?"https://seaf.se/":web.getUrl())));break;
              }
          }).setNegativeButton("Close",null).show();
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
    @Override protected void onPause(){CookieManager.getInstance().flush();super.onPause();}
    @Override protected void onDestroy(){handler.removeCallbacks(clock);web.destroy();super.onDestroy();}
}
