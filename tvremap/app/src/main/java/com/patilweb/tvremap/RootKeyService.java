package com.patilweb.tvremap;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Root-backed remote key listener (getevent) with single / double / hold gestures.
 */
public class RootKeyService extends Service implements ActionRunner.Host {
    private static final String TAG = "TvRemapRoot";
    private static final String CHANNEL = "tvremap_root";

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Handler main = new Handler(Looper.getMainLooper());
    private Process process;
    private Thread thread;
    private GestureEngine engine;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(42, buildNotification());
        if (engine == null) {
            engine = new GestureEngine(this, binding -> ActionRunner.run(this, binding));
        }
        if (running.compareAndSet(false, true)) {
            thread = new Thread(this::loop, "tvremap-getevent");
            thread.start();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running.set(false);
        if (process != null) process.destroy();
        if (thread != null) thread.interrupt();
        if (engine != null) engine.shutdown();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void loop() {
        String[] devices = {
                "/dev/input/event3",
                "/dev/input/event1",
                "/dev/input/event2",
        };
        StringBuilder cmd = new StringBuilder();
        for (String d : devices) {
            if (cmd.length() > 0) cmd.append(' ');
            cmd.append(d);
        }
        String shell = "for d in " + cmd + "; do getevent -t \"$d\" & done; wait";
        try {
            process = Runtime.getRuntime().exec(new String[]{"su", "0", "sh", "-c", shell});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while (running.get() && (line = reader.readLine()) != null) {
                parseLine(line.trim());
            }
        } catch (Exception e) {
            Log.e(TAG, "getevent failed", e);
        } finally {
            running.set(false);
        }
    }

    private void parseLine(String line) {
        if (engine == null) return;
        int bracket = line.lastIndexOf(']');
        String body = bracket >= 0 ? line.substring(bracket + 1).trim() : line;
        int colon = body.lastIndexOf(':');
        if (colon >= 0 && body.startsWith("/dev/")) {
            body = body.substring(colon + 1).trim();
        }
        String[] parts = body.split("\\s+");
        if (parts.length < 3) return;
        try {
            int type = Integer.parseInt(parts[0], 16);
            int code = Integer.parseInt(parts[1], 16);
            int value = (int) Long.parseLong(parts[2], 16);
            if (type != 1 /* EV_KEY */) return;
            // value 1 = DOWN, 0 = UP, 2 = repeat (ignore)
            if (value == 1) {
                main.post(() -> {
                    if (CaptureActivity.capturing || CaptureBridge.isActive()) {
                        CaptureBridge.offer(KeyEvent.KEYCODE_UNKNOWN, code);
                        return;
                    }
                    engine.onDown(KeyEvent.KEYCODE_UNKNOWN, code);
                });
            } else if (value == 0) {
                main.post(() -> {
                    if (CaptureActivity.capturing || CaptureBridge.isActive()) return;
                    engine.onUp(KeyEvent.KEYCODE_UNKNOWN, code);
                });
            }
        } catch (NumberFormatException ignored) {
        }
    }

    @Override
    public Context context() {
        return this;
    }

    @Override
    public void startActivity(Intent intent) {
        super.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    @Override
    public void performBack() {
        try {
            Runtime.getRuntime().exec(new String[]{"su", "0", "input", "keyevent", "4"});
        } catch (Exception e) {
            Log.e(TAG, "back failed", e);
        }
    }

    @Override
    public void toast(String msg) {
        main.post(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
    }

    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL, "TV Remap", NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        return b.setContentTitle("TV Remap")
                .setContentText("Listening (root) · click / double / hold")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    public static boolean hasRoot() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "0", "id"});
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = r.readLine();
            p.waitFor();
            return line != null && line.contains("uid=0");
        } catch (Exception e) {
            return false;
        }
    }
}
