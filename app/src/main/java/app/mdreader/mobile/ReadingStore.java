package app.mdreader.mobile;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class ReadingStore {
    static final class Bookmark {
        final String id;
        final float ratio;
        final String label;
        final long createdAt;
        Bookmark(String id, float ratio, String label, long createdAt) {
            this.id = id; this.ratio = ratio; this.label = label; this.createdAt = createdAt;
        }
    }

    static final class EditorState {
        final int selectionStart;
        final int selectionEnd;
        final int scrollY;

        EditorState(int selectionStart, int selectionEnd, int scrollY) {
            this.selectionStart = selectionStart;
            this.selectionEnd = selectionEnd;
            this.scrollY = scrollY;
        }
    }

    private static final String PREF_POSITIONS = "reading_positions_v1";
    private static final String PREF_BOOKMARKS = "reading_bookmarks_v1";
    private static final String PREF_EDITOR_STATES = "editor_states_v1";
    private final SharedPreferences prefs;

    ReadingStore(SharedPreferences prefs) { this.prefs = prefs; }

    synchronized float position(String doc) {
        if (doc == null || doc.isEmpty()) return 0f;
        try { return clamp((float)new JSONObject(prefs.getString(PREF_POSITIONS, "{}")).optDouble(doc, 0d)); }
        catch (Exception ignored) { return 0f; }
    }

    synchronized void savePosition(String doc, float ratio) {
        if (doc == null || doc.isEmpty()) return;
        try {
            JSONObject root = new JSONObject(prefs.getString(PREF_POSITIONS, "{}"));
            root.put(doc, clamp(ratio));
            prefs.edit().putString(PREF_POSITIONS, root.toString()).apply();
        } catch (Exception ignored) {}
    }

    synchronized EditorState editorState(String doc) {
        if (doc == null || doc.isEmpty()) return new EditorState(-1, -1, 0);
        try {
            JSONObject root = new JSONObject(prefs.getString(PREF_EDITOR_STATES, "{}"));
            JSONObject o = root.optJSONObject(doc);
            if (o == null) return new EditorState(-1, -1, 0);
            return new EditorState(
                    Math.max(-1, o.optInt("selectionStart", -1)),
                    Math.max(-1, o.optInt("selectionEnd", -1)),
                    Math.max(0, o.optInt("scrollY", 0)));
        } catch (Exception ignored) {
            return new EditorState(-1, -1, 0);
        }
    }

    synchronized void saveEditorState(String doc, int selectionStart, int selectionEnd, int scrollY) {
        if (doc == null || doc.isEmpty()) return;
        try {
            JSONObject root = new JSONObject(prefs.getString(PREF_EDITOR_STATES, "{}"));
            JSONObject o = new JSONObject()
                    .put("selectionStart", Math.max(0, selectionStart))
                    .put("selectionEnd", Math.max(0, selectionEnd))
                    .put("scrollY", Math.max(0, scrollY));
            root.put(doc, o);
            prefs.edit().putString(PREF_EDITOR_STATES, root.toString()).apply();
        } catch (Exception ignored) {}
    }

    synchronized List<Bookmark> bookmarks(String doc) {
        List<Bookmark> out = new ArrayList<>();
        if (doc == null || doc.isEmpty()) return out;
        try {
            JSONObject root = new JSONObject(prefs.getString(PREF_BOOKMARKS, "{}"));
            JSONArray a = root.optJSONArray(doc);
            if (a == null) return out;
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i); if (o == null) continue;
                out.add(new Bookmark(o.optString("id"), clamp((float)o.optDouble("ratio", 0d)), o.optString("label"), o.optLong("createdAt", 0L)));
            }
        } catch (Exception ignored) {}
        return out;
    }

    synchronized Bookmark addBookmark(String doc, float ratio, String label) {
        if (doc == null || doc.isEmpty()) return null;
        ratio = clamp(ratio);
        label = cleanLabel(label, ratio);
        long now = System.currentTimeMillis();
        String id = Long.toString(now) + "-" + Long.toHexString(System.nanoTime());
        try {
            JSONObject root = new JSONObject(prefs.getString(PREF_BOOKMARKS, "{}"));
            JSONArray a = root.optJSONArray(doc); if (a == null) a = new JSONArray();
            for (int i = 0; i < a.length(); i++) {
                JSONObject existing = a.optJSONObject(i);
                if (existing != null && Math.abs(existing.optDouble("ratio", -1d) - ratio) < 0.003d) {
                    existing.put("label", label).put("createdAt", now);
                    root.put(doc, a); prefs.edit().putString(PREF_BOOKMARKS, root.toString()).apply();
                    return new Bookmark(existing.optString("id"), ratio, label, now);
                }
            }
            JSONObject item = new JSONObject().put("id", id).put("ratio", ratio).put("label", label).put("createdAt", now);
            a.put(item);
            while (a.length() > 50) {
                JSONArray trimmed = new JSONArray();
                for (int i = 1; i < a.length(); i++) trimmed.put(a.get(i));
                a = trimmed;
            }
            root.put(doc, a); prefs.edit().putString(PREF_BOOKMARKS, root.toString()).apply();
            return new Bookmark(id, ratio, label, now);
        } catch (Exception ignored) { return null; }
    }

    synchronized void removeBookmark(String doc, String id) {
        if (doc == null || doc.isEmpty() || id == null) return;
        try {
            JSONObject root = new JSONObject(prefs.getString(PREF_BOOKMARKS, "{}"));
            JSONArray a = root.optJSONArray(doc); if (a == null) return;
            JSONArray next = new JSONArray();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o != null && !id.equals(o.optString("id"))) next.put(o);
            }
            if (next.length() == 0) root.remove(doc); else root.put(doc, next);
            prefs.edit().putString(PREF_BOOKMARKS, root.toString()).apply();
        } catch (Exception ignored) {}
    }

    synchronized void move(String from, String to) {
        if (from == null || to == null || from.isEmpty() || to.isEmpty() || from.equals(to)) return;
        try {
            JSONObject pos = new JSONObject(prefs.getString(PREF_POSITIONS, "{}"));
            if (pos.has(from)) { pos.put(to, pos.optDouble(from, 0d)); pos.remove(from); }
            JSONObject bm = new JSONObject(prefs.getString(PREF_BOOKMARKS, "{}"));
            JSONArray a = bm.optJSONArray(from);
            if (a != null) { bm.put(to, a); bm.remove(from); }
            JSONObject editor = new JSONObject(prefs.getString(PREF_EDITOR_STATES, "{}"));
            JSONObject state = editor.optJSONObject(from);
            if (state != null) { editor.put(to, state); editor.remove(from); }
            prefs.edit()
                    .putString(PREF_POSITIONS, pos.toString())
                    .putString(PREF_BOOKMARKS, bm.toString())
                    .putString(PREF_EDITOR_STATES, editor.toString())
                    .apply();
        } catch (Exception ignored) {}
    }

    private static String cleanLabel(String label, float ratio) {
        String s = label == null ? "" : label.replaceAll("\\s+", " ").trim();
        if (s.isEmpty()) s = "موضع القراءة " + Math.round(ratio * 100f) + "%";
        if (s.length() > 96) s = s.substring(0, 93).trim() + "…";
        return s;
    }

    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }
}
