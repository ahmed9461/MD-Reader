import app.mdreader.mobile.MarkdownTransforms;
import app.mdreader.mobile.OutlineParser;
import java.util.List;

public class TransformSmokeTest {
    private static void eq(String actual, String expected, String name) {
        if (!actual.equals(expected)) throw new AssertionError(name + "\nExpected: " + expected + "\nActual: " + actual);
    }

    public static void main(String[] args) {
        MarkdownTransforms.Result b = MarkdownTransforms.wrap("مرحبا", 0, 5, "**", "**", "نص");
        eq(b.text, "**مرحبا**", "bold Arabic selection");

        MarkdownTransforms.Result h = MarkdownTransforms.heading("قديم\nEnglish", 0, 0, 2);
        eq(h.text, "## قديم\nEnglish", "heading first line");

        MarkdownTransforms.Result n = MarkdownTransforms.prefixLines("ألف\nباء", 0, 7, "", true);
        eq(n.text, "1. ألف\n2. باء", "numbered multiline");

        MarkdownTransforms.Result l = MarkdownTransforms.link("OpenAI", 0, 6, false);
        eq(l.text, "[OpenAI](https://)", "link selection");

        List<OutlineParser.Heading> outline = OutlineParser.parse("# عربي\n\n```md\n# ليس عنوان\n```\n\n## English");
        if (outline.size() != 2 || !outline.get(0).title.equals("عربي") || !outline.get(1).title.equals("English")) {
            throw new AssertionError("outline parser");
        }

        System.out.println("TransformSmokeTest OK");
    }
}
