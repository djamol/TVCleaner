package com.patilweb.tvinfo;

import android.app.Activity;
import android.app.UiModeManager;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.KeyEvent;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Locale;

public class MainActivity extends Activity {

    private ScrollView scroll;
    private int scrollStep;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        scroll = findViewById(R.id.scroll);
        TextView info = findViewById(R.id.info_text);
        info.setText(buildReport());
        // Selectable text steals D-pad on TV remotes — keep it off.
        info.setTextIsSelectable(false);
        info.setFocusable(false);
        info.setFocusableInTouchMode(false);
        scrollStep = Math.round(120 * getResources().getDisplayMetrics().density);
        scroll.setFocusable(true);
        scroll.setFocusableInTouchMode(true);
        scroll.setDescendantFocusability(ScrollView.FOCUS_BLOCK_DESCENDANTS);
        scroll.requestFocus();
    }

    private String buildReport() {
        StringBuilder sb = new StringBuilder();

        sb.append("=== DEVICE ===\n");
        line(sb, "Manufacturer", Build.MANUFACTURER);
        line(sb, "Brand", Build.BRAND);
        line(sb, "Model", Build.MODEL);
        line(sb, "Device", Build.DEVICE);
        line(sb, "Product", Build.PRODUCT);
        line(sb, "Board / Hardware", Build.BOARD + " / " + Build.HARDWARE);
        line(sb, "Supported ABIs", join(Build.SUPPORTED_ABIS));
        line(sb, "Fingerprint", Build.FINGERPRINT);
        line(sb, "Android ID", Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID));

        sb.append("\n=== ANDROID / TV ===\n");
        line(sb, "Android version", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        line(sb, "Build display", Build.DISPLAY);
        line(sb, "Build type", Build.TYPE);
        line(sb, "TV characteristic", prop("ro.build.characteristics"));
        line(sb, "UI mode", describeUiMode());
        line(sb, "Leanback feature",
                String.valueOf(getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK)));
        line(sb, "Touchscreen required",
                String.valueOf(getPackageManager().hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)));
        line(sb, "HDMI device type", prop("ro.hdmi.device_type"));
        line(sb, "Hostname", prop("net.hostname"));
        line(sb, "Timezone", prop("persist.sys.timezone"));

        sb.append("\n=== PANEL / SCREEN SIZE ===\n");
        String panelIn = firstNonEmpty(
                prop("ro.boot.mi.panel_size"),
                prop("ro.boot.panel_size"),
                prop("persist.sys.panel_size"));
        if (!panelIn.isEmpty()) {
            line(sb, "Reported panel size", panelIn + " inch");
            try {
                double inches = Double.parseDouble(panelIn.trim());
                double diagCm = inches * 2.54;
                line(sb, "Diagonal (cm)", String.format(Locale.US, "%.1f cm", diagCm));
            } catch (Exception ignored) { }
        } else {
            line(sb, "Reported panel size", "(not in props)");
        }
        line(sb, "Panel vendor", firstNonEmpty(prop("ro.boot.mi.panel_vendor"), prop("ro.boot.panel_vendor")));
        line(sb, "Panel type", firstNonEmpty(prop("ro.boot.panel_type"), prop("ro.boot.mi.panel_type")));

        DisplayMetrics dm = new DisplayMetrics();
        Display display = getWindowManager().getDefaultDisplay();
        display.getRealMetrics(dm);

        float wIn = dm.widthPixels / dm.xdpi;
        float hIn = dm.heightPixels / dm.ydpi;
        double calcDiag = Math.sqrt(wIn * wIn + hIn * hIn);

        line(sb, "Resolution (px)", dm.widthPixels + " × " + dm.heightPixels);
        line(sb, "Density", dm.densityDpi + " dpi  (scale " + dm.density + ")");
        line(sb, "xdpi / ydpi", String.format(Locale.US, "%.1f / %.1f", dm.xdpi, dm.ydpi));
        line(sb, "Logical size (dp)",
                Math.round(dm.widthPixels / dm.density) + " × " + Math.round(dm.heightPixels / dm.density));
        line(sb, "Calc. diagonal from dpi",
                String.format(Locale.US, "%.1f in  (often wrong on TVs — use panel prop)", calcDiag));
        line(sb, "Aspect ratio", String.format(Locale.US, "%.3f (≈ %s)",
                (double) dm.widthPixels / Math.max(1, dm.heightPixels),
                aspectLabel(dm.widthPixels, dm.heightPixels)));

        if (Build.VERSION.SDK_INT >= 23) {
            Display.Mode mode = display.getMode();
            line(sb, "Active mode", mode.getPhysicalWidth() + "×" + mode.getPhysicalHeight()
                    + " @ " + String.format(Locale.US, "%.2f Hz", mode.getRefreshRate()));
            Display.Mode[] modes = display.getSupportedModes();
            StringBuilder ms = new StringBuilder();
            for (int i = 0; i < modes.length; i++) {
                if (i > 0) ms.append(", ");
                ms.append(modes[i].getPhysicalWidth()).append('×').append(modes[i].getPhysicalHeight())
                        .append('@').append(String.format(Locale.US, "%.0f", modes[i].getRefreshRate()));
            }
            line(sb, "Supported modes", ms.toString());
        } else {
            line(sb, "Refresh rate", String.format(Locale.US, "%.2f Hz", display.getRefreshRate()));
        }

        Configuration cfg = getResources().getConfiguration();
        line(sb, "Orientation", cfg.orientation == Configuration.ORIENTATION_LANDSCAPE
                ? "landscape" : String.valueOf(cfg.orientation));
        line(sb, "Smallest width (dp)", String.valueOf(cfg.smallestScreenWidthDp));
        line(sb, "Screen layout size", screenSizeLabel(cfg));

        sb.append("\n=== MEMORY / SYSTEM ===\n");
        Runtime rt = Runtime.getRuntime();
        line(sb, "App max heap", (rt.maxMemory() / (1024 * 1024)) + " MB");
        line(sb, "App used heap", ((rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)) + " MB");
        line(sb, "VM heapsize prop", prop("dalvik.vm.heapsize"));
        line(sb, "Platform", prop("ro.board.platform"));
        line(sb, "OEM key", prop("ro.oem.key1"));

        sb.append("\nBack = exit · D-pad Up/Down = scroll\n");
        return sb.toString();
    }

    private String describeUiMode() {
        UiModeManager um = (UiModeManager) getSystemService(UI_MODE_SERVICE);
        int type = um != null ? um.getCurrentModeType() : Configuration.UI_MODE_TYPE_UNDEFINED;
        String name;
        switch (type) {
            case Configuration.UI_MODE_TYPE_TELEVISION:
                name = "TELEVISION";
                break;
            case Configuration.UI_MODE_TYPE_NORMAL:
                name = "NORMAL";
                break;
            default:
                name = "type=" + type;
        }
        return name + " (cfg=" + (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_TYPE_MASK) + ")";
    }

    private static String screenSizeLabel(Configuration cfg) {
        int size = cfg.screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK;
        switch (size) {
            case Configuration.SCREENLAYOUT_SIZE_SMALL: return "SMALL";
            case Configuration.SCREENLAYOUT_SIZE_NORMAL: return "NORMAL";
            case Configuration.SCREENLAYOUT_SIZE_LARGE: return "LARGE";
            case Configuration.SCREENLAYOUT_SIZE_XLARGE: return "XLARGE";
            default: return "UNKNOWN(" + size + ")";
        }
    }

    private static String aspectLabel(int w, int h) {
        int g = gcd(w, h);
        return (w / g) + ":" + (h / g);
    }

    private static int gcd(int a, int b) {
        while (b != 0) {
            int t = a % b;
            a = b;
            b = t;
        }
        return Math.max(1, a);
    }

    private static String prop(String key) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"/system/bin/getprop", key});
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = r.readLine();
            r.close();
            p.destroy();
            return line == null ? "" : line.trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) return v.trim();
        }
        return "";
    }

    private static String join(String[] arr) {
        if (arr == null || arr.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    private static void line(StringBuilder sb, String label, String value) {
        sb.append(label).append(": ").append(value == null || value.isEmpty() ? "—" : value).append('\n');
    }

    private void scrollBy(int dy) {
        if (scroll == null) return;
        // Instant scroll is more reliable on TV than smoothScrollBy with key-repeat.
        scroll.scrollBy(0, dy);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_PAGE_DOWN:
                case KeyEvent.KEYCODE_CHANNEL_DOWN:
                    scrollBy(scrollStep);
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_PAGE_UP:
                case KeyEvent.KEYCODE_CHANNEL_UP:
                    scrollBy(-scrollStep);
                    return true;
                case KeyEvent.KEYCODE_MOVE_END:
                case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                    if (scroll != null) {
                        scroll.fullScroll(ScrollView.FOCUS_DOWN);
                    }
                    return true;
                case KeyEvent.KEYCODE_MOVE_HOME:
                case KeyEvent.KEYCODE_MEDIA_REWIND:
                    if (scroll != null) {
                        scroll.fullScroll(ScrollView.FOCUS_UP);
                    }
                    return true;
                default:
                    break;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
