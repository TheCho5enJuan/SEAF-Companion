package com.seaf.companion;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.FrameLayout;

/**
 * Keeps SEAF's fixed widescreen page at a stable viewport size in portrait.
 * Magnification is applied to the complete interactive view, rather than to
 * WebView's internal text/page zoom. Original mode and landscape are unmodified.
 */
public final class GameViewport extends FrameLayout {
    public interface ZoomListener { void changed(float zoom); }
    private static final int DESKTOP_WIDTH_DP = 1600;
    private static final int DESKTOP_HEIGHT_DP = 900;
    private static final float MIN_ZOOM = 0.75f, MAX_ZOOM = 3f;
    private final WebView web;
    private final SharedPreferences preferences;
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector doubleTap;
    private ZoomListener listener;
    private boolean originalMode;
    private boolean landscape;
    private boolean rememberZoom;
    private boolean autoCenter;
    private float zoom = 1f;
    private float offsetX = 0f, offsetY = 0f;
    private int reservedBottom = 0;
    private float fittedScale = 1f;
    private float lastFocusX, lastFocusY;
    private boolean multiTouch = false;

    public GameViewport(Activity activity, WebView view) {
        super(activity);
        web=view;
        setClipChildren(true);
        setClipToPadding(true);
        preferences=activity.getPreferences(0);
        rememberZoom=preferences.getBoolean("rememberZoom",true);
        autoCenter=preferences.getBoolean("autoCenter",true);
        zoom=rememberZoom?preferences.getFloat("companionZoomV3",1f):1f;
        zoom=Math.max(MIN_ZOOM,Math.min(MAX_ZOOM,zoom));
        scaleDetector=new ScaleGestureDetector(activity,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScale(ScaleGestureDetector d){
                if(originalMode||landscape)return false;
                setZoom(zoom*d.getScaleFactor());
                return true;
            }
        });
        doubleTap=new GestureDetector(activity,new GestureDetector.SimpleOnGestureListener(){
            @Override public boolean onDown(MotionEvent event){return true;}
            @Override public boolean onDoubleTap(MotionEvent event){
                if(originalMode||landscape)return false;
                if(zoom>1.2f)resetFit();
                else setZoom(1.7f);
                return true;
            }
        });
        // Single-finger touches remain owned by the actual game.
        // Two-finger gestures are owned by the companion viewport.
        web.setOnTouchListener((v,event)->{
            if(originalMode||landscape)return false;
            scaleDetector.onTouchEvent(event);
            doubleTap.onTouchEvent(event);
            int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_POINTER_DOWN && event.getPointerCount()>=2){
                multiTouch=true;
                lastFocusX=(event.getX(0)+event.getX(1))*0.5f;
                lastFocusY=(event.getY(0)+event.getY(1))*0.5f;
                return true;
            }
            if(event.getPointerCount()>=2){
                if(action==MotionEvent.ACTION_MOVE){
                    float cx=(event.getX(0)+event.getX(1))*0.5f;
                    float cy=(event.getY(0)+event.getY(1))*0.5f;
                    offsetX+=(cx-lastFocusX)*Math.max(0.1f,fittedScale*zoom);
                    offsetY+=(cy-lastFocusY)*Math.max(0.1f,fittedScale*zoom);
                    lastFocusX=cx;lastFocusY=cy;
                    layoutGame();
                }
                return true;
            }
            if(action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_CANCEL){
                boolean consumed=multiTouch;
                multiTouch=false;
                return consumed;
            }
            return multiTouch || scaleDetector.isInProgress();
        });
        addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or_,ob)->{
            if(r-l!=or_-ol || b-t!=ob-ot)layoutGame();
        });
    }
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    public void setZoomListener(ZoomListener listener){this.listener=listener;notifyZoom();}
    public float getZoom(){return zoom;}
    public boolean isOriginal(){return originalMode;}
    public void setModes(boolean isLandscape,boolean useOriginal,int reserveBottomDp){
        boolean changed=landscape!=isLandscape || originalMode!=useOriginal;
        landscape=isLandscape;originalMode=useOriginal;
        reservedBottom=dp(reserveBottomDp);
        if(web.getParent()!=null)((ViewGroup)web.getParent()).removeView(web);
        if(isLandscape||useOriginal){
            web.setPivotX(0);web.setPivotY(0);
            web.setScaleX(1);web.setScaleY(1);
            web.setTranslationX(0);web.setTranslationY(0);
            web.setX(0);web.setY(0);
            addView(web,new FrameLayout.LayoutParams(-1,-1));
        }else{
            FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(dp(DESKTOP_WIDTH_DP),dp(DESKTOP_HEIGHT_DP),Gravity.TOP|Gravity.LEFT);
            addView(web,p);
        }
        if(changed && autoCenter)offsetX=offsetY=0;
        post(this::layoutGame);
    }
    public void setReservedBottom(int bottomDp){reservedBottom=dp(bottomDp);post(this::layoutGame);}
    public void center(){offsetX=offsetY=0;post(this::layoutGame);}
    public void pan(float horizontal,float vertical){
        if(landscape||originalMode)return;
        offsetX+=dp(Math.round(horizontal));offsetY+=dp(Math.round(vertical));
        post(this::layoutGame);
    }
    public void setZoom(float next){
        if(landscape||originalMode)return;
        float adjusted=Math.max(MIN_ZOOM,Math.min(MAX_ZOOM,next));
        if(Math.abs(adjusted-zoom)<0.001f)return;
        zoom=adjusted;
        if(rememberZoom)preferences.edit().putFloat("companionZoomV3",zoom).apply();
        layoutGame();notifyZoom();
    }
    public void adjustZoom(float delta){setZoom(zoom+delta);}
    public void resetFit(){setZoom(1f);center();}
    public void fill(){ // Center-crop intentionally, only when explicitly selected.
        if(getWidth()<1)return;
        float w=dp(DESKTOP_WIDTH_DP),h=dp(DESKTOP_HEIGHT_DP);
        float usableH=Math.max(1,getHeight()-reservedBottom);
        float cover=Math.max(getWidth()/w,usableH/h);
        float fit=Math.min(getWidth()/w,usableH/h);
        setZoom(Math.max(1f,cover/Math.max(0.01f,fit)));
        center();
    }
    public void setRememberZoom(boolean remember){
        rememberZoom=remember;
        preferences.edit().putBoolean("rememberZoom",remember).apply();
        if(remember)preferences.edit().putFloat("companionZoomV3",zoom).apply();
    }
    public void setAutoCenter(boolean center){
        autoCenter=center;preferences.edit().putBoolean("autoCenter",center).apply();
    }
    private void notifyZoom(){if(listener!=null)listener.changed(zoom);}
    private void layoutGame(){
        if(landscape||originalMode||web.getParent()!=this)return;
        int width=getWidth(),height=getHeight();
        if(width<=0||height<=0)return;
        int nativeW=dp(DESKTOP_WIDTH_DP),nativeH=dp(DESKTOP_HEIGHT_DP);
        float usableH=Math.max(dp(160),height-reservedBottom);
        fittedScale=Math.min((float)width/nativeW,usableH/nativeH);
        float scale=fittedScale*zoom;
        float shownW=nativeW*scale,shownH=nativeH*scale;
        float maxX=Math.max(0,(shownW-width)*0.5f);
        float maxY=Math.max(0,(shownH-usableH)*0.5f);
        offsetX=Math.max(-maxX,Math.min(maxX,offsetX));
        offsetY=Math.max(-maxY,Math.min(maxY,offsetY));
        web.setPivotX(0f);web.setPivotY(0f);
        web.setScaleX(scale);web.setScaleY(scale);
        web.setX((width-shownW)*0.5f+offsetX);
        web.setY((usableH-shownH)*0.5f+offsetY);
        web.setVisibility(View.VISIBLE);
    }
}
