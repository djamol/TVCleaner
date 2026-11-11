package com.patilweb.streambrowser;

import android.content.Context;

import org.json.JSONArray;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Persists download jobs so pause and resume survive leaving the screen. */
public final class DownloadStore {
    private static final String FILE = "downloads.json";
    private static final Object LOCK = new Object();

    private DownloadStore() {
    }

    public static List<DownloadRecord> all(Context context) {
        synchronized (LOCK) {
            return read(context);
        }
    }

    public static DownloadRecord get(Context context, String id) {
        synchronized (LOCK) {
            for (DownloadRecord record : read(context)) {
                if (record.id.equals(id)) return record;
            }
            return null;
        }
    }

    public static void put(Context context, DownloadRecord record) {
        synchronized (LOCK) {
            List<DownloadRecord> all = read(context);
            boolean replaced = false;
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i).id.equals(record.id)) {
                    all.set(i, record);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) all.add(0, record);
            write(context, all);
        }
    }

    public static void remove(Context context, String id) {
        synchronized (LOCK) {
            List<DownloadRecord> all = read(context);
            List<DownloadRecord> keep = new ArrayList<>();
            for (DownloadRecord record : all) {
                if (!record.id.equals(id)) keep.add(record);
            }
            write(context, keep);
        }
    }

    private static List<DownloadRecord> read(Context context) {
        List<DownloadRecord> out = new ArrayList<>();
        File file = new File(context.getFilesDir(), FILE);
        if (!file.exists()) return out;
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[(int) file.length()];
            int n = 0;
            while (n < buf.length) {
                int r = in.read(buf, n, buf.length - n);
                if (r < 0) break;
                n += r;
            }
            JSONArray arr = new JSONArray(new String(buf, 0, n, StandardCharsets.UTF_8));
            for (int i = 0; i < arr.length(); i++) {
                out.add(DownloadRecord.fromJson(arr.getJSONObject(i)));
            }
        } catch (Exception ignored) {
            // A bad file should not block new downloads.
        }
        return out;
    }

    private static void write(Context context, List<DownloadRecord> records) {
        JSONArray arr = new JSONArray();
        try {
            for (DownloadRecord record : records) arr.put(record.toJson());
            File file = new File(context.getFilesDir(), FILE);
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(arr.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
        }
    }
}
