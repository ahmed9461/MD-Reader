package app.mdreader.mobile;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses ATX Markdown headings while ignoring fenced code blocks. */
public final class OutlineParser {
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*#*\\s*$");

    private OutlineParser() { }

    public static final class Heading {
        public final int level;
        public final String title;
        public final int offset;

        Heading(int level, String title, int offset) {
            this.level = level;
            this.title = title;
            this.offset = offset;
        }
    }

    public static List<Heading> parse(String markdown) {
        List<Heading> result = new ArrayList<>();
        String normalized = markdown.replace("\r\n", "\n");
        String[] lines = normalized.split("\n", -1);
        boolean fenced = false;
        char fenceChar = 0;
        int offset = 0;

        for (String line : lines) {
            String trim = line.trim();
            if (trim.startsWith("```") || trim.startsWith("~~~")) {
                char c = trim.charAt(0);
                if (!fenced) { fenced = true; fenceChar = c; }
                else if (c == fenceChar) fenced = false;
                offset += line.length() + 1;
                continue;
            }
            if (!fenced) {
                Matcher m = HEADING.matcher(line);
                if (m.matches()) {
                    String title = m.group(2).replaceAll("[*_`~]+", "").trim();
                    result.add(new Heading(m.group(1).length(), title, offset));
                }
            }
            offset += line.length() + 1;
        }
        return result;
    }
}
