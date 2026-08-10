package app.mdreader.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class SpeechReader {
    static final String ENGINE_AUTO = "auto";
    static final String ENGINE_AI = "ai";
    static final String ENGINE_DEVICE = "device";

    interface Listener {
        void onState(State state);
        void onError(String message);
    }

    static final class State {
        final boolean ready, active, paused, preparing;
        final int index, total;
        final String text;
        final boolean arabic;
        final float rate;
        final String engine, voice;
        State(boolean ready, boolean active, boolean paused, boolean preparing, int index, int total, String text, boolean arabic, float rate, String engine, String voice) {
            this.ready = ready; this.active = active; this.paused = paused; this.preparing = preparing;
            this.index = index; this.total = total; this.text = text; this.arabic = arabic; this.rate = rate; this.engine = engine; this.voice = voice;
        }
    }

    private static final String PREF_RATE = "tts_speech_rate";
    private static final String PREF_ENGINE = "speech_engine_v2";
    private static final String PREF_AI_VOICE = "speech_ai_voice_v1";

    private final SharedPreferences prefs;
    private final Listener listener;
    private final OpenAiSpeechClient aiClient;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService network = Executors.newFixedThreadPool(2);

    private TextToSpeech tts;
    private MediaPlayer player;
    private List<MarkdownSpeechPlan.Block> blocks = Collections.emptyList();
    private boolean ttsReady = false, ttsFailed = false, active = false, paused = false, closed = false;
    private boolean preparing = false, aiPrepared = false, aiLoading = false;
    private int index = 0, offset = 0, spokenBase = 0;
    private float rate;
    private long generation = 0;
    private String utteranceId = "";
    private String engine;

    SpeechReader(Context context, SharedPreferences prefs, Listener listener) {
        this.prefs = prefs;
        this.listener = listener;
        this.aiClient = new OpenAiSpeechClient(context);
        this.rate = clampRate(prefs.getFloat(PREF_RATE, 1f));
        this.engine = resolveEngine();
        tts = new TextToSpeech(context.getApplicationContext(), status -> {
            synchronized (SpeechReader.this) {
                if (closed) return;
                if (status != TextToSpeech.SUCCESS) {
                    ttsFailed = true; ttsReady = false;
                    if (ENGINE_DEVICE.equals(engine) && active) {
                        active = false;
                        error("تعذر تهيئة محرك القراءة بالصوت على الجهاز");
                    }
                    notifyState();
                    return;
                }
                ttsReady = true; ttsFailed = false;
                tts.setSpeechRate(rate);
                tts.setOnUtteranceProgressListener(progressListener);
                notifyState();
                if (active && !paused && ENGINE_DEVICE.equals(engine) && !blocks.isEmpty()) speakLocalCurrent();
            }
        });
    }

    synchronized void start(List<MarkdownSpeechPlan.Block> source) {
        if (source == null || source.isEmpty()) { error("لا يوجد نص مناسب للقراءة"); return; }
        haltPlayback();
        blocks = new ArrayList<>(source);
        index = 0; offset = 0; active = true; paused = false; preparing = false; generation++;
        engine = resolveEngine();
        if (ENGINE_AI.equals(engine) && !aiClient.hasKey()) {
            active = false;
            error("أضف مفتاح OpenAI أولًا من إعدادات الترجمة لاستخدام صوت AI");
            notifyState();
            return;
        }
        notifyState();
        speakCurrent();
    }

    synchronized void pause() {
        if (!active || paused) return;
        paused = true;
        if (ENGINE_AI.equals(engine)) {
            try { if (player != null && aiPrepared && player.isPlaying()) player.pause(); } catch (Exception ignored) {}
        } else if (tts != null) tts.stop();
        notifyState();
    }

    synchronized void resume() {
        if (!active || !paused) return;
        paused = false;
        if (ENGINE_AI.equals(engine)) {
            try {
                if (player != null && aiPrepared) player.start();
                else if (!aiLoading) speakAiCurrent();
            } catch (Exception e) { failAi(generation, index, e); }
        } else {
            generation++;
            if (ttsReady) speakLocalCurrent();
        }
        notifyState();
    }

    synchronized void stop() {
        active = false; paused = false; preparing = false; offset = 0; generation++; utteranceId = "";
        haltPlayback();
        notifyState();
    }

    synchronized void next() {
        if (blocks.isEmpty()) return;
        haltPlayback();
        index = Math.min(blocks.size() - 1, index + 1); offset = 0; active = true; paused = false; preparing = false; generation++;
        engine = resolveEngine();
        notifyState(); speakCurrent();
    }

    synchronized void previous() {
        if (blocks.isEmpty()) return;
        haltPlayback();
        index = Math.max(0, index - 1); offset = 0; active = true; paused = false; preparing = false; generation++;
        engine = resolveEngine();
        notifyState(); speakCurrent();
    }

    synchronized void setRate(float value) {
        rate = clampRate(value);
        prefs.edit().putFloat(PREF_RATE, rate).apply();
        if (tts != null && ttsReady) tts.setSpeechRate(rate);
        if (active) {
            haltPlayback();
            offset = 0; paused = false; generation++; preparing = false;
            speakCurrent();
        }
        notifyState();
    }

    synchronized float rate() { return rate; }
    synchronized boolean isActive() { return active; }
    synchronized boolean isPaused() { return paused; }
    synchronized State state() { return makeState(); }

    synchronized String enginePreference() { return normalizeEngine(prefs.getString(PREF_ENGINE, ENGINE_AUTO)); }
    synchronized String effectiveEngine() { return engine; }
    synchronized void setEnginePreference(String value) {
        String next = normalizeEngine(value);
        prefs.edit().putString(PREF_ENGINE, next).apply();
        engine = resolveEngine();
        if (active) {
            haltPlayback(); offset = 0; paused = false; preparing = false; generation++;
            if (ENGINE_AI.equals(engine) && !aiClient.hasKey()) {
                active = false; error("أضف مفتاح OpenAI أولًا لاستخدام صوت AI");
            } else speakCurrent();
        }
        notifyState();
    }

    synchronized String voice() { return OpenAiSpeechClient.normalizeVoice(prefs.getString(PREF_AI_VOICE, OpenAiSpeechClient.DEFAULT_VOICE)); }
    synchronized void setVoice(String value) {
        String next = OpenAiSpeechClient.normalizeVoice(value);
        prefs.edit().putString(PREF_AI_VOICE, next).apply();
        if (active && ENGINE_AI.equals(engine)) {
            haltPlayback(); offset = 0; paused = false; preparing = false; generation++; speakCurrent();
        }
        notifyState();
    }

    synchronized boolean hasOpenAiKey() { return aiClient.hasKey(); }
    synchronized long aiCacheBytes() { return aiClient.cacheBytes(); }
    synchronized void clearAiCache() { aiClient.clearCache(); notifyState(); }
    synchronized void testVoice() {
        List<MarkdownSpeechPlan.Block> sample = MarkdownSpeechPlan.blocks("مرحبًا، هذه تجربة للصوت العربي الطبيعي. Welcome, this is an English voice test.");
        start(sample);
    }

    synchronized void shutdown() {
        closed = true; active = false; paused = false; blocks = Collections.emptyList(); generation++;
        haltPlayback();
        if (tts != null) { tts.stop(); tts.shutdown(); tts = null; }
        network.shutdownNow();
    }

    private synchronized void speakCurrent() {
        if (!active || paused || blocks.isEmpty() || index < 0 || index >= blocks.size()) return;
        if (ENGINE_AI.equals(engine)) speakAiCurrent(); else if (ttsReady) speakLocalCurrent(); else if (ttsFailed) {
            active = false; error("محرك القراءة بالصوت على الجهاز غير متاح"); notifyState();
        }
    }

    private synchronized void speakLocalCurrent() {
        if (!ttsReady || tts == null || !active || paused || blocks.isEmpty()) return;
        if (index < 0 || index >= blocks.size()) { active = false; notifyState(); return; }
        MarkdownSpeechPlan.Block block = blocks.get(index);
        String full = block.text == null ? "" : block.text;
        if (offset >= full.length()) offset = 0;
        String part = full.substring(Math.max(0, offset)).trim();
        if (part.isEmpty()) { advanceAfterDone(); return; }

        setLanguage(block.arabic);
        tts.setSpeechRate(rate);
        spokenBase = Math.max(0, offset);
        utteranceId = "mdr-local-" + generation + "-" + index;
        Bundle params = new Bundle();
        int result = tts.speak(part, TextToSpeech.QUEUE_FLUSH, params, utteranceId);
        if (result == TextToSpeech.ERROR) {
            active = false;
            error("تعذر بدء نطق الفقرة الحالية");
        }
        notifyState();
    }

    private synchronized void speakAiCurrent() {
        if (!active || paused || closed || blocks.isEmpty()) return;
        if (!aiClient.hasKey()) {
            active = false; preparing = false;
            error("أضف مفتاح OpenAI أولًا من إعدادات الترجمة لاستخدام صوت AI");
            notifyState(); return;
        }
        final int targetIndex = index;
        final long token = generation;
        final MarkdownSpeechPlan.Block block = blocks.get(targetIndex);
        final String selectedVoice = voice();
        final float selectedRate = rate;
        aiLoading = true; preparing = true; aiPrepared = false;
        notifyState();
        network.execute(() -> {
            try {
                File audio = aiClient.audio(block.text, selectedVoice, selectedRate);
                main.post(() -> playAiFile(token, targetIndex, audio));
            } catch (Exception e) {
                main.post(() -> failAi(token, targetIndex, e));
            }
        });
    }

    private synchronized void playAiFile(long token, int targetIndex, File audio) {
        if (closed || token != generation || targetIndex != index || !active || !ENGINE_AI.equals(engine)) return;
        releasePlayer();
        try {
            MediaPlayer next = new MediaPlayer();
            next.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            next.setDataSource(audio.getAbsolutePath());
            next.setOnPreparedListener(mp -> {
                synchronized (SpeechReader.this) {
                    if (closed || token != generation || targetIndex != index || !active || player != mp) { safeRelease(mp); return; }
                    aiLoading = false; preparing = false; aiPrepared = true;
                    if (!paused) {
                        try { mp.start(); } catch (Exception e) { failAi(token, targetIndex, e); return; }
                    }
                    notifyState();
                    prefetch(targetIndex + 1, token);
                }
            });
            next.setOnCompletionListener(mp -> {
                synchronized (SpeechReader.this) {
                    if (token == generation && targetIndex == index && player == mp) advanceAfterDone();
                }
            });
            next.setOnErrorListener((mp, what, extra) -> {
                synchronized (SpeechReader.this) {
                    if (token == generation && targetIndex == index) failAi(token, targetIndex, new IllegalStateException("تعذر تشغيل ملف الصوت المولد"));
                }
                return true;
            });
            player = next;
            next.prepareAsync();
        } catch (Exception e) { failAi(token, targetIndex, e); }
    }

    private void prefetch(int nextIndex, long token) {
        if (nextIndex < 0 || nextIndex >= blocks.size() || closed || !ENGINE_AI.equals(engine)) return;
        MarkdownSpeechPlan.Block block = blocks.get(nextIndex);
        String selectedVoice = voice(); float selectedRate = rate;
        network.execute(() -> {
            try {
                if (!closed && token == generation) aiClient.audio(block.text, selectedVoice, selectedRate);
            } catch (Exception ignored) {}
        });
    }

    private synchronized void failAi(long token, int targetIndex, Exception failure) {
        if (closed || token != generation || targetIndex != index) return;
        aiLoading = false; aiPrepared = false; preparing = false;
        releasePlayer();
        String message = failure == null || failure.getMessage() == null ? "تعذر توليد الصوت من OpenAI" : failure.getMessage();
        if (ENGINE_AUTO.equals(enginePreference())) {
            engine = ENGINE_DEVICE;
            error("تعذر صوت AI، وتم الانتقال تلقائيًا إلى صوت الجهاز.\n\n" + message);
            if (ttsReady && active && !paused) speakLocalCurrent();
            else if (ttsFailed) { active = false; notifyState(); }
        } else {
            active = false; paused = false;
            error(message);
            notifyState();
        }
    }

    private void setLanguage(boolean arabic) {
        if (tts == null) return;
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
        releasePlayer();
        if (index + 1 >= blocks.size()) {
            active = false; paused = false; preparing = false; offset = 0; notifyState(); return;
        }
        index++; offset = 0; preparing = false; generation++; notifyState(); speakCurrent();
    }

    private synchronized void haltPlayback() {
        if (tts != null) try { tts.stop(); } catch (Exception ignored) {}
        releasePlayer();
        aiLoading = false; aiPrepared = false;
    }

    private synchronized void releasePlayer() {
        MediaPlayer p = player; player = null; aiPrepared = false;
        if (p != null) safeRelease(p);
    }

    private static void safeRelease(MediaPlayer p) {
        try { p.setOnPreparedListener(null); p.setOnCompletionListener(null); p.setOnErrorListener(null); p.stop(); } catch (Exception ignored) {}
        try { p.reset(); } catch (Exception ignored) {}
        try { p.release(); } catch (Exception ignored) {}
    }

    private String resolveEngine() {
        String pref = normalizeEngine(prefs.getString(PREF_ENGINE, ENGINE_AUTO));
        if (ENGINE_AUTO.equals(pref)) return aiClient.hasKey() ? ENGINE_AI : ENGINE_DEVICE;
        return pref;
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
        boolean ready = ENGINE_AI.equals(engine) ? aiClient.hasKey() : ttsReady;
        return new State(ready, active, paused, preparing, blocks.isEmpty() ? 0 : index, blocks.size(), text, ar, rate, engine, voice());
    }

    private void notifyState() { if (listener != null) listener.onState(makeState()); }
    private void error(String message) { if (listener != null) listener.onError(message); }
    private static float clampRate(float value) { return Math.max(0.5f, Math.min(2f, value)); }
    private static String normalizeEngine(String value) {
        if (ENGINE_AI.equals(value) || ENGINE_DEVICE.equals(value)) return value;
        return ENGINE_AUTO;
    }
}
