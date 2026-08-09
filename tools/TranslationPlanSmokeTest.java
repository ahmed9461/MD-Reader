package app.mdreader.mobile;

import java.util.ArrayList;
import java.util.List;

public final class TranslationPlanSmokeTest {
    public static void main(String[] args) {
        String md = "# Installation\n\nRun **this command**:\n\n```bash\nsystemctl restart demo.service\n```\n\nالرابط https://example.com يبقى كما هو.\n";
        List<MarkdownTranslationPlan.Segment> ar = MarkdownTranslationPlan.segments(md, true);
        require(!ar.isEmpty(), "expected English segments");
        for (MarkdownTranslationPlan.Segment s : ar) {
            require(!s.text.contains("systemctl"), "fenced code must not translate");
            require(!s.text.contains("https://"), "URLs must not translate");
        }
        List<String> replacements = new ArrayList<>();
        for (MarkdownTranslationPlan.Segment ignored : ar) replacements.add("عربي");
        String applied = MarkdownTranslationPlan.apply(md, ar, replacements);
        require(applied.contains("```bash\nsystemctl restart demo.service\n```"), "fenced code changed");
        require(applied.contains("https://example.com"), "URL changed");

        String arabic = "# أوامر\n\nاكتب الأمر التالي ثم احفظ الملف.\n`inline_code`\n";
        List<MarkdownTranslationPlan.Segment> en = MarkdownTranslationPlan.segments(arabic, false);
        require(!en.isEmpty(), "expected Arabic segments");
        for (MarkdownTranslationPlan.Segment s : en) require(!s.text.contains("inline_code"), "inline code must not translate");
        System.out.println("TranslationPlanSmokeTest OK");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
