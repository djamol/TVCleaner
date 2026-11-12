package com.patilweb.streambrowser;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.ValueCallback;
import android.webkit.WebView;

/**
 * On-screen mouse pointer for Android TV remotes.
 * D-pad moves it (hold = keep moving), OK injects a tap into the WebView.
 */
public class TvCursorView extends View {

    public interface Listener {
        /** Called after a click; {@code hitInput} if an editable field was under the tip. */
        void onCursorClicked(boolean hitInput);
    }

    private static final float STEP_DP = 22f;
    private static final float FAST_DP = 48f;
    private static final long HOLD_TICK_MS = 28L;

    private WebView webView;
    private Listener listener;
    private Drawable arrow;
    private float cursorX;
    private float cursorY;
    private int arrowW;
    private int arrowH;
    private boolean placed;
    private int heldKey = -1;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable holdMove = new Runnable() {
        @Override
        public void run() {
            if (heldKey < 0) return;
            applyMoveForKey(heldKey, true);
            handler.postDelayed(this, HOLD_TICK_MS);
        }
    };

    public TvCursorView(Context context) {
        super(context);
        init();
    }

    public TvCursorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setFocusable(true);
        setFocusableInTouchMode(true);
        setClickable(true);
        setWillNotDraw(false);
        arrow = getResources().getDrawable(R.drawable.cursor_arrow, getContext().getTheme());
        float d = getResources().getDisplayMetrics().density;
        arrowW = Math.round(36 * d);
        arrowH = Math.round(36 * d);
        if (arrow != null) arrow.setBounds(0, 0, arrowW, arrowH);
    }

    public void attach(WebView web) {
        this.webView = web;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** Tip position in this view's coordinates (same as WebView visible coords). */
    public float tipX() {
        return cursorX + arrowW * 0.15f;
    }

    public float tipY() {
        return cursorY + arrowH * 0.12f;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (!placed && w > 0 && h > 0) {
            cursorX = w * 0.5f;
            cursorY = h * 0.45f;
            placed = true;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (arrow == null) return;
        canvas.save();
        canvas.translate(cursorX, cursorY);
        arrow.draw(canvas);
        canvas.restore();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (isDpad(keyCode)) {
            if (event.getRepeatCount() == 0) {
                heldKey = keyCode;
                applyMoveForKey(keyCode, false);
                handler.removeCallbacks(holdMove);
                handler.postDelayed(holdMove, 220);
            }
            return true;
        }
        if (isClick(keyCode)) {
            if (event.getRepeatCount() == 0) clickAtCursor();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (isDpad(keyCode)) {
            if (heldKey == keyCode) {
                heldKey = -1;
                handler.removeCallbacks(holdMove);
            }
            return true;
        }
        if (isClick(keyCode)) return true;
        return super.onKeyUp(keyCode, event);
    }

    @Override
    protected void onDetachedFromWindow() {
        heldKey = -1;
        handler.removeCallbacks(holdMove);
        super.onDetachedFromWindow();
    }

    private void applyMoveForKey(int keyCode, boolean repeating) {
        float d = getResources().getDisplayMetrics().density;
        float step = (repeating ? FAST_DP : STEP_DP) * d;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                move(-step, 0);
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                move(step, 0);
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                move(0, -step);
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                move(0, step);
                break;
            default:
                break;
        }
    }

    private void move(float dx, float dy) {
        float maxX = Math.max(0, getWidth() - arrowW);
        float maxY = Math.max(0, getHeight() - arrowH);
        float nextX = clamp(cursorX + dx, 0, maxX);
        float nextY = clamp(cursorY + dy, 0, maxY);

        if (webView != null) {
            int scroll = Math.round(56 * getResources().getDisplayMetrics().density);
            if (dx < 0 && cursorX <= 1) webView.scrollBy(-scroll, 0);
            if (dx > 0 && cursorX >= maxX - 1) webView.scrollBy(scroll, 0);
            if (dy < 0 && cursorY <= 1) webView.scrollBy(0, -scroll);
            if (dy > 0 && cursorY >= maxY - 1) webView.scrollBy(0, scroll);
        }

        cursorX = nextX;
        cursorY = nextY;
        invalidate();
    }

    public void clickAtCursor() {
        if (webView == null) return;
        final float x = tipX();
        final float y = tipY();

        // Temporarily allow WebView to take the synthetic touch as a real user gesture.
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);

        long downTime = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(downTime, downTime,
                MotionEvent.ACTION_DOWN, x, y, 0);
        webView.dispatchTouchEvent(down);
        down.recycle();

        handler.postDelayed(() -> {
            long upTime = SystemClock.uptimeMillis();
            MotionEvent up = MotionEvent.obtain(downTime, upTime,
                    MotionEvent.ACTION_UP, x, y, 0);
            webView.dispatchTouchEvent(up);
            up.recycle();

            // Detect AFTER the tap so Google's textarea is focused.
            float scale = webView.getScale();
            if (scale <= 0f) scale = 1f;
            final int jsX = Math.round(x / scale);
            final int jsY = Math.round(y / scale);
            String js = INPUT_DETECT_JS + "(" + jsX + "," + jsY + ")";
            webView.evaluateJavascript(js, (ValueCallback<String>) value -> {
                boolean hitInput = value != null && value.contains("input");
                if (listener != null) listener.onCursorClicked(hitInput);
            });
        }, 60);

        animate().alpha(0.4f).setDuration(50).withEndAction(() ->
                animate().alpha(1f).setDuration(70).start()).start();
    }

    /**
     * Walks from elementFromPoint / activeElement and recognises Google's search box
     * (textarea.gLFyf), role=combobox/searchbox, contenteditable, etc.
     */
    static final String INPUT_DETECT_JS =
            "(function(x,y){try{"
                    + "function editable(el){"
                    + "  if(!el||el===document.body||el===document.documentElement)return false;"
                    + "  var tag=(el.tagName||'').toLowerCase();"
                    + "  if(tag==='textarea')return true;"
                    + "  if(tag==='input'){"
                    + "    var type=(el.type||'text').toLowerCase();"
                    + "    return ['button','submit','checkbox','radio','file','image','reset',"
                    + "            'hidden','range','color'].indexOf(type)<0;"
                    + "  }"
                    + "  if(el.isContentEditable)return true;"
                    + "  var role=(el.getAttribute('role')||'').toLowerCase();"
                    + "  if(role==='textbox'||role==='searchbox'||role==='combobox')return true;"
                    + "  if(el.getAttribute('contenteditable')==='true')return true;"
                    + "  return false;"
                    + "}"
                    + "function findEditable(start){"
                    + "  var cur=start;"
                    + "  for(var i=0;cur&&i<12;i++){"
                    + "    if(editable(cur))return cur;"
                    + "    cur=cur.parentElement;"
                    + "  }"
                    + "  return null;"
                    + "}"
                    + "var hit=document.elementFromPoint(x,y);"
                    + "var el=findEditable(hit)||findEditable(document.activeElement);"
                    + "if(!el){"
                    + "  var q=document.querySelector("
                    + "    'textarea[name=q],input[name=q],textarea.gLFyf,input.gLFyf,[role=searchbox]');"
                    + "  if(q){var r=q.getBoundingClientRect();"
                    + "    if(x>=r.left-8&&x<=r.right+8&&y>=r.top-8&&y<=r.bottom+8)el=q;}"
                    + "}"
                    + "if(!el)return 'none';"
                    + "try{el.focus();}catch(e){}"
                    + "try{el.click();}catch(e){}"
                    + "return 'input';"
                    + "}catch(e){return 'err';}})";

    static boolean isDpad(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_DPAD_UP
                || keyCode == KeyEvent.KEYCODE_DPAD_DOWN;
    }

    static boolean isClick(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                || keyCode == KeyEvent.KEYCODE_BUTTON_A;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
