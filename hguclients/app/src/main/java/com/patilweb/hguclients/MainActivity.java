package com.patilweb.hguclients;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {

    private static final int HEADER = 0xFF17324D;
    private static final int TEXT = 0xFF1C2430;
    private static final int SUB = 0xFF6B7C8D;
    private static final long REFRESH_MS = 8000;

    private static final String[] HEADERS = {
            "Device", "IP", "MAC", "Vendor", "Network",
            "dBm", "Mbps", "Sent", "Recv", "Save", "Sec"
    };
    private static final int[] WIDTHS = {124, 96, 148, 88, 96, 96, 46, 68, 68, 40, 40};

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final Gateway gateway = new Gateway();
    private final Runnable refresh = this::load;

    private ScrollView scroll;
    private HorizontalScrollView hscroll;
    private TableLayout table;
    private TextView status;
    private TextView session;
    private int scrollStep;
    private volatile boolean holdSession = true;
    private boolean resumeOnReturn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        scroll = findViewById(R.id.scroll);
        hscroll = findViewById(R.id.hscroll);
        table = findViewById(R.id.table);
        status = findViewById(R.id.status);
        session = findViewById(R.id.session);
        session.setOnClickListener(v -> onSessionClick());
        scrollStep = Math.round(140 * getResources().getDisplayMetrics().density);
        scroll.setDescendantFocusability(ScrollView.FOCUS_BLOCK_DESCENDANTS);
        for (int i = 0; i < WIDTHS.length; i++) {
            table.setColumnShrinkable(i, false);
            table.setColumnStretchable(i, false);
        }
        scroll.requestFocus();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (resumeOnReturn) {
            resumeOnReturn = false;
            holdSession = true;
        }
        if (holdSession) {
            session.setText(R.string.logout);
            load();
        }
    }

    @Override
    protected void onStop() {
        main.removeCallbacks(refresh);
        if (holdSession) {
            holdSession = false;
            resumeOnReturn = true;
            io.execute(() -> finishSignOut(false));
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacks(refresh);
        io.shutdown();
        super.onDestroy();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int code = event.getKeyCode();
            if (code == KeyEvent.KEYCODE_MENU) {
                session.performClick();
                return true;
            }
            if (getCurrentFocus() == session) {
                if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                    scroll.requestFocus();
                    return true;
                }
                if (code == KeyEvent.KEYCODE_DPAD_CENTER
                        || code == KeyEvent.KEYCODE_ENTER
                        || code == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                    return super.dispatchKeyEvent(event);
                }
                if (code == KeyEvent.KEYCODE_DPAD_UP
                        || code == KeyEvent.KEYCODE_DPAD_LEFT
                        || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    return true;
                }
            }
            if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                scroll.smoothScrollBy(0, scrollStep);
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_UP) {
                if (scroll.getScrollY() == 0) {
                    session.requestFocus();
                } else {
                    scroll.smoothScrollBy(0, -scrollStep);
                }
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                hscroll.smoothScrollBy(scrollStep, 0);
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                hscroll.smoothScrollBy(-scrollStep, 0);
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void onSessionClick() {
        if (holdSession) {
            resumeOnReturn = false;
            holdSession = false;
            main.removeCallbacks(refresh);
            session.setText(R.string.login);
            status.setTextColor(SUB);
            status.setText(R.string.signing_out);
            io.execute(() -> finishSignOut(true));
        } else {
            holdSession = true;
            session.setText(R.string.logout);
            load();
        }
    }

    private void finishSignOut(boolean announce) {
        String error = null;
        try {
            gateway.signOut();
        } catch (Throwable e) {
            error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
        if (!announce) {
            return;
        }
        String message = error;
        main.post(() -> {
            if (holdSession) {
                return;
            }
            session.setText(R.string.login);
            if (message == null) {
                status.setTextColor(SUB);
                status.setText(R.string.signed_out);
            } else {
                status.setTextColor(0xFFA12622);
                status.setText(getString(R.string.logout_failed, message));
            }
        });
    }

    private void load() {
        main.removeCallbacks(refresh);
        if (!holdSession) {
            return;
        }
        if (!busy.compareAndSet(false, true)) {
            if (holdSession) {
                main.postDelayed(refresh, REFRESH_MS);
            }
            return;
        }
        io.execute(() -> {
            try {
                List<Gateway.Client> clients = gateway.load();
                main.post(() -> {
                    if (holdSession) {
                        show(clients, null);
                    }
                });
            } catch (Throwable e) {
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                main.post(() -> {
                    if (holdSession) {
                        show(null, message);
                    }
                });
            } finally {
                busy.set(false);
                if (holdSession) {
                    main.postDelayed(refresh, REFRESH_MS);
                }
            }
        });
    }

    private void show(List<Gateway.Client> clients, String error) {
        int y = scroll.getScrollY();
        int x = hscroll.getScrollX();
        String clock = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        if (error != null) {
            status.setText("Could not read the gateway. " + error);
            status.setTextColor(0xFFA12622);
        } else {
            int n = clients == null ? 0 : clients.size();
            status.setTextColor(SUB);
            status.setText("Device name and IP come from the gateway DHCP list. Radio stats come from the Wi-Fi station table.  "
                    + n + (n == 1 ? " client" : " clients") + " · updated " + clock);
        }
        table.removeAllViews();
        table.addView(headerRow());
        if (clients == null || clients.isEmpty()) {
            TableRow empty = new TableRow(this);
            empty.setBackgroundColor(0xFFFFFFFF);
            TextView cell = textCell(error == null ? "No associated clients." : "—", false, 0);
            empty.addView(cell);
            table.addView(empty);
        } else {
            boolean alt = false;
            for (Gateway.Client client : clients) {
                table.addView(dataRow(client, alt));
                alt = !alt;
            }
        }
        scroll.post(() -> {
            scroll.scrollTo(0, y);
            hscroll.scrollTo(x, 0);
        });
    }

    private TableRow headerRow() {
        TableRow row = new TableRow(this);
        row.setBackgroundColor(HEADER);
        for (int i = 0; i < HEADERS.length; i++) {
            TextView cell = textCell(HEADERS[i], true, WIDTHS[i]);
            cell.setTextColor(0xFFFFFFFF);
            cell.setTypeface(Typeface.DEFAULT_BOLD);
            row.addView(cell);
        }
        return row;
    }

    private TableRow dataRow(Gateway.Client client, boolean alt) {
        TableRow row = new TableRow(this);
        row.setBackgroundColor(alt ? 0xFFF7FAFC : 0xFFFFFFFF);
        row.addView(textCell(client.name, true, WIDTHS[0]));
        row.addView(textCell(client.ip, false, WIDTHS[1]));
        TextView mac = textCell(client.mac, false, WIDTHS[2]);
        mac.setTypeface(Typeface.MONOSPACE);
        row.addView(mac);
        row.addView(textCell(client.vendor, false, WIDTHS[3]));
        TextView network = textCell(client.ssid + "\n" + client.band + " · ch " + client.channel, false, WIDTHS[4]);
        network.setTextColor(TEXT);
        row.addView(network);
        row.addView(signalCell(client));
        row.addView(textCell(client.rate, false, WIDTHS[6]));
        row.addView(textCell(client.tx, false, WIDTHS[7]));
        row.addView(textCell(client.rx, false, WIDTHS[8]));
        row.addView(textCell(client.powerSave, false, WIDTHS[9]));
        row.addView(textCell(client.timeout, false, WIDTHS[10]));
        return row;
    }

    private TextView signalCell(Gateway.Client client) {
        TextView cell = textCell("", false, WIDTHS[5]);
        if (client.quality.isEmpty()) {
            cell.setText("—");
            return cell;
        }
        String label = client.rssi + "   " + client.quality;
        SpannableString span = new SpannableString(label);
        int start = label.lastIndexOf(client.quality);
        int[] colors = badge(client.quality);
        span.setSpan(new BackgroundColorSpan(colors[0]), start, label.length(), 0);
        span.setSpan(new ForegroundColorSpan(colors[1]), start, label.length(), 0);
        cell.setText(span);
        return cell;
    }

    private static int[] badge(String quality) {
        switch (quality) {
            case "Excellent":
                return new int[]{0xFFD9F5E5, 0xFF0D7A3F};
            case "Good":
                return new int[]{0xFFE5F2DC, 0xFF3D6B16};
            case "Fair":
                return new int[]{0xFFFFF3D6, 0xFF8A5A00};
            default:
                return new int[]{0xFFFDE2E1, 0xFFA12622};
        }
    }

    private TextView textCell(String value, boolean bold, int widthDp) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(13);
        tv.setTextColor(TEXT);
        if (bold) {
            tv.setTypeface(Typeface.DEFAULT_BOLD);
        }
        int pad = dp(6);
        tv.setPadding(dp(4), pad, dp(4), pad);
        if (!value.contains("\n")) {
            tv.setMaxLines(1);
            tv.setEllipsize(TextUtils.TruncateAt.END);
        }
        tv.setGravity(Gravity.CENTER_VERTICAL);
        TableRow.LayoutParams lp = new TableRow.LayoutParams(
                widthDp == 0 ? TableRow.LayoutParams.WRAP_CONTENT : dp(widthDp),
                TableRow.LayoutParams.WRAP_CONTENT);
        tv.setLayoutParams(lp);
        return tv;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
