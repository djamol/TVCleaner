package com.patilweb.tvremap;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

/**
 * Filters remote keys and runs mapped single / double / hold actions.
 * Also owns key capture so stock YouTube/Netflix buttons cannot abort binding.
 */
public class RemapService extends AccessibilityService implements ActionRunner.Host {
    private static final String TAG = "TvRemapA11y";
    private GestureEngine engine;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        ensureFilterFlags();
        engine = new GestureEngine(this, this::onGesture);
        // Root listener is what survives when this service is late to bind.
        Intent root = new Intent(this, RootKeyService.class);
        try {
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                startForegroundService(root);
            } else {
                startService(root);
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "root listener start failed", e);
        }
        Log.i(TAG, "connected, key filter on");
    }

    private void ensureFilterFlags() {
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) {
            info = new AccessibilityServiceInfo();
        }
        info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
                | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                | AccessibilityServiceInfo.DEFAULT;
        info.eventTypes |= AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        setServiceInfo(info);
    }

    private void onGesture(Binding binding) {
        Log.i(TAG, "fire " + binding.gestureLabel() + " → " + binding.label);
        ActionRunner.run(this, binding);
        // Some firmwares drop key-filter after startActivity — re-assert.
        main.postDelayed(this::ensureFilterFlags, 400);
        main.postDelayed(this::ensureFilterFlags, 1200);
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int kc = event.getKeyCode();
        int sc = event.getScanCode();

        // Capture mode: swallow app-buttons so YouTube/Netflix don't steal the session.
        if (CaptureActivity.capturing || CaptureBridge.isActive()) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                if (kc == KeyEvent.KEYCODE_BACK) return false;
                if (isNavKey(kc)) return false;
                Log.i(TAG, "capture key=" + kc + " scan=" + sc);
                CaptureBridge.offer(kc, sc);
            }
            // Consume non-nav keys while capturing.
            return !isNavKey(kc) && kc != KeyEvent.KEYCODE_BACK;
        }

        if (engine == null) {
            engine = new GestureEngine(this, this::onGesture);
        }
        boolean handled = engine.onKeyEvent(event);
        if (handled) {
            Log.d(TAG, "handled key=" + kc + " scan=" + sc + " act=" + event.getAction());
        }
        return handled;
    }

    private static boolean isNavKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                return true;
            default:
                return false;
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Re-assert filter when windows change (after opening YouTube etc.).
        if (event != null
                && event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            ensureFilterFlags();
        }
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public Context context() {
        return this;
    }

    @Override
    public void startActivity(Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        super.startActivity(intent);
    }

    @Override
    public void performBack() {
        performGlobalAction(GLOBAL_ACTION_BACK);
    }

    @Override
    public void toast(String msg) {
        main.post(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onDestroy() {
        if (engine != null) engine.shutdown();
        super.onDestroy();
    }
}
