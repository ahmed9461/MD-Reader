package app.mdreader.mobile;

import android.os.Handler;
import android.os.Looper;

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

final class TranslationService {
    interface Callback {
        default void onStatus(String status) { }
        void onProgress(int completed, int total);
        void onSuccess(String translated);
        void onError(Exception error);
    }

    private final Handler main = new Handler(Looper.getMainLooper());

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
        AtomicBoolean finished = new AtomicBoolean(false);

        Runnable timeout = () -> {
            if (finished.compareAndSet(false, true)) {
                translator.close();
                callback.onError(new TimeoutException("انتهت مهلة تنزيل/تجهيز نموذج الترجمة المحلي. جرّب AI API أو أعد المحاولة على اتصال أفضل."));
            }
        };
        main.postDelayed(timeout, 45000);
        callback.onStatus("تنزيل أو تجهيز نموذج الترجمة المحلي…");

        translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener(unused -> {
                    if (finished.get()) return;
                    main.removeCallbacks(timeout);
                    callback.onStatus("تم تجهيز النموذج المحلي. بدء الترجمة…");
                    translateNext(translator, segments, 0, new ArrayList<>(segments.size()), source, callback, finished);
                })
                .addOnFailureListener(error -> {
                    main.removeCallbacks(timeout);
                    if (finished.compareAndSet(false, true)) {
                        translator.close();
                        callback.onError(asException(error));
                    }
                });
    }

    private void translateNext(
            Translator translator,
            List<MarkdownTranslationPlan.Segment> segments,
            int index,
            List<String> translated,
            String source,
            Callback callback,
            AtomicBoolean finished
    ) {
        if (finished.get()) return;
        if (index >= segments.size()) {
            try {
                if (finished.compareAndSet(false, true)) {
                    callback.onSuccess(MarkdownTranslationPlan.apply(source, segments, translated));
                }
            } catch (Exception e) {
                if (finished.compareAndSet(false, true)) callback.onError(e);
            } finally {
                translator.close();
            }
            return;
        }

        MarkdownTranslationPlan.Segment segment = segments.get(index);
        callback.onStatus("الترجمة محليًا…");
        translator.translate(segment.text)
                .addOnSuccessListener(value -> {
                    if (finished.get()) return;
                    translated.add(value);
                    callback.onProgress(index + 1, segments.size());
                    translateNext(translator, segments, index + 1, translated, source, callback, finished);
                })
                .addOnFailureListener(error -> {
                    if (finished.compareAndSet(false, true)) {
                        translator.close();
                        callback.onError(asException(error));
                    }
                });
    }

    private static Exception asException(Exception error) {
        return error == null ? new IllegalStateException("فشلت الترجمة المحلية") : error;
    }
}
