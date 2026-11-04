package com.patilweb.tvremap;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BindingStore {
    private static final String PREFS = "tvremap";
    private static final String KEY_LINES = "bindings";

    private BindingStore() {
    }

    public static List<Binding> all(Context context) {
        SharedPreferences prefs = prefs(context);
        String raw = prefs.getString(KEY_LINES, "");
        List<Binding> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;
        for (String line : raw.split("\n")) {
            Binding b = Binding.fromLine(line.trim());
            if (b != null) out.add(b);
        }
        Collections.sort(out, Comparator
                .comparing(Binding::keyLabel)
                .thenComparing(Binding::gestureLabel));
        return out;
    }

    public static Map<String, Binding> index(Context context) {
        Map<String, Binding> map = new LinkedHashMap<>();
        for (Binding b : all(context)) {
            map.put(b.storageKey(), b);
            // Root listener often only has scancode — mirror by scan+gesture.
            if (b.scanCode != 0) {
                map.put("s:" + b.scanCode + ":" + b.gesture, b);
            }
        }
        return map;
    }

    public static boolean hasAnyFor(Context context, int keyCode, int scanCode) {
        Map<String, Binding> map = index(context);
        return find(map, keyCode, scanCode, Binding.GESTURE_CLICK) != null
                || find(map, keyCode, scanCode, Binding.GESTURE_DOUBLE) != null
                || find(map, keyCode, scanCode, Binding.GESTURE_HOLD) != null;
    }

    public static Binding find(Map<String, Binding> map, int keyCode, int scanCode, String gesture) {
        if (keyCode != KeyEvent.KEYCODE_UNKNOWN && keyCode != 0) {
            Binding b = map.get("k:" + keyCode + ":" + gesture);
            if (b != null) return b;
        }
        if (scanCode != 0) {
            Binding b = map.get("s:" + scanCode + ":" + gesture);
            if (b != null) return b;
        }
        return null;
    }

    public static void put(Context context, Binding binding) {
        Map<String, Binding> map = index(context);
        map.put(binding.storageKey(), binding);
        if (binding.scanCode != 0) {
            map.put("s:" + binding.scanCode + ":" + binding.gesture, binding);
        }
        save(context, map);
    }

    public static void remove(Context context, Binding binding) {
        Map<String, Binding> map = index(context);
        map.remove(binding.storageKey());
        if (binding.scanCode != 0) {
            map.remove("s:" + binding.scanCode + ":" + binding.gesture);
        }
        save(context, map);
    }

    public static void clear(Context context) {
        prefs(context).edit().remove(KEY_LINES).apply();
    }

    private static void save(Context context, Map<String, Binding> map) {
        Map<String, Binding> unique = new LinkedHashMap<>();
        for (Binding b : map.values()) {
            unique.put(b.storageKey(), b);
        }
        StringBuilder sb = new StringBuilder();
        for (Binding b : unique.values()) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(b.toLine());
        }
        // commit() so a binding survives even if the process is killed right after save.
        prefs(context).edit().putString(KEY_LINES, sb.toString()).commit();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
