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
    private FrameLayout base;
    private LinearLayout pane;
    private TextView countdown;
    private FrameLayout zoomPanel;
    private float portraitZoom = 1.65f;
    private GestureDetector doubleTapDetector;
    private final Handler tick = new Handler(Looper.getMainLooper());
    private boolean expanded;
    private boolean landscape;
    private final Runnable clock = new Runnable(){
        public void run(){updateTimer();tick.postDelayed(this,1000);}
    };
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(0xFF07182C);
        getWindow().setNavigationBarColor(0xFF07182C);
        web=new WebView(this);
        WebSettings s=web.getSettings();
        s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);
        s.setUseWideViewPort(true);s.setLoadWithOverviewMode(true);
        s.setSupportZoom(true);s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);
        web.setWebChromeClient(new WebChromeClient());
        portraitZoom=getPreferences(0).getFloat("portraitZoom",1.65f);
        doubleTapDetector=new GestureDetector(this,new GestureDetector.SimpleOnGestureListener(){
            @Override public boolean onDown(MotionEvent e){return true;}
            @Override public boolean onDoubleTap(MotionEvent e){
                if(!landscape){
                    portraitZoom=portraitZoom>1.8f?1.0f:1.85f;
                    setPortraitZoom(portraitZoom);
                    syncZoomSlider();
                    return true;
                }
                return false;
            }
        });
        web.setOnTouchListener((v,event)->{
            if(!landscape && event.getPointerCount()==1 && doubleTapDetector.onTouchEvent(event)
                    && event.getActionMasked()==MotionEvent.ACTION_UP) return true;
            return false;
        });
        web.setWebViewClient(new WebViewClient(){
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
        if(web.getParent()!=null)((android.view.ViewGroup)web.getParent()).removeView(web);
        base=new FrameLayout(this);base.setBackgroundColor(Color.BLACK);
        base.addView(web,new FrameLayout.LayoutParams(-1,-1));
        setPortraitZoom(landscape?1f:portraitZoom);
        zoomPanel=null;
        if(!landscape) addZoomControl();
        pane=new LinearLayout(this);pane.setOrientation(LinearLayout.VERTICAL);
        pane.setPadding(dp(10),dp(8),dp(10),dp(8));pane.setBackgroundColor(0xF007182C);
        FrameLayout.LayoutParams pos=new FrameLayout.LayoutParams(
          landscape?dp(expanded?260:76):-1,dp(expanded?(landscape?220:208):44),
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
        }
        setContentView(base);updateTimer();
    }
    private SeekBar zoomSlider;
    private void setPortraitZoom(float value){
        if(web==null)return;
        float z=landscape?1f:Math.max(1f,Math.min(3f,value));
        if(!landscape){portraitZoom=z;getPreferences(0).edit().putFloat("portraitZoom",z).apply();}
        web.setPivotX(web.getWidth()/2f);
        web.setPivotY(web.getHeight()/2f);
        web.setScaleX(z);web.setScaleY(z);
        web.post(()->{
            web.setPivotX(web.getWidth()/2f);web.setPivotY(web.getHeight()/2f);
        });
    }
    private void syncZoomSlider(){
        if(zoomSlider!=null)zoomSlider.setProgress(Math.round((portraitZoom-1f)*100));
    }
    private void addZoomControl(){
        TextView fab=control("⌕ +",()->{
            zoomPanel.setVisibility(zoomPanel.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);
        });
        FrameLayout.LayoutParams fabPos=new FrameLayout.LayoutParams(dp(60),dp(44),Gravity.BOTTOM|Gravity.RIGHT);
        fabPos.setMargins(0,0,dp(12),dp(62));
        base.addView(fab,fabPos);
        zoomPanel=new FrameLayout(this);
        zoomPanel.setBackgroundColor(0xF007182C);
        FrameLayout.LayoutParams zPos=new FrameLayout.LayoutParams(-1,dp(58),Gravity.BOTTOM);
        zPos.setMargins(dp(12),0,dp(12),dp(112));
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView minus=control("−",()->setPortraitZoom(portraitZoom-0.15f));
        TextView plus=control("+",()->setPortraitZoom(portraitZoom+0.15f));
        TextView reset=control("Fit",()->setPortraitZoom(1.65f));
        zoomSlider=new SeekBar(this);zoomSlider.setMax(200);syncZoomSlider();
        zoomSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int p,boolean fromUser){
                if(fromUser)setPortraitZoom(1f+p/100f);
            }
            public void onStartTrackingTouch(SeekBar b){}
            public void onStopTrackingTouch(SeekBar b){}
        });
        bar.addView(minus,new LinearLayout.LayoutParams(dp(42),dp(46)));
        bar.addView(zoomSlider,new LinearLayout.LayoutParams(0,dp(46),1));
        bar.addView(plus,new LinearLayout.LayoutParams(dp(42),dp(46)));
        bar.addView(reset,new LinearLayout.LayoutParams(dp(52),dp(46)));
        zoomPanel.addView(bar,new FrameLayout.LayoutParams(-1,-1));
        zoomPanel.setVisibility(View.GONE);
        base.addView(zoomPanel,zPos);
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
    @Override protected void onDestroy(){tick.removeCallbacks(clock);web.destroy();super.onDestroy();}
}
