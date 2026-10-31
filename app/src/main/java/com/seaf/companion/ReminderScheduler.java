package com.seaf.companion;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Comparator;

/** Local manual reminders only. Nothing here reads or changes SEAF game state. */
public final class ReminderScheduler {
    private static final String STORE="seaf_reminders_v2";
    private static final int MAX_ACTIVE=12;
    private ReminderScheduler(){}

    public static final class Reminder {
        public final int id;
        public final String label;
        public final long due;
        Reminder(int id,String label,long due){this.id=id;this.label=label;this.due=due;}
    }

    private static android.content.SharedPreferences prefs(Context c){
        return c.getSharedPreferences(STORE,Context.MODE_PRIVATE);
    }
    private static ArrayList<Reminder> load(Context c){
        ArrayList<Reminder> all=new ArrayList<>();
        try{
            JSONArray a=new JSONArray(prefs(c).getString("items","[]"));
            for(int i=0;i<a.length();i++){
                JSONObject o=a.getJSONObject(i);
                int id=o.getInt("id");
                long due=o.getLong("due");
                String label=o.optString("label","Mission");
                if(id>0 && due>0)all.add(new Reminder(id,label,due));
            }
        }catch(Exception ignored){}
        all.sort(Comparator.comparingLong(r->r.due));
        return all;
    }
    private static void save(Context c,ArrayList<Reminder> all){
        JSONArray a=new JSONArray();
        for(Reminder r:all){
            JSONObject o=new JSONObject();
            try{o.put("id",r.id);o.put("label",r.label);o.put("due",r.due);a.put(o);}catch(Exception ignored){}
        }
        prefs(c).edit().putString("items",a.toString()).apply();
    }
    private static PendingIntent pending(Context c,int id,String label){
        Intent i=new Intent(c,TimerReceiver.class);
        i.putExtra("id",id).putExtra("label",label);
        return PendingIntent.getBroadcast(c,id,i,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private static void arm(Context c,Reminder r){
        AlarmManager a=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        if(a==null)return;
        PendingIntent p=pending(c,r.id,r.label);
        // Exact alarms are only available when Android permits them.
        if(Build.VERSION.SDK_INT<31||a.canScheduleExactAlarms()){
            a.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,r.due,p);
        }else{
            a.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,r.due,p);
        }
    }
    public static synchronized ArrayList<Reminder> list(Context c){
        migrateLegacy(c);
        return load(c);
    }
    public static synchronized Reminder schedule(Context c,long due,String label){
        migrateLegacy(c);
        ArrayList<Reminder> all=load(c);
        if(due<=System.currentTimeMillis() || all.size()>=MAX_ACTIVE)return null;
        int id=prefs(c).getInt("nextId",10010);
        prefs(c).edit().putInt("nextId",id+1).apply();
        String safe=label==null?"Mission":label.trim();
        if(safe.isEmpty())safe="Mission";
        if(safe.length()>48)safe=safe.substring(0,48);
        Reminder r=new Reminder(id,safe,due);
        all.add(r);save(c,all);arm(c,r);
        return r;
    }
    /** Old one-timer API remains supported for migrations/callers. */
    public static void schedule(Context c,long due){schedule(c,due,"Mission");}
    public static synchronized void cancel(Context c,int id){
        ArrayList<Reminder> all=load(c);
        for(Reminder r:all){
            if(r.id==id){
                AlarmManager a=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
                if(a!=null)a.cancel(pending(c,r.id,r.label));
                break;
            }
        }
        all.removeIf(r->r.id==id);save(c,all);
    }
    public static synchronized void cancel(Context c){
        for(Reminder r:list(c))cancel(c,r.id);
    }
    public static synchronized boolean finish(Context c,int id){
        ArrayList<Reminder> all=load(c);
        boolean found=all.removeIf(r->r.id==id);
        if(found)save(c,all);
        return found;
    }
    public static synchronized void restore(Context c){
        migrateLegacy(c);
        for(Reminder r:load(c)){
            if(r.due>System.currentTimeMillis())arm(c,r);
            else finish(c,r.id);
        }
    }
    private static void migrateLegacy(Context c){
        android.content.SharedPreferences old=c.getSharedPreferences("MainActivity",Context.MODE_PRIVATE);
        long due=old.getLong("timerDue",0);
        if(due==0)return;
        old.edit().remove("timerDue").apply();
        AlarmManager a=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        Intent legacy=new Intent(c,TimerReceiver.class);
        PendingIntent pi=PendingIntent.getBroadcast(c,1001,legacy,
            PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE);
        if(a!=null&&pi!=null)a.cancel(pi);
        if(due>System.currentTimeMillis()){
            ArrayList<Reminder> all=load(c);
            if(all.size()<MAX_ACTIVE){
                int id=prefs(c).getInt("nextId",10010);
                prefs(c).edit().putInt("nextId",id+1).apply();
                Reminder r=new Reminder(id,"Mission",due);
                all.add(r);save(c,all);arm(c,r);
            }
        }
    }
}
