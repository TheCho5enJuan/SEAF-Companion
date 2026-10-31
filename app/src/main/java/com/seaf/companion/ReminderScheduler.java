package com.seaf.companion;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import java.util.concurrent.TimeUnit;

public final class ReminderScheduler {
    private ReminderScheduler(){}
    private static PendingIntent pending(Context c){
        Intent i=new Intent(c,TimerReceiver.class).putExtra("label","SEAF timer");
        return PendingIntent.getBroadcast(c,1001,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    public static void schedule(Context c,long due){
        c.getSharedPreferences("MainActivity",Context.MODE_PRIVATE).edit().putLong("timerDue",due).apply();
        AlarmManager alarm=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,due,pending(c));
    }
    public static void cancel(Context c){
        c.getSharedPreferences("MainActivity",Context.MODE_PRIVATE).edit().remove("timerDue").apply();
        AlarmManager alarm=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        alarm.cancel(pending(c));
    }
}
