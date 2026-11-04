package com.patilweb.tvremap;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class PickGestureActivity extends Activity {
    public static final String EXTRA_KEYCODE = "keycode";
    public static final String EXTRA_SCANCODE = "scancode";
    public static final String EXTRA_GESTURE = "gesture";

    private static final String[] GESTURES = {
            Binding.GESTURE_CLICK,
            Binding.GESTURE_DOUBLE,
            Binding.GESTURE_HOLD,
    };
    private static final String[] LABELS = {
            "Single click",
            "Double click",
            "Hold (long press)",
    };
    private static final String[] SUBS = {
            "Tap once and release",
            "Two taps quickly",
            "Press and hold ~0.7s",
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pick_action);

        int keyCode = getIntent().getIntExtra(EXTRA_KEYCODE, KeyEvent.KEYCODE_UNKNOWN);
        int scanCode = getIntent().getIntExtra(EXTRA_SCANCODE, 0);
        TextView title = findViewById(R.id.pickTitle);
        TextView sub = findViewById(R.id.pickSubtitle);
        title.setText("Choose gesture");
        sub.setText(KeyEvent.keyCodeToString(keyCode) + "  ·  scan " + scanCode
                + "\nSame button can have all three.");

        ListView list = findViewById(R.id.actionList);
        list.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return GESTURES.length;
            }

            @Override
            public Object getItem(int position) {
                return GESTURES[position];
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View row = convertView;
                if (row == null) {
                    row = LayoutInflater.from(PickGestureActivity.this)
                            .inflate(R.layout.item_row, parent, false);
                }
                ((TextView) row.findViewById(R.id.rowTitle)).setText(LABELS[position]);
                ((TextView) row.findViewById(R.id.rowSubtitle)).setText(SUBS[position]);
                return row;
            }
        });
        list.setOnItemClickListener((parent, view, position, id) -> {
            Intent data = new Intent().putExtra(EXTRA_GESTURE, GESTURES[position]);
            setResult(RESULT_OK, data);
            finish();
        });
        list.requestFocus();
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
}
