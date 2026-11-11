package com.patilweb.streambrowser;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.widget.Toast;

/** Saves WebView downloads through the system Download Manager. */
public final class Downloads {

    private Downloads() {
    }

    public static void enqueue(Activity activity, String url, String userAgent,
                               String contentDisposition, String mimeType) {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            Toast.makeText(activity, R.string.download_unsupported, Toast.LENGTH_LONG).show();
            return;
        }
        DownloadManager dm = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm == null) {
            Toast.makeText(activity, R.string.download_failed, Toast.LENGTH_LONG).show();
            return;
        }
        String name = URLUtil.guessFileName(url, contentDisposition, mimeType);
        name = name.replace('/', '_').replace('\\', '_');
        try {
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            if (userAgent != null && !userAgent.isEmpty()) {
                req.addRequestHeader("User-Agent", userAgent);
            }
            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null && !cookie.isEmpty()) {
                req.addRequestHeader("Cookie", cookie);
            }
            if (mimeType != null && !mimeType.isEmpty()) {
                req.setMimeType(mimeType);
            }
            req.setTitle(name);
            req.setDescription(url);
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
            req.setAllowedOverMetered(true);
            req.setAllowedOverRoaming(true);
            dm.enqueue(req);
            Toast.makeText(activity, activity.getString(R.string.download_started, name),
                    Toast.LENGTH_SHORT).show();
        } catch (RuntimeException e) {
            Toast.makeText(activity, R.string.download_failed, Toast.LENGTH_LONG).show();
        }
    }

    public static void open(Activity activity, long id) {
        DownloadManager dm = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm == null) return;
        Uri uri = dm.getUriForDownloadedFile(id);
        if (uri == null) {
            Toast.makeText(activity, R.string.download_not_ready, Toast.LENGTH_SHORT).show();
            return;
        }
        String mime = null;
        Cursor c = dm.query(new DownloadManager.Query().setFilterById(id));
        if (c != null) {
            try {
                if (c.moveToFirst()) {
                    int col = c.getColumnIndex(DownloadManager.COLUMN_MEDIA_TYPE);
                    if (col >= 0) mime = c.getString(col);
                }
            } finally {
                c.close();
            }
        }
        Intent view = new Intent(Intent.ACTION_VIEW);
        view.setDataAndType(uri, mime);
        view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            activity.startActivity(view);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, R.string.download_open_failed, Toast.LENGTH_SHORT).show();
        }
    }
}
