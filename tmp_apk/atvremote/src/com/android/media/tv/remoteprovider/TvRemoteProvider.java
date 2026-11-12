package com.android.media.tv.remoteprovider;

import android.content.Context;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

/**
 * Stand-in for the platform TV remote bridge. This box has no
 * ITvRemoteServiceInput, so the stock library crashed while pairing.
 * Key events are injected with the box's su binary instead.
 */
public class TvRemoteProvider {
    private static final String TAG = "TvRemoteProvider";
    private final Context mContext;

    public TvRemoteProvider(Context context) {
        mContext = context;
    }

    public IBinder getBinder() {
        return null;
    }

    public Context getContext() {
        return mContext;
    }

    public void openRemoteInputBridge(IBinder token, String name, int width, int height, int maxPointers) {
        Log.i(TAG, "open " + name);
        // The Google TV app waits until an input device named virtual-remote
        // appears. This box has no system TV remote service, so create one.
        if ("virtual-remote".equals(name)) {
            startVirtualRemote();
        }
        new Handler(Looper.getMainLooper()).post(() -> onInputBridgeConnected(token));
    }

    public void closeInputBridge(IBinder token) {
    }

    public void clearInputBridge(IBinder token) {
    }

    public void onInputBridgeConnected(IBinder token) {
    }

    public void sendKeyDown(IBinder token, int keyCode) {
        inject(keyCode);
    }

    public void sendKeyUp(IBinder token, int keyCode) {
    }

    public void sendPointerDown(IBinder token, int pointerId, int x, int y) {
    }

    public void sendPointerUp(IBinder token, int pointerId) {
    }

    public void sendPointerSync(IBinder token) {
    }

    public void sendTimestamp(IBinder token, long timestamp) {
    }

    private static void startVirtualRemote() {
        try {
            Process kill = new ProcessBuilder("/system/xbin/su", "0", "killall", "vremote")
                    .redirectErrorStream(true)
                    .start();
            kill.waitFor();
            new ProcessBuilder("/system/xbin/su", "0", "/data/local/tmp/vremote")
                    .redirectErrorStream(true)
                    .start();
            // InputReader publishes the device a moment after it is created.
            Thread.sleep(500);
            Log.i(TAG, "virtual-remote device created");
        } catch (Exception e) {
            Log.e(TAG, "virtual-remote", e);
        }
    }

    private static void inject(int keyCode) {
        try {
            Process process = new ProcessBuilder(
                    "/system/xbin/su", "0", "input", "keyevent", Integer.toString(keyCode))
                    .redirectErrorStream(true)
                    .start();
            process.waitFor();
        } catch (Exception e) {
            Log.e(TAG, "inject " + keyCode, e);
        }
    }
}
