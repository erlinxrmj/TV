package app.antenne.tv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.List;
import java.util.UUID;

/** Anmeldung: Xtream Codes (Server, Benutzer, Passwort) oder M3U-Playlist. Auch Profilauswahl. */
public class LoginActivity extends Activity {

    private boolean xtreamMode = true;
    private TextView tabX, tabM;
    private LinearLayout form;
    private EditText fName, fUrl, fUser, fPass, fM3u, fEpg;
    private TextView error, submit;
    private ProgressBar busy;
    private Prefs.Profile editing;
    private boolean destroyed;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setBackgroundDrawable(Ui.background());
        String editId = getIntent().getStringExtra("edit");
        if (editId != null) {
            for (Prefs.Profile p : Prefs.profiles()) if (p.id.equals(editId)) editing = p;
        }
        if (getIntent().getBooleanExtra("pick", false) && !Prefs.profiles().isEmpty()) {
            buildPicker();
        } else {
            buildLogin();
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        super.onDestroy();
    }

    private FrameLayout backdrop() {
        FrameLayout root = new FrameLayout(this);
        root.setBackground(Ui.background());
        // Zwei weiche Farbflecken für Tiefe
        View glow1 = new View(this);
        GradientDrawable g1 = new GradientDrawable();
        g1.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g1.setGradientRadius(Ui.dp(this, 420));
        g1.setColors(new int[]{Ui.dark ? 0x26FFFFFF : 0x66FFFFFF, 0x00FFFFFF});
        glow1.setBackground(g1);
        root.addView(glow1, Ui.frame(Ui.dp(this, 840), Ui.dp(this, 840), Gravity.TOP | Gravity.START));
        glow1.setTranslationX(-Ui.dp(this, 300));
        glow1.setTranslationY(-Ui.dp(this, 300));
        View glow2 = new View(this);
        GradientDrawable g2 = new GradientDrawable();
        g2.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g2.setGradientRadius(Ui.dp(this, 420));
        g2.setColors(new int[]{Ui.dark ? 0x1AC8C8D0 : 0x40A0A0A8, 0x00C8C8D0});
        glow2.setBackground(g2);
        root.addView(glow2, Ui.frame(Ui.dp(this, 840), Ui.dp(this, 840), Gravity.BOTTOM | Gravity.END));
        glow2.setTranslationX(Ui.dp(this, 300));
        glow2.setTranslationY(Ui.dp(this, 300));
        return root;
    }

    // ---------------- Login ----------------

    private void buildLogin() {
        FrameLayout root = backdrop();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Ui.dp(this, 64);
        row.setPadding(pad, 0, pad, 0);

        // Links: Begrüßung
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.mipmap.ic_launcher);
        left.addView(logo, Ui.lin(Ui.dp(this, 84), Ui.dp(this, 84)));
        TextView h = Ui.text(this, 44, Ui.TEXT, Ui.bold(this));
        h.setText(editing != null ? "Login bearbeiten" : "Willkommen bei\nTV");
        h.setLetterSpacing(-0.02f);
        h.setLineSpacing(0, 1.05f);
        left.addView(h, Ui.margins(Ui.lin(Ui.WRAP, Ui.WRAP), 0, Ui.dp(this, 24), 0, 0));
        TextView sub = Ui.text(this, 18, Ui.TEXT2, Ui.regular(this));
        sub.setText("Melde dich mit den Zugangsdaten deines IPTV-Anbieters an. Live-TV, TV-Programm, Filme und Serien werden automatisch geladen.");
        sub.setLineSpacing(Ui.dp(this, 4), 1f);
        left.addView(sub, Ui.margins(Ui.lin(Ui.dp(this, 380), Ui.WRAP), 0, Ui.dp(this, 16), 0, 0));
        TextView by = Ui.text(this, 13, Ui.TEXT3, Ui.medium(this));
        by.setText("Entwickelt von Erlin Ramaj");
        left.addView(by, Ui.margins(Ui.lin(Ui.WRAP, Ui.WRAP), 0, Ui.dp(this, 32), 0, 0));
        row.addView(left, Ui.lin(0, Ui.WRAP, 1f));

        // Rechts: Glas-Karte mit Formular
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(false);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        int cp = Ui.dp(this, 32);
        card.setPadding(cp, cp, cp, cp);
        card.setBackground(Ui.round(Ui.GLASS, Ui.dp(this, 28)));

        LinearLayout seg = new LinearLayout(this);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        seg.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
        seg.setBackground(Ui.round(Ui.ink(0x14), Ui.dp(this, 100)));
        tabX = Ui.pill(this, "Xtream Codes", 16);
        tabM = Ui.pill(this, "M3U-Playlist", 16);
        seg.addView(tabX, Ui.lin(0, Ui.WRAP, 1f));
        seg.addView(tabM, Ui.lin(0, Ui.WRAP, 1f));
        tabX.setOnClickListener(v -> setMode(true));
        tabM.setOnClickListener(v -> setMode(false));
        tabX.setOnFocusChangeListener(chain(tabX, () -> setMode(true)));
        tabM.setOnFocusChangeListener(chain(tabM, () -> setMode(false)));
        card.addView(seg, Ui.lin(Ui.MATCH, Ui.WRAP));

        form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        card.addView(form, Ui.margins(Ui.lin(Ui.MATCH, Ui.WRAP), 0, Ui.dp(this, 20), 0, 0));

        fName = field("Profilname (optional)", "z. B. Mein IPTV", InputType.TYPE_CLASS_TEXT);
        fUrl = field("Server-URL", "http://server.tv:8080", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        fUser = field("Benutzername", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        fPass = field("Passwort", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        fM3u = field("M3U-URL", "http://…/get.php?username=…&password=…&type=m3u_plus", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        fEpg = field("EPG-URL (optional, XMLTV)", "http://…/xmltv.php", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);

        error = Ui.text(this, 15, Ui.LIVE, Ui.medium(this));
        error.setVisibility(View.GONE);
        card.addView(error, Ui.margins(Ui.lin(Ui.MATCH, Ui.WRAP), 0, Ui.dp(this, 4), 0, Ui.dp(this, 8)));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        submit = Ui.button(this, editing != null ? "Speichern & verbinden" : "Anmelden", true);
        submit.setOnClickListener(v -> submit());
        bottom.addView(submit, Ui.lin(0, Ui.WRAP, 1f));
        busy = new ProgressBar(this);
        busy.setVisibility(View.GONE);
        bottom.addView(busy, Ui.margins(Ui.lin(Ui.dp(this, 32), Ui.dp(this, 32)), Ui.dp(this, 16), 0, 0, 0));
        if (!Prefs.profiles().isEmpty()) {
            TextView cancel = Ui.button(this, "Abbrechen", false);
            cancel.setOnClickListener(v -> finishBack());
            bottom.addView(cancel, Ui.margins(Ui.lin(Ui.WRAP, Ui.WRAP), Ui.dp(this, 12), 0, 0, 0));
        }
        card.addView(bottom, Ui.margins(Ui.lin(Ui.MATCH, Ui.WRAP), 0, Ui.dp(this, 8), 0, 0));

        sv.addView(card, new ScrollView.LayoutParams(Ui.MATCH, Ui.WRAP));
        row.addView(sv, Ui.lin(Ui.dp(this, 520), Ui.WRAP));
        root.addView(row, Ui.frame(Ui.MATCH, Ui.MATCH, Gravity.CENTER));
        setContentView(root);

        if (editing != null) {
            fName.setText(editing.name);
            if (editing.isXtream()) {
                fUrl.setText(editing.url);
                fUser.setText(editing.user);
                fPass.setText(editing.pass);
            } else {
                fM3u.setText(editing.url);
            }
            fEpg.setText(editing.epgUrl);
            setMode(editing.isXtream());
        } else {
            setMode(true);
        }
        (editing != null ? submit : tabX).requestFocus();
    }

    private View.OnFocusChangeListener chain(TextView pill, Runnable onFocus) {
        View.OnFocusChangeListener base = pill.getOnFocusChangeListener();
        return (v, has) -> {
            if (base != null) base.onFocusChange(v, has);
            if (has) onFocus.run();
        };
    }

    private EditText field(String label, String hint, int type) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView l = Ui.text(this, 14, Ui.TEXT2, Ui.semibold(this));
        l.setText(label);
        box.addView(l, Ui.margins(Ui.lin(Ui.WRAP, Ui.WRAP), Ui.dp(this, 4), 0, 0, Ui.dp(this, 8)));
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(Ui.ink(0x55));
        e.setTextColor(Ui.TEXT);
        e.setTypeface(Ui.medium(this));
        e.setTextSize(17);
        e.setSingleLine(true);
        e.setInputType(type);
        e.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        int p = Ui.dp(this, 16);
        e.setPadding(p, Ui.dp(this, 13), p, Ui.dp(this, 13));
        float r = Ui.dp(this, 14);
        e.setBackground(Ui.round(Ui.ink(0x14), r));
        e.setOnFocusChangeListener((v, has) -> {
            GradientDrawable g = Ui.round(has ? Ui.ink(0x24) : Ui.ink(0x14), r);
            if (has) g.setStroke(Ui.dp(this, 2), Ui.TEXT);
            v.setBackground(g);
        });
        box.addView(e, Ui.lin(Ui.MATCH, Ui.WRAP));
        box.setTag(e);
        e.setTag(box);
        return e;
    }

    private void setMode(boolean xtream) {
        xtreamMode = xtream;
        Ui.setPillSelected(tabX, xtream);
        Ui.setPillSelected(tabM, !xtream);
        form.removeAllViews();
        addField(fName);
        if (xtream) {
            addField(fUrl);
            addField(fUser);
            addField(fPass);
            addField(fEpg);
        } else {
            addField(fM3u);
            addField(fEpg);
        }
        error.setVisibility(View.GONE);
    }

    private void addField(EditText e) {
        View box = (View) e.getTag();
        if (box.getParent() != null) ((LinearLayout) box.getParent()).removeView(box);
        form.addView(box, Ui.margins(Ui.lin(Ui.MATCH, Ui.WRAP), 0, 0, 0, Ui.dp(this, 14)));
    }

    private static String val(EditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }

    private void showError(String msg) {
        error.setText(msg);
        error.setVisibility(View.VISIBLE);
    }

    private void submit() {
        Prefs.Profile p = new Prefs.Profile();
        p.id = editing != null ? editing.id : UUID.randomUUID().toString().substring(0, 8);
        p.epgUrl = val(fEpg);
        String name = val(fName);
        if (xtreamMode) {
            p.type = "xtream";
            p.url = val(fUrl);
            p.user = val(fUser);
            p.pass = val(fPass);
            if (p.url.isEmpty() || p.user.isEmpty() || p.pass.isEmpty()) {
                showError("Bitte Server-URL, Benutzername und Passwort ausfüllen.");
                return;
            }
            p.url = Xtream.normalizeBase(p.url);
        } else {
            String m3u = val(fM3u);
            if (m3u.isEmpty()) {
                showError("Bitte die M3U-URL eingeben.");
                return;
            }
            if (!m3u.matches("(?i)^https?://.*")) m3u = "http://" + m3u;
            Prefs.Profile x = Xtream.fromM3uUrl(m3u);
            if (x != null) {
                // Xtream-Link erkannt: so gibt es auch Filme, Serien, EPG und Abo-Infos
                p.type = "xtream";
                p.url = Xtream.normalizeBase(x.url);
                p.user = x.user;
                p.pass = x.pass;
            } else {
                p.type = "m3u";
                p.url = m3u;
            }
        }
        if (name.isEmpty()) {
            try {
                name = android.net.Uri.parse(p.isXtream() ? p.url : p.url).getHost();
            } catch (Exception ignored) {
            }
            if (name == null || name.isEmpty()) name = "IPTV";
        }
        p.name = name;

        error.setVisibility(View.GONE);
        busy.setVisibility(View.VISIBLE);
        submit.setEnabled(false);
        submit.setText("Prüfe Zugang …");
        Bg.run(() -> {
            String err = null;
            try {
                if (p.isXtream()) {
                    new Xtream(p.url, p.user, p.pass).login();
                } else {
                    File tmp = new File(getCacheDir(), "check.m3u");
                    Http.download(p.url, tmp);
                    byte[] head = new byte[512];
                    int n;
                    try (java.io.FileInputStream in = new java.io.FileInputStream(tmp)) {
                        n = in.read(head);
                    }
                    String s = n > 0 ? new String(head, 0, n, "UTF-8") : "";
                    if (!s.contains("#EXTM3U") && !s.contains("#EXTINF")) err = "Das ist keine gültige M3U-Playlist.";
                    tmp.delete();
                }
            } catch (Exception e) {
                err = StartActivity.friendly(e.getMessage() == null ? e.toString() : e.getMessage());
            }
            String fe = err;
            Bg.ui(() -> {
                if (destroyed) return;
                busy.setVisibility(View.GONE);
                submit.setEnabled(true);
                submit.setText(editing != null ? "Speichern & verbinden" : "Anmelden");
                if (fe != null) {
                    showError(fe);
                    return;
                }
                if (editing != null) Library.clearCache(p);
                Prefs.saveProfile(p);
                Prefs.setActive(p.id);
                Intent i = new Intent(this, StartActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(i);
                finish();
            });
        });
    }

    private void finishBack() {
        if (Library.get().isLoaded()) {
            finish();
        } else {
            Intent i = new Intent(this, StartActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && !Prefs.profiles().isEmpty() && form != null) {
            finishBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // ---------------- Profilauswahl („Wer schaut?“) ----------------

    private void buildPicker() {
        FrameLayout root = backdrop();
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView h = Ui.text(this, 40, Ui.TEXT, Ui.bold(this));
        h.setText("Profil wählen");
        col.addView(h);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setClipChildren(false);
        col.setClipChildren(false);
        List<Prefs.Profile> list = Prefs.profiles();
        Prefs.Profile active = Prefs.active();
        View first = null;
        for (Prefs.Profile p : list) {
            View v = profileTile(p.name, p.isXtream() ? "Xtream Codes" : "M3U-Playlist", false);
            v.setOnClickListener(x -> {
                Prefs.setActive(p.id);
                Intent i = new Intent(this, StartActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(i);
                finish();
            });
            row.addView(v, Ui.margins(Ui.lin(Ui.dp(this, 200), Ui.dp(this, 210)), Ui.dp(this, 14), 0, Ui.dp(this, 14), 0));
            if (first == null || (active != null && active.id.equals(p.id))) first = v;
        }
        View add = profileTile("Hinzufügen", "Neuer Zugang", true);
        add.setOnClickListener(x -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
        row.addView(add, Ui.margins(Ui.lin(Ui.dp(this, 200), Ui.dp(this, 210)), Ui.dp(this, 14), 0, Ui.dp(this, 14), 0));
        col.addView(row, Ui.margins(Ui.lin(Ui.WRAP, Ui.WRAP), 0, Ui.dp(this, 48), 0, 0));
        root.addView(col, Ui.frame(Ui.WRAP, Ui.WRAP, Gravity.CENTER));
        setContentView(root);
        if (first != null) first.requestFocus();
    }

    private View profileTile(String name, String sub, boolean plus) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(this, 12), Ui.dp(this, 20), Ui.dp(this, 12), Ui.dp(this, 20));
        t.setBackground(Ui.round(Ui.GLASS, Ui.dp(this, 24)));
        t.setClickable(true);
        TextView circle = Ui.text(this, 34, plus ? Ui.TEXT : 0xFF1D1D1F, Ui.bold(this));
        circle.setGravity(Gravity.CENTER);
        circle.setText(plus ? "+" : Names.initials(name).substring(0, Math.min(2, Names.initials(name).length())));
        GradientDrawable g = plus ? new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Ui.ink(0x33), Ui.ink(0x1A)}) : Ui.silver(0);
        g.setShape(GradientDrawable.OVAL);
        circle.setBackground(g);
        t.addView(circle, Ui.lin(Ui.dp(this, 92), Ui.dp(this, 92)));
        TextView n = Ui.lines(Ui.text(this, 18, Ui.TEXT, Ui.semibold(this)), 1);
        n.setText(name);
        n.setGravity(Gravity.CENTER);
        t.addView(n, Ui.margins(Ui.lin(Ui.MATCH, Ui.WRAP), 0, Ui.dp(this, 16), 0, 0));
        TextView s = Ui.text(this, 13, Ui.TEXT2, Ui.medium(this));
        s.setText(sub);
        s.setGravity(Gravity.CENTER);
        t.addView(s, Ui.margins(Ui.lin(Ui.MATCH, Ui.WRAP), 0, Ui.dp(this, 4), 0, 0));
        Ui.lift(t, 1.08f, Ui.dp(this, 24), null);
        return t;
    }
}
