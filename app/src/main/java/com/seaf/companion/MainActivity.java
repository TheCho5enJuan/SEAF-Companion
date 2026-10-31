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
    private TextView countdown, zoomLabel, viewLabel, zoomToggle, timerButton;
    private SeekBar slider;
    private boolean expanded,zoomExpanded,original,fullscreen,landscape;
    private boolean changingSlider=false;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable clock=new Runnable(){public void run(){updateTimer();handler.postDelayed(this,1000);}};
    private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(0xFF07182C);getWindow().setNavigationBarColor(0xFF07182C);
        expanded=getPreferences(0).getBoolean("expanded",false);
        zoomExpanded=getPreferences(0).getBoolean("zoomExpanded",false);
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
    private void build(){
        landscape=getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;
        slider=null;zoomLabel=null;viewLabel=null;countdown=null;zoomToggle=null;timerButton=null;
        if(viewport.getParent()!=null)((android.view.ViewGroup)viewport.getParent()).removeView(viewport);
        root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);
        root.addView(viewport,new FrameLayout.LayoutParams(-1,-1));
        viewport.setModes(landscape,original,(!landscape&&!original)?(expanded?220:52):0);
        if(!original){
            panel=new LinearLayout(this);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setPadding(dp(10),dp(7),dp(10),dp(7));
            panel.setBackgroundColor(0xEF07182C);
            FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(
                landscape?dp(expanded?265:72):-1,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                landscape?Gravity.RIGHT|Gravity.CENTER_VERTICAL:Gravity.BOTTOM);
            p.setMargins(dp(8),0,dp(8),dp(8));
            root.addView(panel,p);
            panel.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or_,ob)->{
                if(!landscape&&!original && (b-t)!=(ob-ot)){
                    float density=getResources().getDisplayMetrics().density;
                    viewport.setReservedBottom(Math.round((b-t+dp(8))/density));
                }
            });
            LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
            header.addView(label(expanded?"SEAF COMPANION":"SEAF",14,true),new LinearLayout.LayoutParams(0,dp(34),1));
            header.addView(button(expanded?"−":"+",()->{
                expanded=!expanded;getPreferences(0).edit().putBoolean("expanded",expanded).apply();build();
            }),new LinearLayout.LayoutParams(dp(48),dp(34)));
            panel.addView(header);
            if(expanded){
                countdown=label("No reminder",21,true);
                panel.addView(countdown,new LinearLayout.LayoutParams(-1,dp(37)));
                panel.addView(label("Local timer • Game state not synchronized",11,false));
                LinearLayout actions=new LinearLayout(this);
                timerButton=button("Timers",this::showTimer);
                actions.addView(timerButton,new LinearLayout.LayoutParams(0,dp(37),1));
                actions.addView(button("Settings",this::showSettings),new LinearLayout.LayoutParams(0,dp(37),1));
                panel.addView(actions);
                if(landscape) {
                    LinearLayout orientation=new LinearLayout(this);
                    orientation.addView(button("Portrait",()->applyOrientation(1)),
                        new LinearLayout.LayoutParams(0,dp(38),1));
                    orientation.addView(button("Rotate",this::toggleOrientation),
                        new LinearLayout.LayoutParams(0,dp(38),1));
                    panel.addView(orientation);
                }
                if(!landscape){
                    zoomToggle=button("Zoom ▾",()->{
                        zoomExpanded=!zoomExpanded;
                        getPreferences(0).edit().putBoolean("zoomExpanded",zoomExpanded).apply();
                        build();
                    });
                    panel.addView(zoomToggle,new LinearLayout.LayoutParams(-1,dp(36)));
                    if(zoomExpanded){
                    viewLabel=label("View",12,true);
                    panel.addView(viewLabel,new LinearLayout.LayoutParams(-1,dp(20)));
                    LinearLayout presets=new LinearLayout(this);
                    presets.addView(button("Left",()->{viewport.selectRegion(GameViewport.LEFT);updateZoom();}),
                        new LinearLayout.LayoutParams(0,dp(36),1));
                    presets.addView(button("Center",()->{viewport.selectRegion(GameViewport.CENTER);updateZoom();}),
                        new LinearLayout.LayoutParams(0,dp(36),1));
                    presets.addView(button("Right",()->{viewport.selectRegion(GameViewport.RIGHT);updateZoom();}),
                        new LinearLayout.LayoutParams(0,dp(36),1));
                    panel.addView(presets);
                    zoomLabel=label("Zoom 100%",12,true);
                    panel.addView(zoomLabel,new LinearLayout.LayoutParams(-1,dp(25)));
                    LinearLayout zr=new LinearLayout(this);
                    zr.addView(button("−",()->viewport.adjustZoom(-0.1f)),new LinearLayout.LayoutParams(dp(42),dp(38)));
                    slider=new SeekBar(this);slider.setMax(270);
                    slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                        public void onProgressChanged(SeekBar b,int progress,boolean user){
                            if(user&&!changingSlider)viewport.setZoom(.30f+progress/100f);
                        }
                        public void onStartTrackingTouch(SeekBar b){}
                        public void onStopTrackingTouch(SeekBar b){}
                    });
                    zr.addView(slider,new LinearLayout.LayoutParams(0,dp(38),1));
                    zr.addView(button("+",()->viewport.adjustZoom(0.1f)),new LinearLayout.LayoutParams(dp(42),dp(38)));
                    panel.addView(zr);
                    LinearLayout pr=new LinearLayout(this);
                    pr.addView(button("←",()->viewport.pan(-45,0)),new LinearLayout.LayoutParams(0,dp(35),1));
                    pr.addView(button("Recenter",viewport::center),new LinearLayout.LayoutParams(0,dp(35),2));
                    pr.addView(button("→",()->viewport.pan(45,0)),new LinearLayout.LayoutParams(0,dp(35),1));
                    panel.addView(pr);
                    }
                    LinearLayout nav=new LinearLayout(this);
                    nav.addView(button("Overview",()->{viewport.showOverview();updateZoom();}),new LinearLayout.LayoutParams(0,dp(35),1));
                    nav.addView(button("Rotate",this::toggleOrientation),new LinearLayout.LayoutParams(0,dp(35),1));
                    nav.addView(button("Fullscreen",()->setFullscreen(!fullscreen)),new LinearLayout.LayoutParams(0,dp(35),1));
                    panel.addView(nav);
                }
            }
            if(landscape&&!expanded){
                TextView returnPortrait=button("↻",()->applyOrientation(1));
                FrameLayout.LayoutParams rp=new FrameLayout.LayoutParams(dp(46),dp(46),Gravity.TOP|Gravity.RIGHT);
                rp.setMargins(0,dp(10),dp(10),0);
                root.addView(returnPortrait,rp);
            }
        }else{
            TextView restore=button("Companion",()->setOriginal(false));
            FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(dp(105),dp(42),Gravity.RIGHT|Gravity.BOTTOM);
            p.setMargins(0,0,dp(12),dp(20));root.addView(restore,p);
        }
        setContentView(root);updateZoom();updateTimer();
    }
    private void updateZoom(){
        if(viewport==null)return;
        float zoom=viewport.getZoom();
        if(zoomLabel!=null)zoomLabel.setText(String.format(Locale.US,"Zoom %.0f%%",zoom*100));
        if(zoomToggle!=null)zoomToggle.setText(String.format(Locale.US,"Zoom %s  %.0f%%",
            zoomExpanded?"▴":"▾",zoom*100));
        if(viewLabel!=null){
            String region=viewport.getFocusRegion()==GameViewport.LEFT?"Left":viewport.getFocusRegion()==GameViewport.RIGHT?"Right":"Center";
            viewLabel.setText(viewport.isHeightFit()?"Height-fit • "+region+" third":"Overview • entire game");
        }
        if(slider!=null){
            changingSlider=true;slider.setProgress(Math.round((zoom-.30f)*100));changingSlider=false;
        }
    }
    private void updateTimer(){
        if(countdown==null)return;
        long due=getPreferences(0).getLong("timerDue",0);
        if(due==0){countdown.setText("No reminder");return;}
        long left=Math.max(0,due-System.currentTimeMillis())/1000;
        if(left==0){countdown.setText("Reminder ready");return;}
        countdown.setText(String.format(Locale.US,"%02d:%02d:%02d",left/3600,left/60%60,left%60));
    }
    private void showTimer(){
        EditText field=new EditText(this);field.setInputType(2);field.setHint("Minutes");
        new AlertDialog.Builder(this).setTitle("Manual mission reminder")
            .setMessage("Reminders run independently of the game.")
            .setView(field).setPositiveButton("Set",(d,w)->{
                int n=0;try{n=Integer.parseInt(field.getText().toString());}catch(Exception ignored){}
                if(n<1||n>10080){Toast.makeText(this,"Enter 1–10080 minutes",Toast.LENGTH_LONG).show();return;}
                if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)
                    requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},7);
                ReminderScheduler.schedule(this,System.currentTimeMillis()+n*60000L);updateTimer();
            }).setNeutralButton("Cancel reminder",(d,w)->{ReminderScheduler.cancel(this);updateTimer();})
            .setNegativeButton("Close",null).show();
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
