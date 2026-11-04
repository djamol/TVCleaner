package com.patilweb.tvremap;

import android.os.Handler;
import android.os.Looper;

/**
 * Lets {@link RemapService} deliver a captured key to {@link CaptureActivity}
 * while consuming the event so stock YouTube/Netflix buttons cannot steal focus.
 */
public final class CaptureBridge {
    public interface Listener {
        void onCaptured(int keyCode, int scanCode);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile Listener listener;
    private static volatile boolean active;

    private CaptureBridge() {
    }

    public static void setListener(Listener l) {
        listener = l;
        active = l != null;
    }

    public static void clear() {
        listener = null;
        active = false;
    }

    public static boolean isActive() {
        return active;
    }

    /** @return true if consumed for capture. */
    public static boolean offer(int keyCode, int scanCode) {
        if (!active) return false;
        Listener l = listener;
        if (l == null) return false;
        MAIN.post(() -> l.onCaptured(keyCode, scanCode));
        return true;
    }
}
