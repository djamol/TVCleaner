package com.patilweb.streambrowser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** One saved download, including each connection's byte range. */
public final class DownloadRecord {
    static final String QUEUED = "queued";
    static final String RUNNING = "running";
    static final String PAUSED = "paused";
    static final String DONE = "done";
    static final String FAILED = "failed";

    public String id;
    public String url;
    public String name;
    public String mime;
    public String userAgent;
    public String cookie;
    public String path;
    public long total = -1;
    public String status = QUEUED;
    public String error = "";
    public boolean ranged;
    public final List<Part> parts = new ArrayList<>();

    public long downloaded() {
        long n = 0;
        for (Part part : parts) n += part.written;
        return n;
    }

    public int threads() {
        return Math.max(1, parts.size());
    }

    public int percent() {
        if (total <= 0) return downloaded() > 0 ? -1 : 0;
        return (int) Math.min(100, downloaded() * 100L / total);
    }

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("url", url);
        o.put("name", name);
        o.put("mime", mime == null ? "" : mime);
        o.put("ua", userAgent == null ? "" : userAgent);
        o.put("cookie", cookie == null ? "" : cookie);
        o.put("path", path == null ? "" : path);
        o.put("total", total);
        o.put("status", status);
        o.put("error", error == null ? "" : error);
        o.put("ranged", ranged);
        JSONArray arr = new JSONArray();
        for (Part part : parts) {
            JSONObject p = new JSONObject();
            p.put("start", part.start);
            p.put("end", part.end);
            p.put("written", part.written);
            arr.put(p);
        }
        o.put("parts", arr);
        return o;
    }

    static DownloadRecord fromJson(JSONObject o) throws JSONException {
        DownloadRecord r = new DownloadRecord();
        r.id = o.getString("id");
        r.url = o.getString("url");
        r.name = o.optString("name", "download");
        r.mime = o.optString("mime", "");
        r.userAgent = o.optString("ua", "");
        r.cookie = o.optString("cookie", "");
        r.path = o.optString("path", "");
        r.total = o.optLong("total", -1);
        r.status = o.optString("status", QUEUED);
        r.error = o.optString("error", "");
        r.ranged = o.optBoolean("ranged", false);
        JSONArray arr = o.optJSONArray("parts");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject p = arr.getJSONObject(i);
                Part part = new Part();
                part.start = p.getLong("start");
                part.end = p.getLong("end");
                part.written = p.optLong("written", 0);
                r.parts.add(part);
            }
        }
        return r;
    }

    static final class Part {
        long start;
        long end;
        long written;

        long remaining() {
            return Math.max(0, (end - start + 1) - written);
        }

        boolean finished() {
            return written >= (end - start + 1);
        }
    }
}
