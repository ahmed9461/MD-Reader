package app.mdreader.mobile;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

final class AiTranslationService {
    static final String PREF_MODEL = "translation_ai_model";
    static final String PREF_ENDPOINT = "translation_ai_endpoint";
    static final String DEFAULT_MODEL = "gpt-5-mini";
    static final String DEFAULT_ENDPOINT = "https://api.openai.com/v1/responses";

    private static final int MAX_BATCH_SEGMENTS = 24;
    private static final int MAX_BATCH_CHARS = 12000;

    private final ApiKeyStore keyStore;
    private final SharedPreferences prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    AiTranslationService(ApiKeyStore keyStore, SharedPreferences prefs) {
        this.keyStore = keyStore;
        this.prefs = prefs;
    }

    void translateMarkdown(String source, boolean targetArabic, TranslationService.Callback callback) {
        if (closed.get()) {
            callback.onError(new IllegalStateException("خدمة الترجمة مغلقة"));
            return;
        }
        final String apiKey = keyStore.load();
        if (apiKey.isEmpty()) {
            callback.onError(new IllegalStateException("لم يتم حفظ مفتاح API بعد"));
            return;
        }
        final List<MarkdownTranslationPlan.Segment> segments = MarkdownTranslationPlan.segments(source, targetArabic);
        if (segments.isEmpty()) {
            callback.onSuccess(source == null ? "" : source);
            return;
        }
        callback.onStatus("الاتصال بخدمة الترجمة بالذكاء الاصطناعي…");
        executor.execute(() -> {
            try {
                List<String> translated = new ArrayList<>(segments.size());
                int index = 0;
                while (index < segments.size()) {
                    int start = index;
                    int chars = 0;
                    List<String> batch = new ArrayList<>();
                    while (index < segments.size() && batch.size() < MAX_BATCH_SEGMENTS) {
                        String text = segments.get(index).text;
                        if (!batch.isEmpty() && chars + text.length() > MAX_BATCH_CHARS) break;
                        batch.add(text);
                        chars += text.length();
                        index++;
                    }
                    if (batch.isEmpty()) {
                        batch.add(segments.get(index).text);
                        index++;
                    }
                    callback.onStatus("ترجمة النص بالذكاء الاصطناعي…");
                    List<String> values = requestBatch(apiKey, batch, targetArabic);
                    if (values.size() != batch.size()) {
                        throw new IllegalStateException("عدد نتائج الترجمة لا يطابق النص المرسل");
                    }
                    translated.addAll(values);
                    callback.onProgress(index, segments.size());
                    if (index <= start) throw new IllegalStateException("تعذر متابعة الترجمة");
                }
                callback.onSuccess(MarkdownTranslationPlan.apply(source, segments, translated));
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    private List<String> requestBatch(String apiKey, List<String> batch, boolean targetArabic) throws Exception {
        String endpoint = prefs.getString(PREF_ENDPOINT, DEFAULT_ENDPOINT);
        String model = prefs.getString(PREF_MODEL, DEFAULT_MODEL);
        if (endpoint == null || endpoint.trim().isEmpty()) endpoint = DEFAULT_ENDPOINT;
        if (model == null || model.trim().isEmpty()) model = DEFAULT_MODEL;

        JSONArray inputSegments = new JSONArray();
        for (String value : batch) inputSegments.put(value);
        JSONObject userPayload = new JSONObject()
                .put("target_language", targetArabic ? "Arabic" : "English")
                .put("segments", inputSegments);

        JSONObject itemSchema = new JSONObject().put("type", "string");
        JSONObject translationsSchema = new JSONObject()
                .put("type", "array")
                .put("items", itemSchema);
        JSONObject schema = new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject().put("translations", translationsSchema))
                .put("required", new JSONArray().put("translations"))
                .put("additionalProperties", false);
        JSONObject format = new JSONObject()
                .put("type", "json_schema")
                .put("name", "translation_batch")
                .put("strict", true)
                .put("schema", schema);

        String instructions = "You are a precise translation engine. Translate every input segment to the requested target language. " +
                "Keep commands, identifiers, paths, URLs, numbers, Markdown punctuation, and technical tokens unchanged unless they are ordinary prose. " +
                "Return one translation for every segment in the same order. Do not add explanations.";
        JSONObject body = new JSONObject()
                .put("model", model.trim())
                .put("instructions", instructions)
                .put("input", userPayload.toString())
                .put("store", false)
                .put("text", new JSONObject().put("format", format));

        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint.trim()).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(70000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept", "application/json");

        byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream out = connection.getOutputStream()) {
            out.write(payload);
        }

        int status = connection.getResponseCode();
        String response = readFully(status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream());
        connection.disconnect();
        if (status < 200 || status >= 300) {
            throw new IllegalStateException(readApiError(status, response));
        }

        String outputText = extractOutputText(new JSONObject(response));
        JSONObject result = new JSONObject(outputText);
        JSONArray values = result.getJSONArray("translations");
        List<String> translated = new ArrayList<>(values.length());
        for (int i = 0; i < values.length(); i++) translated.add(values.getString(i));
        return translated;
    }

    private static String extractOutputText(JSONObject root) throws JSONException {
        JSONArray output = root.optJSONArray("output");
        if (output == null) throw new JSONException("استجابة API لا تحتوي على output");
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < output.length(); i++) {
            JSONObject item = output.optJSONObject(i);
            if (item == null) continue;
            JSONArray content = item.optJSONArray("content");
            if (content == null) continue;
            for (int j = 0; j < content.length(); j++) {
                JSONObject part = content.optJSONObject(j);
                if (part == null) continue;
                if ("output_text".equals(part.optString("type"))) text.append(part.optString("text"));
            }
        }
        if (text.length() == 0) throw new JSONException("لم تُرجع خدمة AI نصًا مترجمًا");
        return text.toString();
    }

    private static String readApiError(int status, String body) {
        try {
            JSONObject root = new JSONObject(body == null ? "{}" : body);
            JSONObject error = root.optJSONObject("error");
            String message = error == null ? "" : error.optString("message");
            if (!message.isEmpty()) return "API " + status + ": " + message;
        } catch (Exception ignored) { }
        return "فشل طلب الترجمة (HTTP " + status + ")";
    }

    private static String readFully(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) out.append(line).append('\n');
        }
        return out.toString();
    }

    void shutdown() {
        closed.set(true);
        executor.shutdownNow();
    }
}
