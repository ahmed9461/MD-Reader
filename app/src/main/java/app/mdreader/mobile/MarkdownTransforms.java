package app.mdreader.mobile;

/** Pure Markdown editor transformations. No Android dependencies. */
public final class MarkdownTransforms {
    private MarkdownTransforms() { }

    public static final class Result {
        public final String text;
        public final int selectionStart;
        public final int selectionEnd;

        Result(String text, int selectionStart, int selectionEnd) {
            this.text = text;
            this.selectionStart = selectionStart;
            this.selectionEnd = selectionEnd;
        }
    }

    public static Result wrap(String text, int start, int end, String before, String after, String placeholder) {
        Range r = range(text, start, end);
        String selected = text.substring(r.start, r.end);
        String body = selected.isEmpty() ? placeholder : selected;
        String replacement = before + body + after;
        int selStart = r.start + before.length();
        return replace(text, r.start, r.end, replacement, selStart, selStart + body.length());
    }

    public static Result fencedCode(String text, int start, int end) {
        Range r = range(text, start, end);
        String selected = text.substring(r.start, r.end);
        String body = selected.isEmpty() ? "code" : selected;
        String replacement = "```\n" + body + "\n```";
        return replace(text, r.start, r.end, replacement, r.start + 4, r.start + 4 + body.length());
    }

    public static Result titledFencedCode(String text, int start, int end, String title, String language) {
        Range r = range(text, start, end);
        String safeTitle = normalizeCodeTitle(title);
        if (safeTitle.isEmpty()) throw new IllegalArgumentException("Title is required");
        String safeLanguage = normalizeCodeLanguage(language);
        String selected = text.substring(r.start, r.end);
        String body = selected.isEmpty() ? "المحتوى" : selected;
        String fence = repeat("`", Math.max(3, longestBacktickRun(body) + 1));
        String before = r.start > 0 && text.charAt(r.start - 1) != '\n' ? "\n\n" : "";
        String after = r.end < text.length() && text.charAt(r.end) != '\n' ? "\n\n" : "";
        String info = (safeLanguage.isEmpty() ? "" : safeLanguage + " ") + "title=\"" + escapeCodeTitle(safeTitle) + "\"";
        String open = fence + info + "\n";
        String close = (body.endsWith("\n") ? "" : "\n") + fence;
        String replacement = before + open + body + close + after;
        int bodyStart = r.start + before.length() + open.length();
        return replace(text, r.start, r.end, replacement, bodyStart, bodyStart + body.length());
    }

    public static String normalizeCodeTitle(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder();
        boolean space = false;
        for (int i = 0; i < value.length() && out.length() < 160; i++) {
            char c = value.charAt(i);
            if (Character.isISOControl(c) || Character.isWhitespace(c)) {
                if (!space && out.length() > 0) { out.append(' '); space = true; }
            } else { out.append(c); space = false; }
        }
        int end = out.length();
        while (end > 0 && out.charAt(end - 1) == ' ') end--;
        return out.substring(0, end);
    }

    public static String normalizeCodeLanguage(String value) {
        if (value == null) return "";
        String v = value.trim();
        if (v.isEmpty()) return "";
        if (v.length() > 40 || !v.matches("[A-Za-z0-9_+.-]+")) throw new IllegalArgumentException("Unsupported language");
        return v;
    }

    private static String escapeCodeTitle(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static int longestBacktickRun(String value) {
        int best = 0, current = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == '`') { current++; if (current > best) best = current; }
            else current = 0;
        }
        return best;
    }

    public static Result link(String text, int start, int end, boolean image) {
        Range r = range(text, start, end);
        String selected = text.substring(r.start, r.end);
        String label = selected.isEmpty() ? (image ? "وصف الصورة" : "النص") : selected;
        String url = image ? "image.png" : "https://";
        String replacement = (image ? "![" : "[") + label + "](" + url + ")";
        int urlStart = r.start + (image ? 2 : 1) + label.length() + 2;
        return replace(text, r.start, r.end, replacement, urlStart, urlStart + url.length());
    }

    public static Result insert(String text, int start, int end, String block) {
        Range r = range(text, start, end);
        return replace(text, r.start, r.end, block, r.start + block.length(), r.start + block.length());
    }

    public static Result heading(String text, int start, int end, int level) {
        final String prefix = repeat("#", Math.max(1, Math.min(6, level))) + " ";
        return transformLines(text, start, end, (line, index) -> prefix + line.replaceFirst("^#{1,6}\\s+", ""));
    }

    public static Result prefixLines(String text, int start, int end, String prefix, boolean numbered) {
        return transformLines(text, start, end, (line, index) -> (numbered ? (index + 1) + ". " : prefix) + line);
    }

    private static Result transformLines(String text, int start, int end, LineTransform transform) {
        Range r = range(text, start, end);
        int lineStart = text.lastIndexOf('\n', Math.max(0, r.start - 1));
        lineStart = lineStart < 0 ? 0 : lineStart + 1;
        int lineEnd = text.indexOf('\n', r.end);
        if (lineEnd < 0) lineEnd = text.length();

        String[] lines = text.substring(lineStart, lineEnd).split("\n", -1);
        StringBuilder replacement = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) replacement.append('\n');
            replacement.append(transform.apply(lines[i], i));
        }
        return replace(text, lineStart, lineEnd, replacement.toString(), lineStart, lineStart + replacement.length());
    }

    private static Result replace(String text, int start, int end, String replacement, int selStart, int selEnd) {
        String next = text.substring(0, start) + replacement + text.substring(end);
        int safeStart = clamp(selStart, 0, next.length());
        int safeEnd = clamp(selEnd, safeStart, next.length());
        return new Result(next, safeStart, safeEnd);
    }

    private static Range range(String text, int start, int end) {
        int safeStart = clamp(start, 0, text.length());
        int safeEnd = clamp(end, safeStart, text.length());
        return new Range(safeStart, safeEnd);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String repeat(String value, int count) {
        StringBuilder out = new StringBuilder(value.length() * Math.max(0, count));
        for (int i = 0; i < count; i++) out.append(value);
        return out.toString();
    }

    private interface LineTransform { String apply(String line, int index); }

    private static final class Range {
        final int start;
        final int end;
        Range(int start, int end) { this.start = start; this.end = end; }
    }
}
