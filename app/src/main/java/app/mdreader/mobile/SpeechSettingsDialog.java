package app.mdreader.mobile;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
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

        TextView note = text(activity, "صوت AI مُولَّد بالذكاء الاصطناعي عبر OpenAI. وضع «تلقائي» يستخدم AI عند وجود مفتاح OpenAI، وإلا يرجع لصوت الجهاز.", 13, muted, false);
        note.setPadding(0, 0, 0, dp(activity, 12));
        root.addView(note, lp(-1, -2));

        root.addView(section(activity, "المحرك", text), lp(-1, dp(activity, 34)));
        LinearLayout engineRow = row(activity);
        TextView auto = chip(activity, "تلقائي", text);
        TextView ai = chip(activity, "AI", text);
        TextView device = chip(activity, "الجهاز", text);
        engineRow.addView(auto, weight(dp(activity, 48), 0, 4));
        engineRow.addView(ai, weight(dp(activity, 48), 4, 4));
        engineRow.addView(device, weight(dp(activity, 48), 4, 0));
        root.addView(engineRow, lp(-1, -2));

        root.addView(section(activity, "صوت AI", text), lp(-1, dp(activity, 34)));
        LinearLayout voiceRow = row(activity);
        TextView marin = chip(activity, "Marin", text);
        TextView cedar = chip(activity, "Cedar", text);
        voiceRow.addView(marin, weight(dp(activity, 48), 0, 4));
        voiceRow.addView(cedar, weight(dp(activity, 48), 4, 0));
        root.addView(voiceRow, lp(-1, -2));

        TextView key = text(activity, reader.hasOpenAiKey() ? "✓ مفتاح OpenAI محفوظ — صوت AI جاهز" : "⚠ لا يوجد مفتاح OpenAI محفوظ. أضفه من إعدادات الترجمة.", 13, reader.hasOpenAiKey() ? accent : muted, false);
        key.setPadding(dp(activity, 4), dp(activity, 8), dp(activity, 4), dp(activity, 12));
        root.addView(key, lp(-1, -2));

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
            String voice = reader.voice();
            styleChip(activity, marin, "marin".equals(voice), accent, bg, border, text);
            styleChip(activity, cedar, "cedar".equals(voice), accent, bg, border, text);
            key.setText(reader.hasOpenAiKey() ? "✓ مفتاح OpenAI محفوظ — صوت AI جاهز" : "⚠ لا يوجد مفتاح OpenAI محفوظ. أضفه من إعدادات الترجمة.");
            key.setTextColor(reader.hasOpenAiKey() ? accent : muted);
            cache.setText(cacheLabel(reader));
        };

        auto.setOnClickListener(v -> { reader.setEnginePreference(SpeechReader.ENGINE_AUTO); refresh.run(); });
        ai.setOnClickListener(v -> { reader.setEnginePreference(SpeechReader.ENGINE_AI); refresh.run(); });
        device.setOnClickListener(v -> { reader.setEnginePreference(SpeechReader.ENGINE_DEVICE); refresh.run(); });
        marin.setOnClickListener(v -> { reader.setVoice("marin"); refresh.run(); });
        cedar.setOnClickListener(v -> { reader.setVoice("cedar"); refresh.run(); });
        test.setOnClickListener(v -> {
            if (SpeechReader.ENGINE_AI.equals(reader.enginePreference()) && !reader.hasOpenAiKey()) {
                Toast.makeText(activity, "أضف مفتاح OpenAI أولًا من إعدادات الترجمة", Toast.LENGTH_LONG).show();
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
        ReaderUi.accessibleAction(v);
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

    private static int themeColor(Activity a, int attr, int fallback) {
        TypedValue out = new TypedValue();
        if (a.getTheme().resolveAttribute(attr, out, true)) {
            if (out.type >= TypedValue.TYPE_FIRST_COLOR_INT && out.type <= TypedValue.TYPE_LAST_COLOR_INT) return out.data;
            if (out.resourceId != 0) {
                try { return a.getColor(out.resourceId); } catch (Exception ignored) {}
            }
        }
        return fallback;
    }

    private static int withAlpha(int color, float alpha) { return Color.argb(Math.round(255f * alpha), Color.red(color), Color.green(color), Color.blue(color)); }
    private static float luminance(int c) { return (0.2126f * Color.red(c) + 0.7152f * Color.green(c) + 0.0722f * Color.blue(c)) / 255f; }
    private static int lighten(int c, float amount) { return mix(c, Color.WHITE, amount); }
    private static int darken(int c, float amount) { return mix(c, Color.BLACK, amount); }
    private static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return Color.rgb(Math.round(Color.red(a) * (1f - t) + Color.red(b) * t), Math.round(Color.green(a) * (1f - t) + Color.green(b) * t), Math.round(Color.blue(a) * (1f - t) + Color.blue(b) * t));
    }
    private static int dp(Activity a, int value) { return Math.round(value * a.getResources().getDisplayMetrics().density); }
}
