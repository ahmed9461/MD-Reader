package app.mdreader.mobile;

import java.util.ArrayList;
import java.util.List;

final class MarkdownSpeechPlan {
    static final class Block {
        final String text;
        final boolean arabic;
        Block(String text, boolean arabic) { this.text = text; this.arabic = arabic; }
    }

    private static final int MAX_BLOCK_CHARS = 2800;

    static List<Block> blocks(String markdown) {
        List<Block> out = new ArrayList<>();
        if (markdown == null || markdown.trim().isEmpty()) return out;

        String[] lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        boolean fenced = false;
        char fenceChar = 0;
        boolean frontMatter = lines.length > 0 && lines[0].trim().equals("---");
        StringBuilder paragraph = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            String trim = raw.trim();

            if (frontMatter) {
                if (i > 0 && trim.equals("---")) frontMatter = false;
                continue;
            }

            if (isFence(trim)) {
                char c = trim.charAt(0);
                if (!fenced) {
                    flush(paragraph, out);
                    fenced = true;
                    fenceChar = c;
                } else if (c == fenceChar) {
                    fenced = false;
                }
                continue;
            }
            if (fenced) continue;

            if (trim.isEmpty()) {
                flush(paragraph, out);
                continue;
            }
            if (isTableSeparator(trim)) continue;

            boolean heading = trim.matches("^#{1,6}\\s+.*");
            boolean listItem = trim.matches("^([-+*]|\\d+[.)])\\s+.*") || trim.matches("^-\\s+\\[[ xX]\\]\\s+.*");
            boolean quote = trim.startsWith(">");
            boolean table = looksLikeTableRow(trim);

            String cleaned = cleanLine(trim);
            if (cleaned.isEmpty()) continue;

            if (heading || listItem || quote || table) {
                flush(paragraph, out);
                addChunked(cleaned, out);
            } else {
                if (paragraph.length() > 0) paragraph.append(' ');
                paragraph.append(cleaned);
            }
        }
        flush(paragraph, out);
        return out;
    }

    static String label(String source) {
        List<Block> b = blocks(source);
        String value = b.isEmpty() ? cleanLine(source == null ? "" : source.trim()) : b.get(0).text;
        value = value.replaceAll("\\s+", " ").trim();
        if (value.length() > 96) value = value.substring(0, 93).trim() + "…";
        return value;
    }

    static boolean arabicDominant(String text) {
        if (text == null) return false;
        int ar = 0, latin = 0; Boolean first = null;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean isAr = (c >= '\u0600' && c <= '\u06ff') || (c >= '\u0750' && c <= '\u077f') || (c >= '\u08a0' && c <= '\u08ff');
            boolean isLatin = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
            if (isAr) { ar++; if (first == null) first = Boolean.TRUE; }
            else if (isLatin) { latin++; if (first == null) first = Boolean.FALSE; }
        }
        if (ar == 0) return false;
        if (latin == 0) return true;
        if (ar >= latin) return true;
        return Boolean.TRUE.equals(first) && ar * 2 >= latin;
    }

    private static boolean isFence(String trim) {
        return trim.startsWith("```") || trim.startsWith("~~~");
    }

    private static boolean isTableSeparator(String trim) {
        if (!trim.contains("-") || !trim.contains("|")) return false;
        String reduced = trim.replace("|", "").replace(":", "").replace("-", "").replace(" ", "").replace("\t", "");
        return reduced.isEmpty();
    }

    private static boolean looksLikeTableRow(String trim) {
        if (!trim.contains("|")) return false;
        int bars = 0;
        for (int i = 0; i < trim.length(); i++) if (trim.charAt(i) == '|') bars++;
        return bars >= 2;
    }

    private static String cleanLine(String line) {
        if (line == null) return "";
        String s = line.trim();
        s = s.replaceFirst("^#{1,6}\\s+", "");
        s = s.replaceFirst("^>+\\s*", "");
        s = s.replaceFirst("^-\\s+\\[[ xX]\\]\\s+", "");
        s = s.replaceFirst("^([-+*]|\\d+[.)])\\s+", "");

        if (looksLikeTableRow(s)) {
            String[] cells = s.split("\\|");
            StringBuilder row = new StringBuilder();
            for (String cell : cells) {
                String c = cleanInline(cell);
                if (c.isEmpty()) continue;
                if (row.length() > 0) row.append("، ");
                row.append(c);
            }
            s = row.toString();
        } else {
            s = cleanInline(s);
        }
        return s.replaceAll("\\s+", " ").trim();
    }

    private static String cleanInline(String s) {
        if (s == null) return "";
        String out = s;
        out = out.replaceAll("!\\[([^\\]]*)\\]\\([^)]*\\)", "$1");
        out = out.replaceAll("\\[([^\\]]+)\\]\\([^)]*\\)", "$1");
        out = out.replaceAll("\\[([^\\]]+)\\]\\[[^\\]]*\\]", "$1");
        out = out.replaceAll("`([^`]*)`", "$1");
        out = out.replace("**", "").replace("__", "").replace("~~", "");
        out = out.replaceAll("(?<!\\w)[*_](?!\\s)", "").replaceAll("(?<!\\s)[*_](?!\\w)", "");
        out = out.replaceAll("https?://\\S+", "");
        out = out.replaceAll("<[^>]+>", "");
        return out;
    }

    private static void flush(StringBuilder paragraph, List<Block> out) {
        if (paragraph.length() == 0) return;
        addChunked(paragraph.toString().trim(), out);
        paragraph.setLength(0);
    }

    private static void addChunked(String value, List<Block> out) {
        String text = value == null ? "" : value.trim();
        while (!text.isEmpty()) {
            if (text.length() <= MAX_BLOCK_CHARS) {
                out.add(new Block(text, arabicDominant(text)));
                return;
            }
            int cut = bestCut(text, MAX_BLOCK_CHARS);
            String part = text.substring(0, cut).trim();
            if (!part.isEmpty()) out.add(new Block(part, arabicDominant(part)));
            text = text.substring(cut).trim();
        }
    }

    private static int bestCut(String text, int max) {
        int from = Math.max(1, max - 500);
        for (int i = Math.min(max, text.length() - 1); i >= from; i--) {
            char c = text.charAt(i);
            if (c == '.' || c == '!' || c == '?' || c == '؟' || c == '؛' || c == '\n') return i + 1;
        }
        int space = text.lastIndexOf(' ', max);
        return space > 0 ? space : max;
    }
}
