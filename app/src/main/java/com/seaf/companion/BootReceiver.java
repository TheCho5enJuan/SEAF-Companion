package com.seaf.companion;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()))return;
        long due=context.getSharedPreferences("MainActivity",Context.MODE_PRIVATE).getLong("timerDue",0);
        if(due>System.currentTimeMillis())ReminderScheduler.schedule(context,due);
    }
}
