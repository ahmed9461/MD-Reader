package app.mdreader.mobile;

import android.content.Context;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

final class OpenAiSpeechClient {
    static final String MODEL = "gpt-4o-mini-tts";
    static final String ENDPOINT = "https://api.openai.com/v1/audio/speech";
    static final String DEFAULT_VOICE = "marin";
    static final String[] VOICES = new String[]{"marin", "cedar", "coral", "alloy"};

    private static final long MAX_CACHE_BYTES = 96L * 1024L * 1024L;
    private static final String INSTRUCTIONS = "Read the supplied document text naturally and clearly. Use fluent Modern Standard Arabic for Arabic text and natural English pronunciation for English text. Preserve English technical terms as written. Do not translate, explain, add, omit, or paraphrase any content. Use calm pacing and brief natural pauses at punctuation and headings.";

    private final ApiKeyStore secrets;
    private final File cacheDir;
    private final ConcurrentHashMap<String, Object> keyLocks = new ConcurrentHashMap<>();

    OpenAiSpeechClient(Context context) {
        Context app = context.getApplicationContext();
        this.secrets = new ApiKeyStore(app);
        this.cacheDir = new File(app.getCacheDir(), "openai-speech-v1");
        if (!cacheDir.exists()) cacheDir.mkdirs();
    }

    boolean hasKey() { return secrets.hasKey(ApiKeyStore.PROVIDER_OPENAI); }

    File audio(String text, String voice, float speed) throws Exception {
        String input = text == null ? "" : text.trim();
        if (input.isEmpty()) throw new IllegalArgumentException("لا يوجد نص لتوليد الصوت");
        if (input.length() > 4096) throw new IllegalArgumentException("مقطع الصوت أطول من الحد المسموح");
        String selectedVoice = normalizeVoice(voice);
        float selectedSpeed = clampSpeed(speed);
        String key = sha256(MODEL + "\n" + selectedVoice + "\n" + String.format(Locale.US, "%.2f", selectedSpeed) + "\n" + INSTRUCTIONS + "\n" + input);
        File target = new File(cacheDir, key + ".mp3");
        if (target.isFile() && target.length() > 1024) {
            target.setLastModified(System.currentTimeMillis());
            return target;
        }

        Object lock = keyLocks.computeIfAbsent(key, k -> new Object());
        try {
            synchronized (lock) {
                if (target.isFile() && target.length() > 1024) {
                    target.setLastModified(System.currentTimeMillis());
                    return target;
                }
                request(input, selectedVoice, selectedSpeed, target);
                trimCache();
                return target;
            }
        } finally {
            keyLocks.remove(key, lock);
        }
    }

    void clearCache() {
        File[] files = cacheDir.listFiles();
        if (files == null) return;
        for (File file : files) if (file.isFile()) file.delete();
    }

    long cacheBytes() {
        long total = 0L;
        File[] files = cacheDir.listFiles();
        if (files != null) for (File file : files) if (file.isFile()) total += file.length();
        return total;
    }

    static String normalizeVoice(String value) {
        if (value != null) for (String voice : VOICES) if (voice.equalsIgnoreCase(value.trim())) return voice;
        return DEFAULT_VOICE;
    }

    private void request(String input, String voice, float speed, File target) throws Exception {
        String apiKey = secrets.load(ApiKeyStore.PROVIDER_OPENAI);
        if (apiKey.isEmpty()) throw new IllegalStateException("أضف مفتاح OpenAI أولًا من إعدادات الترجمة");

        JSONObject body = new JSONObject()
                .put("model", MODEL)
                .put("voice", voice)
                .put("input", input)
                .put("instructions", INSTRUCTIONS)
                .put("response_format", "mp3")
                .put("speed", speed);

        HttpURLConnection connection = (HttpURLConnection) new URL(ENDPOINT).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(25000);
        connection.setReadTimeout(90000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept", "audio/mpeg");
        byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(payload.length);
        try {
            connection.getOutputStream().write(payload);
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                String detail = readSmall(connection.getErrorStream());
                throw new IllegalStateException(friendlyError(status, detail));
            }
            File temp = new File(cacheDir, target.getName() + ".tmp-" + System.nanoTime());
            try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(temp)) {
                byte[] buffer = new byte[32768];
                int n;
                while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                out.getFD().sync();
            }
            if (temp.length() < 1024) {
                temp.delete();
                throw new IllegalStateException("استجابة الصوت من OpenAI كانت فارغة أو غير مكتملة");
            }
            if (target.exists()) target.delete();
            if (!temp.renameTo(target)) {
                copy(temp, target);
                temp.delete();
            }
        } finally {
            connection.disconnect();
        }
    }

    private void trimCache() {
        File[] files = cacheDir.listFiles((dir, name) -> name.endsWith(".mp3"));
        if (files == null || files.length == 0) return;
        long total = 0L;
        for (File f : files) total += f.length();
        if (total <= MAX_CACHE_BYTES) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        for (File file : files) {
            if (total <= MAX_CACHE_BYTES) break;
            long size = file.length();
            if (file.delete()) total -= size;
        }
    }

    private static void copy(File from, File to) throws Exception {
        try (FileInputStream in = new FileInputStream(from); FileOutputStream out = new FileOutputStream(to)) {
            byte[] buffer = new byte[32768];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            out.getFD().sync();
        }
    }

    private static String readSmall(InputStream in) {
        if (in == null) return "";
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[2048];
            int total = 0, n;
            while (total < 8192 && (n = input.read(buffer, 0, Math.min(buffer.length, 8192 - total))) != -1) {
                out.write(buffer, 0, n); total += n;
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception ignored) { return ""; }
    }

    private static String friendlyError(int status, String raw) {
        String message = "";
        try { message = new JSONObject(raw).optJSONObject("error").optString("message", ""); } catch (Exception ignored) {}
        if (status == 401) return "مفتاح OpenAI غير صالح أو انتهت صلاحيته";
        if (status == 429) return "تم تجاوز حد OpenAI أو الرصيد المتاح للصوت";
        if (status >= 500) return "خدمة OpenAI الصوتية غير متاحة مؤقتًا";
        if (!message.trim().isEmpty()) return "OpenAI: " + message.trim();
        return "فشل توليد الصوت من OpenAI (HTTP " + status + ")";
    }

    private static float clampSpeed(float value) { return Math.max(0.5f, Math.min(2f, value)); }

    private static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder(digest.length * 2);
        for (byte b : digest) out.append(String.format(Locale.US, "%02x", b & 0xff));
        return out.toString();
    }
}
