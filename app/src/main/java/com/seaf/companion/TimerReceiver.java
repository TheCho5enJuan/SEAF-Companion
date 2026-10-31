package com.seaf.companion;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

public class TimerReceiver extends BroadcastReceiver {
    public static final String CHANNEL_ID = "seaf_mission_alerts_v3";
    public static void ensureChannel(Context context) {
        NotificationManager manager=context.getSystemService(NotificationManager.class);
        NotificationChannel channel=new NotificationChannel(CHANNEL_ID,
            "Mission reminder alerts",NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Audible notifications when a SEAF Companion timer finishes");
        channel.enableVibration(true);
        channel.enableLights(true);
        channel.setSound(Uri.parse("android.resource://"+context.getPackageName()+"/raw/mission_complete"),
            new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
        manager.createNotificationChannel(channel);
    }
    @Override public void onReceive(Context context,Intent intent) {
        int reminderId=intent==null?-1:intent.getIntExtra("id",-1);
        if(reminderId<0 || !ReminderScheduler.finish(context,reminderId,intent.getLongExtra("due",-1L)))return;
        ensureChannel(context);
        if(Build.VERSION.SDK_INT>=33 &&
           context.checkSelfPermission("android.permission.POST_NOTIFICATIONS") !=
             android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        String label=intent.getStringExtra("label");
        if(label==null||label.isEmpty()) label="SEAF timer";
        Intent open=new Intent(context,MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent tap=PendingIntent.getActivity(context,2001,open,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification alert=new Notification.Builder(context,CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(label+" is ready")
            .setContentText("Your manually tracked SEAF timer has finished.")
            .setCategory(Notification.CATEGORY_REMINDER)
            .setPriority(Notification.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_SOUND|Notification.DEFAULT_VIBRATE)
            .setContentIntent(tap)
            .setAutoCancel(true).build();
        context.getSystemService(NotificationManager.class).notify(1001+reminderId,alert);
    }
}
