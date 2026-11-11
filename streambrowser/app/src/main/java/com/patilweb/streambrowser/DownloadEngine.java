package com.patilweb.streambrowser;

import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.webkit.URLUtil;

import java.io.File;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Downloads a file with several HTTP range connections when the server allows it.
 * One connection is used when the server does not advertise a size or ranges.
 */
public final class DownloadEngine {
    static final int THREADS = 4;
    private static final long MIN_SLICE = 512 * 1024;
    private static final int BUF = 64 * 1024;
    private static final Map<String, Runner> LIVE = new ConcurrentHashMap<>();

    private DownloadEngine() {
    }

    public static DownloadRecord enqueue(Context context, String url, String userAgent,
                                         String contentDisposition, String mimeType, String cookie) {
        DownloadRecord record = new DownloadRecord();
        record.id = Long.toString(System.currentTimeMillis());
        record.url = url;
        record.userAgent = userAgent == null ? "" : userAgent;
        record.cookie = cookie == null ? "" : cookie;
        record.mime = mimeType == null ? "" : mimeType;
        String name = URLUtil.guessFileName(url, contentDisposition, mimeType);
        name = name.replace('/', '_').replace('\\', '_').replace(':', '_');
        record.name = name;
        File dir = downloadDir(context);
        File dest = unique(dir, name);
        record.path = dest.getAbsolutePath();
        record.status = DownloadRecord.QUEUED;
        DownloadStore.put(context, record);
        kick(context);
        return record;
    }

    public static void pause(Context context, String id) {
        DownloadRecord record = DownloadStore.get(context, id);
        if (record == null || DownloadRecord.DONE.equals(record.status)) return;
        Runner runner = LIVE.get(id);
        if (runner != null) {
            runner.stop.set(true);
        } else {
            record.status = DownloadRecord.PAUSED;
            DownloadStore.put(context, record);
        }
        kick(context);
    }

    public static void resume(Context context, String id) {
        DownloadRecord record = DownloadStore.get(context, id);
        if (record == null) return;
        if (DownloadRecord.DONE.equals(record.status)) return;
        record.status = DownloadRecord.QUEUED;
        record.error = "";
        DownloadStore.put(context, record);
        kick(context);
    }

    public static void delete(Context context, String id) {
        Runner runner = LIVE.remove(id);
        if (runner != null) runner.stop.set(true);
        DownloadRecord record = DownloadStore.get(context, id);
        if (record != null && record.path != null) {
            File file = new File(record.path);
            if (file.exists() && !file.delete()) file.deleteOnExit();
        }
        DownloadStore.remove(context, id);
        kick(context);
    }

    public static void kick(Context context) {
        Context app = context.getApplicationContext();
        Intent intent = new Intent(app, DownloadService.class);
        try {
            app.startForegroundService(intent);
        } catch (RuntimeException e) {
            app.startService(intent);
        }
    }

    static void pump(Context context) {
        List<DownloadRecord> jobs = DownloadStore.all(context);
        for (DownloadRecord record : jobs) {
            if (!DownloadRecord.QUEUED.equals(record.status)
                    && !DownloadRecord.RUNNING.equals(record.status)) {
                continue;
            }
            if (LIVE.containsKey(record.id)) continue;
            Runner runner = new Runner(context.getApplicationContext(), record.id);
            LIVE.put(record.id, runner);
            new Thread(runner, "dl-" + record.id).start();
        }
    }

    static boolean busy() {
        return !LIVE.isEmpty();
    }

    static File downloadDir(Context context) {
        File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) dir = new File(context.getFilesDir(), "Download");
        if (!dir.exists() && !dir.mkdirs() && !dir.isDirectory()) {
            dir = context.getFilesDir();
        }
        return dir;
    }

    private static File unique(File dir, String name) {
        File file = new File(dir, name);
        if (!file.exists()) return file;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; i < 1000; i++) {
            File alt = new File(dir, base + " (" + i + ")" + ext);
            if (!alt.exists()) return alt;
        }
        return new File(dir, base + "-" + System.currentTimeMillis() + ext);
    }

    private static final class Runner implements Runnable {
        private final Context context;
        private final String id;
        private DownloadRecord record;
        final AtomicBoolean stop = new AtomicBoolean(false);

        Runner(Context context, String id) {
            this.context = context;
            this.id = id;
        }

        @Override
        public void run() {
            try {
                work();
            } finally {
                LIVE.remove(id, this);
                DownloadService.poke(context);
            }
        }

        private void work() {
            record = DownloadStore.get(context, id);
            if (record == null || stop.get()) return;
            try {
                synchronized (record) {
                    record.status = DownloadRecord.RUNNING;
                    if (record.parts.isEmpty()) prepare(record);
                    if (!record.ranged) restartSingle(record);
                    DownloadStore.put(context, record);
                }
                File file = new File(record.path);
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                if (record.total > 0) {
                    try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
                        if (raf.length() < record.total) raf.setLength(record.total);
                    }
                }
                int n = Math.max(1, record.parts.size());
                ExecutorService pool = Executors.newFixedThreadPool(n);
                for (int i = 0; i < n; i++) {
                    final int index = i;
                    pool.execute(() -> fetch(index));
                }
                pool.shutdown();
                while (!pool.awaitTermination(400, TimeUnit.MILLISECONDS)) {
                    saveProgress();
                    DownloadService.poke(context);
                    if (stop.get()) break;
                }
                if (stop.get()) {
                    if (DownloadStore.get(context, id) != null) {
                        synchronized (record) {
                            record.status = DownloadRecord.PAUSED;
                            DownloadStore.put(context, record);
                        }
                    }
                    return;
                }
                synchronized (record) {
                    boolean done = !record.parts.isEmpty();
                    for (DownloadRecord.Part part : record.parts) {
                        if (!part.finished()) done = false;
                    }
                    if (done) {
                        if (record.total < 0) record.total = record.downloaded();
                        record.status = DownloadRecord.DONE;
                        record.error = "";
                    } else {
                        record.status = DownloadRecord.FAILED;
                        if (record.error == null || record.error.isEmpty()) {
                            record.error = "Connection dropped";
                        }
                    }
                    DownloadStore.put(context, record);
                }
            } catch (Exception e) {
                if (!stop.get() && DownloadStore.get(context, id) != null) {
                    synchronized (record) {
                        record.status = DownloadRecord.FAILED;
                        record.error = e.getMessage() == null ? "Download failed" : e.getMessage();
                        DownloadStore.put(context, record);
                    }
                }
            }
        }

        private void prepare(DownloadRecord record) throws Exception {
            Probe probe = probe(record);
            record.total = probe.total;
            record.ranged = probe.ranged && probe.total > 0;
            if (probe.mime != null && !probe.mime.isEmpty()
                    && (record.mime == null || record.mime.isEmpty())) {
                record.mime = probe.mime;
            }
            record.parts.clear();
            if (!record.ranged) {
                DownloadRecord.Part part = new DownloadRecord.Part();
                part.start = 0;
                part.end = Long.MAX_VALUE;
                record.parts.add(part);
            } else {
                int threads = THREADS;
                while (threads > 1 && record.total / threads < MIN_SLICE) threads--;
                long chunk = record.total / threads;
                for (int i = 0; i < threads; i++) {
                    DownloadRecord.Part part = new DownloadRecord.Part();
                    part.start = i * chunk;
                    part.end = (i == threads - 1) ? record.total - 1 : (i + 1) * chunk - 1;
                    record.parts.add(part);
                }
            }
            DownloadStore.put(context, record);
        }

        /** A server that ignores ranges cannot resume; start that file over. */
        private void restartSingle(DownloadRecord record) {
            if (record.ranged || record.parts.isEmpty()) return;
            DownloadRecord.Part part = record.parts.get(0);
            if (part.written <= 0) return;
            File file = new File(record.path);
            if (file.exists() && !file.delete()) file.deleteOnExit();
            part.written = 0;
            part.end = Long.MAX_VALUE;
            DownloadStore.put(context, record);
        }

        private void fetch(int index) {
            DownloadRecord.Part part;
            boolean whole;
            long from;
            String path;
            synchronized (record) {
                if (index >= record.parts.size() || stop.get()) return;
                part = record.parts.get(index);
                if (part.finished()) return;
                whole = !record.ranged;
                from = part.start + part.written;
                path = record.path;
            }
            HttpURLConnection conn = null;
            try {
                conn = open(record);
                if (!whole) conn.setRequestProperty("Range", "bytes=" + from + "-" + part.end);
                int code = conn.getResponseCode();
                if (!whole && code == 416) {
                    synchronized (record) {
                        part.written = part.end - part.start + 1;
                    }
                    return;
                }
                if (code >= 400) throw new java.io.IOException("HTTP " + code);
                try (InputStream in = conn.getInputStream();
                     RandomAccessFile raf = new RandomAccessFile(path, "rw")) {
                    raf.seek(from);
                    byte[] buf = new byte[BUF];
                    int n;
                    while (!stop.get() && (n = in.read(buf)) != -1) {
                        raf.write(buf, 0, n);
                        synchronized (record) {
                            part.written += n;
                            if (!whole && part.written >= part.end - part.start + 1) break;
                        }
                    }
                }
                synchronized (record) {
                    if (!whole && part.written > part.end - part.start + 1) {
                        part.written = part.end - part.start + 1;
                    }
                    if (whole && !stop.get()) part.end = part.start + Math.max(0, part.written) - 1;
                }
            } catch (Exception e) {
                if (!stop.get()) {
                    synchronized (record) {
                        record.error = e.getMessage() == null ? "Download failed" : e.getMessage();
                    }
                }
            } finally {
                if (conn != null) conn.disconnect();
            }
        }

        private void saveProgress() {
            if (stop.get() || record == null) return;
            synchronized (record) {
                DownloadStore.put(context, record);
            }
        }

        private Probe probe(DownloadRecord record) throws Exception {
            HttpURLConnection conn = open(record);
            conn.setRequestProperty("Range", "bytes=0-0");
            Probe probe = new Probe();
            try {
                int code = conn.getResponseCode();
                probe.mime = conn.getContentType();
                if (code == 206) {
                    probe.ranged = true;
                    probe.total = totalFromRange(conn.getHeaderField("Content-Range"));
                } else {
                    String accept = conn.getHeaderField("Accept-Ranges");
                    probe.ranged = accept != null && accept.toLowerCase().contains("bytes");
                    probe.total = conn.getContentLengthLong();
                }
                InputStream in = conn.getInputStream();
                if (in != null) in.close();
            } finally {
                conn.disconnect();
            }
            return probe;
        }

        private HttpURLConnection open(DownloadRecord record) throws Exception {
            HttpURLConnection conn = (HttpURLConnection) new URL(record.url).openConnection();
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(20000);
            conn.setInstanceFollowRedirects(true);
            if (record.userAgent != null && !record.userAgent.isEmpty()) {
                conn.setRequestProperty("User-Agent", record.userAgent);
            }
            if (record.cookie != null && !record.cookie.isEmpty()) {
                conn.setRequestProperty("Cookie", record.cookie);
            }
            conn.setRequestProperty("Accept", "*/*");
            return conn;
        }

        private static long totalFromRange(String header) {
            if (header == null) return -1;
            int slash = header.lastIndexOf('/');
            if (slash < 0 || slash == header.length() - 1) return -1;
            try {
                long n = Long.parseLong(header.substring(slash + 1).trim());
                return n > 0 ? n : -1;
            } catch (NumberFormatException e) {
                return -1;
            }
        }
    }

    private static final class Probe {
        long total = -1;
        boolean ranged;
        String mime;
    }

}
