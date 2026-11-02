package app.antenne.tv;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Alle Inhalte des aktiven Profils. Sender werden zuerst geladen (damit die App schnell startet),
 * Filme, Serien, Logos und TV-Programm folgen im Hintergrund.
 */
public final class Library {

    public interface Listener {
        void onLibraryChanged(String what); // "live", "vod", "series", "logos"
    }

    public interface Progress {
        void step(String message);
    }

    private static final Library INSTANCE = new Library();
    private static final long MAX_AGE = 12 * 3600_000L;

    public static Library get() {
        return INSTANCE;
    }

    public Prefs.Profile profile;
    public Xtream xtream;
    public Xtream.Account account;

    public final List<Model.Category> liveCats = new ArrayList<>();
    public final List<Model.Channel> channels = new ArrayList<>();
    public final List<Model.Category> movieCats = new ArrayList<>();
    public final List<Model.Movie> movies = new ArrayList<>();
    public final List<Model.Category> seriesCats = new ArrayList<>();
    public final List<Model.Series> series = new ArrayList<>();

    public volatile boolean vodLoaded, seriesLoaded;
    public volatile String vodError, seriesError;

    private final Map<String, Model.Channel> channelByKey = new HashMap<>();
    private final Map<String, Model.Movie> movieById = new HashMap<>();
    private final Map<String, Model.Series> seriesById = new HashMap<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private int generation;

    private Library() {}

    public void addListener(Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void fire(String what, int gen) {
        Bg.ui(() -> {
            if (gen != generation) return;
            for (Listener l : listeners) l.onLibraryChanged(what);
        });
    }

    public boolean isLoaded() {
        return profile != null && !channels.isEmpty() || profile != null && (!movies.isEmpty() || !series.isEmpty());
    }

    public Model.Channel channel(String key) {
        return channelByKey.get(key);
    }

    public Model.Movie movie(String id) {
        return movieById.get(id);
    }

    public Model.Series series(String id) {
        return seriesById.get(id);
    }

    public static File dir(Prefs.Profile p) {
        File d = new File(App.get().getFilesDir(), "cache_" + p.id);
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static void clearCache(Prefs.Profile p) {
        File d = dir(p);
        File[] fs = d.listFiles();
        if (fs != null) for (File f : fs) f.delete();
    }

    private static boolean fresh(File f) {
        return f.exists() && f.length() > 2 && System.currentTimeMillis() - f.lastModified() < MAX_AGE;
    }

    /** Lädt das Profil. Blockiert bis die Sender da sind; der Rest folgt im Hintergrund. */
    public void load(Prefs.Profile p, boolean force, Progress progress) throws IOException {
        final int gen;
        synchronized (this) {
            gen = ++generation;
        }
        profile = p;
        account = null;
        xtream = null;
        vodLoaded = false;
        seriesLoaded = false;
        vodError = null;
        seriesError = null;
        clearAll();
        Epg.reset();
        File dir = dir(p);
        if (force) clearCache(p);

        if (p.isXtream()) {
            xtream = new Xtream(p.url, p.user, p.pass);
            progress.step("Anmelden …");
            try {
                account = xtream.login();
            } catch (IOException e) {
                // Ohne Internet trotzdem mit dem Cache starten
                if (!fresh(new File(dir, "live.json"))) throw e;
            }
            progress.step("Sender laden …");
            File lc = new File(dir, "live_cats.json");
            File ls = new File(dir, "live.json");
            if (!fresh(lc)) Http.download(xtream.api("get_live_categories", null), lc);
            if (!fresh(ls)) Http.download(xtream.api("get_live_streams", null), ls);
            List<Model.Category> cats = xtream.categories(lc);
            List<Model.Channel> chs = xtream.channels(ls);
            fill(liveCats, cats, chs, true);
            channels.addAll(chs);
            for (Model.Channel c : chs) channelByKey.put(c.key(), c);

            Bg.run(() -> loadXtreamVod(gen, dir));
            if (p.epgUrl != null && !p.epgUrl.trim().isEmpty()) startXmltv(p.epgUrl.trim(), dir, gen);
        } else {
            progress.step("Playlist laden …");
            File pl = new File(dir, "playlist.m3u");
            if (!fresh(pl)) Http.download(p.url.trim(), pl);
            progress.step("Playlist lesen …");
            M3u m = M3u.parse(pl);
            if (m.channels.isEmpty() && m.movies.isEmpty() && m.series.isEmpty()) {
                throw new IOException("Die Playlist ist leer oder ungültig");
            }
            fill(liveCats, m.liveCats, new ArrayList<Object>(m.channels), true);
            channels.addAll(m.channels);
            for (Model.Channel c : m.channels) channelByKey.put(c.key(), c);
            fillMovies(m.movieCats, m.movies);
            fillSeries(m.seriesCats, m.series);
            vodLoaded = true;
            seriesLoaded = true;
            String epg = p.epgUrl != null && !p.epgUrl.trim().isEmpty() ? p.epgUrl.trim() : m.epgUrl;
            if (epg != null && !epg.isEmpty()) startXmltv(epg, dir, gen);
            else Epg.bulkFailed(channels);
        }

        Bg.run(() -> {
            if (Logos.fill(channels, dir)) fire("logos", gen);
        });
    }

    private void loadXtreamVod(int gen, File dir) {
        try {
            File vc = new File(dir, "vod_cats.json");
            File vs = new File(dir, "vod.json");
            if (!fresh(vc)) Http.download(xtream.api("get_vod_categories", null), vc);
            if (!fresh(vs)) Http.download(xtream.api("get_vod_streams", null), vs);
            List<Model.Category> cats = xtream.categories(vc);
            List<Model.Movie> ms = xtream.movies(vs);
            if (gen != generation) return;
            synchronized (this) {
                fillMovies(cats, ms);
            }
        } catch (Exception e) {
            vodError = e.getMessage();
        }
        vodLoaded = true;
        fire("vod", gen);
        try {
            File sc = new File(dir, "series_cats.json");
            File ss = new File(dir, "series.json");
            if (!fresh(sc)) Http.download(xtream.api("get_series_categories", null), sc);
            if (!fresh(ss)) Http.download(xtream.api("get_series", null), ss);
            List<Model.Category> cats = xtream.categories(sc);
            List<Model.Series> ss2 = xtream.series(ss);
            if (gen != generation) return;
            synchronized (this) {
                fillSeries(cats, ss2);
            }
        } catch (Exception e) {
            seriesError = e.getMessage();
        }
        seriesLoaded = true;
        fire("series", gen);
    }

    private void startXmltv(String url, File dir, int gen) {
        Epg.markBulk();
        List<Model.Channel> snapshot = new ArrayList<>(channels);
        Bg.run(() -> {
            File f = new File(dir, "epg.xml");
            try {
                if (!f.exists() || System.currentTimeMillis() - f.lastModified() > 6 * 3600_000L) Http.download(url, f);
                if (gen != generation) return;
                Epg.loadXmltv(f, snapshot);
            } catch (Exception e) {
                Epg.bulkFailed(snapshot);
            }
        });
    }

    private void fillMovies(List<Model.Category> cats, List<Model.Movie> ms) {
        movies.clear();
        movieCats.clear();
        movieById.clear();
        movies.addAll(ms);
        for (Model.Movie m : ms) movieById.put(m.id, m);
        fill(movieCats, cats, new ArrayList<Object>(ms), false);
    }

    private void fillSeries(List<Model.Category> cats, List<Model.Series> ss) {
        series.clear();
        seriesCats.clear();
        seriesById.clear();
        series.addAll(ss);
        for (Model.Series s : ss) seriesById.put(s.id, s);
        fill(seriesCats, cats, new ArrayList<Object>(ss), false);
    }

    /** Ordnet Einträge ihren Kategorien zu; leere Kategorien fallen weg. */
    @SuppressWarnings("unchecked")
    private static void fill(List<Model.Category> target, List<Model.Category> cats, List<?> items, boolean keepOrder) {
        Map<String, Model.Category> byId = new HashMap<>();
        for (Model.Category c : cats) {
            c.items.clear();
            byId.put(c.id, c);
        }
        Model.Category other = null;
        for (Object o : items) {
            String cid = null;
            if (o instanceof Model.Channel) cid = ((Model.Channel) o).categoryId;
            else if (o instanceof Model.Movie) cid = ((Model.Movie) o).categoryId;
            else if (o instanceof Model.Series) cid = ((Model.Series) o).categoryId;
            Model.Category c = cid == null ? null : byId.get(cid);
            if (c == null) {
                if (other == null) other = new Model.Category("__other", "Weitere");
                c = other;
            }
            c.items.add(o);
        }
        target.clear();
        for (Model.Category c : cats) if (!c.items.isEmpty()) target.add(c);
        if (other != null) target.add(other);
        if (!keepOrder) {
            // Neueste zuerst innerhalb der Kategorie
            for (Model.Category c : target) {
                Collections.sort(c.items, (a, b) -> Long.compare(added(b), added(a)));
            }
        }
    }

    private static long added(Object o) {
        if (o instanceof Model.Movie) return ((Model.Movie) o).added;
        if (o instanceof Model.Series) return ((Model.Series) o).added;
        return 0;
    }

    private void clearAll() {
        liveCats.clear();
        channels.clear();
        movieCats.clear();
        movies.clear();
        seriesCats.clear();
        series.clear();
        channelByKey.clear();
        movieById.clear();
        seriesById.clear();
    }

    // ---------- Adressen ----------

    public String streamUrl(Model.Channel c) {
        if (c.url != null) return c.url;
        return xtream == null ? null : xtream.liveUrl(c.id);
    }

    public String streamUrl(Model.Movie m) {
        if (m.url != null) return m.url;
        return xtream == null ? null : xtream.movieUrl(m.id, m.ext);
    }

    public String streamUrl(Model.Episode e) {
        if (e.url != null) return e.url;
        return xtream == null ? null : xtream.episodeUrl(e.id, e.ext);
    }

    /** Sender einer Live-Kategorie: "fav", "recent", "all" oder "cat:ID". */
    public List<Model.Channel> channelsFor(String catKey) {
        List<Model.Channel> out = new ArrayList<>();
        if (catKey == null || "all".equals(catKey)) {
            out.addAll(channels);
        } else if ("fav".equals(catKey)) {
            for (String k : Store.favorites()) {
                Model.Channel c = channelByKey.get(k);
                if (c != null) out.add(c);
            }
        } else if ("recent".equals(catKey)) {
            for (String k : Store.recents()) {
                Model.Channel c = channelByKey.get(k);
                if (c != null) out.add(c);
            }
        } else if (catKey.startsWith("cat:")) {
            String id = catKey.substring(4);
            for (Model.Category c : liveCats) {
                if (c.id.equals(id)) {
                    for (Object o : c.items) out.add((Model.Channel) o);
                    break;
                }
            }
        }
        return out;
    }

    public String categoryName(String catKey) {
        if (catKey == null || "all".equals(catKey)) return "Alle Sender";
        if ("fav".equals(catKey)) return "Favoriten";
        if ("recent".equals(catKey)) return "Zuletzt gesehen";
        if (catKey.startsWith("cat:")) {
            String id = catKey.substring(4);
            for (Model.Category c : liveCats) if (c.id.equals(id)) return c.name;
        }
        return "";
    }

    /** Neueste Filme über alle Kategorien. */
    public List<Model.Movie> recentMovies(int n) {
        List<Model.Movie> l = new ArrayList<>(movies);
        Collections.sort(l, (a, b) -> Long.compare(b.added, a.added));
        return l.subList(0, Math.min(n, l.size()));
    }

    public List<Model.Series> recentSeries(int n) {
        List<Model.Series> l = new ArrayList<>(series);
        Collections.sort(l, (a, b) -> Long.compare(b.added, a.added));
        return l.subList(0, Math.min(n, l.size()));
    }
}
