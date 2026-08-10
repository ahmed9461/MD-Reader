package app.mdreader.mobile;

import java.util.List;

public final class SpeechPlanSmokeTest {
    public static void main(String[] args) {
        String md = "# دليل عربي\n\nاستخدم [الموقع](https://example.com) لفتح الملف.\n\n```bash\nsystemctl restart app\n```\n\n- خطوة أولى\n- [ ] مهمة\n\n| الاسم | القيمة |\n| --- | --- |\n| اللغة | العربية |\n\n## English section\n\nRun the app normally.";
        List<MarkdownSpeechPlan.Block> blocks = MarkdownSpeechPlan.blocks(md);
        if (blocks.size() < 6) throw new AssertionError("expected speech blocks");
        String joined = join(blocks);
        if (joined.contains("systemctl restart")) throw new AssertionError("fenced code must be skipped");
        if (joined.contains("https://example.com")) throw new AssertionError("URL must not be spoken");
        if (!joined.contains("الموقع")) throw new AssertionError("link label must remain");
        if (!joined.contains("اللغة") || !joined.contains("العربية") || joined.contains("|")) throw new AssertionError("table row should be speech friendly");
        if (!blocks.get(0).arabic) throw new AssertionError("Arabic heading should select Arabic voice");
        boolean english = false; for (MarkdownSpeechPlan.Block b : blocks) if (b.text.contains("English section") && !b.arabic) english = true;
        if (!english) throw new AssertionError("English block should select English voice");
        if (!"عنوان عربي".equals(MarkdownSpeechPlan.label("## عنوان عربي"))) throw new AssertionError("bookmark label cleanup");
        System.out.println("SpeechPlanSmokeTest OK");
    }
    private static String join(List<MarkdownSpeechPlan.Block> blocks) { StringBuilder b = new StringBuilder(); for (MarkdownSpeechPlan.Block x : blocks) b.append(x.text).append('\n'); return b.toString(); }
}
