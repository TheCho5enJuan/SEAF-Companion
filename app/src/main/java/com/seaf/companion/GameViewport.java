package com.seaf.companion;

import android.app.Activity;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.FrameLayout;
import java.util.Locale;

/**
 * Portrait fitting is performed on SEAF's 1600x900 DOM stage, NOT on the
 * Android WebView surface. Scaling a multi-thousand-pixel WebView caused
 * GPU composition seams, missing tiles and blue/black rectangles.
 *
 * The WebView itself always occupies the actual phone viewport.
 * Landscape and Original Game restore the site's original stage styles.
 */
public final class GameViewport extends FrameLayout {
    public interface ZoomListener { void changed(float zoom); }
    private static final float MIN_ZOOM = 0.30f, MAX_ZOOM = 3.0f;
    public static final int LEFT = 0, CENTER = 1, RIGHT = 2;
    private final WebView web;
    private final SharedPreferences preferences;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector doubleTap;
    private ZoomListener listener;
    private boolean originalMode, landscape, rememberZoom, autoCenter;
    private float zoom, offsetX, offsetY;
    private boolean heightFit;
    private int focusRegion;
    private int reservedBottomDp;
    private int pageGeneration;
    private float lastFocusX, lastFocusY;
    private boolean multiTouch;

    // The selector and 1600x900 dimensions come from the original SEAF
    // portrait-fit implementation, not a guess about an arbitrary page.
    // No game state, cookies, network calls or buttons are altered.
    private static final String PAGE_LAYOUT_JS = """
        (function(){
          'use strict';
          if (window.__seafCompanionLayout) return;
          var state = {
            enabled:false, zoom:1, dx:0, dy:0, bottom:0,
            fit:'overview', region:1,
            stage:null, saved:null, signature:'',
            keys:['zoom','left','top','transform','transform-origin'],
            find:function(){
              return document.querySelector('#page-home > .scale-me.pos-abs-c') ||
                     document.querySelector('#page-home .scale-me.pos-abs-c');
            },
            save:function(e){
              var before={};
              this.keys.forEach(function(k){
                before[k]=[e.style.getPropertyValue(k),e.style.getPropertyPriority(k)];
              });
              return before;
            },
            restore:function(){
              if(!this.stage || !this.saved) return;
              var e=this.stage, old=this.saved;
              this.keys.forEach(function(k){
                if(old[k][0])e.style.setProperty(k,old[k][0],old[k][1]);
                else e.style.removeProperty(k);
              });
              this.stage=null; this.saved=null; this.signature='';
            },
            apply:function(){
              if(!this.enabled){
                this.restore();
                return;
              }
              var e=this.find();
              if(!e){
                this.restore();
                return;
              }
              if(this.stage!==e){
                this.restore();
                this.stage=e;
                this.saved=this.save(e);
              }
              var frame=document.getElementById('page-home')||e.parentElement;
              var width=(frame&&frame.clientWidth)||window.innerWidth;
              var height=(frame&&frame.clientHeight)||window.innerHeight;
              var stageW=e.offsetWidth||1600, stageH=e.offsetHeight||900;
              if(width<100||height<100||stageW<100||stageH<100)return;
              var bottom=Math.max(0,Math.min(this.bottom,height*0.55));
              var usableH=Math.max(150,height-bottom);
              // Focus mode uses the height not occupied by the companion.
              // Overview is the only mode that tries to show all 1600x900.
              var fitWidth=(width-4)/stageW;
              var fitHeight=(usableH-4)/stageH;
              var base=this.fit==='height'?fitHeight:Math.min(fitWidth,fitHeight);
              var factor=Math.max(0.01,base*this.zoom);
              var extraX=Math.max(0,(stageW*factor-width)/2);
              var extraY=Math.max(0,(stageH*factor-usableH)/2);
              // Each preset targets the CENTER of the corresponding third.
              // Bound movement so no empty left/right margins appear.
              var third=(this.region-1)*(stageW/3)*factor;
              var pan=Math.max(-extraX,Math.min(extraX,this.dx-third));
              var x=width/2+pan;
              var y=usableH/2+Math.max(-extraY,Math.min(extraY,this.dy));
              var key=[width,height,stageW,stageH,factor,x,y].join('|');
              // Avoid repeatedly invalidating the game renderer each second.
              if(key===this.signature &&
                 e.style.getPropertyValue('transform').indexOf('scale(')>=0) return;
              this.signature=key;
              e.style.setProperty('zoom','1','important');
              e.style.setProperty('left',x+'px','important');
              e.style.setProperty('top',y+'px','important');
              e.style.setProperty('transform-origin','center center','important');
              e.style.setProperty('transform',
                'translate(-50%, -50%) scale('+factor+')','important');
            },
            set:function(enabled,z,x,y,bottom,fit,region){
              this.enabled=!!enabled;
              this.zoom=z;this.dx=x;this.dy=y;this.bottom=bottom;
              this.fit=fit==='height'?'height':'overview';
              this.region=Math.max(0,Math.min(2,region));
              this.signature='';
              this.apply();
            }
          };
          window.__seafCompanionLayout=state;
          window.addEventListener('resize',function(){state.signature='';state.apply();});
          // Handle SEAF's late initialization and SPA screen changes.
          window.setInterval(function(){state.apply();},1200);
        })();
        """;

    public GameViewport(Activity activity, WebView webView){
        super(activity);
        web=webView;
        preferences=activity.getPreferences(0);
        rememberZoom=preferences.getBoolean("rememberZoom",true);
        autoCenter=preferences.getBoolean("autoCenter",true);
        zoom=rememberZoom?preferences.getFloat("companionZoomV4",1f):1f;
        zoom=Math.max(MIN_ZOOM,Math.min(MAX_ZOOM,zoom));
        heightFit=preferences.getBoolean("portraitHeightFitV4",true);
        focusRegion=Math.max(LEFT,Math.min(RIGHT,preferences.getInt("portraitRegionV4",CENTER)));
        setClipChildren(true);
        setClipToPadding(true);
        addView(web,new LayoutParams(-1,-1));
        scaleDetector=new ScaleGestureDetector(activity,
            new ScaleGestureDetector.SimpleOnScaleGestureListener(){
                @Override public boolean onScale(ScaleGestureDetector detector){
                    if(landscape||originalMode)return false;
                    setZoom(zoom*detector.getScaleFactor());
                    return true;
                }
            });
        doubleTap=new GestureDetector(activity,
            new GestureDetector.SimpleOnGestureListener(){
                @Override public boolean onDown(MotionEvent e){return true;}
                @Override public boolean onDoubleTap(MotionEvent e){
                    if(landscape||originalMode)return false;
                    if(zoom>1.2f)resetFit();
                    else setZoom(1.7f);
                    return true;
                }
            });
        web.setOnTouchListener((v,event)->{
            if(landscape||originalMode)return false;
            scaleDetector.onTouchEvent(event);
            doubleTap.onTouchEvent(event);
            int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_POINTER_DOWN && event.getPointerCount()>=2){
                multiTouch=true;
                lastFocusX=(event.getX(0)+event.getX(1))/2f;
                lastFocusY=(event.getY(0)+event.getY(1))/2f;
                return true;
            }
            if(event.getPointerCount()>=2){
                if(action==MotionEvent.ACTION_MOVE){
                    float x=(event.getX(0)+event.getX(1))/2f;
                    float y=(event.getY(0)+event.getY(1))/2f;
                    offsetX+=(x-lastFocusX)/getResources().getDisplayMetrics().density;
                    offsetY+=(y-lastFocusY)/getResources().getDisplayMetrics().density;
                    lastFocusX=x;lastFocusY=y;
                    applyLayout();
                }
                return true;
            }
            if(action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_CANCEL){
                boolean wasMulti=multiTouch;
                multiTouch=false;
                return wasMulti;
            }
            return multiTouch||scaleDetector.isInProgress();
        });
    }

    public void setZoomListener(ZoomListener l){listener=l;notifyZoom();}
    private void notifyZoom(){if(listener!=null)listener.changed(zoom);}
    public float getZoom(){return zoom;}
    public boolean isOriginal(){return originalMode;}

    public void setModes(boolean isLandscape,boolean useOriginal,int reserveBottomDp){
        boolean changed=landscape!=isLandscape||originalMode!=useOriginal;
        landscape=isLandscape;
        originalMode=useOriginal;
        reservedBottomDp=reserveBottomDp;
        // WebView remains match-parent at native screen size for all modes.
        if(web.getLayoutParams().width!=-1||web.getLayoutParams().height!=-1){
            web.setLayoutParams(new FrameLayout.LayoutParams(-1,-1));
        }
        web.setScaleX(1f);web.setScaleY(1f);
        web.setTranslationX(0f);web.setTranslationY(0f);
        if(changed&&autoCenter)offsetX=offsetY=0;
        post(this::applyLayout);
    }

    public void onPageStarted(){pageGeneration++;}
    public void onPageFinished(){
        final int generation=pageGeneration;
        postDelayed(()->{if(generation==pageGeneration)installAndApply();},500);
        postDelayed(()->{if(generation==pageGeneration)installAndApply();},2100);
    }

    private boolean isGameDomain(){
        Uri uri=Uri.parse(web.getUrl()==null?"":web.getUrl());
        String host=uri.getHost();
        return host!=null&&(host.equalsIgnoreCase("seaf.se")||host.equalsIgnoreCase("www.seaf.se"));
    }
    private void installAndApply(){
        if(!isGameDomain()||web.getParent()!=this)return;
        web.evaluateJavascript(PAGE_LAYOUT_JS, result->applyLayout());
    }
    private void applyLayout(){
        if(web.getParent()!=this||!isGameDomain())return;
        boolean active=!landscape&&!originalMode;
        String script=String.format(Locale.US,
            "(function(){var v=window.__seafCompanionLayout;if(v)v.set(%s,%.4f,%.2f,%.2f,%d,'%s',%d);})();",
            active?"true":"false",zoom,offsetX,offsetY,active?reservedBottomDp:0,
            heightFit?"height":"overview",focusRegion);
        web.evaluateJavascript(script,null);
    }
    public void setReservedBottom(int heightDp){reservedBottomDp=heightDp;applyLayout();}
    public void center(){offsetX=0;offsetY=0;applyLayout();}
    public boolean isHeightFit(){return heightFit;}
    public int getFocusRegion(){return focusRegion;}
    private void persistView(){
        SharedPreferences.Editor editor=preferences.edit()
            .putBoolean("portraitHeightFitV4",heightFit)
            .putInt("portraitRegionV4",focusRegion);
        if(rememberZoom)editor.putFloat("companionZoomV4",zoom);
        editor.apply();
    }
    public void selectRegion(int region){
        if(landscape||originalMode)return;
        focusRegion=Math.max(LEFT,Math.min(RIGHT,region));
        heightFit=true;
        zoom=1f;
        offsetX=offsetY=0;
        persistView();
        applyLayout();notifyZoom();
    }
    public void showOverview(){
        if(landscape||originalMode)return;
        heightFit=false;focusRegion=CENTER;
        zoom=1f;offsetX=offsetY=0;
        persistView();applyLayout();notifyZoom();
    }
    public void showHeightFit(){
        if(landscape||originalMode)return;
        heightFit=true;focusRegion=CENTER;
        zoom=1f;offsetX=offsetY=0;
        persistView();applyLayout();notifyZoom();
    }
    public void pan(float x,float y){
        if(landscape||originalMode)return;
        offsetX=Math.max(-1500,Math.min(1500,offsetX+x));
        offsetY=Math.max(-1500,Math.min(1500,offsetY+y));
        applyLayout();
    }
    public void setZoom(float z){
        if(landscape||originalMode)return;
        float next=Math.max(MIN_ZOOM,Math.min(MAX_ZOOM,z));
        if(Math.abs(next-zoom)<0.001f)return;
        zoom=next;
        persistView();
        applyLayout();notifyZoom();
    }
    public void adjustZoom(float delta){setZoom(zoom+delta);}
    public void resetFit(){showOverview();}
    public void fill(){
        // Optional center crop; the panel retains the always-available Fit control.
        showHeightFit();
    }
    public void setRememberZoom(boolean remember){
        rememberZoom=remember;
        preferences.edit().putBoolean("rememberZoom",remember).apply();
        if(remember)preferences.edit().putFloat("companionZoomV4",zoom).apply();
    }
    public void setAutoCenter(boolean value){
        autoCenter=value;
        preferences.edit().putBoolean("autoCenter",value).apply();
    }
}
