package app.antenne.tv;

import android.content.Context;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** TV-Programm: oben Details zur gewählten Sendung, darunter Kategorien und das Raster. */
public class GuideScreen extends Screen implements Epg.Listener, GuideView.Listener {

    private GuideView grid;
    private LinearLayout chips;
    private HorizontalScrollView chipScroll;
    private final List<TextView> chipViews = new ArrayList<>();
    private final List<String> chipKeys = new ArrayList<>();
    private String catKey;
    private ImageView dLogo;
    private TextView dTitle, dMeta, dDesc, dBadge;
    private boolean visible;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!visible) return;
            grid.invalidate();
            Bg.later(this, 30_000);
        }
    };

    public GuideScreen(MainActivity a) {
        super(a);
    }

    @Override
    protected View build() {
        Context c = a;
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dp(c, 32), Ui.dp(c, 84), Ui.dp(c, 32), 0);
        col.setClipChildren(false);
        col.setClipToPadding(false);

        // Details zur Auswahl
        LinearLayout detail = new LinearLayout(c);
        detail.setOrientation(LinearLayout.HORIZONTAL);
        detail.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout tile = new FrameLayout(c);
        tile.setBackground(Ui.round(Ui.GLASS, Ui.dp(c, 14)));
        tile.setPadding(Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 10), Ui.dp(c, 8));
        dLogo = new ImageView(c);
        tile.addView(dLogo, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        detail.addView(tile, Ui.lin(Ui.dp(c, 120), Ui.dp(c, 72)));
        LinearLayout txt = new LinearLayout(c);
        txt.setOrientation(LinearLayout.VERTICAL);
        LinearLayout tl = new LinearLayout(c);
        tl.setOrientation(LinearLayout.HORIZONTAL);
        tl.setGravity(Gravity.CENTER_VERTICAL);
        dTitle = Ui.lines(Ui.text(c, 26, Ui.TEXT, Ui.bold(c)), 1);
        tl.addView(dTitle, Ui.lin(0, Ui.WRAP, 1f));
        dBadge = Ui.badge(c, "LÄUFT", Ui.LIVE, 0xFFFFFFFF);
        tl.addView(dBadge, Ui.margins(Ui.lin(Ui.WRAP, Ui.WRAP), Ui.dp(c, 12), 0, 0, 0));
        txt.addView(tl, Ui.lin(Ui.MATCH, Ui.WRAP));
        dMeta = Ui.lines(Ui.text(c, 15, Ui.TEXT2, Ui.medium(c)), 1);
        txt.addView(dMeta, Ui.margins(Ui.lin(Ui.MATCH, Ui.WRAP), 0, Ui.dp(c, 6), 0, 0));
        dDesc = Ui.lines(Ui.text(c, 14, Ui.TEXT3, Ui.regular(c)), 2);
        txt.addView(dDesc, Ui.margins(Ui.lin(Ui.MATCH, Ui.WRAP), 0, Ui.dp(c, 6), 0, 0));
        detail.addView(txt, Ui.margins(Ui.lin(0, Ui.WRAP, 1f), Ui.dp(c, 18), 0, 0, 0));
        col.addView(detail, Ui.lin(Ui.MATCH, Ui.dp(c, 86)));

        // Kategorie-Chips
        chipScroll = new HorizontalScrollView(c);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipScroll.setClipChildren(false);
        chipScroll.setClipToPadding(false);
        chips = new LinearLayout(c);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(Ui.dp(c, 4), Ui.dp(c, 6), Ui.dp(c, 4), Ui.dp(c, 6));
        chips.setClipChildren(false);
        chipScroll.addView(chips);
        col.addView(chipScroll, Ui.margins(Ui.lin(Ui.MATCH, Ui.WRAP), 0, Ui.dp(c, 8), 0, Ui.dp(c, 6)));

        grid = new GuideView(c);
        grid.setListener(this);
        col.addView(grid, Ui.lin(Ui.MATCH, 0, 1f));

        catKey = Store.favorites().isEmpty() ? "all" : "fav";
        buildChips();
        grid.setChannels(Library.get().channelsFor(catKey), 0);
        return col;
    }

    private void buildChips() {
        chips.removeAllViews();
        chipViews.clear();
        chipKeys.clear();
        addChip("fav", "★ Favoriten");
        addChip("all", "Alle Sender");
        for (Model.Category cat : Library.get().liveCats) addChip("cat:" + cat.id, cat.name);
    }

    private void addChip(String key, String label) {
        TextView t = Ui.pill(a, label, 14);
        Ui.setPillSelected(t, key.equals(catKey));
        t.setOnClickListener(v -> {
            selectCat(key);
            grid.requestFocus();
        });
        t.setOnKeyListener((v, keyCode, e) -> {
            if (e.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (!key.equals(catKey)) selectCat(key);
                grid.requestFocus();
                return true;
            }
            return false;
        });
        LinearLayout.LayoutParams lp = Ui.lin(Ui.WRAP, Ui.WRAP);
        lp.setMargins(0, 0, Ui.dp(a, 6), 0);
        chips.addView(t, lp);
        chipViews.add(t);
        chipKeys.add(key);
    }

    private void selectCat(String key) {
        catKey = key;
        for (int i = 0; i < chipViews.size(); i++) Ui.setPillSelected(chipViews.get(i), chipKeys.get(i).equals(key));
        grid.setChannels(Library.get().channelsFor(key), 0);
    }

    private View selectedChip() {
        int i = chipKeys.indexOf(catKey);
        return i >= 0 ? chipViews.get(i) : (chipViews.isEmpty() ? null : chipViews.get(0));
    }

    // ---------- GuideView.Listener ----------

    @Override
    public void onSelect(Model.Channel c, Model.Programme p) {
        if (c == null) return;
        Img.logo(dLogo, c.logo, c.name);
        long now = System.currentTimeMillis();
        if (p != null) {
            dTitle.setText(p.title);
            String day = dayLabel(p.start);
            dMeta.setText(c.name + "   ·   " + day + Ui.time(p.start) + " – " + Ui.time(p.end) + "   ·   " + Ui.minutes(p.end - p.start));
            dDesc.setText(p.desc);
            dDesc.setVisibility(p.desc.isEmpty() ? View.GONE : View.VISIBLE);
            boolean live = p.isNow(now);
            dBadge.setVisibility(live ? View.VISIBLE : View.GONE);
        } else {
            dTitle.setText(c.name);
            dMeta.setText(Epg.get(c) == null ? "Programm wird geladen …" : "Keine Programminfos");
            dDesc.setVisibility(View.GONE);
            dBadge.setVisibility(View.GONE);
        }
    }

    private static String dayLabel(long t) {
        java.util.Calendar a = java.util.Calendar.getInstance();
        java.util.Calendar b = java.util.Calendar.getInstance();
        b.setTimeInMillis(t);
        int da = a.get(java.util.Calendar.DAY_OF_YEAR), db = b.get(java.util.Calendar.DAY_OF_YEAR);
        if (a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR)) {
            if (db == da) return "";
            if (db == da + 1) return "Morgen ";
            if (db == da - 1) return "Gestern ";
        }
        return new java.text.SimpleDateFormat("EE dd.MM. ", java.util.Locale.GERMANY).format(new java.util.Date(t));
    }

    @Override
    public void onPlay(Model.Channel c) {
        if (c != null) a.playChannel(c, catKey);
    }

    @Override
    public void onFavorite(Model.Channel c) {
        if (c == null) return;
        boolean fav = Store.toggleFavorite(c.key());
        Ui.toast(a, fav ? "★  " + c.name + " zu Favoriten hinzugefügt" : c.name + " aus Favoriten entfernt");
        if ("fav".equals(catKey)) {
            int row = grid.selectedRow();
            grid.setChannels(Library.get().channelsFor("fav"), row);
        }
    }

    @Override
    public void onLeaveTop() {
        View chip = selectedChip();
        if (chip != null) {
            chip.requestFocus();
            chipScroll.post(() -> chipScroll.smoothScrollTo(Math.max(0, chip.getLeft() - Ui.dp(a, 40)), 0));
        } else {
            a.focusNav();
        }
    }

    // ---------- Screen ----------

    @Override
    public void focusFirst() {
        grid.requestFocus();
    }

    @Override
    public void onShow() {
        visible = true;
        Epg.addListener(this);
        if ("fav".equals(catKey)) grid.setChannels(Library.get().channelsFor("fav"), grid.selectedRow());
        grid.invalidate();
        Bg.cancel(tick);
        Bg.later(tick, 30_000);
    }

    @Override
    public void onHide() {
        visible = false;
        Epg.removeListener(this);
        Bg.cancel(tick);
    }

    @Override
    public void onResume() {
        if (visible) grid.invalidate();
    }

    @Override
    public boolean onBack() {
        if (grid.hasFocus()) {
            grid.jumpNow();
            onLeaveTop();
            return true;
        }
        return false;
    }

    @Override
    public void onEpgChanged() {
        if (!visible) return;
        grid.invalidate();
        onSelect(grid.selectedChannel(), grid.selectedProgramme());
    }

    @Override
    public void onLibraryChanged(String what) {
        if ("logos".equals(what)) grid.refreshLogos();
    }
}
