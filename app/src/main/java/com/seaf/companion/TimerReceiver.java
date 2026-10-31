package com.seaf.companion;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class TimerReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String label = intent.getStringExtra("label");
        if (label == null || label.isEmpty()) label = "SEAF timer";
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        String channel = "seaf_timers";
        manager.createNotificationChannel(new NotificationChannel(channel,"SEAF timer reminders",NotificationManager.IMPORTANCE_DEFAULT));
        Notification.Builder builder = new Notification.Builder(context,channel)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(label + " is ready")
            .setContentText("Your manually tracked SEAF timer has finished.")
            .setAutoCancel(true);
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        manager.notify(1001,builder.build());
    }
}
