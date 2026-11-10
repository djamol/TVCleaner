package com.patilweb.streambrowser;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import java.util.ArrayList;
import java.util.Locale;

/** Native mic → text for WebView pages (Web Speech API is often missing on Android TV). */
final class SpeechHelper {
    private static final String TAG = "StvSpeech";

    interface Listener {
        void onPartial(String text);
        void onFinal(String text);
        void onError(String message);
        void onEnded();
    }

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private Listener listener;
    private boolean listening;

    SpeechHelper(Context context) {
        // Prefer Activity context — SpeechRecognizer is flaky with Application context on some TVs.
        this.context = context;
    }

    boolean available() {
        return SpeechRecognizer.isRecognitionAvailable(context);
    }

    boolean isListening() {
        return listening;
    }

    void start(String lang, Listener listener) {
        this.listener = listener;
        main.post(() -> {
            stopInternal(false);
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                if (listener != null) listener.onError("Speech recognition not available on this TV");
                if (listener != null) listener.onEnded();
                return;
            }
            try {
                recognizer = SpeechRecognizer.createSpeechRecognizer(context);
                recognizer.setRecognitionListener(new RecognitionListener() {
                    @Override public void onReadyForSpeech(Bundle params) { }
                    @Override public void onBeginningOfSpeech() { }
                    @Override public void onRmsChanged(float rmsdB) { }
                    @Override public void onBufferReceived(byte[] buffer) { }
                    @Override public void onEndOfSpeech() { }

                    @Override
                    public void onError(int error) {
                        listening = false;
                        String msg;
                        switch (error) {
                            case SpeechRecognizer.ERROR_AUDIO: msg = "Mic audio error"; break;
                            case SpeechRecognizer.ERROR_CLIENT: msg = "Speech client error"; break;
                            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                                msg = "Mic permission denied"; break;
                            case SpeechRecognizer.ERROR_NETWORK:
                            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                                msg = "Speech network error"; break;
                            case SpeechRecognizer.ERROR_NO_MATCH: msg = "No speech matched"; break;
                            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: msg = "Recognizer busy"; break;
                            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: msg = "No speech heard"; break;
                            default: msg = "Speech error " + error; break;
                        }
                        Log.w(TAG, msg);
                        Listener l = SpeechHelper.this.listener;
                        if (l != null) {
                            l.onError(msg);
                            l.onEnded();
                        }
                    }

                    @Override
                    public void onResults(Bundle results) {
                        listening = false;
                        String text = firstResult(results);
                        Listener l = SpeechHelper.this.listener;
                        if (l != null) {
                            if (text != null && !text.isEmpty()) l.onFinal(text);
                            l.onEnded();
                        }
                    }

                    @Override
                    public void onPartialResults(Bundle partialResults) {
                        String text = firstResult(partialResults);
                        Listener l = SpeechHelper.this.listener;
                        if (l != null && text != null && !text.isEmpty()) l.onPartial(text);
                    }

                    @Override
                    public void onEvent(int eventType, Bundle params) { }
                });

                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
                intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
                String locale = (lang == null || lang.isEmpty())
                        ? Locale.getDefault().toLanguageTag() : lang;
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale);
                listening = true;
                recognizer.startListening(intent);
            } catch (Exception e) {
                listening = false;
                Log.e(TAG, "start failed", e);
                if (listener != null) {
                    listener.onError("Could not start mic: " + e.getMessage());
                    listener.onEnded();
                }
            }
        });
    }

    void stop() {
        main.post(() -> stopInternal(true));
    }

    void destroy() {
        main.post(() -> {
            stopInternal(false);
            listener = null;
        });
    }

    private void stopInternal(boolean notifyEnd) {
        listening = false;
        if (recognizer != null) {
            try { recognizer.stopListening(); } catch (Exception ignored) { }
            try { recognizer.cancel(); } catch (Exception ignored) { }
            try { recognizer.destroy(); } catch (Exception ignored) { }
            recognizer = null;
        }
        if (notifyEnd && listener != null) listener.onEnded();
    }

    private static String firstResult(Bundle results) {
        if (results == null) return null;
        ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (list == null || list.isEmpty()) return null;
        return list.get(0);
    }
}
