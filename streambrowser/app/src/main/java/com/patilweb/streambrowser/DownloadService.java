package com.patilweb.streambrowser;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/** Keeps multi-thread downloads running after the browser screen closes. */
public class DownloadService extends Service {
    private static final String CHANNEL = "stream_downloads";
    private static final int NOTE_ID = 7;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::tick;

    public static void poke(Context context) {
        DownloadEngine.kick(context);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ensureChannel();
        Notification note = buildNote();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTE_ID, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTE_ID, note);
        }
        DownloadEngine.pump(this);
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, 700);
        return START_STICKY;
    }

    private void tick() {
        DownloadEngine.pump(this);
        if (!DownloadEngine.busy() && !hasActive()) {
            stopForeground(STOP_FOREGROUND_DETACH);
            stopSelf();
            return;
        }
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(NOTE_ID, buildNote());
        handler.postDelayed(tick, 700);
    }

    private boolean hasActive() {
        for (DownloadRecord record : DownloadStore.all(this)) {
            if (DownloadRecord.QUEUED.equals(record.status)
                    || DownloadRecord.RUNNING.equals(record.status)) {
                return true;
            }
        }
        return false;
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel ch = new NotificationChannel(
                CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(ch);
    }

    private Notification buildNote() {
        int running = 0;
        String name = "Downloads";
        int pct = -1;
        for (DownloadRecord record : DownloadStore.all(this)) {
            if (DownloadRecord.RUNNING.equals(record.status)
                    || DownloadRecord.QUEUED.equals(record.status)) {
                running++;
                name = record.name;
                pct = record.percent();
            }
        }
        String text = running == 0 ? "Finishing" : running + " downloads";
        if (running == 1) {
            for (DownloadRecord record : DownloadStore.all(this)) {
                if (DownloadRecord.RUNNING.equals(record.status)
                        || DownloadRecord.QUEUED.equals(record.status)) {
                    text = record.threads() + " threads"
                            + (pct >= 0 ? " · " + pct + "%" : "")
                            + " · " + record.name;
                    break;
                }
            }
        }
        Intent open = new Intent(this, DownloadsActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        if (name == null) name = "";
        return b.setContentTitle("Downloads")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(tick);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
