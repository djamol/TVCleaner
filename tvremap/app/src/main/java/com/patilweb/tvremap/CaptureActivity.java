package com.patilweb.tvremap;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.TextView;

/** Waits for one remote key press, then returns keycode + scancode. */
public class CaptureActivity extends Activity implements CaptureBridge.Listener {
    public static final String EXTRA_KEYCODE = "keycode";
    public static final String EXTRA_SCANCODE = "scancode";

    /** RemapService / RootKeyService skip remaps while capturing. */
    static volatile boolean capturing;

    private TextView detail;
    private boolean captured;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_capture);
        detail = findViewById(R.id.captureDetail);
        detail.setText("…");
        TextView hint = findViewById(R.id.captureHint);
        if (hint != null) {
            hint.setText("Listening… Back cancels\nApp buttons (YouTube etc.) are blocked while capturing");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        capturing = true;
        CaptureBridge.setListener(this);
    }

    @Override
    protected void onPause() {
        // Keep bridge briefly if an app-button tried to leave — finish handles clear.
        if (!captured) {
            // Stay capturing until we finish; don't clear on transient pause if possible.
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        capturing = false;
        CaptureBridge.clear();
        super.onDestroy();
    }

    @Override
    public void onCaptured(int keyCode, int scanCode) {
        finishWith(keyCode, scanCode);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // Prefer AccessibilityService capture (consumes stock app buttons).
        // Fallback if a11y is off: capture here for normal keys.
        if (event.getAction() != KeyEvent.ACTION_DOWN || captured) {
            return super.dispatchKeyEvent(event);
        }
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            capturing = false;
            CaptureBridge.clear();
            setResult(RESULT_CANCELED);
            finish();
            return true;
        }
        if (isNavKey(event.getKeyCode())) {
            detail.setText("Ignored nav key. Press an app / color / media button.");
            return true;
        }
        if (!CaptureBridge.isActive() || !isServiceLikelyFiltering()) {
            finishWith(event.getKeyCode(), event.getScanCode());
            return true;
        }
        // a11y will call onCaptured; still swallow here if it arrives.
        return true;
    }

    private boolean isServiceLikelyFiltering() {
        // If RemapService is enabled it will offer() first; activity path is backup.
        return CaptureBridge.isActive();
    }

    private void finishWith(int keyCode, int scanCode) {
        if (captured) return;
        if (isNavKey(keyCode) || keyCode == KeyEvent.KEYCODE_BACK) return;
        captured = true;
        capturing = false;
        CaptureBridge.clear();
        detail.setText(KeyEvent.keyCodeToString(keyCode) + "\nscancode " + scanCode);
        Intent data = new Intent()
                .putExtra(EXTRA_KEYCODE, keyCode)
                .putExtra(EXTRA_SCANCODE, scanCode);
        setResult(RESULT_OK, data);
        detail.postDelayed(this::finish, 300);
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
            case KeyEvent.KEYCODE_HOME:
                return true;
            default:
                return false;
        }
    }
}
