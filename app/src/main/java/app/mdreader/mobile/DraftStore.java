package app.mdreader.mobile;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

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
        try (FileOutputStream out = new FileOutputStream(target, false)) {
            out.write((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
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
            String text;
            try (FileInputStream in = new FileInputStream(target);
                 ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                byte[] bytes = new byte[8192];
                int read;
                while ((read = in.read(bytes)) != -1) buffer.write(bytes, 0, read);
                text = buffer.toString(StandardCharsets.UTF_8.name());
            }
            return new Draft(
                    text,
                    preferences.getString(KEY_NAME, "غير محفوظ.md"),
                    preferences.getString(KEY_URI, ""),
                    preferences.getLong(KEY_TIME, target.lastModified())
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    void clear() {
        try {
            File target = new File(context.getFilesDir(), FILE_NAME);
            if (target.exists() && !target.delete()) target.deleteOnExit();
        } catch (Exception ignored) { }
        preferences.edit().remove(KEY_NAME).remove(KEY_URI).remove(KEY_TIME).apply();
    }
}
