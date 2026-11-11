package com.patilweb.streambrowser;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.webkit.CookieManager;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;

/** Starts a multi-thread download and opens a finished file. */
public final class Downloads {

    private Downloads() {
    }

    public static void enqueue(Activity activity, String url, String userAgent,
                               String contentDisposition, String mimeType) {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            Toast.makeText(activity, R.string.download_unsupported, Toast.LENGTH_LONG).show();
            return;
        }
        String cookie = CookieManager.getInstance().getCookie(url);
        DownloadRecord record = DownloadEngine.enqueue(
                activity, url, userAgent, contentDisposition, mimeType, cookie);
        Toast.makeText(activity, activity.getString(R.string.download_started, record.name),
                Toast.LENGTH_SHORT).show();
    }

    public static void open(Activity activity, DownloadRecord record) {
        if (record == null || record.path == null) return;
        File file = new File(record.path);
        if (!file.exists() || !DownloadRecord.DONE.equals(record.status)) {
            Toast.makeText(activity, R.string.download_not_ready, Toast.LENGTH_SHORT).show();
            return;
        }
        String mime = record.mime;
        if (mime == null || mime.isEmpty()) {
            String ext = MimeTypeMap.getFileExtensionFromUrl(file.getName());
            mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        }
        Uri uri = FileProvider.getUriForFile(
                activity, activity.getPackageName() + ".files", file);
        Intent view = new Intent(Intent.ACTION_VIEW);
        view.setDataAndType(uri, mime == null ? "*/*" : mime);
        view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            activity.startActivity(view);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, R.string.download_open_failed, Toast.LENGTH_SHORT).show();
        }
    }
}
