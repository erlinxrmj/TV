package app.antenne.tv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** Detailseite für Filme und Serien (mit Staffeln und Folgen). */
public class DetailActivity extends Activity {

    private boolean series;
    private Model.Movie movie;
    private Model.Series show;
    private ImageView bg, poster;
    private TextView title, meta, plot, credits;
    private LinearLayout buttons;
    private LinearLayout seasonRow;
    private HorizontalScrollView seasonScroll;
    private RecyclerView episodes;
    private ProgressBar busy;
    private List<Model.Episode> allEpisodes = new ArrayList<>();
    private List<Model.Episode> shownEpisodes = new ArrayList<>();
    private final List<TextView> seasonPills = new ArrayList<>();
    private int season = -1;
    private boolean destroyed;
    private String movieExt;
    private EpAdapter epAdapter;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        series = "series".equals(getIntent().getStringExtra("type"));
        String id = getIntent().getStringExtra("id");
        if (series) show = Library.get().series(id);
        else movie = Library.get().movie(id);
        if ((series && show == null) || (!series && movie == null)) {
            finish();
            return;
        }
        buildUi();
        loadInfo();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!series && movie != null && buttons != null) buildMovieButtons();
        if (series && epAdapter != null) {
            epAdapter.notifyDataSetChanged();
            buildSeriesButtons();
        }
    }

    private void buildUi() {
        Context c = this;
        FrameLayout root = new FrameLayout(c);
        root.setBackgroundColor(Ui.BG);
        bg = new ImageView(c);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bg.setAlpha(0f);
        root.addView(bg, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        View shadeL = new View(c);
        shadeL.setBackground(Ui.hgrad(0xF20B0C12, 0x330B0C12));
        root.addView(shadeL, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        View shadeB = new View(c);
        shadeB.setBackground(Ui.vgrad(0x000B0C12, 0xFF0B0C12));
        root.addView(shadeB, Ui.frame(Ui.MATCH, Ui.dp(c, 300), Gravity.BOTTOM));

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dp(c, 56), Ui.dp(c, series ? 32 : 56), Ui.dp(c, 40), Ui.dp(c, 16));
        col.setClipChildren(false);
        col.setClipToPadding(false);

        LinearLayout top = new LinearLayout(c);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setClipChildren(false);
        poster = new ImageView(c);
        poster.setBackground(Ui.round(0xFF1C1E28, Ui.dp(c, 16)));
        Ui.clipRound(poster, Ui.dp(c, 16));
        poster.setElevation(Ui.dp(c, 20));
        top.addView(poster, series ? Ui.lin(Ui.dp(c, 120), Ui.dp(c, 180)) : Ui.lin(Ui.dp(c, 168), Ui.dp(c, 252)));

        LinearLayout txt = new LinearLayout(c);
        txt.setOrientation(LinearLayout.VERTICAL);
        txt.setClipChildren(false);
        title = Ui.lines(Ui.text(c, series ? 32 : 38, Ui.TEXT, Ui.bold(c)), series ? 1 : 2);
        title.setLetterSpacing(-0.015f);
        txt.addView(title, Ui.lin(Ui.MATCH, Ui.WRAP));
        meta = Ui.lines(Ui.text(c, 15, Ui.TEXT2, Ui.semibold(c)), 1);
        txt.addView(meta, Ui.margins(Ui.lin(Ui.MATCH, Ui.WRAP), 0, Ui.dp(c, 10), 0, 0));
        plot = Ui.lines(Ui.text(c, series ? 15 : 16, Ui.TEXT2, Ui.regular(c)), series ? 2 : 5);
        plot.setLineSpacing(Ui.dp(c, 4), 1f);
        txt.addView(plot, Ui.margins(Ui.lin(Ui.dp(c, 560), Ui.WRAP), 0, Ui.dp(c, 14), 0, 0));
        credits = Ui.lines(Ui.text(c, 13, Ui.TEXT3, Ui.medium(c)), series ? 1 : 2);
        txt.addView(credits, Ui.margins(Ui.lin(Ui.dp(c, 560), Ui.WRAP), 0, Ui.dp(c, 10), 0, 0));
        buttons = new LinearLayout(c);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setClipChildren(false);
        txt.addView(buttons, Ui.margins(Ui.lin(Ui.WRAP, Ui.WRAP), 0, Ui.dp(c, series ? 14 : 22), 0, 0));
        busy = new ProgressBar(c);
        txt.addView(busy, Ui.margins(Ui.lin(Ui.dp(c, 28), Ui.dp(c, 28)), 0, Ui.dp(c, 12), 0, 0));
        top.addView(txt, Ui.margins(Ui.lin(0, Ui.WRAP, 1f), Ui.dp(c, 36), 0, 0, 0));
        col.addView(top, Ui.lin(Ui.MATCH, Ui.WRAP));

        if (series) {
            seasonScroll = new HorizontalScrollView(c);
            seasonScroll.setHorizontalScrollBarEnabled(false);
            seasonScroll.setClipChildren(false);
            seasonScroll.setClipToPadding(false);
            seasonRow = new LinearLayout(c);
            seasonRow.setOrientation(LinearLayout.HORIZONTAL);
            seasonRow.setPadding(Ui.dp(c, 2), Ui.dp(c, 6), Ui.dp(c, 2), Ui.dp(c, 6));
            seasonRow.setClipChildren(false);
            seasonScroll.addView(seasonRow);
            col.addView(seasonScroll, Ui.margins(Ui.lin(Ui.MATCH, Ui.WRAP), 0, Ui.dp(c, 10), 0, 0));
            episodes = new RecyclerView(c);
            episodes.setLayoutManager(new LinearLayoutManager(c, LinearLayoutManager.HORIZONTAL, false));
            episodes.setClipToPadding(false);
            episodes.setClipChildren(false);
            episodes.setPadding(Ui.dp(c, 4), Ui.dp(c, 12), Ui.dp(c, 60), Ui.dp(c, 12));
            episodes.setItemAnimator(null);
            epAdapter = new EpAdapter();
            episodes.setAdapter(epAdapter);
            col.addView(episodes, Ui.lin(Ui.MATCH, Ui.WRAP));
        }
        root.addView(col, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        setContentView(root);

        String name = series ? show.name : movie.name;
        String img = series ? show.poster : movie.poster;
        title.setText(name);
        Img.poster(poster, img);
        Img.blur(bg, series && show.backdrop != null ? show.backdrop : img);
        StringBuilder m = new StringBuilder();
        if (series) {
            MediaScreen.append(m, show.year);
            MediaScreen.append(m, show.genre);
            MediaScreen.append(m, MediaScreen.rating(show.rating));
            plot.setText(show.plot == null ? "" : show.plot);
        } else {
            MediaScreen.append(m, movie.year);
            MediaScreen.append(m, MediaScreen.rating(movie.rating));
        }
        meta.setText(m.toString());
        if (!series) buildMovieButtons();
    }

    private void loadInfo() {
        Xtream x = Library.get().xtream;
        if (series && show.m3uEpisodes != null) {
            busy.setVisibility(View.GONE);
            setEpisodes(show.m3uEpisodes);
            return;
        }
        if (x == null) {
            busy.setVisibility(View.GONE);
            return;
        }
        Bg.run(() -> {
            try {
                if (series) {
                    Xtream.SeriesInfo si = x.seriesInfo(show.id);
                    Bg.ui(() -> {
                        if (destroyed) return;
                        busy.setVisibility(View.GONE);
                        applyText(si.plot, si.genre, si.release, si.rating, null, si.cast, null);
                        if (si.backdrop != null) Img.blur(bg, si.backdrop);
                        if (si.poster != null && (show.poster == null || show.poster.isEmpty())) Img.poster(poster, si.poster);
                        setEpisodes(si.episodes);
                    });
                } else {
                    Xtream.MovieInfo mi = x.movieInfo(movie.id);
                    Bg.ui(() -> {
                        if (destroyed) return;
                        busy.setVisibility(View.GONE);
                        movieExt = mi.ext;
                        applyText(mi.plot, mi.genre, mi.release, mi.rating, mi.duration, mi.cast, mi.director);
                        if (mi.backdrop != null) Img.wide(bg, mi.backdrop);
                        bg.animate().alpha(0.85f).setDuration(400).start();
                        if (mi.poster != null && (movie.poster == null || movie.poster.isEmpty())) Img.poster(poster, mi.poster);
                    });
                }
            } catch (Exception e) {
                Bg.ui(() -> {
                    if (destroyed) return;
                    busy.setVisibility(View.GONE);
                    if (series) setEpisodes(new ArrayList<>());
                });
            }
        });
    }

    private void applyText(String p, String genre, String release, String rating, String duration, String cast, String director) {
        if (p != null) plot.setText(p);
        StringBuilder m = new StringBuilder();
        String year = release != null && release.length() >= 4 ? release.substring(0, 4) : (series ? show.year : movie.year);
        MediaScreen.append(m, year);
        MediaScreen.append(m, genre);
        if (duration != null && !duration.startsWith("00:00")) MediaScreen.append(m, prettyDuration(duration));
        MediaScreen.append(m, MediaScreen.rating(rating != null ? rating : (series ? show.rating : movie.rating)));
        meta.setText(m.toString());
        StringBuilder cr = new StringBuilder();
        if (director != null) cr.append("Regie: ").append(director);
        if (cast != null) {
            if (cr.length() > 0) cr.append("   ·   ");
            cr.append("Mit: ").append(cast);
        }
        credits.setText(cr.toString());
    }

    private static String prettyDuration(String d) {
        // „01:52:00“ → „1 Std. 52 Min.“
        try {
            String[] p = d.split(":");
            if (p.length >= 2) {
                int h = Integer.parseInt(p[0]);
                int m = Integer.parseInt(p[1]);
                return (h > 0 ? h + " Std. " : "") + m + " Min.";
            }
        } catch (Exception ignored) {
        }
        return d;
    }

    // ---------- Film ----------

    private void buildMovieButtons() {
        buttons.removeAllViews();
        long pos = Store.position(movie.key());
        TextView play;
        if (pos > 30_000) {
            play = Ui.button(this, "▶  Fortsetzen", true);
            play.setOnClickListener(v -> playMovie(pos));
            TextView restart = Ui.button(this, "Von vorne", false);
            restart.setOnClickListener(v -> playMovie(0));
            buttons.addView(play);
            buttons.addView(restart, Ui.margins(Ui.lin(Ui.WRAP, Ui.WRAP), Ui.dp(this, 14), 0, 0, 0));
        } else {
            play = Ui.button(this, "▶  Abspielen", true);
            play.setOnClickListener(v -> playMovie(0));
            buttons.addView(play);
        }
        play.requestFocus();
    }

    private void playMovie(long from) {
        if (movieExt != null && !movieExt.isEmpty()) movie.ext = movieExt;
        Intent i = new Intent(this, PlayerActivity.class);
        i.putExtra("type", "movie");
        i.putExtra("id", movie.id);
        i.putExtra("pos", from);
        startActivity(i);
    }

    // ---------- Serie ----------

    private void setEpisodes(List<Model.Episode> eps) {
        allEpisodes = new ArrayList<>(eps);
        seasonRow.removeAllViews();
        seasonPills.clear();
        List<Integer> seasons = new ArrayList<>();
        for (Model.Episode e : allEpisodes) if (!seasons.contains(e.season)) seasons.add(e.season);
        java.util.Collections.sort(seasons);
        for (int s : seasons) {
            TextView t = Ui.pill(this, "Staffel " + s, 15);
            t.setTag(s);
            View.OnFocusChangeListener base = t.getOnFocusChangeListener();
            t.setOnFocusChangeListener((v, has) -> {
                base.onFocusChange(v, has);
                if (has && season != s) selectSeason(s);
            });
            t.setOnClickListener(v -> episodes.requestFocus());
            LinearLayout.LayoutParams lp = Ui.lin(Ui.WRAP, Ui.WRAP);
            lp.setMargins(0, 0, Ui.dp(this, 8), 0);
            seasonRow.addView(t, lp);
            seasonPills.add(t);
        }
        if (allEpisodes.isEmpty()) {
            TextView none = Ui.text(this, 16, Ui.TEXT2, Ui.medium(this));
            none.setText("Keine Folgen gefunden.");
            seasonRow.addView(none);
        }
        // Mit der Staffel der zuletzt geschauten Folge starten
        Model.Episode resume = resumeEpisode();
        int start = resume != null ? resume.season : (seasons.isEmpty() ? 1 : seasons.get(0));
        selectSeason(start);
        buildSeriesButtons();
    }

    private void selectSeason(int s) {
        season = s;
        for (TextView t : seasonPills) Ui.setPillSelected(t, Integer.valueOf(s).equals(t.getTag()));
        shownEpisodes = new ArrayList<>();
        for (Model.Episode e : allEpisodes) if (e.season == s) shownEpisodes.add(e);
        epAdapter.notifyDataSetChanged();
        episodes.scrollToPosition(0);
    }

    /** Die Folge zum Weiterschauen: angefangene oder die nach der zuletzt fertig gesehenen. */
    private Model.Episode resumeEpisode() {
        for (Store.Resume r : Store.resumeList()) {
            if (show.id.equals(r.seriesId)) {
                for (Model.Episode e : allEpisodes) if (e.key().equals(r.key)) return e;
            }
        }
        Model.Episode lastDone = null;
        for (Model.Episode e : allEpisodes) if (Store.watched(e.key()) >= 0.94f) lastDone = e;
        if (lastDone != null) {
            int i = allEpisodes.indexOf(lastDone);
            if (i + 1 < allEpisodes.size()) return allEpisodes.get(i + 1);
        }
        return null;
    }

    private void buildSeriesButtons() {
        if (allEpisodes.isEmpty()) {
            buttons.removeAllViews();
            return;
        }
        buttons.removeAllViews();
        Model.Episode r = resumeEpisode();
        Model.Episode target = r != null ? r : allEpisodes.get(0);
        long pos = Store.position(target.key());
        String label = (r == null ? "▶  Abspielen  " : pos > 30_000 ? "▶  Fortsetzen  " : "▶  Weiter  ")
                + "S" + target.season + " F" + target.number;
        TextView play = Ui.button(this, label, true);
        play.setOnClickListener(v -> playEpisode(target, pos));
        buttons.addView(play);
        if (!buttons.hasFocus() && !episodes.hasFocus() && !seasonRow.hasFocus()) play.requestFocus();
    }

    private void playEpisode(Model.Episode e, long from) {
        PlayerActivity.queue = new ArrayList<>(allEpisodes);
        PlayerActivity.queueSeries = show;
        Intent i = new Intent(this, PlayerActivity.class);
        i.putExtra("type", "episode");
        i.putExtra("id", e.id);
        i.putExtra("pos", from);
        startActivity(i);
    }

    private final class EpAdapter extends RecyclerView.Adapter<EpAdapter.VH> {
        final class VH extends RecyclerView.ViewHolder {
            final FrameLayout card;
            final ImageView img;
            final TextView num, name, info;
            final Ui.Bar bar;
            Model.Episode ep;

            VH(LinearLayout v, FrameLayout card, ImageView img, TextView num, TextView name, TextView info, Ui.Bar bar) {
                super(v);
                this.card = card;
                this.img = img;
                this.num = num;
                this.name = name;
                this.info = info;
                this.bar = bar;
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context c = parent.getContext();
            int w = Ui.dp(c, 220);
            LinearLayout v = new LinearLayout(c);
            v.setOrientation(LinearLayout.VERTICAL);
            v.setClipChildren(false);
            v.setFocusable(true);
            v.setClickable(true);
            FrameLayout card = new FrameLayout(c);
            float r = Ui.dp(c, 14);
            card.setBackground(new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TL_BR, new int[]{0xFF262A3A, 0xFF15161E}));
            Ui.clipRound(card, r);
            TextView num = Ui.text(c, 30, 0x33FFFFFF, Ui.bold(c));
            card.addView(num, Ui.frame(Ui.WRAP, Ui.WRAP, Gravity.CENTER));
            ImageView img = new ImageView(c);
            card.addView(img, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
            Ui.Bar bar = new Ui.Bar(c, 0x66000000, 0xFFFFFFFF);
            FrameLayout.LayoutParams blp = Ui.frame(Ui.MATCH, Ui.dp(c, 4), Gravity.BOTTOM);
            blp.setMargins(Ui.dp(c, 10), 0, Ui.dp(c, 10), Ui.dp(c, 8));
            card.addView(bar, blp);
            v.addView(card, Ui.lin(w, Ui.dp(c, 116)));
            TextView name = Ui.lines(Ui.text(c, 14, Ui.TEXT, Ui.semibold(c)), 1);
            v.addView(name, Ui.margins(Ui.lin(w, Ui.WRAP), Ui.dp(c, 2), Ui.dp(c, 10), 0, 0));
            TextView info = Ui.lines(Ui.text(c, 12, Ui.TEXT3, Ui.medium(c)), 2);
            v.addView(info, Ui.margins(Ui.lin(w, Ui.WRAP), Ui.dp(c, 2), Ui.dp(c, 4), 0, 0));
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(Ui.WRAP, Ui.WRAP);
            lp.setMargins(0, 0, Ui.dp(c, 20), 0);
            v.setLayoutParams(lp);
            VH vh = new VH(v, card, img, num, name, info, bar);
            android.graphics.drawable.Drawable ringD = Ui.ring(0xF2FFFFFF, Ui.dp(c, 3), r);
            v.setOnFocusChangeListener((view, has) -> {
                card.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f)
                        .translationZ(has ? Ui.dp(c, 14) : 0).setDuration(160).start();
                name.animate().translationY(has ? Ui.dp(c, 8) : 0).setDuration(160).start();
                info.animate().translationY(has ? Ui.dp(c, 8) : 0).setDuration(160).start();
                if (android.os.Build.VERSION.SDK_INT >= 23) card.setForeground(has ? ringD : null);
            });
            v.setOnClickListener(view -> {
                if (vh.ep != null) playEpisode(vh.ep, Store.position(vh.ep.key()));
            });
            return vh;
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Model.Episode e = shownEpisodes.get(position);
            h.ep = e;
            h.num.setText(String.valueOf(e.number));
            String t = e.title == null || e.title.isEmpty() ? "Folge " + e.number : e.title;
            // Viele Anbieter schreiben „Serie - S01E02 - Titel“: auf den Titel kürzen
            t = t.replaceFirst("(?i)^.*?S\\d{1,2}\\s*E\\d{1,3}\\s*[-:.]*\\s*", "");
            if (t.isEmpty()) t = "Folge " + e.number;
            h.name.setText(e.number + ". " + t);
            StringBuilder info = new StringBuilder();
            if (e.durationSecs > 0) info.append(Ui.minutes(e.durationSecs * 1000L));
            if (e.plot != null && !e.plot.isEmpty()) {
                if (info.length() > 0) info.append("  ·  ");
                info.append(e.plot);
            }
            h.info.setText(info.toString());
            Img.wide(h.img, e.image != null ? e.image : null);
            float w = Store.watched(e.key());
            h.bar.setVisibility(w > 0.01f ? View.VISIBLE : View.GONE);
            h.bar.setProgress(w);
        }

        @Override
        public int getItemCount() {
            return shownEpisodes.size();
        }
    }
}
