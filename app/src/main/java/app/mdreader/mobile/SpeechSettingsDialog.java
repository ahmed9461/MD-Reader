package app.mdreader.mobile;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

final class SpeechSettingsDialog {
    private SpeechSettingsDialog() {}

    static void show(Activity activity, SpeechReader reader) {
        if (activity == null || activity.isFinishing() || reader == null) return;

        UiPalette palette=new UiPalette(activity.getSharedPreferences("md_reader_preferences",Activity.MODE_PRIVATE).getBoolean("dark_mode",false));
        int bg=palette.background,text=palette.text,muted=palette.muted,accent=palette.accent,surface=palette.surface,border=palette.border;

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);

        LinearLayout root=ResponsiveSheet.panel(activity,"إعدادات الصوت",palette);

        root.setFocusableInTouchMode(true);
        root.requestFocus();

        TextView note = text(activity, "في الوضع التلقائي تُقرأ الفقرات عبر OpenRouter عند توفر مفتاحك. يستخدم التطبيق صوت الجهاز عند غياب المفتاح أو تعذّر القراءة السحابية.", 13, muted, false);
        note.setPadding(0, 0, 0, dp(activity, 12));
        root.addView(note, lp(-1, -2));

        root.addView(section(activity, "المحرك", text), lp(-1, dp(activity, 34)));
        LinearLayout engineRow = row(activity);
        TextView auto = chip(activity, "تلقائي", text);
        TextView ai = chip(activity, "OpenRouter", text);
        TextView device = chip(activity, "الجهاز", text);
        engineRow.addView(auto, weight(dp(activity, 48), 0, 4));
        engineRow.addView(ai, weight(dp(activity, 48), 4, 4));
        engineRow.addView(device, weight(dp(activity, 48), 4, 0));
        root.addView(engineRow, lp(-1, -2));

        root.addView(section(activity, "نموذج القراءة عبر OpenRouter", text), lp(-1, dp(activity, 34)));
        LinearLayout modelRow = row(activity);
        TextView flash = chip(activity, "Flash v2.5", text);
        TextView multilingual = chip(activity, "Multilingual v2", text);
        modelRow.addView(flash, weight(dp(activity, 48), 0, 4));
        modelRow.addView(multilingual, weight(dp(activity, 48), 4, 0));
        root.addView(modelRow, lp(-1, -2));
        TextView modelId = text(activity, reader.model(), 14, muted, false);
        modelId.setTextDirection(View.TEXT_DIRECTION_LTR);
        root.addView(modelId, lp(-1, -2));

        root.addView(section(activity, "الصوت", text), lp(-1, dp(activity, 34)));
        LinearLayout voiceRow = row(activity);
        TextView sarah = chip(activity, "Sarah", text);
        TextView george = chip(activity, "George", text);
        voiceRow.addView(sarah, weight(dp(activity, 48), 0, 4));
        voiceRow.addView(george, weight(dp(activity, 48), 4, 0));
        root.addView(voiceRow, lp(-1, -2));

        ApiKeyStore keys = new ApiKeyStore(activity);
        TextView keyStatus = text(activity, "", 13, muted, false);
        keyStatus.setPadding(0, dp(activity, 10), 0, dp(activity, 4));
        root.addView(keyStatus, lp(-1, -2));
        EditText key = new EditText(activity);
        key.setSingleLine();
        key.setHint("مفتاح OpenRouter");
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setTextDirection(View.TEXT_DIRECTION_LTR);
        key.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        key.setSaveEnabled(false);
        ReaderUi.field(key, palette);
        root.addView(key, lp(-1, -2));
        LinearLayout keyRow = row(activity);
        TextView saveKey = action(activity, "حفظ المفتاح", surface, text, false);
        TextView clearKey = action(activity, "حذف المفتاح", surface, text, false);
        keyRow.addView(saveKey, weight(dp(activity, 48), 0, 4));
        keyRow.addView(clearKey, weight(dp(activity, 48), 4, 0));
        root.addView(keyRow, lp(-1, -2));

        TextView test = action(activity, "▶ تجربة عربي + English", accent, palette.onAccent, true);
        root.addView(test, actionLp(activity));

        TextView cache = action(activity, cacheLabel(reader), surface, text, false);
        root.addView(cache, actionLp(activity));

        TextView close = action(activity, "إغلاق", surface, text, false);
        root.addView(close, actionLp(activity));

        Runnable refresh = () -> {
            String engine = reader.enginePreference();
            styleChip(activity, auto, SpeechReader.ENGINE_AUTO.equals(engine), accent, bg, border, text);
            styleChip(activity, ai, SpeechReader.ENGINE_AI.equals(engine), accent, bg, border, text);
            styleChip(activity, device, SpeechReader.ENGINE_DEVICE.equals(engine), accent, bg, border, text);
            boolean useFlash = OpenRouterSpeechClient.DEFAULT_MODEL.equals(reader.model());
            styleChip(activity, flash, useFlash, accent, bg, border, text);
            styleChip(activity, multilingual, !useFlash, accent, bg, border, text);
            modelId.setText(reader.model());
            boolean useSarah = "sarah".equals(reader.voice());
            styleChip(activity, sarah, useSarah, accent, bg, border, text);
            styleChip(activity, george, !useSarah, accent, bg, border, text);
            boolean hasKey = reader.hasOpenRouterKey();
            keyStatus.setText(hasKey ? "مفتاح OpenRouter محفوظ للصوت والترجمة" : "أضف مفتاح OpenRouter للصوت والترجمة");
            keyStatus.setTextColor(hasKey ? accent : muted);
            clearKey.setEnabled(hasKey);
            cache.setText(cacheLabel(reader));
        };

        auto.setOnClickListener(v -> { reader.setEnginePreference(SpeechReader.ENGINE_AUTO); refresh.run(); });
        ai.setOnClickListener(v -> { reader.setEnginePreference(SpeechReader.ENGINE_AI); refresh.run(); });
        device.setOnClickListener(v -> { reader.setEnginePreference(SpeechReader.ENGINE_DEVICE); refresh.run(); });
        flash.setOnClickListener(v -> { reader.setModel(OpenRouterSpeechClient.DEFAULT_MODEL); refresh.run(); });
        multilingual.setOnClickListener(v -> { reader.setModel(OpenRouterSpeechClient.MULTILINGUAL_MODEL); refresh.run(); });
        sarah.setOnClickListener(v -> { reader.setVoice("sarah"); refresh.run(); });
        george.setOnClickListener(v -> { reader.setVoice("george"); refresh.run(); });
        saveKey.setOnClickListener(v -> {
            String entered = key.getText().toString().trim();
            if (entered.isEmpty()) { key.setError("أدخل مفتاح OpenRouter"); return; }
            try {
                keys.save(ApiKeyStore.PROVIDER_OPENROUTER, entered);
                key.setText("");
                if (!reader.isActive()) reader.setEnginePreference(reader.enginePreference());
                refresh.run();
                Toast.makeText(activity, "تم حفظ مفتاح OpenRouter", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                key.setError("تعذر حفظ المفتاح على الجهاز");
            }
        });
        clearKey.setOnClickListener(v -> {
            keys.clear(ApiKeyStore.PROVIDER_OPENROUTER);
            key.setText("");
            if (reader.isActive() && SpeechReader.ENGINE_AI.equals(reader.effectiveEngine())) reader.stop();
            if (!reader.isActive()) reader.setEnginePreference(reader.enginePreference());
            refresh.run();
        });
        test.setOnClickListener(v -> {
            if (SpeechReader.ENGINE_AI.equals(reader.enginePreference()) && !reader.hasOpenRouterKey()) {
                Toast.makeText(activity, "أدخل مفتاح OpenRouter واضغط «حفظ المفتاح»", Toast.LENGTH_LONG).show();
                return;
            }
            reader.testVoice();
            Toast.makeText(activity, "بدأ اختبار الصوت", Toast.LENGTH_SHORT).show();
        });
        cache.setOnClickListener(v -> {
            reader.clearAiCache();
            refresh.run();
            Toast.makeText(activity, "تم مسح كاش صوت AI", Toast.LENGTH_SHORT).show();
        });
        close.setOnClickListener(v -> dialog.dismiss());

        ResponsiveSheet.show(dialog,root,palette);
        refresh.run();
    }

    private static String cacheLabel(SpeechReader reader) {
        double mb = reader.aiCacheBytes() / (1024d * 1024d);
        return "مسح كاش صوت AI  •  " + String.format(Locale.US, "%.1f MB", mb);
    }

    private static LinearLayout row(Activity activity) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return row;
    }

    private static TextView section(Activity a, String value, int color) {
        TextView v = text(a, value, 14, color, true);
        v.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        return v;
    }

    private static TextView chip(Activity a, String value, int color) {
        TextView v = text(a, value, 14, color, true);
        v.setGravity(Gravity.CENTER);
        ReaderUi.accessibleAction(v);
        v.setPadding(dp(a, 8), 0, dp(a, 8), 0);
        return v;
    }

    private static void styleChip(Activity a, TextView v, boolean selected, int accent, int bg, int border, int text) {
        v.setSelected(selected);v.setTextColor(selected ? new UiPalette(a.getSharedPreferences("md_reader_preferences",Activity.MODE_PRIVATE).getBoolean("dark_mode",false)).onAccent : text);
        v.setBackground(round(a, selected ? accent : bg, selected ? accent : border, 12));
    }

    private static TextView action(Activity a, String value, int fill, int color, boolean primary) {
        TextView v = text(a, value, 15, color, primary);
        v.setGravity(Gravity.CENTER);
        ReaderUi.accessibleAction(v);v.setMinHeight(dp(a,48));
        v.setPadding(dp(a,12),dp(a,10),dp(a,12),dp(a,10));v.setBackground(ReaderUi.ripple(a,fill,Color.TRANSPARENT,13,color));
        return v;
    }

    private static TextView text(Activity a, String value, int sp, int color, boolean bold) {
        TextView v = new TextView(a);
        v.setText(value);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTextColor(color);
        v.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        v.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        if (bold) v.setTypeface(v.getTypeface(), android.graphics.Typeface.BOLD);
        return v;
    }

    private static LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }
    private static LinearLayout.LayoutParams weight(int h, int start, int end) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, h, 1f);
        p.setMarginStart(start); p.setMarginEnd(end); return p;
    }
    private static LinearLayout.LayoutParams actionLp(Activity a) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(a, 7); return p;
    }

    private static GradientDrawable round(Activity a, int fill, int stroke, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill); d.setStroke(dp(a, 1), stroke); d.setCornerRadius(dp(a, radiusDp)); return d;
    }

    private static int dp(Activity a, int value) { return Math.round(value * a.getResources().getDisplayMetrics().density); }
}
