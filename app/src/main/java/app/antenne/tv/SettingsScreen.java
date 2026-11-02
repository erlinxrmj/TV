package app.antenne.tv;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Einstellungen mit Abo-Übersicht (Laufzeit, Verbindungen) und Profilen. */
public class SettingsScreen extends Screen {

    private LinearLayout list;
    private TextView aboBig, aboSub, aboDetails, accTitle;
    private Ui.Bar aboBar;
    private View firstRow;

    public SettingsScreen(MainActivity a) {
        super(a);
    }

    @Override
    protected View build() {
        Context c = a;
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(Ui.dp(c, 48), Ui.dp(c, 92), Ui.dp(c, 48), Ui.dp(c, 24));
        row.setClipChildren(false);
        row.setClipToPadding(false);

        // Links: Einstellungen
        ScrollView sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipChildren(false);
        sv.setClipToPadding(false);
        list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Ui.dp(c, 8), Ui.dp(c, 4), Ui.dp(c, 8), Ui.dp(c, 60));
        list.setClipChildren(false);
        sv.addView(list);
        row.addView(sv, Ui.lin(0, Ui.MATCH, 1f));

        // Rechts: Konto-Karte
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(c, 28);
        card.setPadding(p, p, p, p);
        card.setBackground(Ui.round(0x1AFFFFFF, Ui.dp(c, 26)));
        TextView lbl = Ui.text(c, 12, Ui.TEXT3, Ui.bold(c));
        lbl.setText("DEIN ABO");
        lbl.setLetterSpacing(0.12f);
        card.addView(lbl);
        accTitle = Ui.lines(Ui.text(c, 20, Ui.TEXT, Ui.bold(c)), 1);
        card.addView(accTitle, Ui.margins(Ui.lin(Ui.MATCH, Ui.WRAP), 0, Ui.dp(c, 8), 0, 0));
        aboBig = Ui.text(c, 44, Ui.TEXT, Ui.bold(c));
        card.addView(aboBig, Ui.margins(Ui.lin(Ui.WRAP, Ui.WRAP), 0, Ui.dp(c, 18), 0, 0));
        aboSub = Ui.text(c, 15, Ui.TEXT2, Ui.medium(c));
        card.addView(aboSub, Ui.margins(Ui.lin(Ui.WRAP, Ui.WRAP), 0, Ui.dp(c, 6), 0, 0));
        aboBar = new Ui.Bar(c, 0x26FFFFFF, Ui.OK);
        card.addView(aboBar, Ui.margins(Ui.lin(Ui.MATCH, Ui.dp(c, 6)), 0, Ui.dp(c, 16), 0, 0));
        aboDetails = Ui.text(c, 14, Ui.TEXT2, Ui.medium(c));
        aboDetails.setLineSpacing(Ui.dp(c, 6), 1f);
        card.addView(aboDetails, Ui.margins(Ui.lin(Ui.MATCH, Ui.WRAP), 0, Ui.dp(c, 18), 0, 0));
        row.addView(card, Ui.margins(Ui.lin(Ui.dp(c, 340), Ui.WRAP), Ui.dp(c, 32), 0, 0, 0));

        buildList();
        updateAccount();
        return row;
    }

    private void section(String title) {
        TextView t = Ui.text(a, 12, Ui.TEXT3, Ui.bold(a));
        t.setText(title.toUpperCase(Locale.GERMANY));
        t.setLetterSpacing(0.12f);
        list.addView(t, Ui.margins(Ui.lin(Ui.WRAP, Ui.WRAP), Ui.dp(a, 16), Ui.dp(a, list.getChildCount() == 0 ? 0 : 22), 0, Ui.dp(a, 8)));
    }

    private interface Value {
        String get();
    }

    private View item(String label, Value value, Runnable action) {
        Context c = a;
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.HORIZONTAL);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setPadding(Ui.dp(c, 20), Ui.dp(c, 15), Ui.dp(c, 20), Ui.dp(c, 15));
        v.setClickable(true);
        TextView l = Ui.text(c, 17, Ui.TEXT, Ui.semibold(c));
        l.setText(label);
        v.addView(l, Ui.lin(0, Ui.WRAP, 1f));
        TextView val = Ui.text(c, 16, Ui.TEXT2, Ui.medium(c));
        if (value != null) val.setText(value.get());
        v.addView(val);
        float r = Ui.dp(c, 16);
        v.setBackground(Ui.round(0x12FFFFFF, r));
        Ui.lift(v, 1.03f, r, (view, has) -> {
            view.setBackground(Ui.round(has ? 0xFFFFFFFF : 0x12FFFFFF, r));
            l.setTextColor(has ? 0xFF111111 : Ui.TEXT);
            val.setTextColor(has ? 0x99111111 : Ui.TEXT2);
        });
        v.setOnClickListener(x -> {
            action.run();
            if (value != null) val.setText(value.get());
        });
        list.addView(v, Ui.margins(Ui.lin(Ui.MATCH, Ui.WRAP), 0, 0, 0, Ui.dp(c, 8)));
        if (firstRow == null) firstRow = v;
        return v;
    }

    private void buildList() {
        list.removeAllViews();
        firstRow = null;
        Prefs.Profile p = Library.get().profile;

        section("Profil");
        item("Profil wechseln", () -> p.name + "  ›", () -> {
            a.startActivity(new Intent(a, LoginActivity.class).putExtra("pick", true));
        });
        item("Login bearbeiten", () -> "›", () -> {
            Intent i = new Intent(a, LoginActivity.class);
            i.putExtra("edit", p.id);
            a.startActivity(i);
        });
        item("Neues Profil hinzufügen", () -> "+", () -> a.startActivity(new Intent(a, LoginActivity.class)));
        item("Inhalte neu laden", () -> "Sender, Filme, Serien", () -> {
            Intent i = new Intent(a, StartActivity.class);
            i.putExtra("force", true);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            a.startActivity(i);
        });

        section("Wiedergabe");
        if (p.isXtream()) {
            item("Live-Format", () -> "ts".equals(Prefs.liveFormat()) ? "MPEG-TS" : "HLS (m3u8)", () -> {
                Prefs.setLiveFormat("ts".equals(Prefs.liveFormat()) ? "m3u8" : "ts");
                Ui.toast(a, "Gilt ab dem nächsten Senderwechsel");
            });
        }
        item("Vorschau im Live-TV", () -> Prefs.preview() ? "An" : "Aus", () -> Prefs.setPreview(!Prefs.preview()));
        item("Nächste Folge automatisch", () -> Prefs.autoNext() ? "An" : "Aus", () -> Prefs.setAutoNext(!Prefs.autoNext()));

        section("Darstellung");
        item("Sendernamen aufräumen", () -> Prefs.cleanNames() ? "An" : "Aus", () -> {
            Prefs.setCleanNames(!Prefs.cleanNames());
            Ui.toast(a, "Wird beim nächsten Laden übernommen");
        });
        item("User-Agent", () -> shortUa(Prefs.userAgent()), this::chooseUa);

        section("Sonstiges");
        item("Profil löschen", () -> "", () -> new AlertDialog.Builder(a)
                .setTitle("„" + p.name + "“ löschen?")
                .setMessage("Login, Favoriten und Verlauf dieses Profils werden entfernt.")
                .setPositiveButton("Löschen", (d, w) -> {
                    Library.clearCache(p);
                    Store.clearProfile(p.id);
                    Prefs.deleteProfile(p.id);
                    Intent i = new Intent(a, StartActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    a.startActivity(i);
                })
                .setNegativeButton("Abbrechen", null)
                .show());
        String version = "1.0";
        try {
            version = a.getPackageManager().getPackageInfo(a.getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        String fv = version;
        item("Über TV", () -> "Version " + fv, () -> new AlertDialog.Builder(a)
                .setTitle("TV")
                .setMessage("Moderne IPTV-App für Android TV.\n\nEntwickelt von Erlin Ramaj.\n\n"
                        + "Fehlende Senderlogos stammen aus der offenen iptv-org-Datenbank.\n"
                        + "TV ist nur ein Player – Inhalte kommen von deinem IPTV-Anbieter.")
                .setPositiveButton("OK", null)
                .show());
    }

    private static String shortUa(String ua) {
        if (ua.startsWith("VLC")) return "VLC";
        if (ua.contains("Smarters")) return "IPTV Smarters";
        if (ua.contains("TiviMate")) return "TiviMate";
        if (ua.startsWith("okhttp")) return "Standard";
        return ua.length() > 18 ? ua.substring(0, 18) + "…" : ua;
    }

    private void chooseUa() {
        String[] names = {"VLC (empfohlen)", "IPTV Smarters", "TiviMate", "Standard"};
        String[] values = {"VLC/3.0.20 LibVLC/3.0.20", "IPTVSmartersPlayer", "TiviMate/4.7.0 (Android 11)", "okhttp/4.12.0"};
        new AlertDialog.Builder(a)
                .setTitle("User-Agent")
                .setItems(names, (d, w) -> {
                    Prefs.setUserAgent(values[w]);
                    Ui.toast(a, "Gespeichert – manche Anbieter erlauben nur bestimmte Apps");
                    buildList();
                })
                .show();
    }

    private void updateAccount() {
        Library lib = Library.get();
        Prefs.Profile p = lib.profile;
        accTitle.setText(p.name);
        Xtream.Account acc = lib.account;
        StringBuilder d = new StringBuilder();
        if (p.isXtream()) {
            d.append("Server:  ").append(hostOf(p.url)).append('\n');
            d.append("Benutzer:  ").append(p.user).append('\n');
        } else {
            d.append("M3U-Playlist\n").append(hostOf(p.url)).append('\n');
        }
        if (acc == null) {
            aboBig.setText(p.isXtream() ? "–" : "M3U");
            aboSub.setText(p.isXtream() ? "Abo-Infos nicht verfügbar" : "Bei reinen M3U-Links liefert der Anbieter keine Abo-Laufzeit.");
            aboBar.setVisibility(View.GONE);
        } else {
            if (acc.expires <= 0) {
                aboBig.setText("∞");
                aboSub.setText("Unbegrenzt gültig");
                aboBar.setVisibility(View.GONE);
            } else {
                long days = MainActivity.aboDays(acc);
                String date = new SimpleDateFormat("dd. MMMM yyyy", Locale.GERMANY).format(new Date(acc.expires));
                if (days < 0) {
                    aboBig.setText("Abgelaufen");
                    aboSub.setText("seit " + date);
                } else {
                    aboBig.setText(days + (days == 1 ? " Tag" : " Tage"));
                    aboSub.setText("gültig bis " + date);
                }
                aboBig.setTextColor(MainActivity.aboColor(acc) == Ui.TEXT2 ? Ui.TEXT : MainActivity.aboColor(acc));
                aboBar.setVisibility(View.VISIBLE);
                // Balken: Anteil eines Jahres, der noch übrig ist
                aboBar.setProgress(Math.max(0, Math.min(1f, days / 365f)));
                aboBar.setColors(0x26FFFFFF, days <= 7 ? (days < 0 ? Ui.LIVE : Ui.WARN) : Ui.OK);
            }
            d.append("Status:  ").append(statusDe(acc.status)).append(acc.trial ? " (Testzugang)" : "").append('\n');
            if (acc.maxConnections > 0) {
                d.append("Verbindungen:  ").append(acc.activeConnections).append(" von ").append(acc.maxConnections).append(" aktiv").append('\n');
            }
        }
        Library l = Library.get();
        d.append('\n').append(l.channels.size()).append(" Sender  ·  ").append(l.movies.size()).append(" Filme  ·  ").append(l.series.size()).append(" Serien");
        aboDetails.setText(d.toString());
    }

    private static String statusDe(String s) {
        if (s == null) return "?";
        switch (s.toLowerCase(Locale.ROOT)) {
            case "active": return "Aktiv";
            case "expired": return "Abgelaufen";
            case "banned": return "Gesperrt";
            case "disabled": return "Deaktiviert";
            default: return s;
        }
    }

    private static String hostOf(String url) {
        try {
            String h = android.net.Uri.parse(url).getHost();
            return h == null ? url : h;
        } catch (Exception e) {
            return url;
        }
    }

    @Override
    public void focusFirst() {
        if (firstRow != null) firstRow.requestFocus();
    }

    @Override
    public void onShow() {
        updateAccount();
        // Abo-Daten frisch vom Server holen (z. B. aktive Verbindungen)
        Xtream x = Library.get().xtream;
        if (x != null) {
            Bg.run(() -> {
                try {
                    Xtream.Account acc = x.login();
                    Bg.ui(() -> {
                        Library.get().account = acc;
                        updateAccount();
                        a.updateAbo();
                    });
                } catch (Exception ignored) {
                }
            });
        }
    }

    @Override
    public void onLibraryChanged(String what) {
        updateAccount();
    }
}
