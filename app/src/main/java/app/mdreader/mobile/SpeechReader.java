package app.mdreader.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

final class SpeechReader {
    interface Listener {
        void onState(State state);
        void onError(String message);
    }

    static final class State {
        final boolean ready, active, paused;
        final int index, total;
        final String text;
        final boolean arabic;
        final float rate;
        State(boolean ready, boolean active, boolean paused, int index, int total, String text, boolean arabic, float rate) {
            this.ready = ready; this.active = active; this.paused = paused; this.index = index; this.total = total; this.text = text; this.arabic = arabic; this.rate = rate;
        }
    }

    private static final String PREF_RATE = "tts_speech_rate";
    private final SharedPreferences prefs;
    private final Listener listener;
    private TextToSpeech tts;
    private List<MarkdownSpeechPlan.Block> blocks = Collections.emptyList();
    private boolean ready = false, failed = false, active = false, paused = false, closed = false;
    private int index = 0, offset = 0, spokenBase = 0;
    private float rate;
    private long generation = 0;
    private String utteranceId = "";

    SpeechReader(Context context, SharedPreferences prefs, Listener listener) {
        this.prefs = prefs;
        this.listener = listener;
        this.rate = clampRate(prefs.getFloat(PREF_RATE, 1f));
        tts = new TextToSpeech(context.getApplicationContext(), status -> {
            synchronized (SpeechReader.this) {
                if (closed) return;
                if (status != TextToSpeech.SUCCESS) {
                    failed = true; ready = false; active = false;
                    error("تعذر تهيئة محرك القراءة بالصوت على الجهاز");
                    notifyState();
                    return;
                }
                ready = true; failed = false;
                tts.setSpeechRate(rate);
                tts.setOnUtteranceProgressListener(progressListener);
                notifyState();
                if (active && !paused && !blocks.isEmpty()) speakCurrent();
            }
        });
    }

    synchronized void start(List<MarkdownSpeechPlan.Block> source) {
        if (source == null || source.isEmpty()) { error("لا يوجد نص مناسب للقراءة"); return; }
        blocks = new ArrayList<>(source);
        index = 0; offset = 0; active = true; paused = false; generation++;
        notifyState();
        if (ready) speakCurrent();
        else if (failed) error("محرك القراءة بالصوت غير متاح على الجهاز");
    }

    synchronized void pause() {
        if (!active || paused) return;
        paused = true;
        if (tts != null) tts.stop();
        notifyState();
    }

    synchronized void resume() {
        if (!active || !paused) return;
        paused = false; generation++;
        notifyState();
        if (ready) speakCurrent();
    }

    synchronized void stop() {
        active = false; paused = false; offset = 0; generation++; utteranceId = "";
        if (tts != null) tts.stop();
        notifyState();
    }

    synchronized void next() {
        if (blocks.isEmpty()) return;
        if (tts != null) tts.stop();
        index = Math.min(blocks.size() - 1, index + 1); offset = 0; active = true; paused = false; generation++;
        notifyState(); if (ready) speakCurrent();
    }

    synchronized void previous() {
        if (blocks.isEmpty()) return;
        if (tts != null) tts.stop();
        index = Math.max(0, index - 1); offset = 0; active = true; paused = false; generation++;
        notifyState(); if (ready) speakCurrent();
    }

    synchronized void setRate(float value) {
        rate = clampRate(value);
        prefs.edit().putFloat(PREF_RATE, rate).apply();
        if (tts != null && ready) tts.setSpeechRate(rate);
        if (active && !paused && ready) { generation++; speakCurrent(); }
        notifyState();
    }

    synchronized float rate() { return rate; }
    synchronized boolean isActive() { return active; }
    synchronized boolean isPaused() { return paused; }
    synchronized State state() { return makeState(); }

    synchronized void shutdown() {
        closed = true; active = false; paused = false; blocks = Collections.emptyList();
        if (tts != null) { tts.stop(); tts.shutdown(); tts = null; }
    }

    private synchronized void speakCurrent() {
        if (!ready || tts == null || !active || paused || blocks.isEmpty()) return;
        if (index < 0 || index >= blocks.size()) { active = false; notifyState(); return; }
        MarkdownSpeechPlan.Block block = blocks.get(index);
        String full = block.text == null ? "" : block.text;
        if (offset >= full.length()) offset = 0;
        String part = full.substring(Math.max(0, offset)).trim();
        if (part.isEmpty()) { advanceAfterDone(); return; }

        setLanguage(block.arabic);
        tts.setSpeechRate(rate);
        spokenBase = Math.max(0, offset);
        utteranceId = "mdr-" + generation + "-" + index;
        Bundle params = new Bundle();
        int result = tts.speak(part, TextToSpeech.QUEUE_FLUSH, params, utteranceId);
        if (result == TextToSpeech.ERROR) {
            active = false;
            error("تعذر بدء نطق الفقرة الحالية");
        }
        notifyState();
    }

    private void setLanguage(boolean arabic) {
        Locale wanted = arabic ? new Locale("ar") : Locale.ENGLISH;
        int available = tts.isLanguageAvailable(wanted);
        if (available >= TextToSpeech.LANG_AVAILABLE) tts.setLanguage(wanted);
        else {
            Locale fallback = Locale.getDefault();
            if (tts.isLanguageAvailable(fallback) >= TextToSpeech.LANG_AVAILABLE) tts.setLanguage(fallback);
        }
    }

    private synchronized void advanceAfterDone() {
        if (!active || paused) return;
        if (index + 1 >= blocks.size()) {
            active = false; paused = false; offset = 0; notifyState(); return;
        }
        index++; offset = 0; generation++; notifyState(); speakCurrent();
    }

    private final UtteranceProgressListener progressListener = new UtteranceProgressListener() {
        @Override public void onStart(String id) { synchronized (SpeechReader.this) { if (id.equals(utteranceId)) notifyState(); } }
        @Override public void onDone(String id) { synchronized (SpeechReader.this) { if (id.equals(utteranceId)) advanceAfterDone(); } }
        @Override public void onError(String id) { synchronized (SpeechReader.this) { if (id.equals(utteranceId)) { active = false; error("حدث خطأ أثناء القراءة بالصوت"); notifyState(); } } }
        @Override public void onError(String id, int errorCode) { onError(id); }
        @Override public void onStop(String id, boolean interrupted) { synchronized (SpeechReader.this) { if (id.equals(utteranceId)) notifyState(); } }
        @Override public void onRangeStart(String id, int start, int end, int frame) {
            synchronized (SpeechReader.this) {
                if (id.equals(utteranceId)) offset = Math.max(0, spokenBase + start);
            }
        }
    };

    private State makeState() {
        String text = blocks.isEmpty() || index < 0 || index >= blocks.size() ? "" : blocks.get(index).text;
        boolean ar = !blocks.isEmpty() && index >= 0 && index < blocks.size() && blocks.get(index).arabic;
        return new State(ready, active, paused, blocks.isEmpty() ? 0 : index, blocks.size(), text, ar, rate);
    }

    private void notifyState() { if (listener != null) listener.onState(makeState()); }
    private void error(String message) { if (listener != null) listener.onError(message); }
    private static float clampRate(float value) { return Math.max(0.5f, Math.min(2f, value)); }
}
