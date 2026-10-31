package com.seaf.companion;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.text.DateFormat;
import java.util.Date;

public class MainActivity extends Activity {
    private static final String GAME_URL = "https://seaf.se/";
    private WebView web;
    private LinearLayout root, panel;
    private SessionRecorder recorder;
    private TextView timerStatus;
    private EditText minutes;
    private boolean panelVisible = true;
    private int orientationMode;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xFF07182C);
        getWindow().setNavigationBarColor(0xFF07182C);
        getWindow().getDecorView().setSystemUiVisibility(0);
        web = new WebView(this);
        WebSettings s=web.getSettings();
        s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);
        s.setLoadsImagesAutomatically(true);s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest req){
                Uri u=req.getUrl();String scheme=u.getScheme();
                if("https".equals(scheme)||"http".equals(scheme))return false;
                try{startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception ignored){}
                return true;
            }
        });
        recorder=new SessionRecorder(this,web);
        panelVisible=getPreferences(0).getBoolean("showPanel",true);
        orientationMode=getPreferences(0).getInt("orientation",0);
        setRequestedOrientation(orientationMode==1?ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:
            orientationMode==2?ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        renderLayout();
        web.loadUrl(GAME_URL);
    }
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private TextView label(String value,int size){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);
        t.setTextColor(Color.WHITE);t.setPadding(dp(8),dp(4),dp(8),dp(4));return t;
    }
    private Button action(String text,Runnable callback){
        Button b=new Button(this);b.setText(text);b.setAllCaps(false);
        b.setTextSize(12);b.setMinHeight(0);b.setMinimumHeight(0);
        b.setPadding(dp(6),0,dp(6),0);
        b.setOnClickListener(v->callback.run());return b;
    }
    private void renderLayout(){
        if(web.getParent()!=null)((android.view.ViewGroup)web.getParent()).removeView(web);
        root=new LinearLayout(this);
        boolean landscape=getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;
        root.setOrientation(landscape?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.addView(web,new LinearLayout.LayoutParams(landscape?0:-1,landscape?-1:0,1f));
        panel=new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(0xFF07182C);
        panel.setPadding(dp(6),dp(6),dp(6),dp(4));
        if(landscape)root.addView(panel,new LinearLayout.LayoutParams(dp(235),-1));
        else root.addView(panel,new LinearLayout.LayoutParams(-1,dp(192)));
        if(panelVisible) buildPanel(landscape);
        else panel.addView(action("Show Companion",()->{panelVisible=true;getPreferences(0).edit().putBoolean("showPanel",true).apply();renderLayout();}));
        if(!panelVisible){
            LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)panel.getLayoutParams();
            if(landscape)p.width=dp(110);else p.height=dp(48);
            panel.setLayoutParams(p);
        }
        setContentView(root);
    }
    private void buildPanel(boolean landscape){
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand=label("SEAF COMPANION",14);brand.setTypeface(null,1);
        header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        header.addView(action("Hide",()->{panelVisible=false;getPreferences(0).edit().putBoolean("showPanel",false).apply();renderLayout();}),
            new LinearLayout.LayoutParams(dp(68),dp(38)));
        panel.addView(header);
        panel.addView(label("Game status: Live sync not connected",11));
        timerStatus=label("",12);panel.addView(timerStatus);refreshTimer();
        LinearLayout controls=new LinearLayout(this);
        controls.addView(action("Refresh",()->web.reload()),new LinearLayout.LayoutParams(0,dp(40),1));
        controls.addView(action(recorder.isRecording()?"Stop + ZIP":"Capture",()->{
            if(recorder.isRecording())recorder.stop();else recorder.start();
            renderLayout();
        }),new LinearLayout.LayoutParams(0,dp(40),1));
        controls.addView(action("Rotate",()->{
            int mode=getResources().getConfiguration().orientation==Configuration.ORIENTATION_PORTRAIT?2:1;
            getPreferences(0).edit().putInt("orientation",mode).apply();
            setRequestedOrientation(mode==1?ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }),new LinearLayout.LayoutParams(0,dp(40),1));
        panel.addView(controls);
        LinearLayout timerRow=new LinearLayout(this);timerRow.setGravity(Gravity.CENTER_VERTICAL);
        minutes=new EditText(this);minutes.setSingleLine(true);minutes.setInputType(2);
        minutes.setTextColor(Color.WHITE);minutes.setHintTextColor(0xFFB0B9C4);
        minutes.setHint("Minutes");minutes.setTextSize(14);
        timerRow.addView(minutes,new LinearLayout.LayoutParams(0,dp(42),1));
        timerRow.addView(action("Set reminder",this::setTimer),new LinearLayout.LayoutParams(dp(128),dp(42)));
        panel.addView(timerRow);
        if(landscape){
            panel.addView(label("Reminders are manual; original game remains fully available.",11));
        }
    }
    private void refreshTimer(){
        long at=getPreferences(0).getLong("timerDue",0);
        timerStatus.setText(at==0?"No timer set":("Reminder: "+DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(at))));
    }
    private void setTimer(){
        int amount;
        try{amount=Integer.parseInt(minutes.getText().toString());}catch(Exception e){amount=0;}
        if(amount<1||amount>10080){Toast.makeText(this,"Enter 1–10080 minutes",Toast.LENGTH_SHORT).show();return;}
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},5);
        long at=System.currentTimeMillis()+amount*60000L;
        Intent intent=new Intent(this,TimerReceiver.class).putExtra("label","SEAF timer");
        PendingIntent pi=PendingIntent.getBroadcast(this,1001,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        ((AlarmManager)getSystemService(Context.ALARM_SERVICE)).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi);
        getPreferences(0).edit().putLong("timerDue",at).apply();refreshTimer();
        Toast.makeText(this,"Local reminder scheduled",Toast.LENGTH_SHORT).show();
    }
    @Override public void onConfigurationChanged(Configuration config){
        super.onConfigurationChanged(config);renderLayout();
    }
    @Override public void onBackPressed(){
        if(!panelVisible){panelVisible=true;getPreferences(0).edit().putBoolean("showPanel",true).apply();renderLayout();}
        else if(web.canGoBack())web.goBack();else super.onBackPressed();
    }
    @Override protected void onPause(){CookieManager.getInstance().flush();super.onPause();}
    @Override protected void onDestroy(){recorder.close();if(web.getParent()!=null)((android.view.ViewGroup)web.getParent()).removeView(web);web.destroy();super.onDestroy();}
}
