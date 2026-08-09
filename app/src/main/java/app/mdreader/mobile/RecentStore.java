package app.mdreader.mobile;

import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class RecentStore {
    private static final String KEY = "recent_documents_v2";
    private static final int MAX_ITEMS = 30;

    static final class Entry {
        final String uri;
        final String name;
        final long openedAt;
        final boolean favorite;

        Entry(String uri, String name, long openedAt, boolean favorite) {
            this.uri = uri;
            this.name = name;
            this.openedAt = openedAt;
            this.favorite = favorite;
        }

        Uri asUri() { return Uri.parse(uri); }
    }

    private final SharedPreferences preferences;

    RecentStore(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    List<Entry> all() {
        List<Entry> entries = read();
        entries.sort((a, b) -> Long.compare(b.openedAt, a.openedAt));
        return entries;
    }

    List<Entry> favorites() {
        List<Entry> result = new ArrayList<>();
        for (Entry entry : all()) if (entry.favorite) result.add(entry);
        return result;
    }

    void touch(Uri uri, String name) {
        if (uri == null) return;
        String key = uri.toString();
        List<Entry> entries = read();
        boolean favorite = false;
        for (int i = entries.size() - 1; i >= 0; i--) {
            Entry e = entries.get(i);
            if (e.uri.equals(key)) {
                favorite = e.favorite;
                entries.remove(i);
            }
        }
        entries.add(new Entry(key, safeName(name), System.currentTimeMillis(), favorite));
        trim(entries);
        write(entries);
    }

    void setFavorite(String uri, boolean favorite) {
        List<Entry> entries = read();
        List<Entry> updated = new ArrayList<>(entries.size());
        for (Entry e : entries) {
            updated.add(e.uri.equals(uri) ? new Entry(e.uri, e.name, e.openedAt, favorite) : e);
        }
        write(updated);
    }

    void remove(String uri) {
        List<Entry> entries = read();
        entries.removeIf(e -> e.uri.equals(uri));
        write(entries);
    }

    private List<Entry> read() {
        String raw = preferences.getString(KEY, "[]");
        List<Entry> out = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj == null) continue;
                String uri = obj.optString("uri", "");
                if (uri.isEmpty()) continue;
                out.add(new Entry(uri, safeName(obj.optString("name", "document.md")), obj.optLong("openedAt", 0L), obj.optBoolean("favorite", false)));
            }
        } catch (Exception ignored) { }
        return out;
    }

    private void write(List<Entry> entries) {
        entries.sort(Comparator.comparingLong(e -> e.openedAt));
        trim(entries);
        JSONArray array = new JSONArray();
        for (Entry e : entries) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("uri", e.uri);
                obj.put("name", e.name);
                obj.put("openedAt", e.openedAt);
                obj.put("favorite", e.favorite);
                array.put(obj);
            } catch (Exception ignored) { }
        }
        preferences.edit().putString(KEY, array.toString()).apply();
    }

    private static void trim(List<Entry> entries) {
        while (entries.size() > MAX_ITEMS) {
            int removeIndex = -1;
            long oldest = Long.MAX_VALUE;
            for (int i = 0; i < entries.size(); i++) {
                Entry e = entries.get(i);
                if (!e.favorite && e.openedAt < oldest) {
                    oldest = e.openedAt;
                    removeIndex = i;
                }
            }
            if (removeIndex < 0) removeIndex = 0;
            entries.remove(removeIndex);
        }
    }

    private static String safeName(String value) {
        if (value == null || value.trim().isEmpty()) return "document.md";
        return value.trim();
    }
}
