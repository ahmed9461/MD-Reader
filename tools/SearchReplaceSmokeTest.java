package app.mdreader.mobile;

public final class SearchReplaceSmokeTest {
    private static void expect(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }

    public static void main(String[] args) {
        expect(SearchReplaceEngine.count("مرحبا أحمد مرحبا", "مرحبا", true) == 2, "Arabic count");
        expect(SearchReplaceEngine.count("# H1\n## H2\n# H3", "#", true) == 4, "Markdown symbols");
        expect(SearchReplaceEngine.count("Test test TEST", "test", false) == 3, "case insensitive");
        expect(SearchReplaceEngine.count("Test test TEST", "test", true) == 1, "case sensitive");
        expect(SearchReplaceEngine.findNext("one\ntwo\nthree", "two\nthree", 0, true) == 4, "multiline search");

        SearchReplaceEngine.ReplaceResult growth = SearchReplaceEngine.replaceAll("a a", "a", "aa", true);
        expect(growth.count == 2 && "aa aa".equals(growth.text), "replacement containing query");

        SearchReplaceEngine.ReplaceResult symbols = SearchReplaceEngine.replaceAll("A+B / A+B", "A+B", "C&D", true);
        expect(symbols.count == 2 && "C&D / C&D".equals(symbols.text), "literal symbols");

        SearchReplaceEngine.ReplaceResult deletion = SearchReplaceEngine.replaceAll("x--x--x", "--", "", true);
        expect(deletion.count == 2 && "xxx".equals(deletion.text), "empty replacement deletion");

        System.out.println("SearchReplaceSmokeTest OK");
    }
}
