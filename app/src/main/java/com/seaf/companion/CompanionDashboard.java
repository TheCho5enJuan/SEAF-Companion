package com.seaf.companion;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.Locale;

public class CompanionDashboard {
    private final Activity activity;
    private final Runnable openGame;
    private final Runnable openOriginal;
    public CompanionDashboard(Activity activity,Runnable openGame,Runnable openOriginal) {
        this.activity=activity;this.openGame=openGame;this.openOriginal=openOriginal;
    }
    private TextView label(String text,int size){
        TextView t=new TextView(activity);t.setText(text);t.setTextColor(Color.WHITE);
        t.setTextSize(size);t.setPadding(16,12,16,12);return t;
    }
    private void add(LinearLayout l,View v){l.addView(v,new LinearLayout.LayoutParams(-1,-2));}
    public void show(){
        LinearLayout layout=new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(16,24,16,24);
        layout.setBackgroundColor(0xFF07182C);
        add(layout,label("SEAF COMPANION",25));
        add(layout,label("Mobile dashboard · Phase 1",14));
        add(layout,label("Live soldier status: Not connected yet. The game remains the source of truth.",15));
        Button game=new Button(activity);game.setText("Open Game");add(layout,game);
        Button original=new Button(activity);original.setText("Original Game (disable custom fit)");add(layout,original);
        add(layout,label("LOCAL TIMER REMINDER",19));
        add(layout,label("Track a game timer manually for now. This is NOT synchronized with SEAF.",13));
        EditText name=new EditText(activity);name.setSingleLine(true);name.setHint("Timer name (e.g. Mission)");
        name.setTextColor(Color.WHITE);name.setHintTextColor(0xFF9EAABA);add(layout,name);
        EditText mins=new EditText(activity);mins.setHint("Minutes");mins.setInputType(2);
        mins.setTextColor(Color.WHITE);mins.setHintTextColor(0xFF9EAABA);add(layout,mins);
        Button remind=new Button(activity);remind.setText("Set reminder");add(layout,remind);
        TextView state=label("No active timer set in this dashboard.",14);add(layout,state);
        android.content.SharedPreferences prefs=activity.getPreferences(0);
        long due=prefs.getLong("timerDue",0);
        if(due>0)state.setText("Last timer: "+prefs.getString("timerLabel","SEAF")+" — "+new java.util.Date(due));
        ScrollView scroll=new ScrollView(activity);scroll.addView(layout);
        AlertDialog dialog=new AlertDialog.Builder(activity).setView(scroll).create();
        game.setOnClickListener(v->{dialog.dismiss();openGame.run();});
        original.setOnClickListener(v->{dialog.dismiss();openOriginal.run();});
        remind.setOnClickListener(v->{
            int minutes;
            try{minutes=Integer.parseInt(mins.getText().toString());}
            catch(Exception e){minutes=0;}
            if(minutes<1||minutes>10080){Toast.makeText(activity,"Enter 1-10080 minutes",Toast.LENGTH_LONG).show();return;}
            if(Build.VERSION.SDK_INT>=33 && activity.checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)
                activity.requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},5);
            String label=name.getText().toString().trim();if(label.isEmpty())label="SEAF timer";
            long at=System.currentTimeMillis()+minutes*60000L;
            Intent intent=new Intent(activity,TimerReceiver.class);intent.putExtra("label",label);
            PendingIntent pending=PendingIntent.getBroadcast(activity,1001,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            AlarmManager alarm=(AlarmManager)activity.getSystemService(Context.ALARM_SERVICE);
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pending);
            prefs.edit().putLong("timerDue",at).putString("timerLabel",label).apply();
            state.setText(label+" reminder set for "+new java.util.Date(at));
            Toast.makeText(activity,"Local timer scheduled",Toast.LENGTH_SHORT).show();
        });
        dialog.show();
    }
}
