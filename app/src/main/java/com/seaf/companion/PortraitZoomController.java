package com.seaf.companion;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Color;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * Portrait-only, reversible viewport magnification. The entire live WebView remains
 * interactive; its native view transform is centered and never edits game DOM/state.
 */
public final class PortraitZoomController {
    private static final float MIN = 1f, MAX = 5f;
    private final Activity activity;
    private final WebView web;
    private final FrameLayout container;
    private final ScaleGestureDetector scale;
    private final GestureDetector gestures;
    private FrameLayout controls;
    private SeekBar slider;
    private float zoom = 1f;
    private boolean userAdjusted;
    private boolean portrait;
    private boolean sliderOpen = false;

    public PortraitZoomController(Activity activity, WebView web, FrameLayout container) {
        this.activity=activity;this.web=web;this.container=container;
        scale=new ScaleGestureDetector(activity,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScale(ScaleGestureDetector detector){
                if(!portrait)return false;
                setZoom(zoom*detector.getScaleFactor(),true);
                return true;
            }
        });
        gestures=new GestureDetector(activity,new GestureDetector.SimpleOnGestureListener(){
            @Override public boolean onDown(MotionEvent e){return true;}
            @Override public boolean onDoubleTap(MotionEvent e){
                if(!portrait)return false;
                resetToCenterFill();return true;
            }
        });
        web.setOnTouchListener((v,event)->{
            if(!portrait)return false;
            scale.onTouchEvent(event);
            gestures.onTouchEvent(event);
            return scale.isInProgress();
        });
    }
    private int dp(int n){return (int)(n*activity.getResources().getDisplayMetrics().density+.5f);}
    private TextView button(String title, Runnable action){
        TextView t=new TextView(activity);t.setText(title);t.setTextColor(Color.WHITE);
        t.setTextSize(14);t.setGravity(Gravity.CENTER);
        t.setBackgroundColor(0xEE243650);t.setPadding(dp(8),dp(3),dp(8),dp(3));
        t.setOnClickListener(v->action.run());return t;
    }
    public void onLayout(boolean portrait, int companionHeightDp){
        this.portrait=portrait;
        if(controls!=null){container.removeView(controls);controls=null;}
        if(!portrait){
            web.setScaleX(1);web.setScaleY(1);return;
        }
        if(!userAdjusted)resetToCenterFill();
        else apply();
        controls=new FrameLayout(activity);
        FrameLayout.LayoutParams outer=new FrameLayout.LayoutParams(-1,dp(sliderOpen?72:44),
            Gravity.BOTTOM);
        outer.leftMargin=dp(12);outer.rightMargin=dp(12);
        outer.bottomMargin=dp(companionHeightDp+12);
        container.addView(controls,outer);
        TextView fab=button(sliderOpen?"Hide zoom":"Zoom",()->{
            sliderOpen=!sliderOpen;
            onLayout(true,companionHeightDp);
        });
        FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(dp(96),dp(40),Gravity.BOTTOM|Gravity.LEFT);
        controls.addView(fab,fp);
        if(sliderOpen){
            LinearLayout bar=new LinearLayout(activity);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            bar.setBackgroundColor(0xEE07182C);
            slider=new SeekBar(activity);
            slider.setMax(400);
            slider.setProgress(Math.round((zoom-1)*100));
            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                public void onProgressChanged(SeekBar s,int progress,boolean fromUser){
                    if(fromUser)setZoom(1+progress/100f,true);
                }
                public void onStartTrackingTouch(SeekBar s){}
                public void onStopTrackingTouch(SeekBar s){}
            });
            bar.addView(slider,new LinearLayout.LayoutParams(0,dp(42),1));
            TextView reset=button("Fill",this::resetToCenterFill);
            bar.addView(reset,new LinearLayout.LayoutParams(dp(52),dp(38)));
            FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(-1,dp(44),Gravity.TOP);
            controls.addView(bar,bp);
        }
    }
    private float fill(){
        float w=Math.max(1,container.getWidth());
        float h=Math.max(1,container.getHeight());
        // SEAF's captured game viewport is fundamentally 16:9.
        return Math.max(1f,Math.min(MAX,h/(w*9f/16f)));
    }
    public void resetToCenterFill(){userAdjusted=false;setZoom(fill(),false);}
    private void setZoom(float z,boolean changed){
        zoom=Math.max(MIN,Math.min(MAX,z));
        if(changed)userAdjusted=true;
        apply();
        if(slider!=null)slider.setProgress(Math.round((zoom-1)*100));
    }
    private void apply(){
        if(!portrait)return;
        web.setPivotX(web.getWidth()/2f);web.setPivotY(web.getHeight()/2f);
        web.setScaleX(zoom);web.setScaleY(zoom);
    }
}
