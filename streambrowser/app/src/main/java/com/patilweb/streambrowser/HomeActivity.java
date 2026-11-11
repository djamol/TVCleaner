package com.patilweb.streambrowser;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import java.util.ArrayList;
import java.util.List;

/**
 * Stream TV home: site carousel + tiles. Banner is a lightweight colour card only — websites are
 * never loaded until the user opens a site (preview WebViews caused TV hangs).
 */
public class HomeActivity extends Activity {

    public static final String EXTRA_URL = "url";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_AGGRESSIVE = "aggressive";

    private static final long CAROUSEL_INTERVAL_MS = 5500L;
    private static final int TILES_PER_ROW_HINT = 4;

    private SiteStore store;
    private List<Site> sites = new ArrayList<>();
    private final List<View> dots = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private ViewFlipper flipper;
    private View bannerFrame;
    private LinearLayout tileRows;
    private LinearLayout dotsRow;
    private int currentIndex;
    private boolean carouselPaused;
    private String focusSiteId;

    private final Runnable advanceCarousel = new Runnable() {
        @Override
        public void run() {
            if (carouselPaused || flipper == null || flipper.getChildCount() == 0) return;
            showSlide((currentIndex + 1) % flipper.getChildCount(), true);
            handler.postDelayed(this, CAROUSEL_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        store = new SiteStore(this);

        flipper = findViewById(R.id.banner_flipper);
        bannerFrame = findViewById(R.id.banner_frame);
        tileRows = findViewById(R.id.tile_rows);
        dotsRow = findViewById(R.id.banner_dots);

        findViewById(R.id.btn_downloads).setOnClickListener(v ->
                startActivity(new Intent(this, DownloadsActivity.class)));
        findViewById(R.id.btn_add_site).setOnClickListener(v -> showSiteDialog(null));

        bannerFrame.setOnClickListener(v -> {
            if (!sites.isEmpty()) openSite(sites.get(currentIndex));
        });
        bannerFrame.setOnLongClickListener(v -> {
            if (!sites.isEmpty()) {
                showSiteActions(sites.get(currentIndex));
                return true;
            }
            return false;
        });
        bannerFrame.setOnFocusChangeListener((v, hasFocus) -> {
            carouselPaused = hasFocus;
            if (hasFocus) {
                handler.removeCallbacks(advanceCarousel);
                v.animate().scaleX(1.01f).scaleY(1.01f).setDuration(120).start();
            } else {
                v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                scheduleCarousel();
            }
        });
        bannerFrame.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN || sites.isEmpty()) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                showSlide((currentIndex - 1 + sites.size()) % sites.size(), true);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                showSlide((currentIndex + 1) % sites.size(), true);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_INFO) {
                showSiteActions(sites.get(currentIndex));
                return true;
            }
            return false;
        });

        rebuildUi();
        bannerFrame.requestFocus();
        requestMicIfNeeded();
    }

    private void requestMicIfNeeded() {
        if (Build.VERSION.SDK_INT < 23) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 71);
    }

    private void rebuildUi() {
        String keepId = focusSiteId;
        int keepIndex = currentIndex;
        sites = store.allVisible();
        currentIndex = 0;
        if (keepId != null) {
            for (int i = 0; i < sites.size(); i++) {
                if (keepId.equals(sites.get(i).id)) {
                    currentIndex = i;
                    break;
                }
            }
        } else if (keepIndex > 0 && keepIndex < sites.size()) {
            currentIndex = keepIndex;
        }
        setupCarousel();
        setupDots();
        setupTiles();
        if (!sites.isEmpty()) showSlide(currentIndex, false);
        scheduleCarousel();
        restoreTileFocus(keepId);
    }

    private void restoreTileFocus(String siteId) {
        if (siteId == null || tileRows.getChildCount() == 0) return;
        for (int i = 0; i < sites.size() && i < tileRows.getChildCount(); i++) {
            if (siteId.equals(sites.get(i).id)) {
                tileRows.getChildAt(i).requestFocus();
                return;
            }
        }
    }

    private void setupTiles() {
        tileRows.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int tileW = Math.max((int) (140 * density),
                (screenW - (int) (96 * density)) / Math.min(TILES_PER_ROW_HINT, Math.max(1, sites.size())));
        int tileH = (int) (120 * density);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Site site : sites) {
            View tile = inflater.inflate(R.layout.item_site_tile, tileRows, false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tileW, tileH);
            lp.setMarginEnd((int) (10 * density));
            tile.setLayoutParams(lp);
            bindTile(tile, site);
            tileRows.addView(tile);
        }
        if (tileRows.getChildCount() > 0) {
            bannerFrame.setNextFocusDownId(View.NO_ID);
            tileRows.getChildAt(0).setNextFocusUpId(R.id.banner_frame);
        }
    }

    private void bindTile(View tile, Site site) {
        TextView title = tile.findViewById(R.id.tile_title);
        TextView desc = tile.findViewById(R.id.tile_desc);
        View accent = tile.findViewById(R.id.tile_accent);
        title.setText(site.title);
        desc.setText(site.description);

        GradientDrawable bar = new GradientDrawable();
        bar.setCornerRadius(8f);
        bar.setColor(site.accentColor);
        accent.setBackground(bar);

        tile.setOnClickListener(v -> openSite(site));
        tile.setOnLongClickListener(v -> {
            showSiteActions(site);
            return true;
        });
        tile.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_INFO) {
                showSiteActions(site);
                return true;
            }
            return false;
        });
        tile.setOnFocusChangeListener((v, hasFocus) -> {
            float scale = hasFocus ? 1.05f : 1f;
            v.animate().scaleX(scale).scaleY(scale).setDuration(120).start();
            if (hasFocus) focusSiteId = site.id;
        });
        tile.setNextFocusUpId(R.id.banner_frame);
    }

    private void showSiteActions(Site site) {
        focusSiteId = site.id;
        CharSequence[] items = new CharSequence[]{
                getString(R.string.action_edit),
                getString(R.string.action_move_left),
                getString(R.string.action_move_right),
                getString(R.string.action_remove),
        };
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.site_actions_title, site.title))
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0:
                            showSiteDialog(site);
                            break;
                        case 1:
                            moveSite(site, -1);
                            break;
                        case 2:
                            moveSite(site, 1);
                            break;
                        case 3:
                            confirmRemove(site);
                            break;
                        default:
                            break;
                    }
                })
                .setNegativeButton(R.string.add_site_cancel, null)
                .show();
    }

    private void moveSite(Site site, int delta) {
        focusSiteId = site.id;
        if (!store.move(site.id, delta)) {
            Toast.makeText(this, R.string.move_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, R.string.site_moved, Toast.LENGTH_SHORT).show();
        rebuildUi();
    }

    /** null site = add new; non-null = edit existing. */
    private void showSiteDialog(Site existing) {
        View form = getLayoutInflater().inflate(R.layout.dialog_add_site, null);
        TextView heading = form.findViewById(R.id.dialog_heading);
        EditText name = form.findViewById(R.id.input_title);
        EditText url = form.findViewById(R.id.input_url);
        boolean editing = existing != null;
        heading.setText(editing ? R.string.edit_site_title : R.string.add_site_title);
        if (editing) {
            name.setText(existing.title);
            url.setText(existing.url);
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(form)
                .setPositiveButton(editing ? R.string.edit_site_ok : R.string.add_site_ok, null)
                .setNegativeButton(R.string.add_site_cancel, null)
                .create();
        dialog.setOnShowListener(d -> {
            Button ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            ok.setOnClickListener(v -> {
                String u = url.getText() != null ? url.getText().toString() : "";
                if (SiteStore.normalizeUrl(u) == null) {
                    Toast.makeText(this, R.string.add_site_bad_url, Toast.LENGTH_SHORT).show();
                    return;
                }
                String t = name.getText() != null ? name.getText().toString() : "";
                boolean okSave;
                if (editing) {
                    focusSiteId = existing.id;
                    okSave = store.update(existing.id, t, u);
                    if (!okSave) {
                        Toast.makeText(this, R.string.edit_site_failed, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Toast.makeText(this, R.string.site_saved, Toast.LENGTH_SHORT).show();
                } else {
                    okSave = store.addCustom(t, u);
                    if (!okSave) {
                        Toast.makeText(this, R.string.add_site_failed, Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
                dialog.dismiss();
                rebuildUi();
                if (!editing && !sites.isEmpty()) {
                    showSlide(sites.size() - 1, false);
                    focusSiteId = sites.get(sites.size() - 1).id;
                    restoreTileFocus(focusSiteId);
                }
            });
            if (editing) name.requestFocus();
            else url.requestFocus();
        });
        dialog.show();
    }

    private void confirmRemove(Site site) {
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.remove_site_confirm, site.title))
                .setPositiveButton(R.string.remove_site, (d, w) -> {
                    store.remove(site.id);
                    Toast.makeText(this, R.string.site_removed, Toast.LENGTH_SHORT).show();
                    focusSiteId = null;
                    rebuildUi();
                    bannerFrame.requestFocus();
                })
                .setNegativeButton(R.string.add_site_cancel, null)
                .show();
    }

    private void setupCarousel() {
        handler.removeCallbacks(advanceCarousel);
        flipper.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Site site : sites) {
            View slide = inflater.inflate(R.layout.item_banner_slide, flipper, false);
            View bg = slide.findViewById(R.id.banner_bg);
            TextView label = slide.findViewById(R.id.banner_label);
            TextView title = slide.findViewById(R.id.banner_title);
            TextView desc = slide.findViewById(R.id.banner_desc);

            GradientDrawable fill = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[]{site.accentColor, 0xFF0B0D12, 0xFF050608});
            bg.setBackground(fill);
            label.setText(R.string.banner_open);
            title.setText(site.title);
            desc.setText(site.description);
            flipper.addView(slide);
        }
        flipper.setInAnimation(this, android.R.anim.fade_in);
        flipper.setOutAnimation(this, android.R.anim.fade_out);
        if (flipper.getChildCount() > 0) {
            int idx = Math.min(currentIndex, flipper.getChildCount() - 1);
            flipper.setDisplayedChild(Math.max(0, idx));
        }
    }

    private void setupDots() {
        dotsRow.removeAllViews();
        dots.clear();
        int size = (int) (8 * getResources().getDisplayMetrics().density);
        int gap = (int) (6 * getResources().getDisplayMetrics().density);
        for (int i = 0; i < sites.size(); i++) {
            ImageView dot = new ImageView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            if (i > 0) lp.setMarginStart(gap);
            dot.setLayoutParams(lp);
            dot.setImageResource(i == currentIndex ? R.drawable.dot_active : R.drawable.dot_inactive);
            dotsRow.addView(dot);
            dots.add(dot);
        }
    }

    private void showSlide(int index, boolean animate) {
        if (index < 0 || index >= flipper.getChildCount()) return;
        currentIndex = index;
        if (!sites.isEmpty()) focusSiteId = sites.get(index).id;
        if (!animate) {
            flipper.setInAnimation(null);
            flipper.setOutAnimation(null);
            flipper.setDisplayedChild(index);
            flipper.setInAnimation(this, android.R.anim.fade_in);
            flipper.setOutAnimation(this, android.R.anim.fade_out);
        } else {
            flipper.setDisplayedChild(index);
        }
        for (int i = 0; i < dots.size(); i++) {
            ((ImageView) dots.get(i)).setImageResource(
                    i == index ? R.drawable.dot_active : R.drawable.dot_inactive);
        }
    }

    private void scheduleCarousel() {
        handler.removeCallbacks(advanceCarousel);
        if (!carouselPaused && sites.size() > 1) {
            handler.postDelayed(advanceCarousel, CAROUSEL_INTERVAL_MS);
        }
    }

    private void openSite(Site site) {
        Intent i = new Intent(this, BrowserActivity.class);
        i.putExtra(EXTRA_URL, site.url);
        i.putExtra(EXTRA_TITLE, site.title);
        i.putExtra(EXTRA_AGGRESSIVE, site.aggressiveAdBlock);
        startActivity(i);
    }

    @Override
    protected void onResume() {
        super.onResume();
        scheduleCarousel();
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(advanceCarousel);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(advanceCarousel);
        super.onDestroy();
    }
}
