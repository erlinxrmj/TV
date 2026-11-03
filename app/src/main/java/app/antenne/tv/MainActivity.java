package app.antenne.tv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Hauptansicht mit schwebender Navigation oben (wie tvOS) und wechselnden Bereichen. */
public class MainActivity extends Activity implements Library.Listener {

    private FrameLayout content;
    private ImageView backdrop;
    private View backdropShade;
    private LinearLayout nav;
    private TextView clock, abo;
    private final Map<String, TextView> tabs = new LinkedHashMap<>();
    private final Map<String, Screen> screens = new LinkedHashMap<>();
    private String current;
    private String pendingTab;
    private long lastBack;
    private String backdropUrl;

    private final Runnable switchRunnable = () -> {
        if (pendingTab != null) show(pendingTab);
    };

    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            clock.setText(new SimpleDateFormat("HH:mm", Locale.GERMANY).format(new Date()));
            Bg.later(this, 15_000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setBackgroundDrawable(Ui.background());
        if (Library.get().profile == null) {
            startActivity(new Intent(this, StartActivity.class));
            finish();
            return;
        }
        buildUi();
        Library.get().addListener(this);
        String start = Prefs.lastTab();
        if (!screens.containsKey(start) || "search".equals(start) || "settings".equals(start)) start = "live";
        String req = getIntent().getStringExtra("tab");
        if (req != null && screens.containsKey(req)) start = req;
        show(start);
        TextView t = tabs.get(start);
        if (t != null) t.post(() -> {
            Screen s = screens.get(current);
            if (s != null) s.focusFirst();
        });
        String crash = App.takeCrash();
        if (crash != null) {
            TextView msg = Ui.text(this, 12, 0xFF333333, Ui.medium(this));
            msg.setText(crash);
            msg.setTextIsSelectable(false);
            int p = Ui.dp(this, 20);
            msg.setPadding(p, p, p, p);
            android.widget.ScrollView sv = new android.widget.ScrollView(this);
            sv.addView(msg);
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Die App ist zuletzt abgestürzt")
                    .setMessage("Mach bitte ein Foto davon und schick es Claude – dann lässt sich der Fehler beheben.")
                    .setView(sv)
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    @Override
    protected void onDestroy() {
        Library.get().removeListener(this);
        Bg.cancel(clockTick);
        Bg.cancel(switchRunnable);
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Screen s = screens.get(current);
        if (s != null) s.onPause();
        Bg.cancel(clockTick);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (clock != null) clockTick.run();
        Screen s = screens.get(current);
        if (s != null) s.onResume();
        updateAbo();
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackground(Ui.background());

        backdrop = new ImageView(this);
        backdrop.setAlpha(0f);
        root.addView(backdrop, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        backdropShade = new View(this);
        backdropShade.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Ui.scrim(0x99), Ui.scrim(0xC7), Ui.scrim(0xF5)}));
        root.addView(backdropShade, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        content = new FrameLayout(this);
        root.addView(content, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        // Verlauf hinter der Navigation, damit sie über Bildern lesbar bleibt
        View topShade = new View(this);
        topShade.setBackground(Ui.vgrad(Ui.scrim(0xF0), Ui.scrim(0x00)));
        root.addView(topShade, Ui.frame(Ui.MATCH, Ui.dp(this, 84), Gravity.TOP));

        // Obere Leiste: links Logo, Mitte Glas-Kapsel, rechts Uhr. Feste Seitenbreiten,
        // damit sich nichts überlappen kann und die Kapsel genau mittig sitzt.
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.TOP);
        int side = Ui.dp(this, 40);
        bar.setPadding(side, Ui.dp(this, 20), side, 0);
        int sideW = Ui.dp(this, 150);

        FrameLayout left = new FrameLayout(this);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.mipmap.ic_launcher);
        FrameLayout.LayoutParams llp = Ui.frame(Ui.dp(this, 36), Ui.dp(this, 36), Gravity.START | Gravity.TOP);
        llp.topMargin = Ui.dp(this, 2);
        left.addView(logo, llp);
        bar.addView(left, Ui.lin(sideW, Ui.WRAP));

        FrameLayout mid = new FrameLayout(this);
        mid.setClipChildren(false);
        nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(Ui.dp(this, 5), Ui.dp(this, 4), Ui.dp(this, 5), Ui.dp(this, 4));
        nav.setBackground(Ui.round(Ui.dark ? 0x21FFFFFF : 0xCCFFFFFF, Ui.dp(this, 100)));
        nav.setClipChildren(false);
        nav.setClipToPadding(false);
        addTab("live", "Live-TV", 0, new LiveScreen(this));
        addTab("guide", "TV-Programm", 0, new GuideScreen(this));
        addTab("movies", "Filme", 0, new MediaScreen(this, false));
        addTab("series", "Serien", 0, new MediaScreen(this, true));
        addTab("search", null, R.drawable.ic_search, new SearchScreen(this));
        addTab("settings", null, R.drawable.ic_settings, new SettingsScreen(this));
        mid.addView(nav, Ui.frame(Ui.WRAP, Ui.WRAP, Gravity.CENTER_HORIZONTAL | Gravity.TOP));
        bar.addView(mid, Ui.lin(0, Ui.WRAP, 1f));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);
        clock = Ui.text(this, 22, Ui.TEXT, Ui.semibold(this));
        clock.setSingleLine(true);
        right.addView(clock);
        abo = Ui.lines(Ui.text(this, 12, Ui.TEXT2, Ui.semibold(this)), 1);
        abo.setVisibility(View.GONE);
        right.addView(abo, Ui.margins(Ui.lin(Ui.WRAP, Ui.WRAP), 0, Ui.dp(this, 4), 0, 0));
        bar.addView(right, Ui.lin(sideW, Ui.WRAP));

        root.addView(bar, Ui.frame(Ui.MATCH, Ui.WRAP, Gravity.TOP));
        root.setClipChildren(false);
        bar.setClipChildren(false);
        setContentView(root);
        clockTick.run();
        updateAbo();
    }

    private void addTab(String id, String label, int icon, Screen s) {
        TextView t = icon != 0 ? Ui.iconPill(this, icon) : Ui.pill(this, label, 15);
        View.OnFocusChangeListener base = t.getOnFocusChangeListener();
        t.setOnFocusChangeListener((v, has) -> {
            base.onFocusChange(v, has);
            if (has && !id.equals(current)) {
                pendingTab = id;
                Bg.cancel(switchRunnable);
                Bg.later(switchRunnable, 260);
            }
        });
        t.setOnClickListener(v -> {
            show(id);
            Screen sc = screens.get(id);
            if (sc != null) sc.focusFirst();
        });
        t.setOnKeyListener((v, keyCode, e) -> {
            if (e.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                Bg.cancel(switchRunnable);
                if (!id.equals(current)) show(id);
                Screen sc = screens.get(id);
                if (sc != null) sc.focusFirst();
                return true;
            }
            return false;
        });
        LinearLayout.LayoutParams lp = Ui.lin(Ui.WRAP, Ui.WRAP);
        lp.setMargins(Ui.dp(this, 2), 0, Ui.dp(this, 2), 0);
        nav.addView(t, lp);
        tabs.put(id, t);
        screens.put(id, s);
    }

    public void show(String id) {
        if (id.equals(current)) return;
        Screen old = screens.get(current);
        if (old != null) {
            old.onHide();
            View ov = old.view();
            ov.animate().alpha(0f).setDuration(120).withEndAction(() -> {
                if (ov.getParent() != null && old != screens.get(current)) content.removeView(ov);
            }).start();
        }
        current = id;
        for (Map.Entry<String, TextView> e : tabs.entrySet()) Ui.setPillSelected(e.getValue(), e.getKey().equals(id));
        Screen s = screens.get(id);
        View v = s.view();
        if (v.getParent() != null) ((ViewGroup) v.getParent()).removeView(v);
        v.setAlpha(0f);
        content.addView(v, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        v.animate().alpha(1f).setDuration(180).start();
        if (!"movies".equals(id) && !"series".equals(id)) setBackdrop(null);
        s.onShow();
        if (!"search".equals(id) && !"settings".equals(id)) Prefs.setLastTab(id);
    }

    public void focusNav() {
        TextView t = tabs.get(current);
        if (t != null) t.requestFocus();
    }

    public boolean navHasFocus() {
        View f = getCurrentFocus();
        while (f != null) {
            if (f == nav) return true;
            if (!(f.getParent() instanceof View)) break;
            f = (View) f.getParent();
        }
        return false;
    }

    /** Großes weichgezeichnetes Hintergrundbild (Filme/Serien). */
    public void setBackdrop(String url) {
        if (url == null) {
            backdropUrl = null;
            backdrop.animate().alpha(0f).setDuration(300).start();
            return;
        }
        if (url.equals(backdropUrl)) return;
        backdropUrl = url;
        Img.blur(backdrop, url);
    }

    public void updateAbo() {
        if (abo == null) return;
        Xtream.Account acc = Library.get().account;
        if (acc == null) {
            abo.setVisibility(View.GONE);
            return;
        }
        abo.setVisibility(View.VISIBLE);
        abo.setText(aboShort(acc));
        abo.setTextColor(aboColor(acc));
    }

    public static long aboDays(Xtream.Account acc) {
        return (long) Math.floor((acc.expires - System.currentTimeMillis()) / 86400_000.0);
    }

    public static String aboShort(Xtream.Account acc) {
        if (acc.expires <= 0) return "Abo unbegrenzt";
        long d = aboDays(acc);
        if (d < 0) return "Abo abgelaufen";
        if (d == 0) return "Abo endet heute";
        return "Abo: noch " + d + (d == 1 ? " Tag" : " Tage");
    }

    public static int aboColor(Xtream.Account acc) {
        if (acc.expires <= 0) return Ui.TEXT2;
        long d = aboDays(acc);
        if (d < 0) return Ui.LIVE;
        if (d <= 7) return Ui.WARN;
        return Ui.TEXT2;
    }

    @Override
    public void onLibraryChanged(String what) {
        for (Screen s : screens.values()) if (s.isBuilt()) s.onLibraryChanged(what);
        updateAbo();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getKeyCode() != KeyEvent.KEYCODE_BACK) {
            Screen s = screens.get(current);
            if (s != null && !navHasFocus() && s.onKey(e.getKeyCode(), e)) return true;
        }
        return super.dispatchKeyEvent(e);
    }

    @Override
    public void onBackPressed() {
        Screen s = screens.get(current);
        if (!navHasFocus()) {
            if (s != null && s.onBack()) return;
            focusNav();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastBack < 2000) {
            finish();
        } else {
            lastBack = now;
            Ui.toast(this, "Nochmal „Zurück“ zum Beenden");
        }
    }

    // ---------- Navigation zu anderen Ansichten ----------

    public void playChannel(Model.Channel c, String categoryKey) {
        Intent i = new Intent(this, PlayerActivity.class);
        i.putExtra("type", "live");
        i.putExtra("key", c.key());
        if (categoryKey != null) i.putExtra("cat", categoryKey);
        startActivity(i);
    }

    public void openMovie(Model.Movie m) {
        Intent i = new Intent(this, DetailActivity.class);
        i.putExtra("type", "movie");
        i.putExtra("id", m.id);
        startActivity(i);
    }

    public void openSeries(Model.Series s) {
        Intent i = new Intent(this, DetailActivity.class);
        i.putExtra("type", "series");
        i.putExtra("id", s.id);
        startActivity(i);
    }

    public void openResume(Store.Resume r) {
        if (r.key.startsWith("m:")) {
            Model.Movie m = Library.get().movie(r.key.substring(2));
            if (m != null) {
                Intent i = new Intent(this, PlayerActivity.class);
                i.putExtra("type", "movie");
                i.putExtra("id", m.id);
                startActivity(i);
            }
        } else if (r.seriesId != null && !r.seriesId.isEmpty()) {
            Model.Series s = Library.get().series(r.seriesId);
            if (s != null) openSeries(s);
        }
    }
}
