package app.antenne.tv;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Bereinigt die oft unschönen Sendernamen der Anbieter („DE: ZDF HD |FHD|“ → „ZDF HD“). */
public final class Names {

    private static final Pattern PREFIX = Pattern.compile(
            "^\\s*(?:[|\\[(]\\s*[A-Z]{2,4}(?:[- ][A-Z0-9]{1,4})?\\s*[|\\])]\\s*[:\\-|]?\\s*"
                    + "|[A-Z]{2,4}\\s*[:|]\\s*"
                    + "|[A-Z]{2,3}\\s+-\\s+)");
    private static final Pattern TRAIL_TAGS = Pattern.compile(
            "\\s*[|\\[(]\\s*(?:FHD|UHD|HD|SD|4K|HEVC|H265|H\\.265|RAW|50FPS|60FPS|BACKUP|VIP|MULTI)\\s*[|\\])]\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SPACES = Pattern.compile("\\s{2,}");

    private Names() {}

    public static String clean(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (!Prefs.cleanNames()) return s;
        for (int i = 0; i < 2; i++) {
            String t = PREFIX.matcher(s).replaceFirst("");
            if (t.length() >= 2) s = t;
        }
        for (int i = 0; i < 3; i++) s = TRAIL_TAGS.matcher(s).replaceFirst("");
        s = s.replace('_', ' ');
        s = SPACES.matcher(s).replaceAll(" ").trim();
        if (s.startsWith("-") || s.startsWith(":")) s = s.substring(1).trim();
        return s.isEmpty() ? raw.trim() : s;
    }

    /** Vergleichsschlüssel: Kleinbuchstaben, ohne Qualitätszusätze und Sonderzeichen. */
    public static String key(String raw) {
        if (raw == null) return "";
        String s = clean(raw).toLowerCase(Locale.ROOT);
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        s = s.replaceAll("\\b(fhd|uhd|hd|sd|4k|hevc|h265|raw|50fps|60fps|backup|ᴴᴰ|ʰᵈ|tv)\\b", " ");
        s = s.replace("&", "and").replace("+", "plus");
        s = s.replaceAll("[^a-z0-9]", "");
        return s;
    }

    /** Erste Buchstaben für Ersatz-Logos. */
    public static String initials(String name) {
        if (name == null || name.trim().isEmpty()) return "TV";
        String n = name.trim();
        String[] parts = n.split("\\s+");
        if (parts.length >= 2 && parts[0].length() <= 4 && parts[1].length() <= 3 && parts[1].matches("[0-9A-Za-z]+")) {
            String a = parts[0] + (parts[1].matches("[0-9]+") ? parts[1] : "");
            return a.length() <= 5 ? a.toUpperCase(Locale.ROOT) : a.substring(0, 2).toUpperCase(Locale.ROOT);
        }
        if (n.length() <= 5) return n.toUpperCase(Locale.ROOT);
        if (parts.length >= 2) {
            return ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase(Locale.ROOT);
        }
        return n.substring(0, 2).toUpperCase(Locale.ROOT);
    }
}
