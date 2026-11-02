package app.antenne.tv;

import android.net.Uri;
import android.util.Base64;
import android.util.JsonReader;
import android.util.JsonToken;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/** Xtream-Codes-API: Login, Sender, Filme, Serien und EPG. */
public final class Xtream {

    public final String base;
    public final String user;
    public final String pass;

    public Xtream(String url, String user, String pass) {
        this.base = normalizeBase(url);
        this.user = user;
        this.pass = pass;
    }

    /** „server.tv:8080/“ → „http://server.tv:8080“ (ohne player_api.php usw.). */
    public static String normalizeBase(String url) {
        String u = url == null ? "" : url.trim();
        if (!u.matches("(?i)^https?://.*")) u = "http://" + u;
        int q = u.indexOf('?');
        if (q >= 0) u = u.substring(0, q);
        String lower = u.toLowerCase(Locale.ROOT);
        for (String tail : new String[]{"/player_api.php", "/get.php", "/xmltv.php", "/panel_api.php"}) {
            int i = lower.indexOf(tail);
            if (i >= 0) {
                u = u.substring(0, i);
                lower = u.toLowerCase(Locale.ROOT);
            }
        }
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    /** Erkennt, ob eine M3U-Adresse eigentlich ein Xtream-Zugang ist (get.php?username=…&password=…). */
    public static Prefs.Profile fromM3uUrl(String m3u) {
        try {
            Uri u = Uri.parse(m3u.trim());
            String user = u.getQueryParameter("username");
            String pass = u.getQueryParameter("password");
            String path = u.getPath() == null ? "" : u.getPath();
            if (user == null || pass == null || !path.toLowerCase(Locale.ROOT).endsWith("get.php")) return null;
            Prefs.Profile p = new Prefs.Profile();
            p.type = "xtream";
            p.url = u.getScheme() + "://" + u.getEncodedAuthority();
            p.user = user;
            p.pass = pass;
            return p;
        } catch (Exception e) {
            return null;
        }
    }

    String api(String action, String extra) {
        StringBuilder sb = new StringBuilder(base).append("/player_api.php?username=")
                .append(Uri.encode(user)).append("&password=").append(Uri.encode(pass));
        if (action != null) sb.append("&action=").append(action);
        if (extra != null) sb.append(extra);
        return sb.toString();
    }

    // ---------- Login ----------

    public static final class Account {
        public String status;
        public long expires;      // ms, 0 = unbegrenzt
        public int maxConnections;
        public int activeConnections;
        public boolean trial;
        public String serverTz;
    }

    public Account login() throws IOException {
        String body = Http.get(api(null, null));
        try {
            JSONObject o = new JSONObject(body);
            JSONObject ui = o.optJSONObject("user_info");
            if (ui == null) throw new IOException("Unerwartete Antwort vom Server");
            if (ui.optInt("auth", 1) == 0) throw new IOException("Benutzername oder Passwort falsch");
            Account a = new Account();
            a.status = ui.optString("status", "Active");
            String exp = ui.optString("exp_date", "");
            try {
                a.expires = exp.isEmpty() || "null".equals(exp) ? 0 : Long.parseLong(exp) * 1000L;
            } catch (NumberFormatException e) {
                a.expires = 0;
            }
            a.maxConnections = parseInt(ui.optString("max_connections", "0"));
            a.activeConnections = parseInt(ui.optString("active_cons", "0"));
            a.trial = "1".equals(ui.optString("is_trial"));
            JSONObject si = o.optJSONObject("server_info");
            if (si != null) a.serverTz = si.optString("timezone");
            if (!"active".equalsIgnoreCase(a.status)) {
                throw new IOException("Konto ist nicht aktiv (" + a.status + ")");
            }
            return a;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Server antwortet nicht wie ein Xtream-Server");
        }
    }

    // ---------- Stream-Adressen ----------

    public String liveUrl(String streamId) {
        String ext = Prefs.liveFormat();
        return base + "/live/" + Uri.encode(user) + "/" + Uri.encode(pass) + "/" + streamId + "." + ext;
    }

    public String movieUrl(String id, String ext) {
        return base + "/movie/" + Uri.encode(user) + "/" + Uri.encode(pass) + "/" + id + "." + (ext == null || ext.isEmpty() ? "mp4" : ext);
    }

    public String episodeUrl(String id, String ext) {
        return base + "/series/" + Uri.encode(user) + "/" + Uri.encode(pass) + "/" + id + "." + (ext == null || ext.isEmpty() ? "mp4" : ext);
    }

    // ---------- Listen (Datei-Cache + Streaming-Parser) ----------

    public List<Model.Category> categories(File f) throws IOException {
        List<Model.Category> out = new ArrayList<>();
        try (JsonReader r = reader(f)) {
            if (r.peek() != JsonToken.BEGIN_ARRAY) {
                r.skipValue();
                return out;
            }
            r.beginArray();
            while (r.hasNext()) {
                String id = null, name = null;
                r.beginObject();
                while (r.hasNext()) {
                    String k = r.nextName();
                    if ("category_id".equals(k)) id = str(r);
                    else if ("category_name".equals(k)) name = str(r);
                    else r.skipValue();
                }
                r.endObject();
                if (id != null) out.add(new Model.Category(id, name == null ? "?" : name.trim()));
            }
            r.endArray();
        }
        return out;
    }

    public List<Model.Channel> channels(File f) throws IOException {
        List<Model.Channel> out = new ArrayList<>();
        try (JsonReader r = reader(f)) {
            if (r.peek() != JsonToken.BEGIN_ARRAY) {
                r.skipValue();
                return out;
            }
            r.beginArray();
            while (r.hasNext()) {
                Model.Channel c = new Model.Channel();
                r.beginObject();
                while (r.hasNext()) {
                    String k = r.nextName();
                    switch (k) {
                        case "stream_id": c.id = str(r); break;
                        case "num": c.num = parseInt(str(r)); break;
                        case "name": c.rawName = str(r); break;
                        case "stream_icon": c.logo = str(r); break;
                        case "category_id": c.categoryId = str(r); break;
                        case "epg_channel_id": c.epgId = str(r); break;
                        case "tv_archive": c.archive = "1".equals(str(r)); break;
                        case "tv_archive_duration": c.archiveDays = parseInt(str(r)); break;
                        default: r.skipValue();
                    }
                }
                r.endObject();
                if (c.id == null) continue;
                if (c.rawName == null) c.rawName = "Sender " + c.id;
                c.name = Names.clean(c.rawName);
                if (c.logo != null && c.logo.trim().isEmpty()) c.logo = null;
                out.add(c);
            }
            r.endArray();
        }
        return out;
    }

    public List<Model.Movie> movies(File f) throws IOException {
        List<Model.Movie> out = new ArrayList<>();
        try (JsonReader r = reader(f)) {
            if (r.peek() != JsonToken.BEGIN_ARRAY) {
                r.skipValue();
                return out;
            }
            r.beginArray();
            while (r.hasNext()) {
                Model.Movie m = new Model.Movie();
                r.beginObject();
                while (r.hasNext()) {
                    String k = r.nextName();
                    switch (k) {
                        case "stream_id": m.id = str(r); break;
                        case "name": m.name = str(r); break;
                        case "stream_icon": m.poster = str(r); break;
                        case "category_id": m.categoryId = str(r); break;
                        case "container_extension": m.ext = str(r); break;
                        case "rating": m.rating = str(r); break;
                        case "year": m.year = str(r); break;
                        case "added": m.added = parseLong(str(r)); break;
                        default: r.skipValue();
                    }
                }
                r.endObject();
                if (m.id == null) continue;
                if (m.name == null) m.name = "Film";
                m.name = m.name.trim();
                if (m.year == null || m.year.isEmpty()) m.year = yearFromName(m.name);
                out.add(m);
            }
            r.endArray();
        }
        return out;
    }

    public List<Model.Series> series(File f) throws IOException {
        List<Model.Series> out = new ArrayList<>();
        try (JsonReader r = reader(f)) {
            if (r.peek() != JsonToken.BEGIN_ARRAY) {
                r.skipValue();
                return out;
            }
            r.beginArray();
            while (r.hasNext()) {
                Model.Series s = new Model.Series();
                r.beginObject();
                while (r.hasNext()) {
                    String k = r.nextName();
                    switch (k) {
                        case "series_id": s.id = str(r); break;
                        case "name": s.name = str(r); break;
                        case "cover": s.poster = str(r); break;
                        case "category_id": s.categoryId = str(r); break;
                        case "plot": s.plot = str(r); break;
                        case "rating": s.rating = str(r); break;
                        case "genre": s.genre = str(r); break;
                        case "releaseDate":
                        case "release_date": {
                            String d = str(r);
                            if (d != null && d.length() >= 4 && (s.year == null || s.year.isEmpty())) s.year = d.substring(0, 4);
                            break;
                        }
                        case "year": s.year = str(r); break;
                        case "last_modified": s.added = parseLong(str(r)); break;
                        case "backdrop_path": s.backdrop = firstString(r); break;
                        default: r.skipValue();
                    }
                }
                r.endObject();
                if (s.id == null) continue;
                if (s.name == null) s.name = "Serie";
                s.name = s.name.trim();
                out.add(s);
            }
            r.endArray();
        }
        return out;
    }

    // ---------- Details ----------

    public static final class MovieInfo {
        public String plot, cast, director, genre, release, rating, duration, backdrop, poster, trailer;
        public String ext;
    }

    public MovieInfo movieInfo(String id) throws IOException {
        String body = Http.get(api("get_vod_info", "&vod_id=" + id));
        MovieInfo mi = new MovieInfo();
        try {
            JSONObject o = new JSONObject(body);
            JSONObject info = o.optJSONObject("info");
            if (info != null) {
                mi.plot = clean(info.optString("plot", info.optString("description")));
                mi.cast = clean(info.optString("cast", info.optString("actors")));
                mi.director = clean(info.optString("director"));
                mi.genre = clean(info.optString("genre"));
                mi.release = clean(info.optString("releasedate", info.optString("release_date")));
                mi.rating = clean(info.optString("rating"));
                mi.duration = clean(info.optString("duration"));
                mi.poster = clean(info.optString("movie_image", info.optString("cover_big")));
                mi.trailer = clean(info.optString("youtube_trailer"));
                Object bp = info.opt("backdrop_path");
                if (bp instanceof JSONArray && ((JSONArray) bp).length() > 0) mi.backdrop = ((JSONArray) bp).optString(0);
                else if (bp instanceof String) mi.backdrop = (String) bp;
            }
            JSONObject md = o.optJSONObject("movie_data");
            if (md != null) mi.ext = md.optString("container_extension", null);
        } catch (Exception ignored) {
        }
        return mi;
    }

    public static final class SeriesInfo {
        public String plot, cast, genre, rating, backdrop, poster, release;
        public final List<Integer> seasons = new ArrayList<>();
        public final List<Model.Episode> episodes = new ArrayList<>();
    }

    public SeriesInfo seriesInfo(String id) throws IOException {
        String body = Http.get(api("get_series_info", "&series_id=" + id));
        SeriesInfo si = new SeriesInfo();
        try {
            JSONObject o = new JSONObject(body);
            JSONObject info = o.optJSONObject("info");
            if (info != null) {
                si.plot = clean(info.optString("plot"));
                si.cast = clean(info.optString("cast"));
                si.genre = clean(info.optString("genre"));
                si.rating = clean(info.optString("rating"));
                si.poster = clean(info.optString("cover"));
                si.release = clean(info.optString("releaseDate", info.optString("release_date")));
                Object bp = info.opt("backdrop_path");
                if (bp instanceof JSONArray && ((JSONArray) bp).length() > 0) si.backdrop = ((JSONArray) bp).optString(0);
                else if (bp instanceof String) si.backdrop = (String) bp;
            }
            Object eps = o.opt("episodes");
            List<JSONArray> seasonArrays = new ArrayList<>();
            if (eps instanceof JSONObject) {
                JSONObject e = (JSONObject) eps;
                Iterator<String> it = e.keys();
                while (it.hasNext()) {
                    JSONArray a = e.optJSONArray(it.next());
                    if (a != null) seasonArrays.add(a);
                }
            } else if (eps instanceof JSONArray) {
                JSONArray outer = (JSONArray) eps;
                for (int i = 0; i < outer.length(); i++) {
                    Object x = outer.opt(i);
                    if (x instanceof JSONArray) seasonArrays.add((JSONArray) x);
                }
            }
            for (JSONArray a : seasonArrays) {
                for (int i = 0; i < a.length(); i++) {
                    JSONObject ep = a.optJSONObject(i);
                    if (ep == null) continue;
                    Model.Episode e = new Model.Episode();
                    e.id = ep.optString("id");
                    e.seriesId = id;
                    e.season = ep.optInt("season", 1);
                    e.number = ep.optInt("episode_num", i + 1);
                    e.title = clean(ep.optString("title"));
                    e.ext = ep.optString("container_extension", "mp4");
                    JSONObject ei = ep.optJSONObject("info");
                    if (ei != null) {
                        e.plot = clean(ei.optString("plot"));
                        e.image = clean(ei.optString("movie_image"));
                        e.durationSecs = ei.optInt("duration_secs", 0);
                    }
                    si.episodes.add(e);
                    if (!si.seasons.contains(e.season)) si.seasons.add(e.season);
                }
            }
            java.util.Collections.sort(si.seasons);
            java.util.Collections.sort(si.episodes, (x, y) -> x.season != y.season
                    ? Integer.compare(x.season, y.season) : Integer.compare(x.number, y.number));
        } catch (Exception ignored) {
        }
        return si;
    }

    // ---------- EPG ----------

    /** Komplettes Tagesprogramm eines Senders (Titel/Beschreibung sind Base64). */
    public List<Model.Programme> epg(String streamId) throws IOException {
        List<Model.Programme> out = new ArrayList<>();
        String body = Http.get(api("get_simple_data_table", "&stream_id=" + streamId));
        parseEpg(body, out);
        if (out.isEmpty()) {
            body = Http.get(api("get_short_epg", "&stream_id=" + streamId + "&limit=12"));
            parseEpg(body, out);
        }
        java.util.Collections.sort(out, (a, b) -> Long.compare(a.start, b.start));
        // Doppelte/überlappende Einträge entfernen
        List<Model.Programme> clean = new ArrayList<>();
        long lastEnd = Long.MIN_VALUE;
        for (Model.Programme p : out) {
            if (p.end <= p.start) continue;
            if (p.start < lastEnd) continue;
            clean.add(p);
            lastEnd = p.end;
        }
        return clean;
    }

    private static void parseEpg(String body, List<Model.Programme> out) {
        try {
            JSONObject o = new JSONObject(body);
            JSONArray a = o.optJSONArray("epg_listings");
            if (a == null) return;
            for (int i = 0; i < a.length(); i++) {
                JSONObject e = a.optJSONObject(i);
                if (e == null) continue;
                long start = parseLong(e.optString("start_timestamp")) * 1000L;
                long end = parseLong(e.optString("stop_timestamp", e.optString("end_timestamp"))) * 1000L;
                if (start <= 0 || end <= 0) continue;
                String title = b64(e.optString("title"));
                String desc = b64(e.optString("description"));
                out.add(new Model.Programme(start, end, title, desc));
            }
        } catch (Exception ignored) {
        }
    }

    // ---------- Hilfen ----------

    private static JsonReader reader(File f) throws IOException {
        JsonReader r = new JsonReader(new InputStreamReader(new BufferedInputStream(new FileInputStream(f), 64 * 1024), StandardCharsets.UTF_8));
        r.setLenient(true);
        return r;
    }

    private static String str(JsonReader r) throws IOException {
        JsonToken t = r.peek();
        if (t == JsonToken.NULL) {
            r.nextNull();
            return null;
        }
        if (t == JsonToken.STRING || t == JsonToken.NUMBER) return r.nextString();
        if (t == JsonToken.BOOLEAN) return r.nextBoolean() ? "1" : "0";
        if (t == JsonToken.BEGIN_ARRAY) return firstString(r);
        r.skipValue();
        return null;
    }

    private static String firstString(JsonReader r) throws IOException {
        JsonToken t = r.peek();
        if (t == JsonToken.STRING) return r.nextString();
        if (t != JsonToken.BEGIN_ARRAY) {
            r.skipValue();
            return null;
        }
        String first = null;
        r.beginArray();
        while (r.hasNext()) {
            if (first == null && r.peek() == JsonToken.STRING) first = r.nextString();
            else r.skipValue();
        }
        r.endArray();
        return first;
    }

    private static String clean(String s) {
        if (s == null) return null;
        s = s.trim();
        return s.isEmpty() || "null".equalsIgnoreCase(s) ? null : s;
    }

    private static String b64(String s) {
        if (s == null || s.isEmpty()) return "";
        try {
            return new String(Base64.decode(s, Base64.DEFAULT), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return s;
        }
    }

    static int parseInt(String s) {
        try {
            return s == null ? 0 : (int) Double.parseDouble(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    static long parseLong(String s) {
        try {
            return s == null ? 0 : (long) Double.parseDouble(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    static String yearFromName(String name) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[(\\[ ](19[3-9]\\d|20[0-4]\\d)[)\\] ]?\\s*$").matcher(name);
        return m.find() ? m.group(1) : null;
    }
}
