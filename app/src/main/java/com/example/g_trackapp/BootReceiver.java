package com.example.g_trackapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d("BootReceiver", "Device rebooted, restarting location service...");
            Intent serviceIntent = new Intent(context, CollectorLocationService.class);
            serviceIntent.setAction("START_LOCATION_UPDATES");
            context.startForegroundService(serviceIntent);
        }
    }
}
