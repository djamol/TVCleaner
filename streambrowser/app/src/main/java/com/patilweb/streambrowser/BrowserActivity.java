package com.patilweb.streambrowser;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.util.Locale;

public class BrowserActivity extends Activity implements TvCursorView.Listener {

    private static final int REQ_MIC = 42;

    private WebView webView;
    private TvCursorView cursor;
    private ProgressBar progress;
    private View menuBar;
    private EditText imeBridge;
    private FrameLayout fullscreenContainer;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private PermissionRequest pendingWebPermission;
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private SpeechHelper speechHelper;
    private boolean aggressive;
    private boolean menuVisible;
    private boolean keyboardVisible;
    private boolean syncingBridge;
    private boolean voiceListening;
    private String currentPageUrl = "";
    private final Handler handler = new Handler(Looper.getMainLooper());

    /** Unmute HTML5 / common players that start muted for autoplay policies. */
    private static final String UNMUTE_JS =
            "(function(){"
                    + "if(window.__stvAudio)return;window.__stvAudio=1;"
                    + "function fix(v){try{if(!v)return;v.muted=false;v.defaultMuted=false;"
                    + "v.volume=1;v.removeAttribute('muted');"
                    + "var p=v.play();if(p&&p.catch)p.catch(function(){});}catch(e){}}"
                    + "function scan(){document.querySelectorAll('video,audio').forEach(fix);}"
                    + "scan();"
                    + "document.addEventListener('play',function(ev){fix(ev.target);},true);"
                    + "new MutationObserver(scan).observe(document.documentElement||document.body,"
                    + "{childList:true,subtree:true});"
                    + "})();";

    /** Ask HTML5 / common players to enter fullscreen when playback starts. */
    private static final String AUTO_FULLSCREEN_JS =
            "(function(){"
                    + "if(window.__stvFs)return;window.__stvFs=1;"
                    + "function goFs(el){try{"
                    + "if(!el)return;"
                    + "var r=el.requestFullscreen||el.webkitRequestFullscreen||el.webkitEnterFullscreen"
                    + "||el.mozRequestFullScreen||el.msRequestFullscreen;"
                    + "if(r)r.call(el);"
                    + "}catch(e){}}"
                    + "function wire(v){if(!v||v.__stvWired)return;v.__stvWired=1;"
                    + "v.addEventListener('play',function(){goFs(v);},true);"
                    + "v.addEventListener('playing',function(){goFs(v);},true);"
                    + "}"
                    + "function scan(){document.querySelectorAll('video').forEach(wire);}"
                    + "scan();"
                    + "new MutationObserver(scan).observe(document.documentElement||document.body,"
                    + "{childList:true,subtree:true});"
                    + "})();";

    /**
     * Polyfill Web Speech API with the native {@code StvSpeech} bridge so audio→text sites
     * (SpeechTexter, Dictation.io) work on Android TV WebView.
     */
    private static final String SPEECH_POLYFILL_JS =
            "(function(){"
                    + "if(window.__stvSpeechPoly)return;window.__stvSpeechPoly=1;"
                    + "if(window.webkitSpeechRecognition||window.SpeechRecognition)return;"
                    + "if(!window.StvSpeech)return;"
                    + "function Rec(){"
                    + "  this.lang='en-US';this.continuous=false;this.interimResults=true;"
                    + "  this.onresult=null;this.onerror=null;this.onend=null;this.onstart=null;"
                    + "  var self=this;"
                    + "  this.start=function(){try{window.StvSpeech.start(String(self.lang||'en-US'));"
                    + "    if(self.onstart)self.onstart();}catch(e){if(self.onerror)self.onerror({error:'not-allowed'});}};"
                    + "  this.stop=function(){try{window.StvSpeech.stop();}catch(e){}};"
                    + "  this.abort=this.stop;"
                    + "  window.__stvRec=this;"
                    + "}"
                    + "window.webkitSpeechRecognition=Rec;window.SpeechRecognition=Rec;"
                    + "window.__stvOnPartial=function(t){var r=window.__stvRec;if(!r||!r.onresult)return;"
                    + "  var item=[{transcript:String(t||''),confidence:0.7}];item.isFinal=false;"
                    + "  r.onresult({resultIndex:0,results:[item]});};"
                    + "window.__stvOnFinal=function(t){var r=window.__stvRec;if(!r||!r.onresult)return;"
                    + "  var item=[{transcript:String(t||''),confidence:0.9}];item.isFinal=true;"
                    + "  r.onresult({resultIndex:0,results:[item]});};"
                    + "window.__stvOnSpeechError=function(m){var r=window.__stvRec;if(r&&r.onerror)r.onerror({error:String(m||'error')});};"
                    + "window.__stvOnSpeechEnd=function(){var r=window.__stvRec;if(r&&r.onend)r.onend();};"
                    + "})();";

    /** Notify Android when a page input gains focus (covers mouse-less / JS focus). */
    private static final String FOCUS_HOOK_JS =
            "(function(){"
                    + "if(window.__stvKb)return;window.__stvKb=1;"
                    + "function editable(el){"
                    + "  if(!el)return false;"
                    + "  var tag=(el.tagName||'').toLowerCase();"
                    + "  if(tag==='textarea')return true;"
                    + "  if(tag==='input'){"
                    + "    var type=(el.type||'text').toLowerCase();"
                    + "    return ['button','submit','checkbox','radio','file','image','reset',"
                    + "            'hidden','range','color'].indexOf(type)<0;"
                    + "  }"
                    + "  if(el.isContentEditable)return true;"
                    + "  var role=(el.getAttribute('role')||'').toLowerCase();"
                    + "  return role==='textbox'||role==='searchbox'||role==='combobox';"
                    + "}"
                    + "function notify(){"
                    + "  try{if(editable(document.activeElement))"
                    + "    window.StvKeyboard.onInputFocused(String(document.activeElement.value||''));}"
                    + "  catch(e){}"
                    + "}"
                    + "document.addEventListener('focusin',function(ev){"
                    + "  if(editable(ev.target)){"
                    + "    try{window.StvKeyboard.onInputFocused(String(ev.target.value||''));}"
                    + "    catch(e){}"
                    + "  }"
                    + "},true);"
                    + "document.addEventListener('click',function(){setTimeout(notify,30);},true);"
                    + "})();";

    /** Write bridge text into the focused editable (Google search textarea included). */
    private static final String SET_VALUE_JS =
            "(function(v){try{"
                    + "var el=document.activeElement;"
                    + "if(!el)return false;"
                    + "var tag=(el.tagName||'').toLowerCase();"
                    + "if(tag==='input'||tag==='textarea'){"
                    + "  var proto=tag==='textarea'?window.HTMLTextAreaElement.prototype"
                    + "                              :window.HTMLInputElement.prototype;"
                    + "  var setter=Object.getOwnPropertyDescriptor(proto,'value');"
                    + "  if(setter&&setter.set)setter.set.call(el,v);else el.value=v;"
                    + "  el.dispatchEvent(new Event('input',{bubbles:true}));"
                    + "  el.dispatchEvent(new Event('change',{bubbles:true}));"
                    + "  return true;"
                    + "}"
                    + "if(el.isContentEditable){el.textContent=v;"
                    + "  el.dispatchEvent(new Event('input',{bubbles:true}));return true;}"
                    + "return false;"
                    + "}catch(e){return false;}})";

    private static final String SUBMIT_JS =
            "(function(){try{"
                    + "var el=document.activeElement;if(!el)return;"
                    + "var e=new KeyboardEvent('keydown',{key:'Enter',code:'Enter',keyCode:13,"
                    + "which:13,bubbles:true,cancelable:true});"
                    + "el.dispatchEvent(e);"
                    + "if(el.form)el.form.submit();"
                    + "else{"
                    + "  var btn=document.querySelector("
                    + "    'input[type=submit],button[type=submit],button[aria-label*=Search],"
                    + "     button[aria-label*=search],input[name=btnK]');"
                    + "  if(btn)btn.click();"
                    + "}"
                    + "}catch(e){}})();";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browser);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        String url = getIntent().getStringExtra(HomeActivity.EXTRA_URL);
        aggressive = getIntent().getBooleanExtra(HomeActivity.EXTRA_AGGRESSIVE, false);
        if (url == null || url.isEmpty()) {
            finish();
            return;
        }
        currentPageUrl = url;

        webView = findViewById(R.id.webview);
        cursor = findViewById(R.id.tv_cursor);
        progress = findViewById(R.id.progress);
        menuBar = findViewById(R.id.menu_bar);
        imeBridge = findViewById(R.id.ime_bridge);
        fullscreenContainer = findViewById(R.id.fullscreen_container);
        cursor.attach(webView);
        cursor.setListener(this);
        setupImeBridge();

        Button btnBack = findViewById(R.id.btn_back_page);
        Button btnForward = findViewById(R.id.btn_forward);
        Button btnReload = findViewById(R.id.btn_reload);
        Button btnKeyboard = findViewById(R.id.btn_keyboard);
        Button btnVoice = findViewById(R.id.btn_voice);
        Button btnHome = findViewById(R.id.btn_home);

        btnBack.setOnClickListener(v -> {
            if (webView.canGoBack()) webView.goBack();
            hideMenu();
        });
        btnForward.setOnClickListener(v -> {
            if (webView.canGoForward()) webView.goForward();
            hideMenu();
        });
        btnReload.setOnClickListener(v -> {
            webView.reload();
            hideMenu();
        });
        btnKeyboard.setOnClickListener(v -> {
            hideMenu();
            toggleKeyboard();
        });
        btnVoice.setOnClickListener(v -> {
            hideMenu();
            toggleVoiceTyping();
        });
        btnHome.setOnClickListener(v -> finish());

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        speechHelper = new SpeechHelper(this);
        ensureMicPermission();
        setupWebView();
        webView.loadUrl(url);
        restoreMouseMode();
        requestPlaybackAudioFocus();
    }

    private void ensureMicPermission() {
        if (Build.VERSION.SDK_INT < 23) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_MIC) return;
        if (pendingWebPermission != null) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                grantWebPermissions(pendingWebPermission);
            } else {
                pendingWebPermission.deny();
            }
            pendingWebPermission = null;
        }
    }

    private void requestPlaybackAudioFocus() {
        if (audioManager == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                if (audioFocusRequest == null) {
                    audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                            .setAudioAttributes(new AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                                    .build())
                            .setOnAudioFocusChangeListener(focusChange -> { })
                            .build();
                }
                audioManager.requestAudioFocus(audioFocusRequest);
            } else {
                audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN);
            }
            // Unmute music stream if something (Assistant) left it muted.
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_UNMUTE, 0);
        } catch (Exception ignored) { }
    }

    private void abandonPlaybackAudioFocus() {
        if (audioManager == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 26 && audioFocusRequest != null) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest);
            } else {
                audioManager.abandonAudioFocus(null);
            }
        } catch (Exception ignored) { }
    }

    private void grantWebPermissions(PermissionRequest request) {
        if (request == null) return;
        try {
            // Prefer audio-only when the page asked for mic (speech sites).
            String[] wanted = request.getResources();
            java.util.ArrayList<String> grant = new java.util.ArrayList<>();
            for (String r : wanted) {
                if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)
                        || PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)
                        || PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID.equals(r)) {
                    grant.add(r);
                }
            }
            if (grant.isEmpty()) {
                request.grant(wanted);
            } else {
                request.grant(grant.toArray(new String[0]));
            }
            Toast.makeText(this, R.string.mic_ready, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            try {
                request.deny();
            } catch (Exception ignored) { }
        }
    }

    private void setupImeBridge() {
        imeBridge.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                if (syncingBridge || !keyboardVisible || webView == null) return;
                String text = s == null ? "" : s.toString();
                String escaped = jsonEscape(text);
                webView.evaluateJavascript(SET_VALUE_JS + "(\"" + escaped + "\")", null);
            }
        });
        imeBridge.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || actionId == EditorInfo.IME_ACTION_DONE
                    || actionId == EditorInfo.IME_ACTION_GO
                    || enter) {
                if (webView != null) webView.evaluateJavascript(SUBMIT_JS, null);
                hideKeyboard();
                return true;
            }
            return false;
        });
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        // Recent Chrome desktop UA — Kick/Twitch gate older WebView UAs and serve broken shells.
        s.setUserAgentString(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setFocusable(false);
        webView.setFocusableInTouchMode(false);
        webView.addJavascriptInterface(new KeyboardBridge(), "StvKeyboard");
        webView.addJavascriptInterface(new SpeechBridge(), "StvSpeech");
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MUTE_AUDIO)) {
            WebViewCompat.setAudioMuted(webView, false);
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request == null || request.getUrl() == null) return false;
                String u = request.getUrl().toString();
                if (AdBlocker.shouldBlock(u, currentPageUrl)) return true;
                String scheme = request.getUrl().getScheme();
                if (scheme != null && !scheme.equals("http") && !scheme.equals("https")) {
                    return true;
                }
                return false;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (AdBlocker.shouldBlock(request, currentPageUrl)) return AdBlocker.emptyResponse();
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (url != null) currentPageUrl = url;
                progress.setVisibility(View.VISIBLE);
                progress.setProgress(5);
                if (keyboardVisible) hideKeyboard();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (url != null) currentPageUrl = url;
                progress.setVisibility(View.GONE);
                injectPageScripts();
                if (!keyboardVisible) restoreMouseMode();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
                if (newProgress > 40) injectPageScripts();
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                if (request == null) return;
                handler.post(() -> {
                    boolean needsMic = false;
                    for (String r : request.getResources()) {
                        if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) {
                            needsMic = true;
                            break;
                        }
                    }
                    if (needsMic && Build.VERSION.SDK_INT >= 23
                            && checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                            != PackageManager.PERMISSION_GRANTED) {
                        pendingWebPermission = request;
                        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
                        return;
                    }
                    grantWebPermissions(request);
                    requestPlaybackAudioFocus();
                });
            }

            @Override
            public void onPermissionRequestCanceled(PermissionRequest request) {
                if (pendingWebPermission == request) pendingWebPermission = null;
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture,
                                         android.os.Message resultMsg) {
                Toast.makeText(BrowserActivity.this, R.string.blocked_popup, Toast.LENGTH_SHORT).show();
                return false;
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                enterFullscreen(view, callback);
            }

            @Override
            public void onHideCustomView() {
                exitFullscreen();
            }
        });
    }

    private void injectPageScripts() {
        webView.evaluateJavascript(AdBlocker.cleanupScript(aggressive, currentPageUrl), null);
        webView.evaluateJavascript(SPEECH_POLYFILL_JS, null);
        // Auto-fullscreen breaks Kick/Twitch React players — only on junk stream sites.
        if (!AdBlocker.isProtectedPage(currentPageUrl)) {
            webView.evaluateJavascript(AUTO_FULLSCREEN_JS, null);
        }
        webView.evaluateJavascript(FOCUS_HOOK_JS, null);
        webView.evaluateJavascript(UNMUTE_JS, null);
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MUTE_AUDIO)) {
            WebViewCompat.setAudioMuted(webView, false);
        }
    }

    private final class KeyboardBridge {
        @JavascriptInterface
        public void onInputFocused(final String currentValue) {
            handler.post(() -> showKeyboard(currentValue == null ? "" : currentValue, true));
        }
    }

    private final class SpeechBridge {
        @JavascriptInterface
        public void start(String lang) {
            handler.post(() -> startNativeSpeech(lang, true));
        }

        @JavascriptInterface
        public void stop() {
            handler.post(() -> stopNativeSpeech(false));
        }
    }

    private void toggleVoiceTyping() {
        if (voiceListening) {
            stopNativeSpeech(true);
            return;
        }
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, R.string.voice_need_mic, Toast.LENGTH_LONG).show();
            ensureMicPermission();
            return;
        }
        if (speechHelper == null || !speechHelper.available()) {
            Toast.makeText(this, R.string.voice_unavailable, Toast.LENGTH_LONG).show();
            return;
        }
        startNativeSpeech(Locale.getDefault().toLanguageTag(), false);
    }

    private void startNativeSpeech(String lang, boolean fromWeb) {
        if (speechHelper == null) return;
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ensureMicPermission();
            if (fromWeb) {
                webView.evaluateJavascript(
                        "try{window.__stvOnSpeechError&&window.__stvOnSpeechError('not-allowed');"
                                + "window.__stvOnSpeechEnd&&window.__stvOnSpeechEnd();}catch(e){}",
                        null);
            }
            return;
        }
        requestPlaybackAudioFocus();
        voiceListening = true;
        if (!fromWeb) {
            Toast.makeText(this, R.string.voice_listening, Toast.LENGTH_SHORT).show();
        }
        speechHelper.start(lang, new SpeechHelper.Listener() {
            @Override
            public void onPartial(String text) {
                if (fromWeb) {
                    webView.evaluateJavascript(
                            "try{window.__stvOnPartial&&window.__stvOnPartial(\""
                                    + jsonEscape(text) + "\");}catch(e){}", null);
                }
            }

            @Override
            public void onFinal(String text) {
                if (fromWeb) {
                    webView.evaluateJavascript(
                            "try{window.__stvOnFinal&&window.__stvOnFinal(\""
                                    + jsonEscape(text) + "\");}catch(e){}", null);
                } else {
                    appendVoiceToPage(text);
                }
            }

            @Override
            public void onError(String message) {
                voiceListening = false;
                if (fromWeb) {
                    webView.evaluateJavascript(
                            "try{window.__stvOnSpeechError&&window.__stvOnSpeechError(\""
                                    + jsonEscape(message) + "\");}catch(e){}", null);
                } else {
                    Toast.makeText(BrowserActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onEnded() {
                voiceListening = false;
                if (fromWeb) {
                    webView.evaluateJavascript(
                            "try{window.__stvOnSpeechEnd&&window.__stvOnSpeechEnd();}catch(e){}", null);
                }
            }
        });
    }

    private void stopNativeSpeech(boolean toast) {
        voiceListening = false;
        if (speechHelper != null) speechHelper.stop();
        if (toast) Toast.makeText(this, R.string.voice_stopped, Toast.LENGTH_SHORT).show();
    }

    private void appendVoiceToPage(String text) {
        if (text == null || text.isEmpty() || webView == null) return;
        // Append into focused field, or seed the IME bridge.
        String escaped = jsonEscape(text);
        webView.evaluateJavascript(
                "(function(t){try{"
                        + "var el=document.activeElement;"
                        + "function editable(e){if(!e)return false;var tag=(e.tagName||'').toLowerCase();"
                        + "  return tag==='textarea'||tag==='input'||e.isContentEditable;}"
                        + "if(editable(el)){"
                        + "  if(el.isContentEditable){el.textContent=(el.textContent||'')+(el.textContent?' ':'')+t;}"
                        + "  else{el.value=(el.value||'')+(el.value?' ':'')+t;"
                        + "    el.dispatchEvent(new Event('input',{bubbles:true}));}"
                        + "  return 'ok';"
                        + "}"
                        + "return 'none';"
                        + "}catch(e){return 'err';}})(\"" + escaped + "\")",
                value -> {
                    if (value != null && value.contains("none")) {
                        showKeyboard(text, false);
                    } else {
                        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    public void onCursorClicked(boolean hitInput) {
        if (hitInput) {
            // Read current field value, then open the TV keyboard bridge.
            webView.evaluateJavascript(
                    "(function(){try{var el=document.activeElement;"
                            + "return el?(el.value!=null?String(el.value):String(el.textContent||'')):'';}"
                            + "catch(e){return '';}})()",
                    value -> {
                        String text = unwrapJsString(value);
                        showKeyboard(text, true);
                    });
        } else {
            handler.postDelayed(this::restoreMouseMode, 80);
        }
    }

    private void toggleKeyboard() {
        if (keyboardVisible) {
            hideKeyboard();
            Toast.makeText(this, R.string.keyboard_hidden, Toast.LENGTH_SHORT).show();
        } else {
            showKeyboard("", false);
        }
    }

    private void showKeyboard(String seed, boolean fromField) {
        if (webView == null || imeBridge == null) return;
        keyboardVisible = true;
        if (cursor != null) cursor.setVisibility(View.INVISIBLE);

        syncingBridge = true;
        imeBridge.setVisibility(View.VISIBLE);
        imeBridge.setText(seed);
        if (seed != null) imeBridge.setSelection(seed.length());
        syncingBridge = false;

        imeBridge.setFocusable(true);
        imeBridge.setFocusableInTouchMode(true);
        imeBridge.requestFocus();

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);

        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.restartInput(imeBridge);
            imm.showSoftInput(imeBridge, InputMethodManager.SHOW_FORCED);
            // TV IMEs (Leanback / Gboard TV) often need a second nudge after layout.
            handler.postDelayed(() -> {
                if (!keyboardVisible) return;
                imeBridge.requestFocus();
                imm.showSoftInput(imeBridge, InputMethodManager.SHOW_IMPLICIT);
                try {
                    imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
                } catch (Exception ignored) { }
            }, 180);
            handler.postDelayed(() -> {
                if (!keyboardVisible) return;
                imeBridge.requestFocus();
                imm.showSoftInput(imeBridge, InputMethodManager.SHOW_FORCED);
            }, 400);
        }

        if (!fromField) {
            Toast.makeText(this, R.string.keyboard_shown, Toast.LENGTH_SHORT).show();
        }
    }

    private void hideKeyboard() {
        keyboardVisible = false;
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && imeBridge != null) {
            imm.hideSoftInputFromWindow(imeBridge.getWindowToken(), 0);
        }
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        if (imeBridge != null) {
            imeBridge.clearFocus();
            imeBridge.setVisibility(View.GONE);
        }
        restoreMouseMode();
    }

    private void restoreMouseMode() {
        if (keyboardVisible) return;
        if (webView != null) {
            webView.setFocusable(false);
            webView.setFocusableInTouchMode(false);
            webView.clearFocus();
        }
        if (!isFullscreen() && !menuVisible && cursor != null) {
            cursor.setVisibility(View.VISIBLE);
            cursor.requestFocus();
        }
    }

    private void enterFullscreen(View view, WebChromeClient.CustomViewCallback callback) {
        if (view == null) return;
        if (customView != null) {
            if (callback != null) callback.onCustomViewHidden();
            return;
        }
        customView = view;
        customViewCallback = callback;
        hideMenu();
        hideKeyboard();
        if (cursor != null) cursor.setVisibility(View.GONE);
        webView.setVisibility(View.GONE);
        fullscreenContainer.setVisibility(View.VISIBLE);
        fullscreenContainer.removeAllViews();
        fullscreenContainer.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        fullscreenContainer.setBackgroundColor(Color.BLACK);
        fullscreenContainer.requestFocus();
        applyImmersiveUi(true);
    }

    private void exitFullscreen() {
        if (customView == null) return;
        applyImmersiveUi(false);
        fullscreenContainer.removeAllViews();
        fullscreenContainer.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        if (customViewCallback != null) {
            try {
                customViewCallback.onCustomViewHidden();
            } catch (Exception ignored) { }
        }
        customView = null;
        customViewCallback = null;
        restoreMouseMode();
    }

    private boolean isFullscreen() {
        return customView != null;
    }

    private void applyImmersiveUi(boolean on) {
        View decor = getWindow().getDecorView();
        if (on) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            decor.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    private void toggleMenu() {
        if (isFullscreen()) return;
        if (menuVisible) hideMenu();
        else showMenu();
    }

    private void showMenu() {
        menuVisible = true;
        if (keyboardVisible) hideKeyboard();
        if (cursor != null) cursor.setVisibility(View.INVISIBLE);
        menuBar.setVisibility(View.VISIBLE);
        View first = findViewById(R.id.btn_back_page);
        first.requestFocus();
    }

    private void hideMenu() {
        menuVisible = false;
        menuBar.setVisibility(View.GONE);
        if (!isFullscreen() && !keyboardVisible) {
            restoreMouseMode();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return super.dispatchKeyEvent(event);
        }

        if (keyCode == KeyEvent.KEYCODE_MENU
                || keyCode == KeyEvent.KEYCODE_INFO
                || keyCode == KeyEvent.KEYCODE_TV_CONTENTS_MENU
                || keyCode == KeyEvent.KEYCODE_GUIDE) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                toggleMenu();
            }
            return true;
        }

        // While the IME bridge is up, let keys go to the EditText / IME.
        if (keyboardVisible && !menuVisible && !isFullscreen()) {
            return super.dispatchKeyEvent(event);
        }

        if (!menuVisible && !isFullscreen() && cursor != null
                && cursor.getVisibility() == View.VISIBLE
                && (TvCursorView.isDpad(keyCode) || TvCursorView.isClick(keyCode))) {
            if (!cursor.hasFocus()) cursor.requestFocus();
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                return cursor.onKeyDown(keyCode, event);
            }
            if (event.getAction() == KeyEvent.ACTION_UP) {
                return cursor.onKeyUp(keyCode, event);
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (keyboardVisible) {
                hideKeyboard();
                Toast.makeText(this, R.string.keyboard_hidden, Toast.LENGTH_SHORT).show();
                return true;
            }
            if (isFullscreen()) {
                webView.evaluateJavascript(
                        "(function(){try{if(document.fullscreenElement||document.webkitFullscreenElement)"
                                + "{(document.exitFullscreen||document.webkitExitFullscreen).call(document);}}"
                                + "catch(e){}})();",
                        null);
                exitFullscreen();
                return true;
            }
            if (menuVisible) {
                hideMenu();
                return true;
            }
            if (webView != null && webView.canGoBack()) {
                webView.goBack();
                return true;
            }
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onPause() {
        abandonPlaybackAudioFocus();
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
        requestPlaybackAudioFocus();
        if (isFullscreen()) applyImmersiveUi(true);
        else if (!keyboardVisible) restoreMouseMode();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (speechHelper != null) {
            speechHelper.destroy();
            speechHelper = null;
        }
        abandonPlaybackAudioFocus();
        if (isFullscreen()) exitFullscreen();
        if (webView != null) {
            webView.removeJavascriptInterface("StvKeyboard");
            webView.removeJavascriptInterface("StvSpeech");
            webView.stopLoading();
            ViewGroup parent = (ViewGroup) webView.getParent();
            if (parent != null) parent.removeView(webView);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private static String jsonEscape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"': sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String unwrapJsString(String value) {
        if (value == null || value.equals("null")) return "";
        String v = value.trim();
        if (v.length() >= 2 && v.charAt(0) == '"' && v.charAt(v.length() - 1) == '"') {
            v = v.substring(1, v.length() - 1);
            v = v.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n");
        }
        return v;
    }
}
