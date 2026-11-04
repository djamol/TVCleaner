package com.patilweb.tvremap;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Turns DOWN/UP into single-click / double-click / hold for a physical remote key.
 *
 * Timing: hold ≥ {@link #HOLD_MS}; second press within {@link #DOUBLE_MS} = double.
 */
public final class GestureEngine {
    public static final long HOLD_MS = 700;
    public static final long DOUBLE_MS = 420;

    public interface Callback {
        void fire(Binding binding);
    }

    private static final class State {
        long downAt;
        boolean holdFired;
        boolean down;
        Runnable holdRunnable;
        Runnable clickRunnable;
        long armedClickUpAt; // last UP that armed a pending single/double window
    }

    private final Context app;
    private final Callback callback;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, State> states = new HashMap<>();

    public GestureEngine(Context context, Callback callback) {
        this.app = context.getApplicationContext();
        this.callback = callback;
    }

    public boolean caresAbout(int keyCode, int scanCode) {
        return BindingStore.hasAnyFor(app, keyCode, scanCode);
    }

    /** @return true if this key is remapped (caller should consume the event). */
    public boolean onDown(int keyCode, int scanCode) {
        if (!caresAbout(keyCode, scanCode)) return false;
        final String id = physicalId(keyCode, scanCode);
        State st = states.get(id);
        if (st == null) {
            st = new State();
            states.put(id, st);
        }
        cancel(st.holdRunnable);
        st.down = true;
        st.holdFired = false;
        st.downAt = System.currentTimeMillis();

        Map<String, Binding> map = BindingStore.index(app);
        Binding hold = BindingStore.find(map, keyCode, scanCode, Binding.GESTURE_HOLD);
        if (hold != null) {
            final int kc = keyCode;
            final int sc = scanCode;
            final State state = st;
            st.holdRunnable = () -> {
                if (!state.down || state.holdFired) return;
                state.holdFired = true;
                cancel(state.clickRunnable);
                state.armedClickUpAt = 0;
                Binding b = BindingStore.find(BindingStore.index(app), kc, sc, Binding.GESTURE_HOLD);
                if (b != null) callback.fire(b);
            };
            handler.postDelayed(st.holdRunnable, HOLD_MS);
        }
        return true;
    }

    public boolean onUp(int keyCode, int scanCode) {
        if (!caresAbout(keyCode, scanCode)) return false;
        final String id = physicalId(keyCode, scanCode);
        State st = states.get(id);
        if (st == null) return true;
        st.down = false;
        cancel(st.holdRunnable);

        if (st.holdFired) {
            // Hold already ran — swallow the release.
            return true;
        }

        Map<String, Binding> map = BindingStore.index(app);
        Binding click = BindingStore.find(map, keyCode, scanCode, Binding.GESTURE_CLICK);
        Binding dbl = BindingStore.find(map, keyCode, scanCode, Binding.GESTURE_DOUBLE);
        long now = System.currentTimeMillis();

        if (dbl != null && st.armedClickUpAt > 0 && now - st.armedClickUpAt <= DOUBLE_MS) {
            cancel(st.clickRunnable);
            st.armedClickUpAt = 0;
            callback.fire(dbl);
            return true;
        }

        if (dbl != null) {
            // Wait to see if a second press arrives; then fire single if mapped.
            st.armedClickUpAt = now;
            cancel(st.clickRunnable);
            final int kc = keyCode;
            final int sc = scanCode;
            final State state = st;
            st.clickRunnable = () -> {
                state.armedClickUpAt = 0;
                Binding c = BindingStore.find(BindingStore.index(app), kc, sc, Binding.GESTURE_CLICK);
                if (c != null) callback.fire(c);
            };
            handler.postDelayed(st.clickRunnable, DOUBLE_MS);
            return true;
        }

        if (click != null) {
            st.armedClickUpAt = 0;
            callback.fire(click);
        }
        return true;
    }

    /** Convenience for KeyEvent path. */
    public boolean onKeyEvent(KeyEvent event) {
        int kc = event.getKeyCode();
        int sc = event.getScanCode();
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() > 0) {
                // Repeats are ignored; hold is timer-based.
                return caresAbout(kc, sc);
            }
            return onDown(kc, sc);
        }
        if (event.getAction() == KeyEvent.ACTION_UP) {
            return onUp(kc, sc);
        }
        return false;
    }

    public void shutdown() {
        for (State st : states.values()) {
            cancel(st.holdRunnable);
            cancel(st.clickRunnable);
        }
        states.clear();
        handler.removeCallbacksAndMessages(null);
    }

    private void cancel(Runnable r) {
        if (r != null) handler.removeCallbacks(r);
    }

    private static String physicalId(int keyCode, int scanCode) {
        if (keyCode != KeyEvent.KEYCODE_UNKNOWN && keyCode != 0) return "k:" + keyCode;
        return "s:" + scanCode;
    }
}
