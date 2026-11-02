package app.antenne.tv;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.GZIPInputStream;

/**
 * TV-Programm. Bei Xtream wird es pro Sender bei Bedarf geladen (schont alte Fernseher),
 * bei M3U einmal aus der XMLTV-Datei gelesen.
 */
public final class Epg {

    public interface Listener {
        void onEpgChanged();
    }

    private static final Map<String, List<Model.Programme>> DATA = new ConcurrentHashMap<>();
    private static final Map<String, Long> LOADED_AT = new ConcurrentHashMap<>();
    private static final Set<String> PENDING = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile boolean bulkMode;
    private static volatile boolean bulkLoading;
    private static boolean notifyQueued;

    private Epg() {}

    public static void addListener(Listener l) {
        if (!LISTENERS.contains(l)) LISTENERS.add(l);
    }

    public static void removeListener(Listener l) {
        LISTENERS.remove(l);
    }

    public static void reset() {
        DATA.clear();
        LOADED_AT.clear();
        PENDING.clear();
        bulkMode = false;
    }

    /** Programm des Senders (oder null, solange es lädt). Startet bei Bedarf das Laden. */
    public static List<Model.Programme> get(Model.Channel c) {
        if (c == null) return null;
        String k = c.key();
        List<Model.Programme> l = DATA.get(k);
        Long at = LOADED_AT.get(k);
        boolean stale = at != null && System.currentTimeMillis() - at > 3 * 3600_000L;
        if ((l == null || stale) && !bulkMode) request(c);
        return l;
    }

    public static boolean isLoading(Model.Channel c) {
        return c != null && (PENDING.contains(c.key()) || (bulkMode && bulkLoading && !DATA.containsKey(c.key())));
    }

    public static Model.Programme now(Model.Channel c) {
        return at(c, System.currentTimeMillis());
    }

    public static Model.Programme at(Model.Channel c, long t) {
        List<Model.Programme> l = get(c);
        if (l == null) return null;
        for (Model.Programme p : l) if (p.start <= t && t < p.end) return p;
        return null;
    }

    public static Model.Programme next(Model.Channel c) {
        List<Model.Programme> l = get(c);
        if (l == null) return null;
        long now = System.currentTimeMillis();
        for (Model.Programme p : l) if (p.start > now) return p;
        return null;
    }

    private static void request(Model.Channel c) {
        Xtream x = Library.get().xtream;
        if (x == null) return;
        String k = c.key();
        if (!PENDING.add(k)) return;
        Bg.epg(() -> {
            List<Model.Programme> list;
            try {
                list = x.epg(c.id);
            } catch (Exception e) {
                list = new ArrayList<>();
            }
            DATA.put(k, list);
            LOADED_AT.put(k, System.currentTimeMillis());
            PENDING.remove(k);
            notifyChanged();
        });
    }

    private static void notifyChanged() {
        Bg.ui(() -> {
            if (notifyQueued) return;
            notifyQueued = true;
            // Mehrere Antworten kurz sammeln, damit Listen nicht flackern
            Bg.later(() -> {
                notifyQueued = false;
                for (Listener l : LISTENERS) l.onEpgChanged();
            }, 150);
        });
    }

    // ---------- XMLTV ----------

    /** Liest eine XMLTV-Datei (auch .gz) und behält nur Sendungen der vorhandenen Sender im Zeitfenster. */
    public static void loadXmltv(File f, List<Model.Channel> channels) {
        bulkMode = true;
        bulkLoading = true;
        Map<String, List<Model.Channel>> byId = new HashMap<>();
        Map<String, List<Model.Channel>> byName = new HashMap<>();
        for (Model.Channel c : channels) {
            if (c.epgId != null) add(byId, c.epgId.toLowerCase(Locale.ROOT), c);
            add(byName, Names.key(c.rawName), c);
        }
        Map<String, List<Model.Channel>> xmlToChannels = new HashMap<>();
        Map<String, List<Model.Programme>> tmp = new HashMap<>();
        long now = System.currentTimeMillis();
        long from = now - 6 * 3600_000L;
        long to = now + 48 * 3600_000L;
        SimpleDateFormat fmtTz = new SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US);
        SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
        try (InputStream raw = new BufferedInputStream(new FileInputStream(f), 64 * 1024)) {
            InputStream in = raw;
            raw.mark(4);
            int b1 = raw.read(), b2 = raw.read();
            raw.reset();
            if (b1 == 0x1f && b2 == 0x8b) in = new GZIPInputStream(raw, 64 * 1024);
            XmlPullParser p = Xml.newPullParser();
            p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            p.setInput(in, null);
            String curChannelId = null;
            Set<Model.Channel> curTargets = null;
            long ps = 0, pe = 0;
            String title = null, desc = null;
            String tag = null;
            int ev = p.getEventType();
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    tag = p.getName();
                    if ("channel".equals(tag)) {
                        curChannelId = p.getAttributeValue(null, "id");
                        if (curChannelId != null) {
                            List<Model.Channel> m = byId.get(curChannelId.toLowerCase(Locale.ROOT));
                            if (m != null) xmlToChannels.put(curChannelId, new ArrayList<>(m));
                        }
                    } else if ("display-name".equals(tag) && curChannelId != null) {
                        String name = p.nextText();
                        List<Model.Channel> m = byName.get(Names.key(name));
                        if (m != null) {
                            List<Model.Channel> ex = xmlToChannels.get(curChannelId);
                            if (ex == null) xmlToChannels.put(curChannelId, ex = new ArrayList<>());
                            for (Model.Channel c : m) if (c.epgId == null && !ex.contains(c)) ex.add(c);
                        }
                    } else if ("programme".equals(tag)) {
                        String ch = p.getAttributeValue(null, "channel");
                        List<Model.Channel> targets = ch == null ? null : xmlToChannels.get(ch);
                        if (targets == null && ch != null) targets = byId.get(ch.toLowerCase(Locale.ROOT));
                        curTargets = targets == null ? null : new HashSet<>(targets);
                        ps = parseTime(p.getAttributeValue(null, "start"), fmtTz, fmt);
                        pe = parseTime(p.getAttributeValue(null, "stop"), fmtTz, fmt);
                        title = null;
                        desc = null;
                        if (curTargets != null && (pe < from || ps > to)) curTargets = null;
                    } else if (curTargets != null && "title".equals(tag) && title == null) {
                        title = p.nextText();
                    } else if (curTargets != null && "desc".equals(tag) && desc == null) {
                        desc = p.nextText();
                    }
                } else if (ev == XmlPullParser.END_TAG) {
                    String name = p.getName();
                    if ("channel".equals(name)) curChannelId = null;
                    else if ("programme".equals(name)) {
                        if (curTargets != null && pe > ps) {
                            Model.Programme pr = new Model.Programme(ps, pe, title, desc);
                            for (Model.Channel c : curTargets) {
                                List<Model.Programme> l = tmp.get(c.key());
                                if (l == null) tmp.put(c.key(), l = new ArrayList<>());
                                l.add(pr);
                            }
                        }
                        curTargets = null;
                    }
                }
                ev = p.next();
            }
        } catch (Exception ignored) {
            // Was bis hierhin gelesen wurde, wird trotzdem verwendet
        }
        long t = System.currentTimeMillis();
        for (Map.Entry<String, List<Model.Programme>> e : tmp.entrySet()) {
            List<Model.Programme> l = e.getValue();
            Collections.sort(l, (a, b) -> Long.compare(a.start, b.start));
            DATA.put(e.getKey(), l);
            LOADED_AT.put(e.getKey(), t);
        }
        // Sender ohne Daten: leere Liste, damit „Keine Programminfos“ statt „Lädt…“ erscheint
        for (Model.Channel c : channels) {
            if (!DATA.containsKey(c.key())) DATA.put(c.key(), new ArrayList<>());
        }
        bulkLoading = false;
        notifyChanged();
    }

    public static void markBulk() {
        bulkMode = true;
        bulkLoading = true;
    }

    public static void bulkFailed(List<Model.Channel> channels) {
        bulkLoading = false;
        for (Model.Channel c : channels) if (!DATA.containsKey(c.key())) DATA.put(c.key(), new ArrayList<>());
        notifyChanged();
    }

    private static void add(Map<String, List<Model.Channel>> m, String k, Model.Channel c) {
        if (k == null || k.isEmpty()) return;
        List<Model.Channel> l = m.get(k);
        if (l == null) m.put(k, l = new ArrayList<>());
        l.add(c);
    }

    private static long parseTime(String s, SimpleDateFormat withTz, SimpleDateFormat plain) {
        if (s == null) return 0;
        s = s.trim();
        try {
            if (s.length() > 15) return withTz.parse(s).getTime();
            return plain.parse(s.substring(0, Math.min(14, s.length()))).getTime();
        } catch (Exception e) {
            try {
                return plain.parse(s.substring(0, Math.min(14, s.length()))).getTime();
            } catch (Exception e2) {
                return 0;
            }
        }
    }
}
