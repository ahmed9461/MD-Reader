package app.mdreader.mobile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

final class GitHubMarkdownSource {
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 30000;
    private static final int MAX_REDIRECTS = 5;

    static final class Parsed {
        final String originalUrl;
        final String downloadUrl;
        final String fileName;

        Parsed(String originalUrl, String downloadUrl, String fileName) {
            this.originalUrl = originalUrl;
            this.downloadUrl = downloadUrl;
            this.fileName = fileName;
        }
    }

    static final class DownloadedFile {
        final String fileName;
        final String text;
        final String sourceUrl;

        DownloadedFile(String fileName, String text, String sourceUrl) {
            this.fileName = fileName;
            this.text = text;
            this.sourceUrl = sourceUrl;
        }
    }

    static Parsed parse(String input) {
        String value = input == null ? "" : input.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("empty_url");

        URI uri;
        try {
            uri = URI.create(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid_url", e);
        }

        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("https_required");
        }

        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        String path = uri.getPath() == null ? "" : uri.getPath();
        String rawPath = uri.getRawPath() == null ? "" : uri.getRawPath();
        String fileName = fileName(path);
        if (!isMarkdownName(fileName)) throw new IllegalArgumentException("not_markdown");

        if ("github.com".equals(host) || "www.github.com".equals(host)) {
            int blob = path.indexOf("/blob/");
            if (blob < 0 || blob + 6 >= path.length()) {
                throw new IllegalArgumentException("not_blob_url");
            }
            String tail = path.substring(blob + 6);
            if (tail.indexOf('/') <= 0) {
                throw new IllegalArgumentException("missing_file_path");
            }
            return new Parsed(value, "https://github.com" + rawPath + "?raw=1", fileName);
        }

        if ("raw.githubusercontent.com".equals(host)) {
            if (path.split("/", -1).length < 5) {
                throw new IllegalArgumentException("invalid_raw_url");
            }
            String raw = "https://raw.githubusercontent.com" + rawPath
                    + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
            return new Parsed(value, raw, fileName);
        }

        throw new IllegalArgumentException("unsupported_host");
    }

    static DownloadedFile download(String input) throws IOException {
        Parsed parsed;
        try {
            parsed = parse(input);
        } catch (IllegalArgumentException e) {
            throw new IOException(e.getMessage(), e);
        }

        URL current = new URL(parsed.downloadUrl);
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            ensureAllowedDownloadUrl(current);
            HttpURLConnection connection = (HttpURLConnection) current.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "MD-Reader/0.9");
            connection.setRequestProperty("Accept", "text/markdown,text/plain;q=0.9,*/*;q=0.1");

            try {
                int code = connection.getResponseCode();
                if (code >= 300 && code < 400) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || location.trim().isEmpty()) {
                        throw new IOException("redirect_without_location");
                    }
                    current = new URL(current, location);
                    continue;
                }

                if (code < 200 || code >= 300) throw new IOException("http_" + code);

                String contentType = connection.getContentType();
                try (InputStream in = connection.getInputStream()) {
                    byte[] data = readAll(in);
                    int off = data.length >= 3
                            && (data[0] & 255) == 0xef
                            && (data[1] & 255) == 0xbb
                            && (data[2] & 255) == 0xbf ? 3 : 0;
                    String text = new String(data, off, data.length - off, StandardCharsets.UTF_8);
                    if (contentType != null
                            && contentType.toLowerCase(Locale.ROOT).contains("text/html")
                            && looksLikeHtml(text)) {
                        throw new IOException("html_instead_of_markdown");
                    }
                    return new DownloadedFile(parsed.fileName, text, parsed.originalUrl);
                }
            } finally {
                connection.disconnect();
            }
        }

        throw new IOException("too_many_redirects");
    }

    static String userMessage(Exception error) {
        String code = error == null ? "" : String.valueOf(error.getMessage());
        if (code.contains("http_404")) return "الملف غير موجود أو المستودع خاص";
        if (code.contains("http_403")) return "GitHub رفض الوصول إلى الملف";
        if (code.contains("html_instead_of_markdown")) {
            return "لم يرجع GitHub ملف Markdown خامًا؛ قد يكون المستودع خاصًا";
        }
        if (code.contains("https_required")
                || code.contains("unsupported_host")
                || code.contains("not_blob_url")
                || code.contains("not_markdown")) {
            return "الرابط غير مدعوم؛ استخدم رابط ملف Markdown من GitHub";
        }
        if (code.contains("timeout") || error instanceof java.net.SocketTimeoutException) {
            return "انتهت مهلة الاتصال بـ GitHub";
        }
        return "تحقق من الرابط والاتصال بالإنترنت ثم حاول مرة أخرى";
    }

    private static void ensureAllowedDownloadUrl(URL url) throws IOException {
        if (!"https".equalsIgnoreCase(url.getProtocol())) throw new IOException("https_required");
        String host = url.getHost() == null ? "" : url.getHost().toLowerCase(Locale.ROOT);
        if (!"github.com".equals(host)
                && !"www.github.com".equals(host)
                && !"raw.githubusercontent.com".equals(host)) {
            throw new IOException("unsafe_redirect");
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[16384];
        int n;
        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        return out.toByteArray();
    }

    private static String fileName(String path) {
        int slash = path.lastIndexOf('/');
        String raw = slash >= 0 ? path.substring(slash + 1) : path;
        try {
            return URLDecoder.decode(raw.replace("+", "%2B"), "UTF-8");
        } catch (Exception e) {
            return raw;
        }
    }

    private static boolean isMarkdownName(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return n.endsWith(".md") || n.endsWith(".markdown") || n.endsWith(".mdown") || n.endsWith(".mkd");
    }

    private static boolean looksLikeHtml(String text) {
        String t = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        return t.startsWith("<!doctype html") || t.startsWith("<html");
    }

    private GitHubMarkdownSource() {}
}
