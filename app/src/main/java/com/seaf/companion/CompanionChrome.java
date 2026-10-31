package com.seaf.companion;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

/** Drawn Android-native HUD icons. No icon-font or network dependencies. */
public final class CompanionChrome {
    public static final int CYAN=0xFF33B3FF;
    public static final int WHITE=0xFFF2FAFF;
    public static final int GOLD=0xFFFFD22B;
    private CompanionChrome(){}

    public static GradientDrawable glass(float density, boolean selected){
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            selected?new int[]{0xE12A91E5,0xE10D5193}:
                     new int[]{0xE3263B60,0xE308172B});
        d.setCornerRadius(15*density);
        d.setStroke((int)Math.max(1,density),selected?0xFF75D7FF:0x705786AC);
        return d;
    }
    public static GradientDrawable panel(float density){
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{0xEE243B60,0xF0081429,0xEE14263F});
        d.setCornerRadius(23*density);
        d.setStroke((int)Math.max(1,density),0x8095BCE1);
        return d;
    }

    public static class Icon extends View {
        private final String kind;
        private final Paint p=new Paint(3);
        private boolean selected;
        private boolean active;
        public Icon(Context c,String type){super(c);kind=type;setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        public void setSelectedState(boolean value){selected=value;invalidate();}
        public void setActive(boolean value){active=value;invalidate();}
        private void line(Canvas c,float a,float b,float x,float y){
            c.drawLine(a,b,x,y,p);
        }
        private void path(Canvas c,float... pts){
            Path path=new Path();path.moveTo(pts[0],pts[1]);
            for(int i=2;i<pts.length;i+=2)path.lineTo(pts[i],pts[i+1]);
            c.drawPath(path,p);
        }
        private void rect(Canvas c,float l,float t,float r,float b){c.drawRect(l,t,r,b,p);}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            canvas.save();
            float s=Math.min(getWidth(),getHeight())/48f;
            canvas.translate((getWidth()-48*s)/2,(getHeight()-48*s)/2);
            canvas.scale(s,s);
            p.setColor(selected?WHITE:0xFFF0F6FF);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.7f);
            p.setStrokeJoin(Paint.Join.ROUND);p.setStrokeCap(Paint.Cap.ROUND);
            switch(kind){
                case "left":
                    path(canvas,29,13,15,13,15,35,29,35);
                    line(canvas,21,17,21,31);
                    p.setStrokeWidth(1.4f);
                    for(int y=14;y<=34;y+=5)line(canvas,34,y,36,y);
                    break;
                case "center":
                    path(canvas,15,21,15,14,22,14);
                    path(canvas,26,14,33,14,33,21);
                    path(canvas,33,26,33,34,26,34);
                    path(canvas,22,34,15,34,15,26);
                    break;
                case "right":
                    path(canvas,19,13,33,13,33,35,19,35);
                    line(canvas,27,17,27,31);
                    p.setStrokeWidth(1.4f);
                    for(int y=14;y<=34;y+=5)line(canvas,12,y,14,y);
                    break;
                case "overview":
                    path(canvas,12,20,12,12,20,12);
                    path(canvas,28,12,36,12,36,20);
                    path(canvas,36,28,36,36,28,36);
                    path(canvas,20,36,12,36,12,28);
                    path(canvas,12,12,20,20);
                    path(canvas,36,12,28,20);
                    path(canvas,12,36,20,28);
                    path(canvas,36,36,28,28);
                    break;
                case "bell":
                    p.setColor(GOLD);p.setStyle(Paint.Style.FILL);
                    Path bell=new Path();bell.moveTo(16,32);bell.lineTo(19,27);
                    bell.lineTo(19,21);bell.cubicTo(19,12,29,12,29,21);
                    bell.lineTo(29,27);bell.lineTo(32,32);bell.close();
                    canvas.drawPath(bell,p);
                    canvas.drawCircle(24,36,2.5f,p);
                    p.setColor(0xFFBB8921);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.5f);
                    canvas.drawPath(bell,p);break;
                case "settings":
                    p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(4.4f);
                    for(int i=0;i<8;i++){
                        double a=i*Math.PI/4;
                        line(canvas,24+(float)Math.cos(a)*13,24+(float)Math.sin(a)*13,
                            24+(float)Math.cos(a)*19,24+(float)Math.sin(a)*19);
                    }
                    p.setStrokeWidth(4.4f);canvas.drawCircle(24,24,12,p);
                    p.setStrokeWidth(2.7f);canvas.drawCircle(24,24,4,p);
                    break;
                case "orientation":
                    // Clear portrait/landscape device silhouette rather than circular reload arrow.
                    p.setStrokeWidth(2.8f);
                    canvas.drawRoundRect(10,17,31,33,3,3,p);
                    canvas.drawRoundRect(24,9,37,27,3,3,p);
                    break;
                case "rotate":
                    p.setStrokeWidth(3.5f);
                    canvas.drawArc(10,10,38,38,-80,295,false,p);
                    p.setStyle(Paint.Style.FILL);
                    path(canvas,34,13,39,13,38,21,31,17);
                    break;
                case "expand":
                    p.setStrokeWidth(3.2f);
                    path(canvas,15,24,33,24);path(canvas,24,15,24,33);
                    break;
                case "collapse":
                    p.setStrokeWidth(3.2f);line(canvas,15,24,33,24);break;
                case "handle":
                    p.setColor(0xFFC9E9FF);p.setStyle(Paint.Style.FILL);
                    for(int x=18;x<=30;x+=6)for(int y=17;y<=31;y+=7)canvas.drawCircle(x,y,1.8f,p);
                    break;
                case "fullscreen":
                    path(canvas,13,21,13,13,21,13);
                    path(canvas,27,13,35,13,35,21);
                    path(canvas,35,27,35,35,27,35);
                    path(canvas,21,35,13,35,13,27);
                    break;
            }
            canvas.restore();
        }
    }
}
