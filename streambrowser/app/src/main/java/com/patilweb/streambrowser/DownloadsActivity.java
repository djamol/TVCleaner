package com.patilweb.streambrowser;

import android.app.Activity;
import android.app.DownloadManager;
import android.database.Cursor;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Lists files saved by the system Download Manager. OK opens the finished file. */
public class DownloadsActivity extends Activity {

    private final List<Row> rows = new ArrayList<>();
    private RowAdapter adapter;
    private TextView empty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_downloads);
        empty = findViewById(R.id.downloads_empty);
        ListView list = findViewById(R.id.downloads_list);
        adapter = new RowAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            Row row = rows.get(position);
            if (row.status == DownloadManager.STATUS_SUCCESSFUL) {
                Downloads.open(this, row.id);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
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
        DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        if (dm != null) {
            Cursor c = dm.query(new DownloadManager.Query());
            if (c != null) {
                try {
                    int idCol = c.getColumnIndex(DownloadManager.COLUMN_ID);
                    int titleCol = c.getColumnIndex(DownloadManager.COLUMN_TITLE);
                    int statusCol = c.getColumnIndex(DownloadManager.COLUMN_STATUS);
                    int soFarCol = c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                    int totalCol = c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                    while (c.moveToNext()) {
                        Row row = new Row();
                        row.id = c.getLong(idCol);
                        row.title = c.getString(titleCol);
                        if (row.title == null || row.title.isEmpty()) row.title = "Download";
                        row.status = c.getInt(statusCol);
                        row.soFar = c.getLong(soFarCol);
                        row.total = c.getLong(totalCol);
                        rows.add(row);
                    }
                } finally {
                    c.close();
                }
            }
        }
        empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        adapter.notifyDataSetChanged();
    }

    private String statusText(Row row) {
        switch (row.status) {
            case DownloadManager.STATUS_SUCCESSFUL:
                return "Ready · OK to open";
            case DownloadManager.STATUS_FAILED:
                return "Failed";
            case DownloadManager.STATUS_PAUSED:
                return "Paused";
            case DownloadManager.STATUS_PENDING:
                return "Waiting";
            default:
                if (row.total > 0) {
                    int pct = (int) (row.soFar * 100L / row.total);
                    return "Downloading " + pct + "%";
                }
                return "Downloading";
        }
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
            return rows.get(position).id;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            if (row == null) {
                row = getLayoutInflater().inflate(android.R.layout.simple_list_item_2, parent, false);
            }
            TextView t1 = row.findViewById(android.R.id.text1);
            TextView t2 = row.findViewById(android.R.id.text2);
            Row item = rows.get(position);
            t1.setText(item.title);
            t1.setTextColor(0xFFFFFFFF);
            t2.setText(statusText(item));
            t2.setTextColor(0xFFB7C3D0);
            row.setBackgroundColor(0xFF151922);
            int pad = Math.round(12 * getResources().getDisplayMetrics().density);
            row.setPadding(pad, pad, pad, pad);
            return row;
        }
    }

    private static final class Row {
        long id;
        String title;
        int status;
        long soFar;
        long total;
    }
}
