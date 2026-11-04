package com.seaf.companion;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/**
 * Original, inexpensive ambient-light rendering for the game letterbox.
 * Only low-frequency colors are used; no copied game pixels/text/buttons
 * can ever appear in the backdrop. Colors crossfade gently.
 */
public final class AmbientGlowView extends View {
    private static final int BASE=Color.rgb(5,10,22);
    private final Paint paint=new Paint(3);
    private final int[] color={0xFF17487D,0xFF574B8B,0xFFAF704A,0xFF123D7C};
    private final int[] start=color.clone();
    private final int[] target=color.clone();
    private ValueAnimator animation;

    public AmbientGlowView(Context context){super(context);setLayerType(View.LAYER_TYPE_HARDWARE,null);}
    private static int blend(int a,int b,float t){
        int r=Math.round(Color.red(a)*(1-t)+Color.red(b)*t);
        int g=Math.round(Color.green(a)*(1-t)+Color.green(b)*t);
        int v=Math.round(Color.blue(a)*(1-t)+Color.blue(b)*t);
        return Color.rgb(r,g,v);
    }
    private static int tone(int raw){
        // Preserve hue but lower contrast and brightness for a dark ambient background.
        int r=Math.min(210,Math.round(Color.red(raw)*.62f+8));
        int g=Math.min(190,Math.round(Color.green(raw)*.59f+10));
        int b=Math.min(220,Math.round(Color.blue(raw)*.66f+19));
        return Color.rgb(r,g,b);
    }
    public void acceptSample(android.graphics.Bitmap image){
        if(image==null||image.isRecycled())return;
        int w=image.getWidth(),h=image.getHeight();
        if(w<6||h<6)return;
        int[][] newColors=new int[4][];
        int[][] points={{w/4,h/4},{w*3/4,h/4},{w*3/4,h*3/4},{w/4,h*3/4}};
        for(int index=0;index<4;index++){
            long r=0,g=0,b=0,count=0;
            int cx=points[index][0],cy=points[index][1];
            for(int y=Math.max(0,cy-h/7);y<Math.min(h,cy+h/7);y+=3){
                for(int x=Math.max(0,cx-w/7);x<Math.min(w,cx+w/7);x+=3){
                    int pixel=image.getPixel(x,y);
                    int pr=Color.red(pixel),pg=Color.green(pixel),pb=Color.blue(pixel);
                    int maximum=Math.max(pr,Math.max(pg,pb));
                    if(maximum<25||Math.min(pr,Math.min(pg,pb))>242)continue;
                    r+=pr;g+=pg;b+=pb;count++;
                }
            }
            if(count<4)continue;
            int sampled=Color.rgb((int)(r/count),(int)(g/count),(int)(b/count));
            target[index]=tone(sampled);
        }
        for(int i=0;i<4;i++)start[i]=color[i];
        if(animation!=null)animation.cancel();
        animation=ValueAnimator.ofFloat(0f,1f);
        animation.setDuration(1400);
        animation.addUpdateListener(anim->{
            float f=(Float)anim.getAnimatedValue();
            for(int i=0;i<4;i++)color[i]=blend(start[i],target[i],f);
            invalidate();
        });
        animation.start();
    }
    private void glow(Canvas canvas,float x,float y,float radius,int color,float strength){
        int inner=Color.argb(Math.round(120*strength),
            Color.red(color),Color.green(color),Color.blue(color));
        int middle=Color.argb(Math.round(48*strength),
            Color.red(color),Color.green(color),Color.blue(color));
        paint.setShader(new RadialGradient(x,y,radius,
            new int[]{inner,middle,Color.TRANSPARENT},
            new float[]{0f,.48f,1f},Shader.TileMode.CLAMP));
        canvas.drawRect(0,0,getWidth(),getHeight(),paint);
        paint.setShader(null);
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        canvas.drawColor(BASE);
        float w=getWidth(),h=getHeight();
        if(w<=0||h<=0)return;
        float radius=Math.max(w*.75f,h*.38f);
        glow(canvas,w*.16f,h*.28f,radius,color[0],.78f);
        glow(canvas,w*.82f,h*.34f,radius,color[1],.68f);
        glow(canvas,w*.81f,h*.72f,radius,color[2],.64f);
        glow(canvas,w*.15f,h*.69f,radius,color[3],.7f);
        paint.setColor(0x66030B19);
        canvas.drawRect(0,0,w,h,paint);
    }
    public void stop(){if(animation!=null)animation.cancel();}
}
