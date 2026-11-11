package com.patilweb.streambrowser;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Manage downloads: open, pause, resume, cancel, and delete. */
public class DownloadsActivity extends Activity {

    private final List<DownloadRecord> rows = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private RowAdapter adapter;
    private TextView empty;
    private boolean visible;

    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            if (!visible) return;
            load();
            handler.postDelayed(this, 800);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_downloads);
        empty = findViewById(R.id.downloads_empty);
        ListView list = findViewById(R.id.downloads_list);
        adapter = new RowAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> manage(rows.get(position)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
        load();
        handler.removeCallbacks(refresh);
        handler.postDelayed(refresh, 800);
    }

    @Override
    protected void onPause() {
        visible = false;
        handler.removeCallbacks(refresh);
        super.onPause();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void load() {
        rows.clear();
        rows.addAll(DownloadStore.all(this));
        empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        adapter.notifyDataSetChanged();
    }

    private void manage(DownloadRecord record) {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (DownloadRecord.DONE.equals(record.status)) {
            labels.add(getString(R.string.download_open));
            actions.add(() -> Downloads.open(this, DownloadStore.get(this, record.id)));
        }
        if (DownloadRecord.RUNNING.equals(record.status)
                || DownloadRecord.QUEUED.equals(record.status)) {
            labels.add(getString(R.string.download_pause));
            actions.add(() -> DownloadEngine.pause(this, record.id));
        }
        if (DownloadRecord.PAUSED.equals(record.status)
                || DownloadRecord.FAILED.equals(record.status)) {
            labels.add(getString(R.string.download_resume));
            actions.add(() -> DownloadEngine.resume(this, record.id));
        }
        labels.add(getString(R.string.download_delete));
        actions.add(() -> {
            DownloadEngine.delete(this, record.id);
            load();
        });
        new AlertDialog.Builder(this)
                .setTitle(record.name)
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    actions.get(which).run();
                    load();
                })
                .show();
    }

    private String detail(DownloadRecord record) {
        String state;
        switch (record.status) {
            case DownloadRecord.DONE:
                state = "Ready · OK to open";
                break;
            case DownloadRecord.PAUSED:
                state = "Paused";
                break;
            case DownloadRecord.FAILED:
                state = "Failed";
                break;
            case DownloadRecord.QUEUED:
                state = "Waiting";
                break;
            default:
                int pct = record.percent();
                state = pct >= 0 ? "Downloading " + pct + "%" : "Downloading";
                break;
        }
        return record.threads() + (record.threads() == 1 ? " thread" : " threads")
                + " · " + bytes(record.downloaded())
                + (record.total > 0 ? " / " + bytes(record.total) : "")
                + " · " + state;
    }

    private static String bytes(long n) {
        if (n < 1024) return n + " B";
        if (n < 1024 * 1024) return String.format(Locale.US, "%.1f KB", n / 1024.0);
        if (n < 1024L * 1024 * 1024) return String.format(Locale.US, "%.1f MB", n / (1024.0 * 1024));
        return String.format(Locale.US, "%.2f GB", n / (1024.0 * 1024 * 1024));
    }

    private final class RowAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return rows.size();
        }

        @Override
        public Object getItem(int position) {
            return rows.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            if (row == null) {
                row = getLayoutInflater().inflate(android.R.layout.simple_list_item_2, parent, false);
            }
            DownloadRecord item = rows.get(position);
            TextView t1 = row.findViewById(android.R.id.text1);
            TextView t2 = row.findViewById(android.R.id.text2);
            t1.setText(item.name);
            t1.setTextColor(0xFFFFFFFF);
            t2.setText(detail(item));
            t2.setTextColor(0xFFB7C3D0);
            row.setBackgroundColor(0xFF151922);
            int pad = Math.round(12 * getResources().getDisplayMetrics().density);
            row.setPadding(pad, pad, pad, pad);
            return row;
        }
    }
}
