package com.patilweb.tvremap;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQ_CAPTURE = 1;
    private static final int REQ_GESTURE = 2;
    private static final int REQ_PICK = 3;

    private TextView serviceStatus;
    private TextView emptyHint;
    private ListView bindingList;
    private final List<Binding> bindings = new ArrayList<>();
    private BindingAdapter adapter;
    private int pendingKeyCode;
    private int pendingScanCode;
    private String pendingGesture = Binding.GESTURE_CLICK;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        serviceStatus = findViewById(R.id.serviceStatus);
        emptyHint = findViewById(R.id.emptyHint);
        bindingList = findViewById(R.id.bindingList);
        Button btnAdd = findViewById(R.id.btnAdd);
        Button btnEnable = findViewById(R.id.btnEnable);
        Button btnClear = findViewById(R.id.btnClear);

        adapter = new BindingAdapter();
        bindingList.setAdapter(adapter);
        bindingList.setOnItemClickListener((parent, view, position, id) -> {
            Binding b = bindings.get(position);
            BindingStore.remove(this, b);
            Toast.makeText(this, "Removed " + b.gestureLabel() + " on " + b.keyLabel(),
                    Toast.LENGTH_SHORT).show();
            refresh();
        });

        btnAdd.setOnClickListener(v ->
                startActivityForResult(new Intent(this, CaptureActivity.class), REQ_CAPTURE));
        btnEnable.setOnClickListener(v -> ensureListenerRunning(true));
        btnClear.setOnClickListener(v -> {
            BindingStore.clear(this);
            refresh();
            Toast.makeText(this, "Cleared", Toast.LENGTH_SHORT).show();
        });

        btnAdd.requestFocus();
        ensureListenerRunning(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshServiceStatus();
        refresh();
        ensureListenerRunning(false);
    }

    private void ensureListenerRunning(boolean fromButton) {
        if (RootKeyService.hasRoot()) {
            Intent svc = new Intent(this, RootKeyService.class);
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(svc);
            } else {
                startService(svc);
            }
            if (fromButton) {
                Toast.makeText(this, "Root key listener started", Toast.LENGTH_SHORT).show();
            }
            refreshServiceStatus();
            return;
        }
        if (fromButton) openAccessibilitySettings();
    }

    private void refresh() {
        bindings.clear();
        bindings.addAll(BindingStore.all(this));
        adapter.notifyDataSetChanged();
        emptyHint.setVisibility(bindings.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void refreshServiceStatus() {
        boolean root = RootKeyService.hasRoot();
        boolean a11y = isServiceEnabled();
        boolean on = root || a11y;
        if (root) {
            serviceStatus.setText("ROOT LISTENER");
        } else if (a11y) {
            serviceStatus.setText("SERVICE ON");
        } else {
            serviceStatus.setText("SERVICE OFF — enable it");
        }
        serviceStatus.setBackgroundColor(on ? 0xFF3DDC97 : 0xFFE85D4C);
        serviceStatus.setTextColor(on ? 0xFF0A0E14 : 0xFFFFFFFF);
    }

    private boolean isServiceEnabled() {
        String enabled = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(enabled)) return false;
        ComponentName mine = new ComponentName(this, RemapService.class);
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabled);
        while (splitter.hasNext()) {
            ComponentName parsed = ComponentName.unflattenFromString(splitter.next());
            if (mine.equals(parsed)) return true;
        }
        return false;
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Exception e) {
            Toast.makeText(this,
                    "Open Settings → Accessibility → TV Remap and turn it on",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        if (requestCode == REQ_CAPTURE) {
            pendingKeyCode = data.getIntExtra(CaptureActivity.EXTRA_KEYCODE, KeyEvent.KEYCODE_UNKNOWN);
            pendingScanCode = data.getIntExtra(CaptureActivity.EXTRA_SCANCODE, 0);
            Intent gesture = new Intent(this, PickGestureActivity.class)
                    .putExtra(PickGestureActivity.EXTRA_KEYCODE, pendingKeyCode)
                    .putExtra(PickGestureActivity.EXTRA_SCANCODE, pendingScanCode);
            startActivityForResult(gesture, REQ_GESTURE);
            return;
        }

        if (requestCode == REQ_GESTURE) {
            pendingGesture = data.getStringExtra(PickGestureActivity.EXTRA_GESTURE);
            if (pendingGesture == null) pendingGesture = Binding.GESTURE_CLICK;
            Intent pick = new Intent(this, PickActionActivity.class)
                    .putExtra(PickActionActivity.EXTRA_KEYCODE, pendingKeyCode)
                    .putExtra(PickActionActivity.EXTRA_SCANCODE, pendingScanCode)
                    .putExtra(PickActionActivity.EXTRA_GESTURE, pendingGesture);
            startActivityForResult(pick, REQ_PICK);
            return;
        }

        if (requestCode == REQ_PICK) {
            String type = data.getStringExtra(PickActionActivity.EXTRA_TYPE);
            String pkg = data.getStringExtra(PickActionActivity.EXTRA_PACKAGE);
            String label = data.getStringExtra(PickActionActivity.EXTRA_LABEL);
            Binding binding = new Binding(
                    pendingKeyCode, pendingScanCode, pendingGesture, type, pkg, label);
            BindingStore.put(this, binding);
            Toast.makeText(this,
                    binding.keyLabel() + " · " + binding.gestureLabel() + " → " + label,
                    Toast.LENGTH_SHORT).show();
            refresh();
            if (!RootKeyService.hasRoot() && !isServiceEnabled()) {
                Toast.makeText(this, "Enable the accessibility service for remaps to work",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private class BindingAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return bindings.size();
        }

        @Override
        public Binding getItem(int position) {
            return bindings.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            if (row == null) {
                row = LayoutInflater.from(MainActivity.this)
                        .inflate(R.layout.item_row, parent, false);
            }
            Binding b = getItem(position);
            TextView title = row.findViewById(R.id.rowTitle);
            TextView sub = row.findViewById(R.id.rowSubtitle);
            title.setText(b.keyLabel() + "  ·  " + b.gestureLabel() + "  →  " + b.label);
            sub.setText((b.type.equals(Binding.TYPE_APP) ? b.packageName : b.type)
                    + "  ·  OK to remove");
            return row;
        }
    }
}
