package app.antenne.tv;

import android.content.Context;
import android.view.Gravity;
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

/** Poster-Reihen (Filme, Serien, Sender, Weiterschauen) im tvOS-Stil. */
public final class Posters {

    private Posters() {}

    public interface Callbacks {
        void onFocus(Object item, View card);

        void onClick(Object item);
    }

    /** Eine Reihe: Titel + Elemente. */
    public static final class Row {
        public final String title;
        public final List<Object> items;

        public Row(String title, List<?> items) {
            this.title = title;
            this.items = new ArrayList<>(items);
        }
    }

    public static String title(Object o) {
        if (o instanceof Model.Movie) return ((Model.Movie) o).name;
        if (o instanceof Model.Series) return ((Model.Series) o).name;
        if (o instanceof Model.Channel) return ((Model.Channel) o).name;
        if (o instanceof Store.Resume) return ((Store.Resume) o).title;
        return "";
    }

    public static String image(Object o) {
        if (o instanceof Model.Movie) return ((Model.Movie) o).poster;
        if (o instanceof Model.Series) return ((Model.Series) o).poster;
        if (o instanceof Model.Channel) return ((Model.Channel) o).logo;
        if (o instanceof Store.Resume) return ((Store.Resume) o).image;
        return null;
    }

    /** Horizontale Reihe mit Postern. */
    public static final class RowAdapter extends RecyclerView.Adapter<RowAdapter.VH> {
        private final List<Object> items;
        private final Callbacks cb;

        public RowAdapter(List<Object> items, Callbacks cb) {
            this.items = items;
            this.cb = cb;
        }

        static final class VH extends RecyclerView.ViewHolder {
            final FrameLayout card;
            final ImageView img;
            final TextView fallback, label, badge;
            final Ui.Bar bar;
            Object item;

            VH(LinearLayout v, FrameLayout card, ImageView img, TextView fallback, TextView label, TextView badge, Ui.Bar bar) {
                super(v);
                this.card = card;
                this.img = img;
                this.fallback = fallback;
                this.label = label;
                this.badge = badge;
                this.bar = bar;
            }
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position) instanceof Model.Channel ? 1 : 0;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context c = parent.getContext();
            boolean channel = viewType == 1;
            int w = channel ? Ui.dp(c, 168) : Ui.dp(c, 116);
            int h = channel ? Ui.dp(c, 100) : Ui.dp(c, 174);
            LinearLayout v = new LinearLayout(c);
            v.setOrientation(LinearLayout.VERTICAL);
            v.setClipChildren(false);
            v.setClipToPadding(false);
            FrameLayout card = new FrameLayout(c);
            float rad = Ui.dp(c, 14);
            card.setBackground(Ui.round(channel ? Ui.TILE : Ui.GLASS, rad));
            Ui.clipRound(card, rad);
            TextView fb = Ui.text(c, 15, Ui.TEXT2, Ui.semibold(c));
            fb.setGravity(Gravity.CENTER);
            fb.setPadding(Ui.dp(c, 10), 0, Ui.dp(c, 10), 0);
            Ui.lines(fb, 4);
            card.addView(fb, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
            ImageView img = new ImageView(c);
            FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH);
            if (channel) ilp.setMargins(Ui.dp(c, 18), Ui.dp(c, 14), Ui.dp(c, 18), Ui.dp(c, 14));
            card.addView(img, ilp);
            TextView badge = Ui.badge(c, "", 0xB3000000, 0xFFFFFFFF);
            badge.setTextSize(11);
            FrameLayout.LayoutParams blp = Ui.frame(Ui.WRAP, Ui.WRAP, Gravity.TOP | Gravity.END);
            blp.setMargins(0, Ui.dp(c, 6), Ui.dp(c, 6), 0);
            card.addView(badge, blp);
            Ui.Bar bar = new Ui.Bar(c, 0x66000000, 0xFFFFFFFF);
            FrameLayout.LayoutParams barLp = Ui.frame(Ui.MATCH, Ui.dp(c, 4), Gravity.BOTTOM);
            barLp.setMargins(Ui.dp(c, 10), 0, Ui.dp(c, 10), Ui.dp(c, 8));
            card.addView(bar, barLp);
            v.addView(card, Ui.lin(w, h));
            TextView label = Ui.lines(Ui.text(c, 13, Ui.TEXT2, Ui.medium(c)), 1);
            v.addView(label, Ui.margins(Ui.lin(w, Ui.WRAP), Ui.dp(c, 2), Ui.dp(c, 9), 0, 0));
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(Ui.WRAP, Ui.WRAP);
            lp.setMargins(0, 0, Ui.dp(c, 20), 0);
            v.setLayoutParams(lp);
            v.setFocusable(true);
            v.setClickable(true);
            VH vh = new VH(v, card, img, fb, label, badge, bar);
            float r = rad;
            android.graphics.drawable.Drawable ringD = Ui.ring(Ui.RING, Ui.dp(c, 3), r);
            v.setOnFocusChangeListener((view, has) -> {
                card.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f)
                        .translationZ(has ? Ui.dp(c, 12) : 0).setDuration(150).start();
                label.animate().translationY(has ? Ui.dp(c, 9) : 0).setDuration(150).start();
                label.setTextColor(has ? Ui.TEXT : Ui.TEXT2);
                if (android.os.Build.VERSION.SDK_INT >= 23) card.setForeground(has ? ringD : null);
                if (has && cb != null && vh.item != null) cb.onFocus(vh.item, view);
            });
            v.setOnClickListener(view -> {
                if (cb != null && vh.item != null) cb.onClick(vh.item);
            });
            return vh;
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Object o = items.get(position);
            h.item = o;
            String t = title(o);
            h.label.setText(t);
            h.fallback.setText(t);
            String img = image(o);
            if (o instanceof Model.Channel) {
                Img.logo(h.img, img, t);
                h.fallback.setVisibility(View.GONE);
            } else {
                Img.poster(h.img, img);
                h.fallback.setVisibility(View.VISIBLE);
            }
            String badge = null;
            if (o instanceof Model.Movie) {
                String rt = ((Model.Movie) o).rating;
                if (rt != null && !rt.isEmpty() && !rt.equals("0") && !rt.startsWith("0.")) {
                    try {
                        double d = Double.parseDouble(rt);
                        if (d > 0) badge = "★ " + String.format(java.util.Locale.GERMANY, "%.1f", d > 10 ? d / 10 : d);
                    } catch (Exception ignored) {
                    }
                }
            }
            h.badge.setText(badge == null ? "" : badge);
            h.badge.setVisibility(badge == null ? View.GONE : View.VISIBLE);
            if (o instanceof Store.Resume) {
                h.bar.setVisibility(View.VISIBLE);
                h.bar.setProgress(((Store.Resume) o).progress());
                String st = ((Store.Resume) o).subtitle;
                if (st != null && !st.isEmpty()) h.label.setText(st);
            } else {
                h.bar.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    /** Vertikale Liste aus Reihen; die fokussierte Reihe rutscht sanft nach oben. */
    public static final class RowsAdapter extends RecyclerView.Adapter<RowsAdapter.VH> {
        private final List<Row> rows = new ArrayList<>();
        private final Callbacks cb;
        private RecyclerView outer;

        public RowsAdapter(Callbacks cb) {
            this.cb = cb;
        }

        public void setRows(List<Row> r) {
            rows.clear();
            rows.addAll(r);
            notifyDataSetChanged();
        }

        public List<Row> rows() {
            return rows;
        }

        @Override
        public void onAttachedToRecyclerView(@NonNull RecyclerView rv) {
            outer = rv;
        }

        static final class VH extends RecyclerView.ViewHolder {
            final TextView title;
            final TvList list;

            VH(LinearLayout v, TextView title, TvList list) {
                super(v);
                this.title = title;
                this.list = list;
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context c = parent.getContext();
            LinearLayout v = new LinearLayout(c);
            v.setOrientation(LinearLayout.VERTICAL);
            v.setClipChildren(false);
            v.setClipToPadding(false);
            TextView title = Ui.lines(Ui.text(c, 19, Ui.TEXT, Ui.bold(c)), 1);
            v.addView(title, Ui.margins(Ui.lin(Ui.MATCH, Ui.WRAP), Ui.dp(c, 4), 0, 0, Ui.dp(c, 10)));
            TvList list = new TvList(c, true, 0f);
            list.setPadding(Ui.dp(c, 10), Ui.dp(c, 14), Ui.dp(c, 60), Ui.dp(c, 16));
            v.addView(list, Ui.lin(Ui.MATCH, Ui.WRAP));
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(Ui.MATCH, Ui.WRAP);
            lp.bottomMargin = Ui.dp(c, 10);
            v.setLayoutParams(lp);
            VH vh = new VH(v, title, list);
            Callbacks wrapped = new Callbacks() {
                @Override
                public void onFocus(Object item, View card) {
                    if (cb != null) cb.onFocus(item, card);
                }

                @Override
                public void onClick(Object item) {
                    if (cb != null) cb.onClick(item);
                }
            };
            vh.list.setTag(wrapped);
            return vh;
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Row r = rows.get(position);
            h.title.setText(r.title);
            h.list.setAdapter(new RowAdapter(r.items, (Callbacks) h.list.getTag()));
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
    }

}
