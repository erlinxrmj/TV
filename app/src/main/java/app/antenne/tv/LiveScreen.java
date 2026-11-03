package app.antenne.tv;

import android.content.Context;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Live-TV im Apple-TV-Stil: links schlanke Kategorien (Favoriten ganz oben), rechts grosse
 * Kacheln mit Senderlogo, laufender Sendung und Fortschritt. OK lang drücken = Favorit.
 */
public class LiveScreen extends Screen implements Epg.Listener {

    private static final int COLUMNS = 4;

    private TvList catList, grid;
    private CatAdapter catAdapter;
    private TileAdapter tileAdapter;
    private final List<String[]> cats = new ArrayList<>(); // {key, name, count}
    private String selectedCat;
    private List<Model.Channel> shown = new ArrayList<>();
    private Model.Channel focused;
    private TextView listTitle, listSub, empty;
    private boolean visible;
    private String pendingCat;

    private final Runnable selectCat = () -> {
        if (pendingCat != null && !pendingCat.equals(selectedCat)) selectCategory(pendingCat, false);
    };
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!visible) return;
            refreshEpg();
            Bg.later(this, 30_000);
        }
    };
    private final Runnable epgRefresh = this::refreshEpg;

    public LiveScreen(MainActivity a) {
        super(a);
    }

    @Override
    protected View build() {
        Context c = a;
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(Ui.dp(c, 28), Ui.dp(c, 80), Ui.dp(c, 20), 0);

        // ---- Kategorien ----
        LinearLayout catCol = new LinearLayout(c);
        catCol.setOrientation(LinearLayout.VERTICAL);
        TextView ch = Ui.text(c, 12, Ui.TEXT3, Ui.bold(c));
        ch.setText("KATEGORIEN");
        ch.setLetterSpacing(0.12f);
        catCol.addView(ch, Ui.margins(Ui.lin(Ui.WRAP, Ui.WRAP), Ui.dp(c, 16), Ui.dp(c, 8), 0, Ui.dp(c, 6)));
        FrameLayout catClip = new FrameLayout(c); // schneidet sauber ab: nichts rutscht über die Überschrift
        catList = new TvList(c, false, 0.3f);
        catList.setPadding(Ui.dp(c, 4), Ui.dp(c, 6), Ui.dp(c, 4), Ui.dp(c, 30));
        catAdapter = new CatAdapter();
        catList.setAdapter(catAdapter);
        catClip.addView(catList, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        catCol.addView(catClip, Ui.lin(Ui.MATCH, 0, 1f));
        row.addView(catCol, Ui.lin(Ui.dp(c, 196), Ui.MATCH));

        // ---- Kacheln ----
        LinearLayout gridCol = new LinearLayout(c);
        gridCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.BOTTOM);
        listTitle = Ui.lines(Ui.text(c, 24, Ui.TEXT, Ui.bold(c)), 1);
        head.addView(listTitle, Ui.lin(0, Ui.WRAP, 1f));
        listSub = Ui.lines(Ui.text(c, 13, Ui.TEXT3, Ui.semibold(c)), 1);
        head.addView(listSub, Ui.margins(Ui.lin(Ui.WRAP, Ui.WRAP), Ui.dp(c, 12), 0, Ui.dp(c, 12), Ui.dp(c, 3)));
        gridCol.addView(head, Ui.margins(Ui.lin(Ui.MATCH, Ui.WRAP), Ui.dp(c, 14), 0, 0, Ui.dp(c, 4)));
        FrameLayout gridClip = new FrameLayout(c);
        grid = new TvList(c, COLUMNS, 0.25f);
        grid.setPadding(Ui.dp(c, 10), Ui.dp(c, 14), Ui.dp(c, 10), Ui.dp(c, 60));
        tileAdapter = new TileAdapter();
        grid.setAdapter(tileAdapter);
        gridClip.addView(grid, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        empty = Ui.text(c, 17, Ui.TEXT2, Ui.medium(c));
        empty.setGravity(Gravity.CENTER);
        empty.setVisibility(View.GONE);
        gridClip.addView(empty, Ui.frame(Ui.dp(c, 420), Ui.WRAP, Gravity.CENTER));
        gridCol.addView(gridClip, Ui.lin(Ui.MATCH, 0, 1f));
        row.addView(gridCol, Ui.margins(Ui.lin(0, Ui.MATCH, 1f), Ui.dp(c, 6), 0, 0, 0));

        rebuildCategories();
        selectCategory(Store.favorites().isEmpty() ? "all" : "fav", false);
        return row;
    }

    // ---------- Kategorien ----------

    private void rebuildCategories() {
        cats.clear();
        Library lib = Library.get();
        cats.add(new String[]{"fav", "★  Favoriten", String.valueOf(lib.channelsFor("fav").size())});
        int rec = lib.channelsFor("recent").size();
        if (rec > 0) cats.add(new String[]{"recent", "Zuletzt gesehen", String.valueOf(rec)});
        cats.add(new String[]{"all", "Alle Sender", String.valueOf(lib.channels.size())});
        for (Model.Category cat : lib.liveCats) {
            cats.add(new String[]{"cat:" + cat.id, cat.name, String.valueOf(cat.items.size())});
        }
        if (catAdapter != null) catAdapter.notifyDataSetChanged();
    }

    /** Nur die Darstellung der sichtbaren Kategorien auffrischen – ohne die Liste neu aufzubauen. */
    private void restyleCategories() {
        for (int i = 0; i < catList.getChildCount(); i++) {
            RecyclerView.ViewHolder vh = catList.getChildViewHolder(catList.getChildAt(i));
            if (vh instanceof CatAdapter.VH) catAdapter.style((CatAdapter.VH) vh, vh.itemView.hasFocus());
        }
    }

    private void selectCategory(String key, boolean focusGrid) {
        selectedCat = key;
        shown = Library.get().channelsFor(key);
        listTitle.setText(Library.get().categoryName(key));
        listSub.setText(shown.size() + " Sender  ·  OK lang: ★ Favorit");
        restyleCategories();
        tileAdapter.notifyDataSetChanged();
        grid.scrollToPosition(0);
        if (shown.isEmpty()) {
            empty.setVisibility(View.VISIBLE);
            empty.setText("fav".equals(key)
                    ? "Noch keine Favoriten.\nHalte bei einem Sender OK gedrückt, um ihn mit ★ zu markieren."
                    : "Keine Sender in dieser Kategorie.");
        } else {
            empty.setVisibility(View.GONE);
        }
        if (focusGrid && !shown.isEmpty()) grid.post(() -> grid.focusPosition(0));
    }

    private int catIndex() {
        for (int i = 0; i < cats.size(); i++) if (cats.get(i)[0].equals(selectedCat)) return i;
        return 0;
    }

    private void focusSelectedCategory() {
        catList.focusPosition(catIndex());
    }

    private final class CatAdapter extends RecyclerView.Adapter<CatAdapter.VH> {
        final class VH extends RecyclerView.ViewHolder {
            final TextView name, count;

            VH(LinearLayout v, TextView name, TextView count) {
                super(v);
                this.name = name;
                this.count = count;
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context c = parent.getContext();
            LinearLayout v = new LinearLayout(c);
            v.setOrientation(LinearLayout.HORIZONTAL);
            v.setGravity(Gravity.CENTER_VERTICAL);
            v.setPadding(Ui.dp(c, 14), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 10));
            v.setFocusable(true);
            v.setClickable(true);
            TextView name = Ui.lines(Ui.text(c, 15, Ui.TEXT2, Ui.semibold(c)), 1);
            v.addView(name, Ui.lin(0, Ui.WRAP, 1f));
            TextView count = Ui.text(c, 12, Ui.TEXT3, Ui.semibold(c));
            v.addView(count, Ui.margins(Ui.lin(Ui.WRAP, Ui.WRAP), Ui.dp(c, 8), 0, 0, 0));
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(Ui.MATCH, Ui.WRAP);
            lp.bottomMargin = Ui.dp(c, 3);
            v.setLayoutParams(lp);
            VH vh = new VH(v, name, count);
            v.setOnFocusChangeListener((view, has) -> {
                style(vh, has);
                int pos = vh.getBindingAdapterPosition();
                if (has && pos >= 0 && pos < cats.size()) {
                    pendingCat = cats.get(pos)[0];
                    Bg.cancel(selectCat);
                    Bg.later(selectCat, 260);
                }
            });
            v.setOnClickListener(view -> {
                int pos = vh.getBindingAdapterPosition();
                if (pos < 0) return;
                Bg.cancel(selectCat);
                String k = cats.get(pos)[0];
                if (!k.equals(selectedCat)) selectCategory(k, true);
                else if (!shown.isEmpty()) grid.focusPosition(0);
            });
            v.setOnKeyListener((view, keyCode, e) -> {
                if (e.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    Bg.cancel(selectCat);
                    int pos = vh.getBindingAdapterPosition();
                    if (pos >= 0 && !cats.get(pos)[0].equals(selectedCat)) selectCategory(cats.get(pos)[0], true);
                    else if (!shown.isEmpty()) grid.focusPosition(Math.max(0, shown.indexOf(focused)));
                    return true;
                }
                return false;
            });
            return vh;
        }

        void style(VH vh, boolean has) {
            Context c = vh.itemView.getContext();
            int pos = vh.getBindingAdapterPosition();
            boolean sel = pos >= 0 && pos < cats.size() && cats.get(pos)[0].equals(selectedCat);
            float r = Ui.dp(c, 12);
            if (has) {
                vh.itemView.setBackground(Ui.round(Ui.FOCUS_BG, r));
                vh.name.setTextColor(Ui.FOCUS_FG);
                vh.count.setTextColor(Ui.focusInk(0x99));
            } else {
                vh.itemView.setBackground(sel ? Ui.round(Ui.GLASS_HI, r) : null);
                vh.name.setTextColor(sel ? Ui.TEXT : Ui.TEXT2);
                vh.count.setTextColor(Ui.TEXT3);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            String[] cat = cats.get(position);
            h.name.setText(cat[1]);
            h.count.setText(cat[2]);
            style(h, h.itemView.hasFocus());
        }

        @Override
        public int getItemCount() {
            return cats.size();
        }
    }

    // ---------- Senderkacheln ----------

    /** Rahmen mit festem 16:9-Seitenverhältnis. */
    static final class Ratio extends FrameLayout {
        Ratio(Context c) {
            super(c);
        }

        @Override
        protected void onMeasure(int w, int h) {
            int width = MeasureSpec.getSize(w);
            super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(width * 9 / 16, MeasureSpec.EXACTLY));
        }
    }

    private final class TileAdapter extends RecyclerView.Adapter<TileAdapter.VH> {
        final class VH extends RecyclerView.ViewHolder {
            final Ratio card;
            final ImageView logo;
            final TextView star, name, now;
            final Ui.Bar bar;
            Model.Channel ch;

            VH(View v, Ratio card, ImageView logo, TextView star, TextView name, TextView now, Ui.Bar bar) {
                super(v);
                this.card = card;
                this.logo = logo;
                this.star = star;
                this.name = name;
                this.now = now;
                this.bar = bar;
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context c = parent.getContext();
            LinearLayout v = new LinearLayout(c);
            v.setOrientation(LinearLayout.VERTICAL);
            v.setFocusable(true);
            v.setClickable(true);
            v.setLongClickable(true);
            v.setClipChildren(false);
            int gap = Ui.dp(c, 9);
            v.setPadding(gap, gap, gap, Ui.dp(c, 14));

            Ratio card = new Ratio(c);
            float r = Ui.dp(c, 16);
            card.setBackground(Ui.round(Ui.TILE, r));
            Ui.clipRound(card, r);
            ImageView logo = new ImageView(c);
            FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH);
            llp.setMargins(Ui.dp(c, 22), Ui.dp(c, 16), Ui.dp(c, 22), Ui.dp(c, 16));
            card.addView(logo, llp);
            TextView star = Ui.text(c, 14, Ui.FAV, Ui.bold(c));
            star.setText("★");
            FrameLayout.LayoutParams slp = Ui.frame(Ui.WRAP, Ui.WRAP, Gravity.TOP | Gravity.END);
            slp.setMargins(0, Ui.dp(c, 7), Ui.dp(c, 9), 0);
            card.addView(star, slp);
            Ui.Bar bar = new Ui.Bar(c, 0x40FFFFFF, 0xF2FFFFFF);
            FrameLayout.LayoutParams blp = Ui.frame(Ui.MATCH, Ui.dp(c, 3), Gravity.BOTTOM);
            blp.setMargins(Ui.dp(c, 12), 0, Ui.dp(c, 12), Ui.dp(c, 8));
            card.addView(bar, blp);
            v.addView(card, Ui.lin(Ui.MATCH, Ui.WRAP));

            TextView name = Ui.lines(Ui.text(c, 14, Ui.TEXT, Ui.semibold(c)), 1);
            v.addView(name, Ui.margins(Ui.lin(Ui.MATCH, Ui.WRAP), Ui.dp(c, 2), Ui.dp(c, 10), 0, 0));
            TextView now = Ui.lines(Ui.text(c, 12, Ui.TEXT3, Ui.medium(c)), 1);
            v.addView(now, Ui.margins(Ui.lin(Ui.MATCH, Ui.WRAP), Ui.dp(c, 2), Ui.dp(c, 4), 0, 0));

            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(Ui.MATCH, Ui.WRAP);
            v.setLayoutParams(lp);
            VH vh = new VH(v, card, logo, star, name, now, bar);
            android.graphics.drawable.Drawable ringD = Ui.ring(Ui.RING, Ui.dp(c, 3), r);
            v.setOnFocusChangeListener((view, has) -> {
                card.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f)
                        .translationZ(has ? Ui.dp(c, 12) : 0).setDuration(150).start();
                card.setForeground(has ? ringD : null);
                card.setBackground(Ui.round(has ? (Ui.dark ? 0x38FFFFFF : 0xFF4A4A4E) : Ui.TILE, r));
                // Text rückt mit nach unten, damit die vergrösserte Kachel nichts überdeckt
                name.animate().translationY(has ? Ui.dp(c, 6) : 0).setDuration(150).start();
                now.animate().translationY(has ? Ui.dp(c, 6) : 0).setDuration(150).start();
                name.setTextColor(has ? Ui.TEXT : Ui.ink(0xD9));
                if (has && vh.ch != null) focused = vh.ch;
            });
            v.setOnClickListener(view -> {
                if (vh.ch != null) a.playChannel(vh.ch, selectedCat);
            });
            v.setOnLongClickListener(view -> {
                toggleFavorite(vh);
                return true;
            });
            v.setOnKeyListener((view, keyCode, e) -> {
                if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_BOOKMARK) {
                    toggleFavorite(vh);
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    int pos = vh.getBindingAdapterPosition();
                    if (pos >= 0 && pos % COLUMNS == 0) {
                        focusSelectedCategory();
                        return true;
                    }
                }
                return false;
            });
            return vh;
        }

        private void toggleFavorite(VH vh) {
            if (vh.ch == null) return;
            boolean fav = Store.toggleFavorite(vh.ch.key());
            Ui.toast(a, fav ? "★  " + vh.ch.name + " zu Favoriten hinzugefügt" : vh.ch.name + " aus Favoriten entfernt");
            vh.star.setVisibility(fav ? View.VISIBLE : View.GONE);
            updateFavCount();
            if ("fav".equals(selectedCat) && !fav) {
                int pos = vh.getBindingAdapterPosition();
                shown = Library.get().channelsFor("fav");
                notifyDataSetChanged();
                listSub.setText(shown.size() + " Sender  ·  OK lang: ★ Favorit");
                if (!shown.isEmpty()) grid.post(() -> grid.focusPosition(Math.min(pos, shown.size() - 1)));
                else {
                    empty.setVisibility(View.VISIBLE);
                    empty.setText("Noch keine Favoriten.\nHalte bei einem Sender OK gedrückt, um ihn mit ★ zu markieren.");
                    focusSelectedCategory();
                }
            }
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position, @NonNull List<Object> payloads) {
            if (!payloads.isEmpty() && position < shown.size() && h.ch == shown.get(position)) {
                bindEpg(h);
                return;
            }
            onBindViewHolder(h, position);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Model.Channel c = shown.get(position);
            h.ch = c;
            h.name.setText(c.name);
            h.star.setVisibility(Store.isFavorite(c.key()) ? View.VISIBLE : View.GONE);
            Img.logo(h.logo, c.logo, c.name);
            bindEpg(h);
        }

        private void bindEpg(VH h) {
            Model.Programme p = Epg.now(h.ch);
            if (p != null) {
                h.now.setText(Ui.time(p.start) + "  " + p.title);
                h.bar.setProgress(p.progress(System.currentTimeMillis()));
                h.bar.setVisibility(View.VISIBLE);
            } else {
                h.now.setText(Epg.get(h.ch) == null ? "Programm wird geladen …" : "Keine Programminfos");
                h.bar.setVisibility(View.INVISIBLE);
            }
        }

        @Override
        public int getItemCount() {
            return shown.size();
        }
    }

    private void updateFavCount() {
        if (cats.isEmpty()) return;
        cats.get(0)[2] = String.valueOf(Library.get().channelsFor("fav").size());
        catAdapter.notifyItemChanged(0);
    }

    /** Programminfos in den sichtbaren Kacheln auffrischen (ohne Fokus oder Scrollen zu stören). */
    private void refreshEpg() {
        if (!visible || tileAdapter == null) return;
        if (grid.getScrollState() != RecyclerView.SCROLL_STATE_IDLE) {
            Bg.cancel(epgRefresh);
            Bg.later(epgRefresh, 400);
            return;
        }
        tileAdapter.notifyItemRangeChanged(0, shown.size(), "epg");
    }

    // ---------- Lebenszyklus ----------

    @Override
    public void focusFirst() {
        if (shown.isEmpty()) {
            focusSelectedCategory();
            return;
        }
        grid.focusPosition(Math.max(0, shown.indexOf(focused)));
    }

    @Override
    public void onShow() {
        visible = true;
        Epg.addListener(this);
        // Favoriten/Verlauf können sich im Player geändert haben
        int favs = Library.get().channelsFor("fav").size();
        int rec = Library.get().channelsFor("recent").size();
        boolean recentShown = cats.size() > 1 && "recent".equals(cats.get(1)[0]);
        if (!cats.isEmpty() && (!cats.get(0)[2].equals(String.valueOf(favs)) || recentShown != (rec > 0))) {
            rebuildCategories();
        }
        if ("fav".equals(selectedCat) || "recent".equals(selectedCat)) {
            List<Model.Channel> now = Library.get().channelsFor(selectedCat);
            if (!now.equals(shown)) {
                shown = now;
                tileAdapter.notifyDataSetChanged();
                listSub.setText(shown.size() + " Sender  ·  OK lang: ★ Favorit");
                empty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
            }
        }
        Bg.cancel(tick);
        Bg.later(tick, 30_000);
    }

    @Override
    public void onHide() {
        visible = false;
        Epg.removeListener(this);
        Bg.cancel(tick);
        Bg.cancel(selectCat);
    }

    @Override
    public void onResume() {
        if (visible) {
            onShow();
            refreshEpg();
        }
    }

    @Override
    public boolean onBack() {
        if (grid.hasFocus()) {
            focusSelectedCategory();
            return true;
        }
        return false;
    }

    @Override
    public void onEpgChanged() {
        refreshEpg();
    }

    @Override
    public void onLibraryChanged(String what) {
        if ("logos".equals(what)) tileAdapter.notifyItemRangeChanged(0, shown.size());
    }
}
