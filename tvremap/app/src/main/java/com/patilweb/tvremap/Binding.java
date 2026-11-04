package com.patilweb.tvremap;

import android.view.KeyEvent;

/** One remote-key + gesture → action mapping. */
public final class Binding {
    public static final String GESTURE_CLICK = "click";
    public static final String GESTURE_DOUBLE = "double";
    public static final String GESTURE_HOLD = "hold";

    public static final String TYPE_APP = "app";
    public static final String TYPE_HOME = "home";
    public static final String TYPE_SETTINGS = "settings";
    public static final String TYPE_INPUTS = "inputs";
    public static final String TYPE_ALL_APPS = "all_apps";
    public static final String TYPE_BACK = "back";
    public static final String TYPE_NONE = "none";

    public final int keyCode;
    public final int scanCode;
    public final String gesture;
    public final String type;
    public final String packageName;
    public final String label;

    public Binding(int keyCode, int scanCode, String gesture,
                   String type, String packageName, String label) {
        this.keyCode = keyCode;
        this.scanCode = scanCode;
        this.gesture = (gesture == null || gesture.isEmpty()) ? GESTURE_CLICK : gesture;
        this.type = type;
        this.packageName = packageName == null ? "" : packageName;
        this.label = label == null ? "" : label;
    }

    /** Physical key identity without gesture (used by the gesture engine). */
    public String physicalKey() {
        if (keyCode != KeyEvent.KEYCODE_UNKNOWN && keyCode != 0) {
            return "k:" + keyCode;
        }
        return "s:" + scanCode;
    }

    public String storageKey() {
        return physicalKey() + ":" + gesture;
    }

    public String keyLabel() {
        String name = KeyEvent.keyCodeToString(keyCode);
        if (name != null && name.startsWith("KEYCODE_")) {
            name = name.substring("KEYCODE_".length());
        }
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN || keyCode == 0) {
            name = "SCAN_" + scanCode;
        } else {
            name = name + " (" + keyCode + ")";
        }
        return name;
    }

    public String gestureLabel() {
        switch (gesture) {
            case GESTURE_DOUBLE: return "Double click";
            case GESTURE_HOLD: return "Hold";
            default: return "Single click";
        }
    }

    public String toLine() {
        return keyCode + "|" + scanCode + "|" + gesture + "|" + type + "|"
                + packageName + "|" + label.replace('|', '/');
    }

    public static Binding fromLine(String line) {
        String[] p = line.split("\\|", -1);
        try {
            if (p.length >= 6) {
                return new Binding(
                        Integer.parseInt(p[0]),
                        Integer.parseInt(p[1]),
                        p[2],
                        p[3],
                        p[4],
                        p[5]);
            }
            // Legacy: key|scan|type|pkg|label  → treat as single click
            if (p.length >= 5) {
                return new Binding(
                        Integer.parseInt(p[0]),
                        Integer.parseInt(p[1]),
                        GESTURE_CLICK,
                        p[2],
                        p[3],
                        p[4]);
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
    }
}
