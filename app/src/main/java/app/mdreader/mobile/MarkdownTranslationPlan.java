package app.mdreader.mobile;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class MarkdownTranslationPlan {
    static final class Segment {
        final int start;
        final int end;
        final String text;
        Segment(int start, int end, String text) { this.start = start; this.end = end; this.text = text; }
    }

    private static final Pattern URL = Pattern.compile("(?i)(https?://|mailto:|tel:)[^\\s)]+$");

    private MarkdownTranslationPlan() { }

    static List<Segment> segments(String source, boolean targetArabic) {
        List<Segment> out = new ArrayList<>();
        if (source == null || source.isEmpty()) return out;
        boolean fenced = false;
        String fenceToken = null;
        int lineStart = 0;
        while (lineStart < source.length()) {
            int lineEnd = source.indexOf('\n', lineStart);
            if (lineEnd < 0) lineEnd = source.length();
            String line = source.substring(lineStart, lineEnd);
            String trimmed = line.trim();
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                String token = trimmed.startsWith("```") ? "```" : "~~~";
                if (!fenced) { fenced = true; fenceToken = token; }
                else if (token.equals(fenceToken)) { fenced = false; fenceToken = null; }
            } else if (!fenced) {
                collectLineSegments(source, lineStart, lineEnd, targetArabic, out);
            }
            lineStart = lineEnd + 1;
        }
        return out;
    }

    static String apply(String source, List<Segment> segments, List<String> translations) {
        if (segments.size() != translations.size()) throw new IllegalArgumentException("Segment/translation count mismatch");
        StringBuilder out = new StringBuilder(source.length() + 64);
        int cursor = 0;
        for (int i = 0; i < segments.size(); i++) {
            Segment s = segments.get(i);
            out.append(source, cursor, s.start);
            out.append(translations.get(i));
            cursor = s.end;
        }
        out.append(source, cursor, source.length());
        return out.toString();
    }

    private static void collectLineSegments(String source, int start, int end, boolean targetArabic, List<Segment> out) {
        int cursor = start;
        boolean inlineCode = false;
        while (cursor < end) {
            char c = source.charAt(cursor);
            if (c == '`') { inlineCode = !inlineCode; cursor++; continue; }
            if (inlineCode) { cursor++; continue; }
            if (isMarkdownSyntax(c)) { cursor++; continue; }
            int partStart = cursor;
            while (cursor < end) {
                char d = source.charAt(cursor);
                if (d == '`' || isMarkdownSyntax(d)) break;
                cursor++;
            }
            int partEnd = cursor;
            if (partEnd <= partStart) continue;
            String raw = source.substring(partStart, partEnd);
            int lead = 0;
            int trail = raw.length();
            while (lead < trail && Character.isWhitespace(raw.charAt(lead))) lead++;
            while (trail > lead && Character.isWhitespace(raw.charAt(trail - 1))) trail--;
            if (lead >= trail) continue;
            String core = raw.substring(lead, trail);
            if (!shouldTranslate(core, targetArabic) || looksLikeUrl(core)) continue;
            out.add(new Segment(partStart + lead, partStart + trail, core));
        }
    }

    private static boolean isMarkdownSyntax(char c) {
        return c == '*' || c == '_' || c == '~' || c == '#' || c == '>' || c == '|' || c == '[' || c == ']' || c == '(' || c == ')' || c == '!' || c == '\\';
    }

    private static boolean shouldTranslate(String text, boolean targetArabic) {
        boolean arabic = containsArabic(text);
        boolean latin = containsLatin(text);
        return targetArabic ? (latin && !arabic) : arabic;
    }

    private static boolean containsArabic(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c >= 0x0600 && c <= 0x06FF) || (c >= 0x0750 && c <= 0x077F) || (c >= 0x08A0 && c <= 0x08FF)) return true;
        }
        return false;
    }

    private static boolean containsLatin(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) return true;
        }
        return false;
    }

    private static boolean looksLikeUrl(String text) {
        return URL.matcher(text).find() || text.matches("(?i)^[a-z][a-z0-9+.-]*://.*$");
    }
}
