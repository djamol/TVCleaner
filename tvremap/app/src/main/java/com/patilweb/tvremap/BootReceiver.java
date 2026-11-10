package com.patilweb.tvremap;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * Starts the root listener on boot. This firmware often finishes
 * BOOT_COMPLETED before sideloaded receivers are reached, so also listen for
 * the clock sync that follows boot.
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "TvRemapBoot";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_TIME_CHANGED.equals(action)
                && !Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                && !"android.intent.action.QUICKBOOT_POWERON".equals(action)
                && !"com.htc.intent.action.QUICKBOOT_POWERON".equals(action)) {
            return;
        }
        Log.i(TAG, "starting listener for " + action);
        Intent svc = new Intent(context, RootKeyService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(svc);
            } else {
                context.startService(svc);
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "listener start failed", e);
        }
    }
}
