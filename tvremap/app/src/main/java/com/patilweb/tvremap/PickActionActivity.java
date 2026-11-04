package com.patilweb.tvremap;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class PickActionActivity extends Activity {
    public static final String EXTRA_KEYCODE = "keycode";
    public static final String EXTRA_SCANCODE = "scancode";
    public static final String EXTRA_GESTURE = "gesture";
    public static final String EXTRA_TYPE = "type";
    public static final String EXTRA_PACKAGE = "package";
    public static final String EXTRA_LABEL = "label";

    private final List<ActionItem> items = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pick_action);

        int keyCode = getIntent().getIntExtra(EXTRA_KEYCODE, KeyEvent.KEYCODE_UNKNOWN);
        int scanCode = getIntent().getIntExtra(EXTRA_SCANCODE, 0);
        String gesture = getIntent().getStringExtra(EXTRA_GESTURE);
        if (gesture == null) gesture = Binding.GESTURE_CLICK;
        TextView title = findViewById(R.id.pickTitle);
        TextView sub = findViewById(R.id.pickSubtitle);
        title.setText("Choose action");
        String gLabel = Binding.GESTURE_DOUBLE.equals(gesture) ? "Double click"
                : Binding.GESTURE_HOLD.equals(gesture) ? "Hold" : "Single click";
        sub.setText(KeyEvent.keyCodeToString(keyCode) + "  ·  " + gLabel + "  ·  scan " + scanCode);

        buildItems();
        ListView list = findViewById(R.id.actionList);
        list.setAdapter(new ActionAdapter());
        list.setOnItemClickListener((parent, view, position, id) -> {
            ActionItem item = items.get(position);
            Intent data = new Intent()
                    .putExtra(EXTRA_TYPE, item.type)
                    .putExtra(EXTRA_PACKAGE, item.packageName)
                    .putExtra(EXTRA_LABEL, item.label);
            setResult(RESULT_OK, data);
            finish();
        });
        list.requestFocus();
    }

    private void buildItems() {
        items.add(new ActionItem(Binding.TYPE_HOME, "", "Go Home"));
        items.add(new ActionItem(Binding.TYPE_SETTINGS, "", "Open Settings"));
        items.add(new ActionItem(Binding.TYPE_INPUTS, "", "Open Inputs / Sources"));
        items.add(new ActionItem(Binding.TYPE_ALL_APPS, "", "Open Apps list"));
        items.add(new ActionItem(Binding.TYPE_BACK, "", "Back"));

        PackageManager pm = getPackageManager();
        Set<String> seen = new LinkedHashSet<>();
        List<ResolveInfo> leanback = pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0);
        List<ResolveInfo> launcher = pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);

        List<ResolveInfo> all = new ArrayList<>(leanback);
        all.addAll(launcher);
        List<ActionItem> apps = new ArrayList<>();
        for (ResolveInfo ri : all) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (getPackageName().equals(pkg) || !seen.add(pkg)) continue;
            CharSequence label = ri.loadLabel(pm);
            if (label == null) {
                try {
                    ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                    label = pm.getApplicationLabel(ai);
                } catch (PackageManager.NameNotFoundException e) {
                    label = pkg;
                }
            }
            apps.add(new ActionItem(Binding.TYPE_APP, pkg, "Open " + label));
        }
        Collections.sort(apps, Comparator.comparing(a -> a.label.toLowerCase()));
        items.addAll(apps);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            setResult(RESULT_CANCELED);
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private static final class ActionItem {
        final String type;
        final String packageName;
        final String label;

        ActionItem(String type, String packageName, String label) {
            this.type = type;
            this.packageName = packageName;
            this.label = label;
        }
    }

    private class ActionAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public ActionItem getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            if (row == null) {
                row = LayoutInflater.from(PickActionActivity.this)
                        .inflate(R.layout.item_row, parent, false);
            }
            ActionItem item = getItem(position);
            TextView title = row.findViewById(R.id.rowTitle);
            TextView sub = row.findViewById(R.id.rowSubtitle);
            title.setText(item.label);
            sub.setText(item.type.equals(Binding.TYPE_APP) ? item.packageName : "System action");
            return row;
        }
    }
}
