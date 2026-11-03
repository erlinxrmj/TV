package app.antenne.tv;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Suche über Sender, Filme und Serien gleichzeitig. */
public class SearchScreen extends Screen implements Posters.Callbacks {

    private EditText field;
    private TvList rows;
    private Posters.RowsAdapter adapter;
    private TextView hint;
    private int searchGen;
    private final Runnable doSearch = this::search;

    public SearchScreen(MainActivity a) {
        super(a);
    }

    @Override
    protected View build() {
        Context c = a;
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dp(c, 44), Ui.dp(c, 84), 0, 0);

        field = new EditText(c);
        field.setHint("Sender, Filme oder Serien suchen");
        field.setHintTextColor(Ui.TEXT3);
        field.setTextColor(Ui.TEXT);
        field.setTypeface(Ui.semibold(c));
        field.setTextSize(22);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        field.setPadding(Ui.dp(c, 24), Ui.dp(c, 14), Ui.dp(c, 24), Ui.dp(c, 14));
        float r = Ui.dp(c, 100);
        field.setBackground(Ui.round(Ui.GLASS, r));
        field.setOnFocusChangeListener((v, has) -> {
            GradientDrawable g = Ui.round(has ? Ui.GLASS_HI : Ui.GLASS, r);
            if (has) g.setStroke(Ui.dp(c, 2), Ui.TEXT);
            v.setBackground(g);
        });
        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int co, int af) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int be, int co) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                Bg.cancel(doSearch);
                Bg.later(doSearch, 350);
            }
        });
        field.setOnEditorActionListener((v, actionId, e) -> {
            Bg.cancel(doSearch);
            search();
            hideKeyboard();
            rows.postDelayed(this::focusResults, 150);
            return true;
        });
        field.setOnKeyListener((v, keyCode, e) -> {
            if (e.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN && adapter.getItemCount() > 0) {
                hideKeyboard();
                focusResults();
                return true;
            }
            return false;
        });
        col.addView(field, Ui.margins(Ui.lin(Ui.dp(c, 620), Ui.WRAP), 0, 0, 0, Ui.dp(c, 18)));

        FrameLayout box = new FrameLayout(c);
        rows = new TvList(c, false, 0f);
        rows.setPadding(0, 0, 0, Ui.dp(c, 300));
        rows.setItemAnimator(null);
        adapter = new Posters.RowsAdapter(this);
        rows.setAdapter(adapter);
        box.addView(rows, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        hint = Ui.text(c, 17, Ui.TEXT3, Ui.medium(c));
        hint.setGravity(Gravity.CENTER);
        hint.setText("Tippe einen Namen ein – Ergebnisse erscheinen sofort.");
        box.addView(hint, Ui.frame(Ui.WRAP, Ui.WRAP, Gravity.CENTER));
        col.addView(box, Ui.lin(Ui.MATCH, 0, 1f));
        return col;
    }

    private static String norm(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n;
    }

    private void search() {
        String q = norm(field.getText().toString().trim());
        int gen = ++searchGen;
        if (q.length() < 2) {
            adapter.setRows(new ArrayList<>());
            hint.setText("Tippe einen Namen ein – Ergebnisse erscheinen sofort.");
            hint.setVisibility(View.VISIBLE);
            return;
        }
        Library lib = Library.get();
        List<Model.Channel> chs = new ArrayList<>(lib.channels);
        List<Model.Movie> mvs = new ArrayList<>(lib.movies);
        List<Model.Series> srs = new ArrayList<>(lib.series);
        Bg.run(() -> {
            List<Object> c = new ArrayList<>(), m = new ArrayList<>(), s = new ArrayList<>();
            List<Object> cStarts = new ArrayList<>(), mStarts = new ArrayList<>(), sStarts = new ArrayList<>();
            for (Model.Channel x : chs) {
                String n = norm(x.name);
                if (n.startsWith(q)) cStarts.add(x);
                else if (n.contains(q)) c.add(x);
            }
            for (Model.Movie x : mvs) {
                String n = norm(x.name);
                if (n.startsWith(q)) mStarts.add(x);
                else if (n.contains(q)) m.add(x);
            }
            for (Model.Series x : srs) {
                String n = norm(x.name);
                if (n.startsWith(q)) sStarts.add(x);
                else if (n.contains(q)) s.add(x);
            }
            cStarts.addAll(c);
            mStarts.addAll(m);
            sStarts.addAll(s);
            List<Posters.Row> out = new ArrayList<>();
            if (!cStarts.isEmpty()) out.add(new Posters.Row("Sender", cap(cStarts)));
            if (!mStarts.isEmpty()) out.add(new Posters.Row("Filme", cap(mStarts)));
            if (!sStarts.isEmpty()) out.add(new Posters.Row("Serien", cap(sStarts)));
            Bg.ui(() -> {
                if (gen != searchGen) return;
                adapter.setRows(out);
                rows.scrollToPosition(0);
                if (out.isEmpty()) {
                    hint.setText("Nichts gefunden.");
                    hint.setVisibility(View.VISIBLE);
                } else {
                    hint.setVisibility(View.GONE);
                }
            });
        });
    }

    private static List<Object> cap(List<Object> l) {
        return l.size() > 80 ? new ArrayList<>(l.subList(0, 80)) : l;
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(field.getWindowToken(), 0);
    }

    private void focusResults() {
        if (adapter.getItemCount() == 0) return;
        RecyclerView.ViewHolder vh = rows.findViewHolderForAdapterPosition(0);
        if (vh != null) {
            RecyclerView inner = ((Posters.RowsAdapter.VH) vh).list;
            RecyclerView.ViewHolder first = inner.findViewHolderForAdapterPosition(0);
            if (first != null) first.itemView.requestFocus();
        }
    }

    @Override
    public void onFocus(Object item, View card) {
        if (item instanceof Model.Movie || item instanceof Model.Series) a.setBackdrop(Posters.image(item));
        else a.setBackdrop(null);
    }

    @Override
    public void onClick(Object item) {
        if (item instanceof Model.Channel) a.playChannel((Model.Channel) item, "all");
        else if (item instanceof Model.Movie) a.openMovie((Model.Movie) item);
        else if (item instanceof Model.Series) a.openSeries((Model.Series) item);
    }

    @Override
    public void focusFirst() {
        field.requestFocus();
    }

    @Override
    public boolean onBack() {
        if (!field.hasFocus()) {
            field.requestFocus();
            return true;
        }
        return false;
    }
}
