package app.mdreader.mobile;

public final class GitHubMarkdownSourceSmokeTest {
    public static void main(String[] args) {
        GitHubMarkdownSource.Parsed blob = GitHubMarkdownSource.parse(
                "https://github.com/ahmed9461/GitDock/blob/main/CHANGELOG.md");
        eq("CHANGELOG.md", blob.fileName);
        eq("https://github.com/ahmed9461/GitDock/blob/main/CHANGELOG.md?raw=1", blob.downloadUrl);

        GitHubMarkdownSource.Parsed query = GitHubMarkdownSource.parse(
                "https://github.com/u/r/blob/main/docs/a%20b.markdown?plain=1#L2");
        eq("a b.markdown", query.fileName);
        eq("https://github.com/u/r/blob/main/docs/a%20b.markdown?raw=1", query.downloadUrl);

        GitHubMarkdownSource.Parsed raw = GitHubMarkdownSource.parse(
                "https://raw.githubusercontent.com/u/r/main/docs/readme.md");
        eq("readme.md", raw.fileName);
        eq("https://raw.githubusercontent.com/u/r/main/docs/readme.md", raw.downloadUrl);

        rejects("http://github.com/u/r/blob/main/a.md");
        rejects("https://github.com/u/r/blob/main/a.txt");
        rejects("https://github.com/u/r/tree/main/docs/a.md");
        rejects("https://example.com/a.md");
        System.out.println("GitHubMarkdownSourceSmokeTest OK");
    }

    private static void rejects(String value) {
        try {
            GitHubMarkdownSource.parse(value);
            throw new AssertionError("accepted: " + value);
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void eq(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("expected=" + expected + " actual=" + actual);
        }
    }
}
