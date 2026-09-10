package app.mdreader.mobile;

/**
 * Literal Unicode search/replace helpers for editor text.
 *
 * All offsets are UTF-16 indexes, matching Android EditText selection indexes.
 * Matching is deliberately literal (not word-bound and not regex-bound), so the
 * query may contain spaces, punctuation, Markdown syntax, emoji, or newlines.
 */
final class SearchReplaceEngine {
    static final class ReplaceResult {
        final String text;
        final int count;

        ReplaceResult(String text, int count) {
            this.text = text;
            this.count = count;
        }
    }

    private SearchReplaceEngine() {}

    static boolean matchesAt(String text, String query, int index, boolean matchCase) {
        if (text == null || query == null || query.isEmpty() || index < 0 || index + query.length() > text.length()) {
            return false;
        }
        return text.regionMatches(!matchCase, index, query, 0, query.length());
    }

    static int findNext(String text, String query, int fromIndex, boolean matchCase) {
        if (text == null || query == null || query.isEmpty() || query.length() > text.length()) return -1;
        int start = Math.max(0, Math.min(fromIndex, text.length()));
        if (matchCase) return text.indexOf(query, start);
        int last = text.length() - query.length();
        for (int i = start; i <= last; i++) {
            if (text.regionMatches(true, i, query, 0, query.length())) return i;
        }
        return -1;
    }

    static int findPrevious(String text, String query, int fromIndex, boolean matchCase) {
        if (text == null || query == null || query.isEmpty() || query.length() > text.length()) return -1;
        int start = Math.min(Math.max(0, fromIndex), text.length() - query.length());
        if (matchCase) return text.lastIndexOf(query, start);
        for (int i = start; i >= 0; i--) {
            if (text.regionMatches(true, i, query, 0, query.length())) return i;
        }
        return -1;
    }

    static int count(String text, String query, boolean matchCase) {
        if (text == null || query == null || query.isEmpty()) return 0;
        int count = 0;
        int from = 0;
        while (from <= text.length() - query.length()) {
            int index = findNext(text, query, from, matchCase);
            if (index < 0) break;
            count++;
            from = index + query.length();
        }
        return count;
    }

    /** 1-based ordinal of the last match starting at or before caret. */
    static int ordinalAt(String text, String query, int caret, boolean matchCase) {
        if (text == null || query == null || query.isEmpty()) return 0;
        int ordinal = 0;
        int from = 0;
        int limit = Math.max(0, Math.min(caret, text.length()));
        while (from <= text.length() - query.length()) {
            int index = findNext(text, query, from, matchCase);
            if (index < 0 || index > limit) break;
            ordinal++;
            from = index + query.length();
        }
        return ordinal;
    }

    static ReplaceResult replaceAll(String text, String query, String replacement, boolean matchCase) {
        if (text == null) text = "";
        if (query == null || query.isEmpty()) return new ReplaceResult(text, 0);
        if (replacement == null) replacement = "";

        StringBuilder out = new StringBuilder(Math.max(text.length(), 16));
        int from = 0;
        int count = 0;
        while (from <= text.length() - query.length()) {
            int index = findNext(text, query, from, matchCase);
            if (index < 0) break;
            out.append(text, from, index).append(replacement);
            count++;
            from = index + query.length();
        }
        if (count == 0) return new ReplaceResult(text, 0);
        out.append(text, from, text.length());
        return new ReplaceResult(out.toString(), count);
    }
}
