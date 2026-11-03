package app.antenne.tv;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Gespeicherte Profile (Logins) und Einstellungen. */
public final class Prefs {

    private static SharedPreferences sp;

    private Prefs() {}

    static void init(Context c) {
        sp = c.getSharedPreferences("antenne", Context.MODE_PRIVATE);
    }

    // ---------- Profile ----------

    public static final class Profile {
        public String id;
        public String name;
        public String type; // "xtream" oder "m3u"
        public String url;
        public String user;
        public String pass;
        public String epgUrl;

        public boolean isXtream() {
            return "xtream".equals(type);
        }

        JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("id", id);
                o.put("name", name);
                o.put("type", type);
                o.put("url", url);
                o.put("user", user == null ? "" : user);
                o.put("pass", pass == null ? "" : pass);
                o.put("epg", epgUrl == null ? "" : epgUrl);
            } catch (Exception ignored) {
            }
            return o;
        }

        static Profile fromJson(JSONObject o) {
            Profile p = new Profile();
            p.id = o.optString("id");
            p.name = o.optString("name");
            p.type = o.optString("type", "xtream");
            p.url = o.optString("url");
            p.user = o.optString("user");
            p.pass = o.optString("pass");
            p.epgUrl = o.optString("epg");
            return p;
        }
    }

    public static List<Profile> profiles() {
        List<Profile> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp.getString("profiles", "[]"));
            for (int i = 0; i < a.length(); i++) out.add(Profile.fromJson(a.getJSONObject(i)));
        } catch (Exception ignored) {
        }
        return out;
    }

    private static void saveAll(List<Profile> list) {
        JSONArray a = new JSONArray();
        for (Profile p : list) a.put(p.toJson());
        sp.edit().putString("profiles", a.toString()).apply();
    }

    public static void saveProfile(Profile p) {
        List<Profile> list = profiles();
        boolean found = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(p.id)) {
                list.set(i, p);
                found = true;
            }
        }
        if (!found) list.add(p);
        saveAll(list);
    }

    public static void deleteProfile(String id) {
        List<Profile> list = profiles();
        List<Profile> keep = new ArrayList<>();
        for (Profile p : list) if (!p.id.equals(id)) keep.add(p);
        saveAll(keep);
        if (id.equals(sp.getString("active", ""))) {
            sp.edit().putString("active", keep.isEmpty() ? "" : keep.get(0).id).apply();
        }
    }

    public static Profile active() {
        String id = sp.getString("active", "");
        List<Profile> list = profiles();
        for (Profile p : list) if (p.id.equals(id)) return p;
        return list.isEmpty() ? null : list.get(0);
    }

    public static void setActive(String id) {
        sp.edit().putString("active", id).apply();
    }

    // ---------- Einstellungen ----------

    /** Live-Format: "ts" (MPEG-TS) oder "m3u8" (HLS). */
    public static String liveFormat() {
        return sp.getString("live_format", "ts");
    }

    public static void setLiveFormat(String f) {
        sp.edit().putString("live_format", f).apply();
    }

    /**
     * Vorschau im Live-TV. Bei Abos mit nur einer Verbindung automatisch aus, sonst würde
     * die Vorschau die einzige Verbindung belegen und der Vollbild-Player hängen.
     */
    public static boolean preview() {
        if (!sp.getBoolean("preview", true)) return false;
        Xtream.Account a = Library.get().account;
        return a == null || a.maxConnections <= 0 || a.maxConnections > 1;
    }

    public static void setPreview(boolean b) {
        sp.edit().putBoolean("preview", b).apply();
    }

    public static boolean previewSetting() {
        return sp.getBoolean("preview", true);
    }

    public static boolean cleanNames() {
        return sp.getBoolean("clean_names", true);
    }

    public static void setCleanNames(boolean b) {
        sp.edit().putBoolean("clean_names", b).apply();
    }

    public static boolean autoNext() {
        return sp.getBoolean("auto_next", true);
    }

    public static void setAutoNext(boolean b) {
        sp.edit().putBoolean("auto_next", b).apply();
    }

    public static String userAgent() {
        return sp.getString("ua", "VLC/3.0.20 LibVLC/3.0.20");
    }

    public static void setUserAgent(String ua) {
        sp.edit().putString("ua", ua).apply();
    }

    // ---------- Darstellung & Player ----------

    public static boolean darkMode() {
        return sp.getBoolean("dark", true);
    }

    public static void setDarkMode(boolean b) {
        sp.edit().putBoolean("dark", b).apply();
    }

    /** Puffer: 0 = Klein (schneller Start), 1 = Normal, 2 = Gross, 3 = Sehr gross (stabil). */
    public static int buffer() {
        return sp.getInt("buffer", 1);
    }

    public static void setBuffer(int b) {
        sp.edit().putInt("buffer", b).apply();
    }

    public static final String[] BUFFER_NAMES = {"Klein · schneller Start", "Normal", "Gross · stabiler", "Sehr gross · für schwache Streams"};

    /** Player: "exo" (Standard), "exo_sw" (Kompatibel), "vlc" (VLC-Engine), "ext" (andere App). */
    public static String engine() {
        return sp.getString("engine", "exo");
    }

    public static void setEngine(String e) {
        sp.edit().putString("engine", e).apply();
    }

    public static final String[] ENGINE_IDS = {"exo", "exo_sw", "vlc", "ext"};
    public static final String[] ENGINE_NAMES = {"Standard", "Kompatibel (Software)", "VLC-Engine", "Externe App"};

    public static String engineName(String id) {
        for (int i = 0; i < ENGINE_IDS.length; i++) if (ENGINE_IDS[i].equals(id)) return ENGINE_NAMES[i];
        return ENGINE_NAMES[0];
    }

    /** Höchste Qualität: 0 = Automatisch, sonst maximale Bildhöhe (2160, 1080, 720, 480). */
    public static int maxQuality() {
        return sp.getInt("max_q", 0);
    }

    public static void setMaxQuality(int h) {
        sp.edit().putInt("max_q", h).apply();
    }

    public static String qualityName(int h) {
        return h <= 0 ? "Automatisch" : h >= 2160 ? "Bis 4K" : "Bis " + h + "p";
    }

    public static String lastTab() {
        return sp.getString("last_tab", "live");
    }

    public static void setLastTab(String t) {
        sp.edit().putString("last_tab", t).apply();
    }
}
