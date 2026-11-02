package app.antenne.tv;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Liest M3U/M3U8-Playlists: Sender, Filme und Serien (nach Adresse bzw. Namensmuster sortiert). */
public final class M3u {

    private static final Pattern ATTR = Pattern.compile("([a-zA-Z0-9_-]+)=\"([^\"]*)\"");
    private static final Pattern SXE = Pattern.compile("^(.*?)[\\s._-]*[Ss](\\d{1,2})\\s*[Ee](\\d{1,3})(.*)$");

    public final List<Model.Category> liveCats = new ArrayList<>();
    public final List<Model.Channel> channels = new ArrayList<>();
    public final List<Model.Category> movieCats = new ArrayList<>();
    public final List<Model.Movie> movies = new ArrayList<>();
    public final List<Model.Category> seriesCats = new ArrayList<>();
    public final List<Model.Series> series = new ArrayList<>();
    public String epgUrl;

    public static M3u parse(File f) throws IOException {
        M3u m = new M3u();
        Map<String, Model.Category> live = new LinkedHashMap<>();
        Map<String, Model.Category> mov = new LinkedHashMap<>();
        Map<String, Model.Category> ser = new LinkedHashMap<>();
        Map<String, Model.Series> seriesByName = new LinkedHashMap<>();
        int n = 0;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8), 64 * 1024)) {
            String line;
            Map<String, String> attrs = null;
            String title = null;
            String groupLine = null;
            boolean first = true;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (first) {
                    if (line.startsWith("﻿")) line = line.substring(1);
                    first = false;
                }
                if (line.isEmpty()) continue;
                if (line.startsWith("#EXTM3U")) {
                    Map<String, String> h = attrs(line);
                    String e = h.get("url-tvg");
                    if (e == null) e = h.get("x-tvg-url");
                    if (e != null && !e.isEmpty()) m.epgUrl = e.split(",")[0].trim();
                    continue;
                }
                if (line.startsWith("#EXTINF")) {
                    attrs = attrs(line);
                    int comma = lastTopLevelComma(line);
                    title = comma >= 0 ? line.substring(comma + 1).trim() : "";
                    groupLine = null;
                    continue;
                }
                if (line.startsWith("#EXTGRP:")) {
                    groupLine = line.substring(8).trim();
                    continue;
                }
                if (line.startsWith("#")) continue;
                // Adresszeile
                if (attrs == null) attrs = new LinkedHashMap<>();
                String url = line;
                String name = title != null && !title.isEmpty() ? title : attrs.getOrDefault("tvg-name", "Sender");
                String group = attrs.get("group-title");
                if (group == null || group.isEmpty()) group = groupLine;
                if (group == null || group.isEmpty()) group = "Ohne Kategorie";
                String logo = attrs.get("tvg-logo");
                if (logo != null && logo.trim().isEmpty()) logo = null;
                String lower = url.toLowerCase(Locale.ROOT);
                n++;

                Matcher sxe = SXE.matcher(name);
                boolean isSeries = lower.contains("/series/") || (looksVod(lower) && sxe.find());
                boolean isMovie = !isSeries && (lower.contains("/movie/") || looksVod(lower));

                if (isSeries) {
                    Model.Category cat = ser.get(group);
                    if (cat == null) {
                        cat = new Model.Category("s" + ser.size(), group);
                        ser.put(group, cat);
                    }
                    Matcher mm = SXE.matcher(name);
                    String sName = name;
                    int season = 1, ep = 1;
                    String epTitle = null;
                    if (mm.find()) {
                        sName = mm.group(1).trim();
                        season = Xtream.parseInt(mm.group(2));
                        ep = Xtream.parseInt(mm.group(3));
                        epTitle = mm.group(4).replaceFirst("^[\\s._:-]+", "").trim();
                    }
                    if (sName.isEmpty()) sName = name;
                    String sKey = cat.id + "|" + sName.toLowerCase(Locale.ROOT);
                    Model.Series s = seriesByName.get(sKey);
                    if (s == null) {
                        s = new Model.Series();
                        s.id = "m3u" + seriesByName.size();
                        s.name = sName;
                        s.poster = logo;
                        s.categoryId = cat.id;
                        s.m3uEpisodes = new ArrayList<>();
                        seriesByName.put(sKey, s);
                        m.series.add(s);
                    }
                    Model.Episode e = new Model.Episode();
                    e.id = s.id + "_" + s.m3uEpisodes.size();
                    e.seriesId = s.id;
                    e.season = season;
                    e.number = ep;
                    e.title = epTitle == null || epTitle.isEmpty() ? "Folge " + ep : epTitle;
                    e.image = logo;
                    e.url = url;
                    s.m3uEpisodes.add(e);
                } else if (isMovie) {
                    Model.Category cat = mov.get(group);
                    if (cat == null) {
                        cat = new Model.Category("v" + mov.size(), group);
                        mov.put(group, cat);
                    }
                    Model.Movie mv = new Model.Movie();
                    mv.id = "m3u" + n;
                    mv.name = name;
                    mv.poster = logo;
                    mv.categoryId = cat.id;
                    mv.url = url;
                    mv.year = Xtream.yearFromName(name);
                    m.movies.add(mv);
                } else {
                    Model.Category cat = live.get(group);
                    if (cat == null) {
                        cat = new Model.Category("l" + live.size(), group);
                        live.put(group, cat);
                    }
                    Model.Channel c = new Model.Channel();
                    c.id = String.valueOf(n);
                    c.num = Xtream.parseInt(attrs.get("tvg-chno"));
                    c.rawName = name;
                    c.name = Names.clean(name);
                    c.logo = logo;
                    c.categoryId = cat.id;
                    c.epgId = attrs.get("tvg-id");
                    if (c.epgId != null && c.epgId.isEmpty()) c.epgId = null;
                    c.url = url;
                    String cu = attrs.get("catchup-days");
                    if (cu != null) {
                        c.archive = true;
                        c.archiveDays = Xtream.parseInt(cu);
                    }
                    m.channels.add(c);
                }
                attrs = null;
                title = null;
                groupLine = null;
            }
        }
        m.liveCats.addAll(live.values());
        m.movieCats.addAll(mov.values());
        m.seriesCats.addAll(ser.values());
        for (Model.Series s : m.series) {
            java.util.Collections.sort(s.m3uEpisodes, (a, b) -> a.season != b.season
                    ? Integer.compare(a.season, b.season) : Integer.compare(a.number, b.number));
        }
        return m;
    }

    private static boolean looksVod(String lowerUrl) {
        String path = lowerUrl;
        int q = path.indexOf('?');
        if (q >= 0) path = path.substring(0, q);
        return path.endsWith(".mp4") || path.endsWith(".mkv") || path.endsWith(".avi")
                || path.endsWith(".mov") || path.endsWith(".m4v") || path.endsWith(".wmv");
    }

    private static Map<String, String> attrs(String line) {
        Map<String, String> m = new LinkedHashMap<>();
        Matcher mt = ATTR.matcher(line);
        while (mt.find()) m.put(mt.group(1).toLowerCase(Locale.ROOT), mt.group(2));
        return m;
    }

    /** Das Komma nach den Attributen (Kommas in Anführungszeichen ignorieren). */
    private static int lastTopLevelComma(String line) {
        boolean inQuote = false;
        int found = -1;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') inQuote = !inQuote;
            else if (ch == ',' && !inQuote) {
                found = i;
                break;
            }
        }
        return found;
    }
}
