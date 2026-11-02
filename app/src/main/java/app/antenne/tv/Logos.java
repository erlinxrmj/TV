package app.antenne.tv;

import android.util.JsonReader;
import android.util.JsonToken;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Findet fehlende Senderlogos automatisch über die offene iptv-org-Datenbank
 * (über 30 000 Sender weltweit). Sender aus CH/DE/AT werden bevorzugt.
 */
public final class Logos {

    private static final String CHANNELS = "https://iptv-org.github.io/api/channels.json";
    private static final String LOGOS = "https://iptv-org.github.io/api/logos.json";
    private static final String[] COUNTRY_PREF = {"CH", "DE", "AT", "IT", "FR", "GB", "US"};

    private Logos() {}

    /** Ergänzt Logos für Sender ohne Logo. Läuft im Hintergrund, Ergebnis wird gecacht. */
    public static boolean fill(List<Model.Channel> channels, File cacheDir) {
        Set<String> needed = new HashSet<>();
        for (Model.Channel c : channels) if (c.logo == null) needed.add(Names.key(c.rawName));
        needed.remove("");
        if (needed.isEmpty()) return false;

        File cache = new File(cacheDir, "logo_map.json");
        Map<String, String> found = readCache(cache);
        boolean fresh = cache.exists() && System.currentTimeMillis() - cache.lastModified() < 7L * 86400_000L;
        boolean allKnown = found.keySet().containsAll(needed);

        if (!fresh || !allKnown) {
            try {
                Map<String, String> fetched = lookup(needed, cacheDir);
                found.putAll(fetched);
                for (String k : needed) if (!found.containsKey(k)) found.put(k, "");
                writeCache(cache, found);
            } catch (Exception ignored) {
            }
        }
        boolean changed = false;
        for (Model.Channel c : channels) {
            if (c.logo != null) continue;
            String url = found.get(Names.key(c.rawName));
            if (url != null && !url.isEmpty()) {
                c.logo = url;
                changed = true;
            }
        }
        return changed;
    }

    private static Map<String, String> lookup(Set<String> needed, File dir) throws Exception {
        File chFile = new File(dir, "iptvorg_channels.json");
        File lgFile = new File(dir, "iptvorg_logos.json");
        if (!chFile.exists() || System.currentTimeMillis() - chFile.lastModified() > 14L * 86400_000L) {
            Http.download(CHANNELS, chFile);
        }
        if (!lgFile.exists() || System.currentTimeMillis() - lgFile.lastModified() > 14L * 86400_000L) {
            Http.download(LOGOS, lgFile);
        }

        // 1) Name → passende Sender-IDs (mit Land, um CH/DE/AT zu bevorzugen)
        Map<String, String> bestId = new HashMap<>();
        Map<String, Integer> bestRank = new HashMap<>();
        Map<String, String> idToKey = new HashMap<>();
        try (JsonReader r = new JsonReader(new InputStreamReader(new BufferedInputStream(new FileInputStream(chFile), 64 * 1024), StandardCharsets.UTF_8))) {
            r.setLenient(true);
            r.beginArray();
            while (r.hasNext()) {
                String id = null, name = null, country = null;
                String oldLogo = null;
                java.util.List<String> alts = new java.util.ArrayList<>();
                boolean closed = false;
                r.beginObject();
                while (r.hasNext()) {
                    String k = r.nextName();
                    if (r.peek() == JsonToken.NULL) {
                        r.nextNull();
                        continue;
                    }
                    switch (k) {
                        case "id": id = r.nextString(); break;
                        case "name": name = r.nextString(); break;
                        case "country": country = r.nextString(); break;
                        case "logo": oldLogo = r.nextString(); break;
                        case "closed": closed = true; r.skipValue(); break;
                        case "alt_names":
                            if (r.peek() == JsonToken.BEGIN_ARRAY) {
                                r.beginArray();
                                while (r.hasNext()) {
                                    if (r.peek() == JsonToken.STRING) alts.add(r.nextString());
                                    else r.skipValue();
                                }
                                r.endArray();
                            } else r.skipValue();
                            break;
                        default: r.skipValue();
                    }
                }
                r.endObject();
                if (id == null || name == null) continue;
                int rank = rank(country) + (closed ? 20 : 0);
                java.util.List<String> all = new java.util.ArrayList<>(alts);
                all.add(0, name);
                for (String n : all) {
                    String key = Names.key(n);
                    if (!needed.contains(key)) continue;
                    Integer prev = bestRank.get(key);
                    if (prev == null || rank < prev) {
                        bestRank.put(key, rank);
                        bestId.put(key, id);
                        idToKey.put(id, key);
                    }
                }
                // Ältere API-Version hatte das Logo direkt am Sender
                if (oldLogo != null && idToKey.containsKey(id)) idToKey.put(id + "#logo", oldLogo);
            }
            r.endArray();
        }

        Map<String, String> out = new HashMap<>();
        for (Map.Entry<String, String> e : bestId.entrySet()) {
            String old = idToKey.get(e.getValue() + "#logo");
            if (old != null) out.put(e.getKey(), old);
        }
        Set<String> wanted = new HashSet<>(bestId.values());
        if (wanted.isEmpty()) return out;

        // 2) Logos zu den IDs (größtes, nicht-feed-spezifisches bevorzugt)
        Map<String, String> logoForId = new HashMap<>();
        Map<String, Integer> logoScore = new HashMap<>();
        try (JsonReader r = new JsonReader(new InputStreamReader(new BufferedInputStream(new FileInputStream(lgFile), 64 * 1024), StandardCharsets.UTF_8))) {
            r.setLenient(true);
            r.beginArray();
            while (r.hasNext()) {
                String channel = null, url = null, format = null;
                boolean feed = false;
                int width = 0;
                r.beginObject();
                while (r.hasNext()) {
                    String k = r.nextName();
                    if (r.peek() == JsonToken.NULL) {
                        r.nextNull();
                        continue;
                    }
                    switch (k) {
                        case "channel": channel = r.nextString(); break;
                        case "url": url = r.nextString(); break;
                        case "format": format = r.nextString(); break;
                        case "feed": feed = true; r.skipValue(); break;
                        case "width": width = (int) r.nextDouble(); break;
                        default: r.skipValue();
                    }
                }
                r.endObject();
                if (channel == null || url == null || !wanted.contains(channel)) continue;
                int score = (feed ? 0 : 10000) + Math.min(width, 2000)
                        + ("SVG".equalsIgnoreCase(format) ? -5000 : 0); // SVG kann Glide nicht
                Integer prev = logoScore.get(channel);
                if (prev == null || score > prev) {
                    logoScore.put(channel, score);
                    logoForId.put(channel, url);
                }
            }
            r.endArray();
        }
        for (Map.Entry<String, String> e : bestId.entrySet()) {
            String url = logoForId.get(e.getValue());
            if (url != null) out.put(e.getKey(), url);
        }
        return out;
    }

    private static int rank(String country) {
        if (country == null) return 50;
        for (int i = 0; i < COUNTRY_PREF.length; i++) if (COUNTRY_PREF[i].equalsIgnoreCase(country)) return i;
        return 30;
    }

    private static Map<String, String> readCache(File f) {
        Map<String, String> m = new HashMap<>();
        if (!f.exists()) return m;
        try (InputStreamReader in = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192];
            int n;
            while ((n = in.read(buf)) > 0) sb.append(buf, 0, n);
            JSONObject o = new JSONObject(sb.toString());
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                m.put(k, o.optString(k));
            }
        } catch (Exception ignored) {
        }
        return m;
    }

    private static void writeCache(File f, Map<String, String> m) {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            w.write(new JSONObject(m).toString());
        } catch (Exception ignored) {
        }
    }
}
