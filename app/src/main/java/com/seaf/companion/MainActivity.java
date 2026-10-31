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
    private GameWebView web;
    private SeekBar zoomSlider;
    private TextView zoomLabel;
    private boolean updatingZoomUi = false;
    private class GameWebView extends WebView {
        GameWebView(Context ctx){ super(ctx); }
        int maxHorizontal(){return Math.max(0,computeHorizontalScrollRange()-computeHorizontalScrollExtent());}
        void moveHorizontal(float fraction){
            int max=maxHorizontal();
            scrollTo(Math.round(max*Math.max(0f,Math.min(1f,fraction))),getScrollY());
        }
    }
    private boolean landscape;
    private boolean zoomRestored = false;
    private float savedPortraitScale = 0f;
    private int zoomRestoreGeneration = 0;
    private int stableLayoutSamples = 0;
    private String previousLayout = "";
    private boolean restoringProgrammatically = false;
    private final Handler zoomHandler = new Handler(Looper.getMainLooper());
    private final Runnable saveZoom = () -> {
        if(zoomRestored && !landscape && web != null) {
            float scale=web.getScale();
            if(Float.isFinite(scale) && scale>=0.15f && scale<=5f) {
                savedPortraitScale=scale;
                getPreferences(0).edit().putFloat("savedPortraitScale",scale).apply();
            }
        }
    };
    private void waitForGameLayout(int generation,int attempt){
        if(generation!=zoomRestoreGeneration || landscape || zoomRestored || web==null)return;
        // SEAF sizes its widescreen game asynchronously after WebView onPageFinished.
        // Wait for DOM width/height and the prominent game container to stop changing.
        web.evaluateJavascript("(function(){var d=document.documentElement;"+
          "var game=document.querySelector('#page-home')||document.querySelector('.scale-me')||document.body;"+
          "var r=game.getBoundingClientRect();return [d.scrollWidth,d.scrollHeight,"+
          "Math.round(r.width),Math.round(r.height),innerWidth,innerHeight].join(',')})()",
          result->{
              if(generation!=zoomRestoreGeneration || zoomRestored)return;
              String layout=result==null?"":result;
              if(layout.equals(previousLayout) && layout.length()>6)stableLayoutSamples++;
              else {previousLayout=layout;stableLayoutSamples=0;}
              if(stableLayoutSamples>=2 || attempt>=18){
                  restoreZoom(generation);
              }else{
                  zoomHandler.postDelayed(()->waitForGameLayout(generation,attempt+1),350);
              }
          });
    }
    private void restoreZoom(int generation){
        if(generation!=zoomRestoreGeneration || zoomRestored)return;
        if(landscape){zoomRestored=true;return;}
        zoomHandler.removeCallbacks(saveZoom);
        restoringProgrammatically=true;
        if(savedPortraitScale>=0.15f && savedPortraitScale<=3.5f){
            float current=web.getScale();
            if(current>0f && Math.abs(current-savedPortraitScale)>0.015f)
                web.zoomBy(Math.max(0.05f,Math.min(4.99f,savedPortraitScale/current)));
        }
        zoomHandler.postDelayed(()->{
            if(generation!=zoomRestoreGeneration)return;
            centerPortrait();
            restoringProgrammatically=false;
            zoomRestored=true;
            updateZoomUi();
        },300);
    }
    private FrameLayout base;
    private LinearLayout pane;
    private TextView countdown;
    private final Handler tick = new Handler(Looper.getMainLooper());
    private boolean expanded;
    private final Runnable clock = new Runnable(){
        public void run(){updateTimer();tick.postDelayed(this,1000);}
    };
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(0xFF07182C);
        getWindow().setNavigationBarColor(0xFF07182C);
        savedPortraitScale=getPreferences(0).getFloat("savedPortraitScale",0f);
        web=new GameWebView(this);
        WebSettings s=web.getSettings();
        s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);
        s.setUseWideViewPort(true);s.setLoadWithOverviewMode(true);
        s.setSupportZoom(true);s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);
        web.setWebChromeClient(new WebChromeClient());
        web.setInitialScale(0);
        web.setWebViewClient(new WebViewClient(){
            @Override public void onScaleChanged(WebView view,float oldScale,float newScale){
                super.onScaleChanged(view,oldScale,newScale);
                if(!landscape) updateZoomUi();
                if(zoomRestored && !landscape && !restoringProgrammatically) {
                    zoomHandler.removeCallbacks(saveZoom);
                    zoomHandler.postDelayed(saveZoom,450);
                }
            }
            @Override public void onPageStarted(WebView v,String url,android.graphics.Bitmap favicon){
                zoomRestored=false;++zoomRestoreGeneration;stableLayoutSamples=0;previousLayout="";
                zoomHandler.removeCallbacks(saveZoom);
            }
            @Override public void onPageFinished(WebView v,String url){
                final int generation=zoomRestoreGeneration;
                v.postDelayed(()->waitForGameLayout(generation,0),500);
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){
                String scheme=r.getUrl().getScheme();
                if("https".equals(scheme)||"http".equals(scheme))return false;
                try{startActivity(new Intent(Intent.ACTION_VIEW,r.getUrl()));}catch(Exception ignored){}
                return true;
            }
        });
        expanded=getPreferences(0).getBoolean("expanded",true);
        TimerReceiver.ensureChannel(this);
        build();
        tick.post(clock);
        web.loadUrl("https://seaf.se/");
    }
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String value,int size,boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextColor(Color.WHITE);
        t.setTextSize(size);if(bold)t.setTypeface(null,1);t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }
    private TextView control(String title,Runnable task){
        TextView t=text(title,13,true);t.setGravity(Gravity.CENTER);
        t.setBackgroundColor(0xFF243650);t.setPadding(dp(10),0,dp(10),0);
        t.setOnClickListener(v->task.run());return t;
    }
    private void build(){
        landscape=getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;
        zoomSlider=null;zoomLabel=null;
        if(web.getParent()!=null)((android.view.ViewGroup)web.getParent()).removeView(web);
        base=new FrameLayout(this);base.setBackgroundColor(Color.BLACK);
        base.addView(web,new FrameLayout.LayoutParams(-1,-1));
        // The WebView must stay untransformed. Native view scaling crops the
        // hardcoded 16:9 game rather than changing its layout/scroll bounds.
        web.setScaleX(1f);web.setScaleY(1f);
        web.setTranslationX(0f);web.setTranslationY(0f);
        pane=new LinearLayout(this);pane.setOrientation(LinearLayout.VERTICAL);
        pane.setPadding(dp(10),dp(8),dp(10),dp(8));pane.setBackgroundColor(0xF007182C);
        FrameLayout.LayoutParams pos=new FrameLayout.LayoutParams(
          landscape?dp(expanded?260:76):-1,dp(expanded?(landscape?220:332):44),
          landscape?Gravity.RIGHT|Gravity.CENTER_VERTICAL:Gravity.BOTTOM);
        pos.setMargins(landscape?0:dp(8),0,landscape?dp(8):dp(8),landscape?0:dp(8));
        base.addView(pane,pos);
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
        TextView name=text(expanded?"SEAF COMPANION":"SEAF",landscape?13:15,true);
        head.addView(name,new LinearLayout.LayoutParams(0,dp(30),1));
        TextView toggle=control(expanded?"−":"＋",()->{
            expanded=!expanded;getPreferences(0).edit().putBoolean("expanded",expanded).apply();build();
        });
        head.addView(toggle,new LinearLayout.LayoutParams(dp(44),dp(32)));
        pane.addView(head);
        if(expanded){
            countdown=text("No reminder set",landscape?19:22,true);
            pane.addView(countdown,new LinearLayout.LayoutParams(-1,dp(38)));
            pane.addView(text("Local reminder · SEAF sync not connected",11,false));
            LinearLayout actions=new LinearLayout(this);actions.setPadding(0,dp(8),0,0);
            TextView timers=control("Timer",this::showTimer);
            TextView settings=control("Settings",this::showSettings);
            for(TextView v:new TextView[]{timers,settings}){
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(40),1);
                p.setMargins(dp(2),0,dp(2),0);actions.addView(v,p);
            }
            pane.addView(actions);
            if(!landscape) {
                zoomLabel=text("Zoom",12,true);
                pane.addView(zoomLabel,new LinearLayout.LayoutParams(-1,dp(25)));
                LinearLayout zoomRow=new LinearLayout(this);
                zoomRow.setGravity(Gravity.CENTER_VERTICAL);
                zoomRow.addView(control("−",()->changeZoom(-0.12f)),new LinearLayout.LayoutParams(dp(45),dp(38)));
                zoomSlider=new SeekBar(this);
                zoomSlider.setMax(325);
                zoomSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                    public void onProgressChanged(SeekBar bar,int progress,boolean fromUser){
                        if(fromUser && !updatingZoomUi){
                            float target=0.25f+progress/100f;
                            float current=web.getScale();
                            if(current>0f) web.zoomBy(Math.max(0.05f,Math.min(4.99f,target/current)));
                            updateZoomUi();
                        }
                    }
                    public void onStartTrackingTouch(SeekBar bar){}
                    public void onStopTrackingTouch(SeekBar bar){}
                });
                zoomRow.addView(zoomSlider,new LinearLayout.LayoutParams(0,dp(40),1));
                zoomRow.addView(control("+",()->changeZoom(0.12f)),new LinearLayout.LayoutParams(dp(45),dp(38)));
                pane.addView(zoomRow);
                LinearLayout position=new LinearLayout(this);
                position.setPadding(0,dp(3),0,0);
                position.addView(control("←",()->panHorizontal(-0.15f)),new LinearLayout.LayoutParams(0,dp(36),1));
                position.addView(control("Center",this::centerPortrait),new LinearLayout.LayoutParams(0,dp(36),2));
                position.addView(control("→",()->panHorizontal(0.15f)),new LinearLayout.LayoutParams(0,dp(36),1));
                pane.addView(position);
                pane.addView(control("Reset zoom / full view",this::resetZoomToOverview),new LinearLayout.LayoutParams(-1,dp(36)));
                updateZoomUi();
            }
        }
        setContentView(base);updateTimer();
    }
    private void updateZoomUi(){
        if(web==null||landscape)return;
        float scale=web.getScale();
        if(zoomLabel!=null)zoomLabel.setText(String.format(Locale.US,"Zoom  %.0f%%",scale*100));
        if(zoomSlider!=null && !updatingZoomUi){
            updatingZoomUi=true;
            zoomSlider.setProgress(Math.max(0,Math.min(300,Math.round((scale-0.25f)*100))));
            updatingZoomUi=false;
        }
    }
    private void changeZoom(float change){
        float current=web.getScale();
        float target=Math.max(0.25f,Math.min(3.5f,current+change));
        if(current>0f)web.zoomBy(Math.max(0.05f,Math.min(4.99f,target/current)));
        web.postDelayed(this::centerPortrait,180);
        updateZoomUi();
    }
    private void panHorizontal(float delta){
        int max=web.maxHorizontal();
        web.scrollTo(Math.max(0,Math.min(max,web.getScrollX()+Math.round(max*delta))),web.getScrollY());
    }
    private void resetZoomToOverview(){
        savedPortraitScale=0f;
        getPreferences(0).edit().remove("savedPortraitScale").apply();
        zoomRestored=false;
        web.setInitialScale(0);
        web.reload();
    }
    private void centerPortrait(){
        // Recenters the document horizontally without reloading or changing zoom.
        web.post(()->{
            web.moveHorizontal(0.5f);
            web.evaluateJavascript("(function(){var x=Math.max(0,(document.documentElement.scrollWidth-innerWidth)/2); window.scrollTo(x,scrollY);})();",null);
        });
    }
    private void updateTimer(){
        if(countdown==null)return;
        if(!expanded)return;
        long due=getPreferences(0).getLong("timerDue",0);
        long delta=Math.max(0,due-System.currentTimeMillis());
        if(due==0){countdown.setText("No reminder set");return;}
        if(delta==0){countdown.setText("Reminder ready");return;}
        long sec=delta/1000;
        countdown.setText(String.format(Locale.US,"%02d:%02d:%02d",sec/3600,(sec/60)%60,sec%60));
    }
    private void showTimer(){
        LinearLayout form=new LinearLayout(this);form.setPadding(dp(22),dp(8),dp(22),0);
        form.setOrientation(LinearLayout.VERTICAL);
        EditText minutes=new EditText(this);minutes.setInputType(2);minutes.setHint("Minutes until reminder");
        form.addView(minutes);
        new AlertDialog.Builder(this).setTitle("Manual mission reminder")
          .setMessage("This timer is local and is not synchronized with SEAF.")
          .setView(form).setPositiveButton("Set", (d,w)->{
              int n=0;try{n=Integer.parseInt(minutes.getText().toString());}catch(Exception ignored){}
              if(n<1||n>10080){Toast.makeText(this,"Enter 1–10080 minutes",Toast.LENGTH_LONG).show();return;}
              if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)
                  requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},7);
              long due=System.currentTimeMillis()+n*60000L;
              ReminderScheduler.schedule(this,due);
              updateTimer();
          }).setNeutralButton("Cancel existing",(d,w)->{
              ReminderScheduler.cancel(this);updateTimer();
          }).setNegativeButton("Close",null).show();
    }
    private void showSettings(){
        String[] options={"Mission alert sound settings","Battery/background settings","Mobile data settings","Refresh original game"};
        new AlertDialog.Builder(this).setTitle("Companion settings").setItems(options,(d,which)->{
            if(which==0){
                try{Intent i=new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
                    i.putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName());
                    i.putExtra(Settings.EXTRA_CHANNEL_ID,TimerReceiver.CHANNEL_ID);startActivity(i);
                }catch(Exception e){openSettings(Settings.ACTION_APP_NOTIFICATION_SETTINGS,"android.provider.extra.APP_PACKAGE");}
            }
            if(which==1)openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,null);
            if(which==2)openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,null);
            if(which==3)web.reload();
        }).setNegativeButton("Close",null).show();
    }
    private void openSettings(String action,String extra){
        try{
            Intent i=new Intent(action);
            if(extra==null)i.setData(Uri.parse("package:"+getPackageName()));
            else i.putExtra(extra,getPackageName());
            startActivity(i);
        }catch(Exception e){Toast.makeText(this,"Open Android App Info manually",Toast.LENGTH_LONG).show();}
    }
    @Override public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);build();}
    @Override public void onBackPressed(){if(web.canGoBack())web.goBack();else super.onBackPressed();}
    @Override protected void onPause(){CookieManager.getInstance().flush();super.onPause();}
    @Override protected void onDestroy(){tick.removeCallbacks(clock);zoomHandler.removeCallbacks(saveZoom);web.destroy();super.onDestroy();}
}
