package com.seaf.companion;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.net.Uri;
import android.webkit.WebView;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;

public class MobileControls {
    private final Activity activity;
    private final WebView web;
    private static final String FIND = """
      (function(){
        const list=Array.from(document.querySelectorAll('button,a,[role="button"],[onclick],[tabindex]'));
        const result=[];
        for(let i=0;i<list.length&&result.length<75;i++){
          const e=list[i],r=e.getBoundingClientRect(),s=getComputedStyle(e);
          if(r.width<18||r.height<15||s.display==='none'||s.visibility==='hidden')continue;
          const raw=(e.innerText||e.getAttribute('aria-label')||e.getAttribute('title')||'').trim().replace(/\\s+/g,' ');
          const name=raw.slice(0,70);
          if(!name)continue;
          result.push({i:i,name:name});
        }
        return JSON.stringify(result);
      })();
      """;
    public MobileControls(Activity a,WebView w){activity=a;web=w;}
    public void open(){
        Uri uri=Uri.parse(web.getUrl()==null?"":web.getUrl());
        if(!"seaf.se".equalsIgnoreCase(uri.getHost())){
            Toast.makeText(activity,"Open SEAF before using controls",Toast.LENGTH_SHORT).show();return;
        }
        web.evaluateJavascript(FIND,encoded->{
            try{
                JSONArray data=new JSONArray((String)new org.json.JSONTokener(encoded).nextValue());
                if(data.length()==0){
                    Toast.makeText(activity,"No accessible controls found on this screen",Toast.LENGTH_LONG).show();
                    return;
                }
                String[] names=new String[data.length()];
                int[] indices=new int[data.length()];
                for(int j=0;j<data.length();j++){
                    JSONObject x=data.getJSONObject(j);indices[j]=x.getInt("i");names[j]=x.getString("name");
                }
                new AlertDialog.Builder(activity)
                    .setTitle("Mobile controls")
                    .setItems(names,(dialog,which)->{
                        int index=indices[which];
                        String script="(function(){if(location.hostname!=='seaf.se')return false;"+
                          "var a=document.querySelectorAll('button,a,[role=\\\"button\\\"],[onclick],[tabindex]');"+
                          "var el=a["+index+"];if(!el)return false;"+
                          "var r=el.getBoundingClientRect(),s=getComputedStyle(el);"+
                          "if(r.width<18||r.height<15||s.display==='none'||s.visibility==='hidden')return false;"+
                          "el.click();return true;})()";
                        web.evaluateJavascript(script,ok->{
                            if(!"true".equals(ok))
                                Toast.makeText(activity,"Control changed; refresh list",Toast.LENGTH_SHORT).show();
                        });
                    }).setNegativeButton("Close",null).show();
            }catch(Exception e){Toast.makeText(activity,"Controls unavailable",Toast.LENGTH_LONG).show();}
        });
    }
}
