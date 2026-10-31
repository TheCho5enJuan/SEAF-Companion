package com.seaf.companion;

import android.app.Activity;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.webkit.WebView;
import android.widget.Toast;
import org.json.JSONTokener;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class SessionRecorder {
    private final Activity activity;
    private final WebView web;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<Snapshot> snapshots = new ArrayList<>();
    private boolean recording = false, busy = false;
    private String previousFingerprint = "";
    private long lastCapture = 0;
    private int sequence = 0;
    private static final int MAX_CAPTURES = 35;
    private static final long POLL_MS = 2500;
    private static final String FINGERPRINT_JS = """
        (function(){
          const nodes=Array.from(document.querySelectorAll('main button,main a,main [id],main [class]'));
          const visible=nodes.filter(e=>{let r=e.getBoundingClientRect(),s=getComputedStyle(e);
            return r.width>15&&r.height>10&&s.display!=='none'&&s.visibility!=='hidden';}).slice(0,90);
          return JSON.stringify({path:location.pathname,orientation:screen.orientation?.type,
            w:innerWidth,h:innerHeight,
            nodes:visible.map(e=>{let r=e.getBoundingClientRect();
              return [e.tagName,e.id||'',String(e.className||'').slice(0,80),
                Math.round(r.x/20),Math.round(r.y/20),Math.round(r.width/20),Math.round(r.height/20)];})});
        })();
        """;
    private static final String LAYOUT_JS = """
        (function(){
          const safe=s=>String(s||'').slice(0,120);
          const els=Array.from(document.querySelectorAll('*')).slice(0,1800);
          const rows=els.map((e,i)=>{
            const r=e.getBoundingClientRect(),s=getComputedStyle(e);
            let n=e,parts=[];
            for(let j=0;j<4&&n&&n.nodeType===1;j++,n=n.parentElement){
              let p=n.tagName.toLowerCase();
              if(n.id)p+='#'+CSS.escape(n.id).slice(0,80);
              else if(n.classList.length)p+='.'+Array.from(n.classList).slice(0,3).map(x=>CSS.escape(x)).join('.');
              parts.unshift(p);
            }
            return {i,selector:safe(parts.join(' > ')),tag:e.tagName.toLowerCase(),
              rect:{x:Math.round(r.x),y:Math.round(r.y),w:Math.round(r.width),h:Math.round(r.height)},
              style:{display:s.display,position:s.position,overflow:s.overflow,
                width:s.width,height:s.height,fontSize:s.fontSize,transform:s.transform,
                flexDirection:s.flexDirection,zIndex:s.zIndex}};
          });
          return JSON.stringify({capturedAt:new Date().toISOString(),origin:location.origin,
            pathname:location.pathname,
            viewport:{innerWidth,innerHeight,devicePixelRatio,
              orientation:screen.orientation?.type||null,visualScale:visualViewport?.scale||null},
            document:{scrollWidth:document.documentElement.scrollWidth,
              scrollHeight:document.documentElement.scrollHeight},
            elements:rows});
        })();
        """;
    private static class Snapshot {
        final String name, json;
        final byte[] png;
        Snapshot(String n,String j,byte[] p){name=n;json=j;png=p;}
    }
    public SessionRecorder(Activity activity,WebView web){this.activity=activity;this.web=web;}
    public boolean isRecording(){return recording;}
    public void start(){
        if(recording)return;
        snapshots.clear(); sequence=0; previousFingerprint=""; lastCapture=0;
        recording=true; busy=false;
        Toast.makeText(activity,"Capture started. Navigate through SEAF.",Toast.LENGTH_LONG).show();
        handler.post(poll);
    }
    private boolean allowed(){
        Uri u=Uri.parse(web.getUrl()==null?"":web.getUrl());
        String host=u.getHost(), path=u.getPath()==null?"":u.getPath().toLowerCase(Locale.ROOT);
        return host!=null&&host.equalsIgnoreCase("seaf.se")&&
           !path.matches(".*(auth|oauth|callback|login|signin|token|account).*");
    }
    private final Runnable poll=new Runnable(){
        @Override public void run(){
            if(!recording)return;
            if(allowed()&&!busy){
                busy=true;
                web.evaluateJavascript(FINGERPRINT_JS, value->{
                    if(!recording){busy=false;return;}
                    try{
                        String fingerprint=(String)new JSONTokener(value).nextValue();
                        if(!fingerprint.equals(previousFingerprint)&&
                           System.currentTimeMillis()-lastCapture>2200&&snapshots.size()<MAX_CAPTURES){
                            previousFingerprint=fingerprint;
                            lastCapture=System.currentTimeMillis();
                            capture();
                        }else busy=false;
                    }catch(Exception ex){busy=false;}
                });
            }
            if(recording)handler.postDelayed(this,POLL_MS);
        }
    };
    public void recordNow(){
        if(recording&&allowed()&&!busy){busy=true;capture();}
    }
    private void capture(){
        web.evaluateJavascript(LAYOUT_JS,value->{
            try{
                if(!allowed()){busy=false;return;}
                String json=(String)new JSONTokener(value).nextValue();
                int w=web.getWidth(),h=web.getHeight();
                if(w<=0||h<=0||w>5000||h>5000){busy=false;return;}
                Bitmap bmp=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
                web.draw(new Canvas(bmp));
                ByteArrayOutputStream out=new ByteArrayOutputStream();
                bmp.compress(Bitmap.CompressFormat.PNG,95,out);
                bmp.recycle();
                snapshots.add(new Snapshot(String.format(Locale.US,"screen-%03d",++sequence),json,out.toByteArray()));
            }catch(Exception ignored){}finally{busy=false;}
        });
    }
    public void stop(){
        if(!recording)return;
        recording=false; handler.removeCallbacks(poll);
        if(busy){
            handler.postDelayed(this::export,700);
        }else export();
    }
    private void export(){
        if(snapshots.isEmpty()){
            Toast.makeText(activity,"No game screens recorded",Toast.LENGTH_LONG).show();
            return;
        }
        try{
            String stamp=new java.text.SimpleDateFormat("yyyyMMdd-HHmmss",Locale.US).format(new java.util.Date());
            ContentValues v=new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME,"seaf-session-"+stamp+".zip");
            v.put(MediaStore.MediaColumns.MIME_TYPE,"application/zip");
            v.put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/SEAFCompanion");
            Uri uri=activity.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);
            if(uri==null)throw new Exception("Cannot create ZIP");
            try(OutputStream os=activity.getContentResolver().openOutputStream(uri);
                ZipOutputStream zip=new ZipOutputStream(os)){
                add(zip,"README.txt",("SEAF Companion layout capture. "+snapshots.size()+
                    " screens. Contains UI screenshots, element selectors, geometry and computed CSS. "+
                    "No network capture, cookies, storage or input field values.").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                for(Snapshot s:snapshots){
                    add(zip,s.name+".json",s.json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    add(zip,s.name+".png",s.png);
                }
            }
            Toast.makeText(activity,"Saved "+snapshots.size()+" screens to Downloads/SEAFCompanion",Toast.LENGTH_LONG).show();
        }catch(Exception e){
            Toast.makeText(activity,"Session export failed: "+e.getMessage(),Toast.LENGTH_LONG).show();
        }
        snapshots.clear();
    }
    private static void add(ZipOutputStream zip,String name,byte[] data)throws java.io.IOException{
        zip.putNextEntry(new ZipEntry(name));zip.write(data);zip.closeEntry();
    }
    public void close(){recording=false;handler.removeCallbacksAndMessages(null);}
}
