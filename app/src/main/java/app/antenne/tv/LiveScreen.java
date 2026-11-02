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
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Live-TV: links Kategorien (Favoriten ganz oben), in der Mitte Sender mit Logo und laufender
 * Sendung, rechts eine Vorschau mit Jetzt/Danach. OK lang drücken = Favorit.
 */
public class LiveScreen extends Screen implements Epg.Listener {

    private RecyclerView catList, chList;
    private CatAdapter catAdapter;
    private ChAdapter chAdapter;
    private final List<String[]> cats = new ArrayList<>(); // {key, name, count}
    private String selectedCat;
    private List<Model.Channel> shown = new ArrayList<>();
    private Model.Channel focused;
    private TextView listTitle, listCount, empty;

    // Vorschau
    private FrameLayout previewBox;
    private PlayerView playerView;
    private ExoPlayer player;
    private ImageView previewLogo;
    private TextView previewHint;
    private ImageView infoLogo;
    private TextView infoName, infoNowTitle, infoTime, infoDesc, infoNext, infoBadge;
    private Ui.Bar infoBar;
    private boolean visible;
    private String previewKey;

    private String pendingCat;
    private final Runnable startPreview = this::doStartPreview;
    private final Runnable selectCat = () -> {
        if (pendingCat != null) selectCategory(pendingCat, false);
    };
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!visible) return;
            if (chAdapter != null) chAdapter.notifyItemRangeChanged(0, shown.size(), "epg");
            updateInfo();
            Bg.later(this, 30_000);
        }
    };

    public LiveScreen(MainActivity a) {
        super(a);
    }

    @Override
    protected View build() {
        Context c = a;
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(Ui.dp(c, 30), Ui.dp(c, 84), Ui.dp(c, 32), 0);
        row.setClipChildren(false);
        row.setClipToPadding(false);

        // ---- Kategorien ----
        LinearLayout catCol = new LinearLayout(c);
        catCol.setOrientation(LinearLayout.VERTICAL);
        catCol.setClipChildren(false);
        TextView ch = Ui.text(c, 13, Ui.TEXT3, Ui.bold(c));
        ch.setText("KATEGORIEN");
        ch.setLetterSpacing(0.12f);
        catCol.addView(ch, Ui.margins(Ui.lin(Ui.WRAP, Ui.WRAP), Ui.dp(c, 16), 0, 0, Ui.dp(c, 10)));
        catList = new RecyclerView(c);
        catList.setLayoutManager(new LinearLayoutManager(c));
        catList.setClipToPadding(false);
        catList.setClipChildren(false);
        catList.setPadding(Ui.dp(c, 6), Ui.dp(c, 4), Ui.dp(c, 6), Ui.dp(c, 40));
        catList.setItemAnimator(null);
        catAdapter = new CatAdapter();
        catList.setAdapter(catAdapter);
        catCol.addView(catList, Ui.lin(Ui.MATCH, Ui.MATCH));
        row.addView(catCol, Ui.lin(Ui.dp(c, 196), Ui.MATCH));

        // ---- Sender ----
        LinearLayout chCol = new LinearLayout(c);
        chCol.setOrientation(LinearLayout.VERTICAL);
        chCol.setClipChildren(false);
        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.BOTTOM);
        listTitle = Ui.lines(Ui.text(c, 26, Ui.TEXT, Ui.bold(c)), 1);
        head.addView(listTitle, Ui.lin(0, Ui.WRAP, 1f));
        listCount = Ui.text(c, 14, Ui.TEXT3, Ui.semibold(c));
        head.addView(listCount);
        chCol.addView(head, Ui.margins(Ui.lin(Ui.MATCH, Ui.WRAP), Ui.dp(c, 14), 0, Ui.dp(c, 14), Ui.dp(c, 8)));
        FrameLayout listBox = new FrameLayout(c);
        listBox.setClipChildren(false);
        chList = new RecyclerView(c);
        chList.setLayoutManager(new LinearLayoutManager(c));
        chList.setClipToPadding(false);
        chList.setClipChildren(false);
        chList.setPadding(Ui.dp(c, 8), Ui.dp(c, 6), Ui.dp(c, 8), Ui.dp(c, 60));
        chList.setItemAnimator(null);
        chAdapter = new ChAdapter();
        chList.setAdapter(chAdapter);
        listBox.addView(chList, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        empty = Ui.text(c, 18, Ui.TEXT2, Ui.medium(c));
        empty.setGravity(Gravity.CENTER);
        empty.setVisibility(View.GONE);
        listBox.addView(empty, Ui.frame(Ui.dp(c, 380), Ui.WRAP, Gravity.CENTER));
        chCol.addView(listBox, Ui.lin(Ui.MATCH, Ui.MATCH));
        row.addView(chCol, Ui.margins(Ui.lin(0, Ui.MATCH, 1f), Ui.dp(c, 8), 0, Ui.dp(c, 14), 0));

        // ---- Vorschau & Info ----
        LinearLayout info = new LinearLayout(c);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(0, Ui.dp(c, 26), 0, 0);
        previewBox = new FrameLayout(c);
        previewBox.setBackground(Ui.round(0xFF000000, Ui.dp(c, 18)));
        Ui.clipRound(previewBox, Ui.dp(c, 18));
        playerView = new PlayerView(c);
        playerView.setUseController(false);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        playerView.setShutterBackgroundColor(0xFF000000);
        playerView.setKeepContentOnPlayerReset(false);
        previewBox.addView(playerView, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        previewLogo = new ImageView(c);
        previewBox.addView(previewLogo, Ui.frame(Ui.dp(c, 120), Ui.dp(c, 72), Gravity.CENTER));
        previewHint = Ui.text(c, 12, Ui.TEXT2, Ui.semibold(c));
        previewHint.setText("Vorschau …");
        FrameLayout.LayoutParams hl = Ui.frame(Ui.WRAP, Ui.WRAP, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        hl.bottomMargin = Ui.dp(c, 12);
        previewBox.addView(previewHint, hl);
        infoBadge = Ui.badge(c, "LIVE", Ui.LIVE, 0xFFFFFFFF);
        FrameLayout.LayoutParams bl = Ui.frame(Ui.WRAP, Ui.WRAP, Gravity.TOP | Gravity.START);
        bl.setMargins(Ui.dp(c, 12), Ui.dp(c, 12), 0, 0);
        previewBox.addView(infoBadge, bl);
        info.addView(previewBox, Ui.lin(Ui.dp(c, 300), Ui.dp(c, 169)));

        LinearLayout nameRow = new LinearLayout(c);
        nameRow.setOrientation(LinearLayout.HORIZONTAL);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout logoTile = new FrameLayout(c);
        logoTile.setBackground(Ui.round(Ui.GLASS, Ui.dp(c, 10)));
        logoTile.setPadding(Ui.dp(c, 6), Ui.dp(c, 4), Ui.dp(c, 6), Ui.dp(c, 4));
        infoLogo = new ImageView(c);
        logoTile.addView(infoLogo, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        nameRow.addView(logoTile, Ui.lin(Ui.dp(c, 60), Ui.dp(c, 38)));
        infoName = Ui.lines(Ui.text(c, 20, Ui.TEXT, Ui.bold(c)), 1);
        nameRow.addView(infoName, Ui.margins(Ui.lin(0, Ui.WRAP, 1f), Ui.dp(c, 12), 0, 0, 0));
        info.addView(nameRow, Ui.margins(Ui.lin(Ui.dp(c, 300), Ui.WRAP), 0, Ui.dp(c, 18), 0, 0));

        TextView nowLbl = Ui.text(c, 12, Ui.ACCENT, Ui.bold(c));
        nowLbl.setText("JETZT");
        nowLbl.setLetterSpacing(0.12f);
        info.addView(nowLbl, Ui.margins(Ui.lin(Ui.WRAP, Ui.WRAP), 0, Ui.dp(c, 18), 0, 0));
        infoNowTitle = Ui.lines(Ui.text(c, 19, Ui.TEXT, Ui.semibold(c)), 2);
        info.addView(infoNowTitle, Ui.margins(Ui.lin(Ui.dp(c, 300), Ui.WRAP), 0, Ui.dp(c, 6), 0, 0));
        LinearLayout timeRow = new LinearLayout(c);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        timeRow.setGravity(Gravity.CENTER_VERTICAL);
        infoTime = Ui.text(c, 14, Ui.TEXT2, Ui.medium(c));
        timeRow.addView(infoTime);
        infoBar = new Ui.Bar(c, 0x33FFFFFF, Ui.TEXT);
        timeRow.addView(infoBar, Ui.margins(Ui.lin(0, Ui.dp(c, 4), 1f), Ui.dp(c, 12), 0, 0, 0));
        info.addView(timeRow, Ui.margins(Ui.lin(Ui.dp(c, 300), Ui.WRAP), 0, Ui.dp(c, 10), 0, 0));
        infoDesc = Ui.lines(Ui.text(c, 14, Ui.TEXT2, Ui.regular(c)), 3);
        infoDesc.setLineSpacing(Ui.dp(c, 3), 1f);
        info.addView(infoDesc, Ui.margins(Ui.lin(Ui.dp(c, 300), Ui.WRAP), 0, Ui.dp(c, 14), 0, 0));
        infoNext = Ui.lines(Ui.text(c, 14, Ui.TEXT3, Ui.medium(c)), 2);
        info.addView(infoNext, Ui.margins(Ui.lin(Ui.dp(c, 300), Ui.WRAP), 0, Ui.dp(c, 14), 0, 0));

        TextView hint = Ui.text(c, 12, Ui.TEXT3, Ui.medium(c));
        hint.setText("OK lang drücken: ★ Favorit");
        info.addView(hint, Ui.margins(Ui.lin(Ui.WRAP, Ui.WRAP), 0, Ui.dp(c, 12), 0, 0));
        row.addView(info, Ui.lin(Ui.dp(c, 300), Ui.MATCH));

        rebuildCategories();
        String first = Store.favorites().isEmpty() ? "all" : "fav";
        selectCategory(first, false);
        return row;
    }

    // ---------- Kategorien ----------

    private void rebuildCategories() {
        cats.clear();
        Library lib = Library.get();
        int favs = lib.channelsFor("fav").size();
        cats.add(new String[]{"fav", "★  Favoriten", String.valueOf(favs)});
        int rec = lib.channelsFor("recent").size();
        if (rec > 0) cats.add(new String[]{"recent", "Zuletzt gesehen", String.valueOf(rec)});
        cats.add(new String[]{"all", "Alle Sender", String.valueOf(lib.channels.size())});
        for (Model.Category cat : lib.liveCats) {
            cats.add(new String[]{"cat:" + cat.id, cat.name, String.valueOf(cat.items.size())});
        }
        if (catAdapter != null) catAdapter.notifyDataSetChanged();
    }

    private void selectCategory(String key, boolean focusList) {
        selectedCat = key;
        shown = Library.get().channelsFor(key);
        listTitle.setText(Library.get().categoryName(key));
        listCount.setText(shown.size() + (shown.size() == 1 ? " Sender" : " Sender"));
        catAdapter.notifyDataSetChanged();
        chAdapter.notifyDataSetChanged();
        chList.scrollToPosition(0);
        if (shown.isEmpty()) {
            empty.setVisibility(View.VISIBLE);
            empty.setText("fav".equals(key)
                    ? "Noch keine Favoriten.\nHalte bei einem Sender OK gedrückt, um ihn mit ★ zu markieren."
                    : "Keine Sender in dieser Kategorie.");
        } else {
            empty.setVisibility(View.GONE);
        }
        if (focused == null && !shown.isEmpty()) {
            focused = shown.get(0);
            updateInfo();
        }
        if (focusList && !shown.isEmpty()) chList.post(() -> focusChannelAt(0));
    }

    private void focusChannelAt(int pos) {
        RecyclerView.ViewHolder vh = chList.findViewHolderForAdapterPosition(pos);
        if (vh != null) vh.itemView.requestFocus();
        else {
            chList.scrollToPosition(pos);
            chList.post(() -> {
                RecyclerView.ViewHolder v2 = chList.findViewHolderForAdapterPosition(pos);
                if (v2 != null) v2.itemView.requestFocus();
            });
        }
    }

    private void focusSelectedCategory() {
        int idx = 0;
        for (int i = 0; i < cats.size(); i++) if (cats.get(i)[0].equals(selectedCat)) idx = i;
        int fi = idx;
        RecyclerView.ViewHolder vh = catList.findViewHolderForAdapterPosition(fi);
        if (vh != null) vh.itemView.requestFocus();
        else {
            catList.scrollToPosition(fi);
            catList.post(() -> {
                RecyclerView.ViewHolder v2 = catList.findViewHolderForAdapterPosition(fi);
                if (v2 != null) v2.itemView.requestFocus();
            });
        }
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
            v.setPadding(Ui.dp(c, 16), Ui.dp(c, 11), Ui.dp(c, 14), Ui.dp(c, 11));
            v.setFocusable(true);
            v.setClickable(true);
            TextView name = Ui.lines(Ui.text(c, 16, Ui.TEXT2, Ui.semibold(c)), 1);
            v.addView(name, Ui.lin(0, Ui.WRAP, 1f));
            TextView count = Ui.text(c, 13, Ui.TEXT3, Ui.semibold(c));
            v.addView(count, Ui.margins(Ui.lin(Ui.WRAP, Ui.WRAP), Ui.dp(c, 8), 0, 0, 0));
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(Ui.MATCH, Ui.WRAP);
            lp.bottomMargin = Ui.dp(c, 4);
            v.setLayoutParams(lp);
            VH vh = new VH(v, name, count);
            v.setOnFocusChangeListener((view, has) -> {
                style(vh, has);
                int pos = vh.getBindingAdapterPosition();
                if (has && pos >= 0 && pos < cats.size()) {
                    pendingCat = cats.get(pos)[0];
                    Bg.cancel(selectCat);
                    if (!pendingCat.equals(selectedCat)) Bg.later(selectCat, 220);
                }
            });
            v.setOnClickListener(view -> {
                int pos = vh.getBindingAdapterPosition();
                if (pos < 0) return;
                Bg.cancel(selectCat);
                String k = cats.get(pos)[0];
                if (!k.equals(selectedCat)) selectCategory(k, true);
                else if (!shown.isEmpty()) focusChannelAt(0);
            });
            v.setOnKeyListener((view, keyCode, e) -> {
                if (e.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    Bg.cancel(selectCat);
                    int pos = vh.getBindingAdapterPosition();
                    if (pos >= 0 && !cats.get(pos)[0].equals(selectedCat)) selectCategory(cats.get(pos)[0], true);
                    else if (!shown.isEmpty()) focusChannelAt(Math.max(0, indexOf(focused)));
                    return true;
                }
                return false;
            });
            return vh;
        }

        private void style(VH vh, boolean focused) {
            Context c = vh.itemView.getContext();
            int pos = vh.getBindingAdapterPosition();
            boolean sel = pos >= 0 && pos < cats.size() && cats.get(pos)[0].equals(selectedCat);
            float r = Ui.dp(c, 12);
            if (focused) {
                vh.itemView.setBackground(Ui.round(0xFFFFFFFF, r));
                vh.name.setTextColor(0xFF111111);
                vh.count.setTextColor(0x99111111);
                vh.itemView.animate().scaleX(1.04f).scaleY(1.04f).translationZ(Ui.dp(c, 8)).setDuration(130).start();
            } else {
                vh.itemView.setBackground(sel ? Ui.round(Ui.GLASS_HI, r) : null);
                vh.name.setTextColor(sel ? Ui.TEXT : Ui.TEXT2);
                vh.count.setTextColor(Ui.TEXT3);
                vh.itemView.animate().scaleX(1f).scaleY(1f).translationZ(0).setDuration(130).start();
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

    private int indexOf(Model.Channel c) {
        if (c == null) return -1;
        for (int i = 0; i < shown.size(); i++) if (shown.get(i) == c) return i;
        return -1;
    }

    // ---------- Sender ----------

    private final class ChAdapter extends RecyclerView.Adapter<ChAdapter.VH> {
        final class VH extends RecyclerView.ViewHolder {
            final LinearLayout root;
            final TextView num, name, now, time, star;
            final ImageView logo;
            final FrameLayout logoTile;
            final Ui.Bar bar;
            Model.Channel ch;

            VH(LinearLayout root, TextView num, FrameLayout logoTile, ImageView logo, TextView name,
               TextView star, TextView now, TextView time, Ui.Bar bar) {
                super(root);
                this.root = root;
                this.num = num;
                this.logoTile = logoTile;
                this.logo = logo;
                this.name = name;
                this.star = star;
                this.now = now;
                this.time = time;
                this.bar = bar;
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context c = parent.getContext();
            LinearLayout v = new LinearLayout(c);
            v.setOrientation(LinearLayout.HORIZONTAL);
            v.setGravity(Gravity.CENTER_VERTICAL);
            v.setPadding(Ui.dp(c, 8), Ui.dp(c, 9), Ui.dp(c, 16), Ui.dp(c, 9));
            v.setFocusable(true);
            v.setClickable(true);
            v.setLongClickable(true);
            TextView num = Ui.text(c, 14, Ui.TEXT3, Ui.semibold(c));
            num.setGravity(Gravity.END);
            v.addView(num, Ui.lin(Ui.dp(c, 28), Ui.WRAP));
            FrameLayout tile = new FrameLayout(c);
            tile.setPadding(Ui.dp(c, 8), Ui.dp(c, 6), Ui.dp(c, 8), Ui.dp(c, 6));
            tile.setBackground(Ui.round(Ui.GLASS, Ui.dp(c, 12)));
            ImageView logo = new ImageView(c);
            tile.addView(logo, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
            v.addView(tile, Ui.margins(Ui.lin(Ui.dp(c, 84), Ui.dp(c, 52)), Ui.dp(c, 10), 0, Ui.dp(c, 14), 0));

            LinearLayout text = new LinearLayout(c);
            text.setOrientation(LinearLayout.VERTICAL);
            LinearLayout top = new LinearLayout(c);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = Ui.lines(Ui.text(c, 17, Ui.TEXT, Ui.semibold(c)), 1);
            top.addView(name, Ui.lin(0, Ui.WRAP, 1f));
            TextView star = Ui.text(c, 18, Ui.FAV, Ui.bold(c));
            star.setText("★");
            top.addView(star, Ui.margins(Ui.lin(Ui.WRAP, Ui.WRAP), Ui.dp(c, 8), 0, 0, 0));
            text.addView(top, Ui.lin(Ui.MATCH, Ui.WRAP));
            TextView now = Ui.lines(Ui.text(c, 14, Ui.TEXT2, Ui.medium(c)), 1);
            text.addView(now, Ui.margins(Ui.lin(Ui.MATCH, Ui.WRAP), 0, Ui.dp(c, 5), 0, 0));
            LinearLayout br = new LinearLayout(c);
            br.setOrientation(LinearLayout.HORIZONTAL);
            br.setGravity(Gravity.CENTER_VERTICAL);
            Ui.Bar bar = new Ui.Bar(c, 0x26FFFFFF, 0xCCFFFFFF);
            br.addView(bar, Ui.lin(0, Ui.dp(c, 3), 1f));
            TextView time = Ui.text(c, 12, Ui.TEXT3, Ui.semibold(c));
            br.addView(time, Ui.margins(Ui.lin(Ui.WRAP, Ui.WRAP), Ui.dp(c, 10), 0, 0, 0));
            text.addView(br, Ui.margins(Ui.lin(Ui.MATCH, Ui.WRAP), 0, Ui.dp(c, 8), 0, 0));
            v.addView(text, Ui.lin(0, Ui.WRAP, 1f));

            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(Ui.MATCH, Ui.WRAP);
            lp.bottomMargin = Ui.dp(c, 6);
            v.setLayoutParams(lp);
            VH vh = new VH(v, num, tile, logo, name, star, now, time, bar);

            v.setOnFocusChangeListener((view, has) -> {
                style(vh, has);
                if (has && vh.ch != null) {
                    focused = vh.ch;
                    updateInfo();
                    schedulePreview();
                }
            });
            v.setOnClickListener(view -> {
                if (vh.ch != null) {
                    stopPreview();
                    a.playChannel(vh.ch, selectedCat);
                }
            });
            v.setOnLongClickListener(view -> {
                if (vh.ch == null) return true;
                boolean fav = Store.toggleFavorite(vh.ch.key());
                Ui.toast(a, fav ? "★  " + vh.ch.name + " zu Favoriten hinzugefügt" : vh.ch.name + " aus Favoriten entfernt");
                vh.star.setVisibility(fav ? View.VISIBLE : View.GONE);
                rebuildCategories();
                if ("fav".equals(selectedCat) && !fav) {
                    int pos = vh.getBindingAdapterPosition();
                    selectCategory("fav", false);
                    if (!shown.isEmpty()) focusChannelAt(Math.min(pos, shown.size() - 1));
                    else focusSelectedCategory();
                }
                return true;
            });
            v.setOnKeyListener((view, keyCode, e) -> {
                if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    focusSelectedCategory();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) return true;
                if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_BOOKMARK) {
                    view.performLongClick();
                    return true;
                }
                return false;
            });
            return vh;
        }

        private void style(VH vh, boolean has) {
            Context c = vh.itemView.getContext();
            float r = Ui.dp(c, 18);
            if (has) {
                vh.root.setBackground(Ui.round(0xFFFFFFFF, r));
                vh.name.setTextColor(0xFF111111);
                vh.now.setTextColor(0xB3111111);
                vh.num.setTextColor(0x80111111);
                vh.time.setTextColor(0x80111111);
                vh.bar.setColors(0x1F000000, 0xFF111111);
                vh.logoTile.setBackground(Ui.round(0x14000000, Ui.dp(c, 12)));
                vh.root.animate().scaleX(1.03f).scaleY(1.03f).translationZ(Ui.dp(c, 12)).setDuration(140).start();
            } else {
                vh.root.setBackground(null);
                vh.name.setTextColor(Ui.TEXT);
                vh.now.setTextColor(Ui.TEXT2);
                vh.num.setTextColor(Ui.TEXT3);
                vh.time.setTextColor(Ui.TEXT3);
                vh.bar.setColors(0x26FFFFFF, 0xCCFFFFFF);
                vh.logoTile.setBackground(Ui.round(Ui.GLASS, Ui.dp(c, 12)));
                vh.root.animate().scaleX(1f).scaleY(1f).translationZ(0).setDuration(140).start();
            }
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position, @NonNull List<Object> payloads) {
            if (!payloads.isEmpty() && h.ch == shown.get(position)) {
                bindEpg(h);
                return;
            }
            onBindViewHolder(h, position);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Model.Channel c = shown.get(position);
            h.ch = c;
            h.num.setText(String.valueOf(c.num > 0 && "all".equals(selectedCat) ? c.num : position + 1));
            h.name.setText(c.name);
            h.star.setVisibility(Store.isFavorite(c.key()) ? View.VISIBLE : View.GONE);
            Img.logo(h.logo, c.logo, c.name);
            bindEpg(h);
            style(h, h.itemView.hasFocus());
        }

        private void bindEpg(VH h) {
            Model.Programme p = Epg.now(h.ch);
            long now = System.currentTimeMillis();
            if (p != null) {
                h.now.setText(p.title);
                h.time.setText(Ui.time(p.start) + " – " + Ui.time(p.end));
                h.bar.setProgress(p.progress(now));
                h.bar.setVisibility(View.VISIBLE);
                h.time.setVisibility(View.VISIBLE);
            } else {
                h.now.setText(Epg.isLoading(h.ch) || Epg.get(h.ch) == null ? "Programm wird geladen …" : "Keine Programminfos");
                h.bar.setVisibility(View.INVISIBLE);
                h.time.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() {
            return shown.size();
        }
    }

    // ---------- Info-Bereich ----------

    private void updateInfo() {
        Model.Channel c = focused;
        if (c == null || infoName == null) return;
        infoName.setText(c.name);
        Img.logo(infoLogo, c.logo, c.name);
        if (!c.key().equals(previewKey)) {
            Img.logo(previewLogo, c.logo, c.name);
            previewLogo.setVisibility(View.VISIBLE);
        }
        Model.Programme p = Epg.now(c);
        long now = System.currentTimeMillis();
        if (p != null) {
            infoNowTitle.setText(p.title);
            long left = p.end - now;
            infoTime.setText(Ui.time(p.start) + " – " + Ui.time(p.end) + "  ·  noch " + Ui.minutes(left));
            infoBar.setProgress(p.progress(now));
            infoBar.setVisibility(View.VISIBLE);
            infoDesc.setText(p.desc);
            infoDesc.setVisibility(p.desc.isEmpty() ? View.GONE : View.VISIBLE);
        } else {
            infoNowTitle.setText(Epg.get(c) == null ? "Programm wird geladen …" : "Keine Programminfos verfügbar");
            infoTime.setText("");
            infoBar.setVisibility(View.INVISIBLE);
            infoDesc.setVisibility(View.GONE);
        }
        Model.Programme n = Epg.next(c);
        infoNext.setText(n == null ? "" : "Danach  " + Ui.time(n.start) + "   " + n.title);
    }

    // ---------- Vorschau ----------

    private void schedulePreview() {
        Bg.cancel(startPreview);
        if (!Prefs.preview()) return;
        Bg.later(startPreview, 900);
    }

    private void doStartPreview() {
        if (!visible || focused == null || !Prefs.preview()) return;
        if (focused.key().equals(previewKey) && player != null) return;
        String url = Library.get().streamUrl(focused);
        if (url == null) return;
        if (player == null) {
            player = Players.create(a, true);
            player.addListener(new Player.Listener() {
                @Override
                public void onRenderedFirstFrame() {
                    previewLogo.animate().alpha(0f).setDuration(250).start();
                    previewHint.setVisibility(View.GONE);
                }

                @Override
                public void onPlayerError(@NonNull PlaybackException error) {
                    previewHint.setText("Vorschau nicht verfügbar");
                    previewHint.setVisibility(View.VISIBLE);
                    previewLogo.setAlpha(1f);
                }
            });
            playerView.setPlayer(player);
        }
        previewKey = focused.key();
        previewLogo.setAlpha(1f);
        previewHint.setText("Vorschau …");
        previewHint.setVisibility(View.VISIBLE);
        player.setMediaItem(Players.item(url));
        player.prepare();
        player.setPlayWhenReady(true);
    }

    private void stopPreview() {
        Bg.cancel(startPreview);
        previewKey = null;
        if (player != null) {
            playerView.setPlayer(null);
            player.release();
            player = null;
        }
        if (previewLogo != null) previewLogo.setAlpha(1f);
        if (previewHint != null) previewHint.setVisibility(View.GONE);
    }

    // ---------- Lebenszyklus ----------

    @Override
    public void focusFirst() {
        if (shown.isEmpty()) {
            focusSelectedCategory();
            return;
        }
        int i = indexOf(focused);
        focusChannelAt(Math.max(0, i));
    }

    @Override
    public void onShow() {
        visible = true;
        Epg.addListener(this);
        // Favoriten/Verlauf können sich im Player geändert haben
        rebuildCategories();
        if ("fav".equals(selectedCat) || "recent".equals(selectedCat)) {
            List<Model.Channel> now = Library.get().channelsFor(selectedCat);
            if (now.size() != shown.size()) selectCategory(selectedCat, false);
            else chAdapter.notifyDataSetChanged();
        } else {
            chAdapter.notifyDataSetChanged();
        }
        Bg.cancel(tick);
        Bg.later(tick, 30_000);
        updateInfo();
    }

    @Override
    public void onHide() {
        visible = false;
        Epg.removeListener(this);
        Bg.cancel(tick);
        stopPreview();
    }

    @Override
    public void onPause() {
        stopPreview();
    }

    @Override
    public void onResume() {
        if (visible) {
            onShow();
            if (focused != null && chList.hasFocus()) schedulePreview();
        }
    }

    @Override
    public boolean onBack() {
        if (chList.hasFocus()) {
            focusSelectedCategory();
            return true;
        }
        return false;
    }

    @Override
    public void onEpgChanged() {
        if (!visible) return;
        chAdapter.notifyItemRangeChanged(0, shown.size(), "epg");
        updateInfo();
    }

    @Override
    public void onLibraryChanged(String what) {
        if ("logos".equals(what)) {
            chAdapter.notifyItemRangeChanged(0, shown.size());
            updateInfo();
        }
    }
}
