package app.antenne.tv;

import java.util.ArrayList;
import java.util.List;

/** Datenklassen für Sender, Filme, Serien und Sendungen. */
public final class Model {

    private Model() {}

    public static final class Category {
        public final String id;
        public final String name;
        public final List<Object> items = new ArrayList<>();

        public Category(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public static final class Channel {
        public String id;          // stream_id (Xtream) oder laufende Nummer (M3U)
        public int num;
        public String name;        // Anzeigename (bereinigt)
        public String rawName;
        public String logo;        // Logo vom Anbieter oder automatisch gefunden
        public String categoryId;
        public String epgId;       // tvg-id / epg_channel_id
        public String url;         // nur M3U: direkte Adresse
        public boolean archive;    // Catch-up verfügbar
        public int archiveDays;

        /** Eindeutiger Schlüssel für Favoriten/Verlauf. */
        public String key() {
            return "c:" + id;
        }
    }

    public static final class Movie {
        public String id;
        public String name;
        public String poster;
        public String categoryId;
        public String ext = "mp4";
        public String rating;
        public String year;
        public long added;
        public String url; // nur M3U

        public String key() {
            return "m:" + id;
        }
    }

    public static final class Series {
        public String id;
        public String name;
        public String poster;
        public String backdrop;
        public String categoryId;
        public String plot;
        public String rating;
        public String year;
        public String genre;
        public long added;
        /** Nur M3U: Folgen direkt aus der Playlist. */
        public List<Episode> m3uEpisodes;

        public String key() {
            return "s:" + id;
        }
    }

    public static final class Episode {
        public String id;
        public String seriesId;
        public int season;
        public int number;
        public String title;
        public String plot;
        public String image;
        public String ext = "mp4";
        public int durationSecs;
        public String url; // nur M3U

        public String key() {
            return "e:" + id;
        }
    }

    public static final class Programme {
        public final long start;
        public final long end;
        public final String title;
        public final String desc;

        public Programme(long start, long end, String title, String desc) {
            this.start = start;
            this.end = end;
            this.title = title == null ? "" : title;
            this.desc = desc == null ? "" : desc;
        }

        public boolean isNow(long now) {
            return start <= now && now < end;
        }

        public float progress(long now) {
            if (end <= start) return 0f;
            return Math.max(0f, Math.min(1f, (now - start) / (float) (end - start)));
        }
    }
}
