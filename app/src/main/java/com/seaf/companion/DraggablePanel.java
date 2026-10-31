package com.seaf.companion;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;

/**
 * A hold (420 ms) anywhere on the Companion HUD starts a drag.
 * Ordinary taps and short presses continue to reach the icon buttons.
 */
public final class DraggablePanel extends LinearLayout {
    public interface Listener {
        void onMove(float dx,float dy);
        void onDrop();
    }

    private static final long HOLD_MS=420;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final int slop;
    private Listener listener;
    private boolean holding,tracking;
    private float downX,downY;
    private float previousX,previousY;
    private final Runnable activate=()->{
        if(!tracking)return;
        holding=true;
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        // The next move/up will intercept and cancel the child button click.
        getParent().requestDisallowInterceptTouchEvent(false);
    };

    public DraggablePanel(Context context){
        super(context);
        setOrientation(VERTICAL);
        slop=ViewConfiguration.get(context).getScaledTouchSlop();
        setClickable(true);
    }
    public void setDragListener(Listener value){listener=value;}
    private void start(MotionEvent e){
        tracking=true;holding=false;
        downX=previousX=e.getRawX();
        downY=previousY=e.getRawY();
        handler.removeCallbacks(activate);
        handler.postDelayed(activate,HOLD_MS);
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent e){
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                start(e);
                return false;
            case MotionEvent.ACTION_MOVE:
                if(holding)return true;
                if(Math.hypot(e.getRawX()-downX,e.getRawY()-downY)>slop){
                    tracking=false;handler.removeCallbacks(activate);
                }
                return false;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                handler.removeCallbacks(activate);
                if(holding)return true;
                tracking=false;
                return false;
            default:return false;
        }
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                if(!tracking)start(e);
                return true;
            case MotionEvent.ACTION_MOVE:
                if(holding && listener!=null){
                    float x=e.getRawX(),y=e.getRawY();
                    listener.onMove(x-previousX,y-previousY);
                    previousX=x;previousY=y;
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                handler.removeCallbacks(activate);
                if(holding&&listener!=null)listener.onDrop();
                tracking=false;holding=false;
                return true;
            default:return true;
        }
    }
    @Override protected void onDetachedFromWindow(){
        handler.removeCallbacks(activate);
        tracking=false;holding=false;
        super.onDetachedFromWindow();
    }
}
