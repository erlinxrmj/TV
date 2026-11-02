package app.antenne.tv;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Favoriten, zuletzt gesehene Sender und Wiedergabepositionen – pro Profil. */
public final class Store {

    private static SharedPreferences sp;

    private Store() {}

    static void init(Context c) {
        sp = c.getSharedPreferences("antenne_store", Context.MODE_PRIVATE);
    }

    private static String scope(String key) {
        Prefs.Profile p = Prefs.active();
        return (p == null ? "none" : p.id) + ":" + key;
    }

    private static LinkedHashSet<String> readSet(String key) {
        LinkedHashSet<String> s = new LinkedHashSet<>();
        try {
            JSONArray a = new JSONArray(sp.getString(scope(key), "[]"));
            for (int i = 0; i < a.length(); i++) s.add(a.getString(i));
        } catch (Exception ignored) {
        }
        return s;
    }

    private static void writeList(String key, List<String> list) {
        JSONArray a = new JSONArray();
        for (String s : list) a.put(s);
        sp.edit().putString(scope(key), a.toString()).apply();
    }

    // ---------- Favoriten ----------

    public static boolean isFavorite(String key) {
        return readSet("favs").contains(key);
    }

    /** @return true, wenn danach Favorit */
    public static boolean toggleFavorite(String key) {
        LinkedHashSet<String> s = readSet("favs");
        boolean now;
        if (s.contains(key)) {
            s.remove(key);
            now = false;
        } else {
            s.add(key);
            now = true;
        }
        writeList("favs", new ArrayList<>(s));
        return now;
    }

    public static List<String> favorites() {
        return new ArrayList<>(readSet("favs"));
    }

    // ---------- Zuletzt gesehen ----------

    public static void addRecent(String key) {
        LinkedHashSet<String> s = readSet("recent");
        s.remove(key);
        List<String> list = new ArrayList<>();
        list.add(key);
        list.addAll(s);
        while (list.size() > 30) list.remove(list.size() - 1);
        writeList("recent", list);
    }

    public static List<String> recents() {
        return new ArrayList<>(readSet("recent"));
    }

    public static String lastChannel() {
        List<String> r = recents();
        return r.isEmpty() ? null : r.get(0);
    }

    // ---------- Weiterschauen ----------

    public static final class Resume {
        public String key;       // m:ID oder e:ID
        public String title;
        public String subtitle;
        public String image;
        public String seriesId;  // bei Folgen
        public long pos;
        public long dur;
        public long time;

        public float progress() {
            return dur <= 0 ? 0f : Math.min(1f, pos / (float) dur);
        }
    }

    private static List<Resume> readResume() {
        List<Resume> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp.getString(scope("resume"), "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Resume r = new Resume();
                r.key = o.optString("k");
                r.title = o.optString("t");
                r.subtitle = o.optString("st");
                r.image = o.optString("i");
                r.seriesId = o.optString("s");
                r.pos = o.optLong("p");
                r.dur = o.optLong("d");
                r.time = o.optLong("tm");
                out.add(r);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static void writeResume(List<Resume> list) {
        JSONArray a = new JSONArray();
        for (Resume r : list) {
            JSONObject o = new JSONObject();
            try {
                o.put("k", r.key);
                o.put("t", r.title);
                o.put("st", r.subtitle == null ? "" : r.subtitle);
                o.put("i", r.image == null ? "" : r.image);
                o.put("s", r.seriesId == null ? "" : r.seriesId);
                o.put("p", r.pos);
                o.put("d", r.dur);
                o.put("tm", r.time);
            } catch (Exception ignored) {
            }
            a.put(o);
        }
        sp.edit().putString(scope("resume"), a.toString()).apply();
    }

    public static void saveResume(Resume r) {
        List<Resume> list = readResume();
        List<Resume> keep = new ArrayList<>();
        for (Resume x : list) {
            if (x.key.equals(r.key)) continue;
            // Bei Serien nur die zuletzt geschaute Folge behalten
            if (r.seriesId != null && !r.seriesId.isEmpty() && r.seriesId.equals(x.seriesId)) continue;
            keep.add(x);
        }
        boolean finished = r.dur > 0 && r.pos > r.dur * 0.94;
        if (!finished && r.pos > 30_000) keep.add(0, r);
        while (keep.size() > 40) keep.remove(keep.size() - 1);
        writeResume(keep);
        // Position separat merken (auch für fertig geschaute Folgen: Häkchen)
        sp.edit().putString(scope("pos:" + r.key), r.pos + "/" + r.dur).apply();
    }

    public static List<Resume> resumeList() {
        return readResume();
    }

    public static long position(String key) {
        String v = sp.getString(scope("pos:" + key), null);
        if (v == null) return 0;
        try {
            String[] p = v.split("/");
            long pos = Long.parseLong(p[0]);
            long dur = Long.parseLong(p[1]);
            if (dur > 0 && pos > dur * 0.94) return 0;
            return pos;
        } catch (Exception e) {
            return 0;
        }
    }

    /** 0..1 für Fortschrittsbalken unter Folgen; -1 wenn nie gesehen. */
    public static float watched(String key) {
        String v = sp.getString(scope("pos:" + key), null);
        if (v == null) return -1f;
        try {
            String[] p = v.split("/");
            long pos = Long.parseLong(p[0]);
            long dur = Long.parseLong(p[1]);
            if (dur <= 0) return 0f;
            return Math.min(1f, pos / (float) dur);
        } catch (Exception e) {
            return -1f;
        }
    }

    public static void clearProfile(String profileId) {
        SharedPreferences.Editor e = sp.edit();
        for (String k : sp.getAll().keySet()) if (k.startsWith(profileId + ":")) e.remove(k);
        e.apply();
    }
}
