package app.antenne.tv;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** Filme bzw. Serien: großer Kopfbereich zur Auswahl, darunter Reihen pro Kategorie. */
public class MediaScreen extends Screen implements Posters.Callbacks {

    private final boolean seriesMode;
    private TvList rows;
    private Posters.RowsAdapter adapter;
    private TextView hTitle, hMeta, hPlot, loading;
    private boolean built;
    private Object lastFocus;
    private String resumeSig = "";
    private Object pendingHero;
    private final Runnable heroRunnable = () -> {
        if (pendingHero != null) showHero(pendingHero);
    };

    public MediaScreen(MainActivity a, boolean series) {
        super(a);
        this.seriesMode = series;
    }

    @Override
    protected View build() {
        Context c = a;
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dp(c, 44), Ui.dp(c, 84), 0, 0);

        LinearLayout hero = new LinearLayout(c);
        hero.setOrientation(LinearLayout.VERTICAL);
        hTitle = Ui.lines(Ui.text(c, 30, Ui.TEXT, Ui.bold(c)), 1);
        hTitle.setLetterSpacing(-0.01f);
        hero.addView(hTitle, Ui.lin(Ui.dp(c, 700), Ui.WRAP));
        hMeta = Ui.lines(Ui.text(c, 15, Ui.TEXT2, Ui.semibold(c)), 1);
        hero.addView(hMeta, Ui.margins(Ui.lin(Ui.dp(c, 700), Ui.WRAP), 0, Ui.dp(c, 8), 0, 0));
        hPlot = Ui.lines(Ui.text(c, 15, Ui.TEXT2, Ui.regular(c)), 2);
        hPlot.setLineSpacing(Ui.dp(c, 3), 1f);
        hero.addView(hPlot, Ui.margins(Ui.lin(Ui.dp(c, 620), Ui.WRAP), 0, Ui.dp(c, 8), 0, 0));
        col.addView(hero, Ui.lin(Ui.MATCH, Ui.dp(c, 120)));

        FrameLayout box = new FrameLayout(c);
        rows = new TvList(c, false, 0f);
        rows.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 320));
        rows.setItemAnimator(null);
        rows.setItemViewCacheSize(4);
        adapter = new Posters.RowsAdapter(this);
        rows.setAdapter(adapter);
        box.addView(rows, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        loading = Ui.text(c, 18, Ui.TEXT2, Ui.medium(c));
        loading.setGravity(Gravity.CENTER);
        box.addView(loading, Ui.frame(Ui.WRAP, Ui.WRAP, Gravity.CENTER));
        col.addView(box, Ui.lin(Ui.MATCH, 0, 1f));
        built = true;
        refresh();
        return col;
    }

    private void refresh() {
        if (!built) return;
        Library lib = Library.get();
        boolean loaded = seriesMode ? lib.seriesLoaded : lib.vodLoaded;
        List<Posters.Row> list = new ArrayList<>();
        if (loaded) {
            List<Store.Resume> res = new ArrayList<>();
            for (Store.Resume r : Store.resumeList()) {
                boolean isSeries = r.seriesId != null && !r.seriesId.isEmpty();
                if (isSeries == seriesMode) res.add(r);
            }
            StringBuilder sig = new StringBuilder();
            for (Store.Resume r : res) sig.append(r.key).append(',');
            resumeSig = sig.toString();
            if (!res.isEmpty()) list.add(new Posters.Row("Weiterschauen", res));
            if (seriesMode) {
                List<Model.Series> recent = lib.recentSeries(40);
                if (recent.size() > 8 && hasDates(recent)) list.add(new Posters.Row("Neu hinzugefügt", recent));
                for (Model.Category cat : lib.seriesCats) list.add(new Posters.Row(cat.name, cat.items));
            } else {
                List<Model.Movie> recent = lib.recentMovies(40);
                if (recent.size() > 8 && hasDates(recent)) list.add(new Posters.Row("Neu hinzugefügt", recent));
                for (Model.Category cat : lib.movieCats) list.add(new Posters.Row(cat.name, cat.items));
            }
        }
        adapter.setRows(list);
        if (!loaded) {
            loading.setText(seriesMode ? "Serien werden geladen …" : "Filme werden geladen …");
            loading.setVisibility(View.VISIBLE);
        } else if (list.isEmpty()) {
            String err = seriesMode ? lib.seriesError : lib.vodError;
            loading.setText(err != null ? "Konnte nicht geladen werden:\n" + StartActivity.friendly(err)
                    : (seriesMode ? "Dein Zugang enthält keine Serien." : "Dein Zugang enthält keine Filme."));
            loading.setVisibility(View.VISIBLE);
        } else {
            loading.setVisibility(View.GONE);
        }
        if (lastFocus == null && !list.isEmpty() && !list.get(0).items.isEmpty()) showHero(list.get(0).items.get(0));
        else if (list.isEmpty()) {
            hTitle.setText(seriesMode ? "Serien" : "Filme");
            hMeta.setText("");
            hPlot.setText("");
        }
    }

    private static boolean hasDates(List<?> l) {
        int n = 0;
        for (Object o : l) {
            long t = o instanceof Model.Movie ? ((Model.Movie) o).added : o instanceof Model.Series ? ((Model.Series) o).added : 0;
            if (t > 0) n++;
        }
        return n > l.size() / 2;
    }

    private void showHero(Object o) {
        hTitle.setText(Posters.title(o));
        StringBuilder meta = new StringBuilder();
        String plot = "";
        if (o instanceof Model.Movie) {
            Model.Movie m = (Model.Movie) o;
            append(meta, "Film");
            append(meta, m.year);
            append(meta, rating(m.rating));
        } else if (o instanceof Model.Series) {
            Model.Series s = (Model.Series) o;
            append(meta, "Serie");
            append(meta, s.year);
            append(meta, rating(s.rating));
            append(meta, s.genre);
            plot = s.plot == null ? "" : s.plot;
        } else if (o instanceof Store.Resume) {
            Store.Resume r = (Store.Resume) o;
            append(meta, r.subtitle);
            append(meta, "noch " + Ui.minutes(Math.max(0, r.dur - r.pos)));
        }
        hMeta.setText(meta.toString());
        hPlot.setText(plot);
        a.setBackdrop(Posters.image(o));
    }

    static String rating(String r) {
        if (r == null || r.isEmpty()) return null;
        try {
            double d = Double.parseDouble(r);
            if (d <= 0) return null;
            if (d > 10) d = d / 10;
            return "★ " + String.format(java.util.Locale.GERMANY, "%.1f", d);
        } catch (Exception e) {
            return null;
        }
    }

    static void append(StringBuilder sb, String s) {
        if (s == null || s.trim().isEmpty()) return;
        if (sb.length() > 0) sb.append("   ·   ");
        sb.append(s.trim());
    }

    @Override
    public void onFocus(Object item, View card) {
        lastFocus = item;
        // Kurz warten: beim schnellen Durchscrollen nicht jedes Bild laden (ruckelfrei)
        pendingHero = item;
        Bg.cancel(heroRunnable);
        Bg.later(heroRunnable, 140);
    }

    @Override
    public void onClick(Object item) {
        if (item instanceof Model.Movie) a.openMovie((Model.Movie) item);
        else if (item instanceof Model.Series) a.openSeries((Model.Series) item);
        else if (item instanceof Store.Resume) a.openResume((Store.Resume) item);
    }

    @Override
    public void focusFirst() {
        if (adapter.getItemCount() == 0) return;
        rows.scrollToPosition(0);
        rows.post(() -> {
            RecyclerView.ViewHolder vh = rows.findViewHolderForAdapterPosition(0);
            if (vh != null) {
                RecyclerView inner = ((Posters.RowsAdapter.VH) vh).list;
                RecyclerView.ViewHolder first = inner.findViewHolderForAdapterPosition(0);
                if (first != null) first.itemView.requestFocus();
                else vh.itemView.requestFocus();
            }
        });
    }

    @Override
    public void onShow() {
        if (lastFocus != null) showHero(lastFocus);
        refreshIfResumeChanged();
    }

    @Override
    public void onResume() {
        refreshIfResumeChanged();
    }

    /** Nur neu aufbauen, wenn sich „Weiterschauen“ geändert hat (sonst geht der Fokus verloren). */
    private void refreshIfResumeChanged() {
        if (!built) return;
        StringBuilder sig = new StringBuilder();
        for (Store.Resume r : Store.resumeList()) {
            boolean isSeries = r.seriesId != null && !r.seriesId.isEmpty();
            if (isSeries == seriesMode) sig.append(r.key).append(',');
        }
        if (sig.toString().equals(resumeSig)) return;
        boolean hadFocus = rows.hasFocus();
        refresh();
        if (hadFocus) focusFirst();
    }

    @Override
    public boolean onBack() {
        if (rows.computeVerticalScrollOffset() > 0) {
            rows.smoothScrollToPosition(0);
            focusFirst();
            return true;
        }
        return false;
    }

    @Override
    public void onLibraryChanged(String what) {
        if ((seriesMode && "series".equals(what)) || (!seriesMode && "vod".equals(what))) refresh();
    }
}
