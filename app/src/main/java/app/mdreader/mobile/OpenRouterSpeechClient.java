package app.mdreader.mobile;

import android.content.Context;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

final class OpenRouterSpeechClient {
    static final String DEFAULT_MODEL = "elevenlabs/eleven-flash-v2.5";
    static final String MULTILINGUAL_MODEL = "elevenlabs/eleven-multilingual-v2";
    static final String ENDPOINT = "https://openrouter.ai/api/v1/audio/speech";
    static final String DEFAULT_VOICE = "sarah";
    static final String[] VOICES = new String[]{"sarah", "george"};

    private static final long MAX_CACHE_BYTES = 96L * 1024L * 1024L;

    private final ApiKeyStore secrets;
    private final File cacheDir;
    private final ConcurrentHashMap<String, Object> keyLocks = new ConcurrentHashMap<>();

    OpenRouterSpeechClient(Context context) {
        Context app = context.getApplicationContext();
        this.secrets = new ApiKeyStore(app);
        // Reuse the cache directory so clearing it also removes pre-upgrade audio.
        this.cacheDir = new File(app.getCacheDir(), "openai-speech-v1");
        if (!cacheDir.exists()) cacheDir.mkdirs();
    }

    boolean hasKey() { return secrets.hasKey(ApiKeyStore.PROVIDER_OPENROUTER); }

    File audio(String text, String model, String voice) throws Exception {
        String input = text == null ? "" : text.trim();
        if (input.isEmpty()) throw new IllegalArgumentException("لا يوجد نص لتوليد الصوت");
        if (input.length() > 4096) throw new IllegalArgumentException("مقطع الصوت أطول من الحد المسموح");
        String selectedVoice = normalizeVoice(voice);
        String selectedModel = normalizeModel(model);
        String key = sha256(selectedModel + "\n" + selectedVoice + "\n" + input);
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
                request(input, selectedModel, selectedVoice, target);
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

    static String normalizeModel(String model) {
        return MULTILINGUAL_MODEL.equals(model) ? MULTILINGUAL_MODEL : DEFAULT_MODEL;
    }

    static JSONObject requestBody(String input, String model, String voice) throws org.json.JSONException {
        return new JSONObject().put("model", normalizeModel(model)).put("voice", normalizeVoice(voice))
                .put("input", input).put("response_format", "mp3");
    }

    private void request(String input, String model, String voice, File target) throws Exception {
        String apiKey = secrets.load(ApiKeyStore.PROVIDER_OPENROUTER);
        if (apiKey.isEmpty()) throw new IllegalStateException("أضف مفتاح OpenRouter أولًا من إعدادات الصوت أو الترجمة");

        JSONObject body = requestBody(input, model, voice);
        HttpURLConnection connection = (HttpURLConnection) new URL(ENDPOINT).openConnection();
        connection.setInstanceFollowRedirects(false);
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
            try (OutputStream request = connection.getOutputStream()) {
                request.write(payload);
                request.flush();
            }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                String detail = readSmall(connection.getErrorStream());
                throw new IllegalStateException(friendlyError(status, detail).replace(apiKey, "••••"));
            }
            String type = connection.getContentType();
            if (type != null && type.toLowerCase(Locale.ROOT).contains("json")) {
                throw new IllegalStateException("أعاد OpenRouter رسالة بدلاً من ملف صوت");
            }
            File temp = new File(cacheDir, target.getName() + ".tmp-" + System.nanoTime());
            try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(temp)) {
                byte[] buffer = new byte[32768];
                int n;
                while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                out.getFD().sync();
            } catch (Exception e) {
                temp.delete();
                throw e;
            }
            if (temp.length() < 1024) {
                temp.delete();
                throw new IllegalStateException("استجابة الصوت من OpenRouter كانت فارغة أو غير مكتملة");
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
        try {
            JSONObject error = new JSONObject(raw).optJSONObject("error");
            if (error != null) message = error.optString("message", "");
        } catch (Exception ignored) {}
        if (status == 401) return "مفتاح OpenRouter غير صالح أو انتهت صلاحيته";
        if (status == 402) return "رصيد OpenRouter غير كافٍ لتوليد الصوت";
        if (status == 429) return "تم تجاوز حد OpenRouter أو الرصيد المتاح للصوت";
        if (status >= 500) return "خدمة OpenRouter الصوتية غير متاحة مؤقتًا";
        if (!message.trim().isEmpty()) return "OpenRouter: " + message.trim();
        return "فشل توليد الصوت من OpenRouter (HTTP " + status + ")";
    }


    private static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder(digest.length * 2);
        for (byte b : digest) out.append(String.format(Locale.US, "%02x", b & 0xff));
        return out.toString();
    }
}
