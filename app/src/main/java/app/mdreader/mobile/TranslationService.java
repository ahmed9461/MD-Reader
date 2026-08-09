package app.mdreader.mobile;

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.util.ArrayList;
import java.util.List;

final class TranslationService {
    interface Callback {
        void onProgress(int completed, int total);
        void onSuccess(String translated);
        void onError(Exception error);
    }

    void translateMarkdown(String source, boolean targetArabic, Callback callback) {
        List<MarkdownTranslationPlan.Segment> segments = MarkdownTranslationPlan.segments(source, targetArabic);
        if (segments.isEmpty()) {
            callback.onSuccess(source == null ? "" : source);
            return;
        }
        String sourceLanguage = targetArabic ? TranslateLanguage.ENGLISH : TranslateLanguage.ARABIC;
        String targetLanguage = targetArabic ? TranslateLanguage.ARABIC : TranslateLanguage.ENGLISH;
        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(sourceLanguage)
                .setTargetLanguage(targetLanguage)
                .build();
        Translator translator = Translation.getClient(options);
        DownloadConditions conditions = new DownloadConditions.Builder().build();
        translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener(unused -> translateNext(translator, segments, 0, new ArrayList<>(segments.size()), source, callback))
                .addOnFailureListener(error -> {
                    translator.close();
                    callback.onError(error == null ? new IllegalStateException("Translation model download failed") : error);
                });
    }

    private void translateNext(Translator translator, List<MarkdownTranslationPlan.Segment> segments, int index,
                               List<String> translated, String source, Callback callback) {
        if (index >= segments.size()) {
            try {
                callback.onSuccess(MarkdownTranslationPlan.apply(source, segments, translated));
            } catch (Exception e) {
                callback.onError(e);
            } finally {
                translator.close();
            }
            return;
        }
        MarkdownTranslationPlan.Segment segment = segments.get(index);
        translator.translate(segment.text)
                .addOnSuccessListener(value -> {
                    translated.add(value);
                    callback.onProgress(index + 1, segments.size());
                    translateNext(translator, segments, index + 1, translated, source, callback);
                })
                .addOnFailureListener(error -> {
                    translator.close();
                    callback.onError(error == null ? new IllegalStateException("Translation failed") : error);
                });
    }
}
