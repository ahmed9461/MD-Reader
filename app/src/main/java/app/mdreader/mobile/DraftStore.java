package app.mdreader.mobile;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

final class DraftStore {
    private static final String FILE_NAME = "recovery_draft.md";
    private static final String KEY_NAME = "draft_name";
    private static final String KEY_URI = "draft_uri";
    private static final String KEY_TIME = "draft_time";

    static final class Draft {
        final String text;
        final String name;
        final String uri;
        final long time;

        Draft(String text, String name, String uri, long time) {
            this.text = text;
            this.name = name;
            this.uri = uri;
            this.time = time;
        }
    }

    private final Context context;
    private final SharedPreferences preferences;

    DraftStore(Context context, SharedPreferences preferences) {
        this.context = context.getApplicationContext();
        this.preferences = preferences;
    }

    void save(String text, String name, String uri) throws Exception {
        File target = new File(context.getFilesDir(), FILE_NAME);
        Files.writeString(target.toPath(), text == null ? "" : text, StandardCharsets.UTF_8);
        preferences.edit()
                .putString(KEY_NAME, name == null ? "غير محفوظ.md" : name)
                .putString(KEY_URI, uri == null ? "" : uri)
                .putLong(KEY_TIME, System.currentTimeMillis())
                .apply();
    }

    Draft read() {
        File target = new File(context.getFilesDir(), FILE_NAME);
        if (!target.isFile()) return null;
        try {
            String text = Files.readString(target.toPath(), StandardCharsets.UTF_8);
            return new Draft(text, preferences.getString(KEY_NAME, "غير محفوظ.md"), preferences.getString(KEY_URI, ""), preferences.getLong(KEY_TIME, target.lastModified()));
        } catch (Exception ignored) {
            return null;
        }
    }

    void clear() {
        try { Files.deleteIfExists(new File(context.getFilesDir(), FILE_NAME).toPath()); } catch (Exception ignored) { }
        preferences.edit().remove(KEY_NAME).remove(KEY_URI).remove(KEY_TIME).apply();
    }
}
