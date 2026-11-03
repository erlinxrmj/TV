package app.antenne.tv;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Farben, Schriften und Bausteine im Apple-TV-Stil: dunkles Glas, weiche Schatten, Fokus hebt an. */
public final class Ui {

    // ---------- Farben (je nach Modus Dunkel / Hell, Hauptfarbe Silber) ----------
    public static boolean dark = true;
    public static int BG, BG_TOP;          // Hintergrund (Verlauf oben → unten)
    public static int INK;                 // Grundfarbe für Text/Linien
    public static int TEXT, TEXT2, TEXT3;
    public static int GLASS, GLASS_HI, GLASS_LO; // Glasflächen (Karten, Pillen)
    public static int TILE;                // Hintergrund der Logo-Kacheln
    public static int FOCUS_BG, FOCUS_FG;  // fokussiertes Element
    public static int RING;                // Fokusrand um Karten
    public static int ACCENT, ACCENT2;     // Silber
    public static int SCRIM;               // Abdunkeln über Bildern (Basisfarbe)
    public static final int FAV = 0xFFFFD60A;
    public static final int LIVE = 0xFFFF453A;
    public static final int OK = 0xFF30D158;
    public static final int WARN = 0xFFFF9F0A;

    static {
        apply(true);
    }

    /** Setzt alle Farben für den gewählten Modus. */
    public static void apply(boolean darkMode) {
        dark = darkMode;
        if (darkMode) {
            BG = 0xFF0E0F12;
            BG_TOP = 0xFF1E1F24;
            INK = 0xFFF5F5F7;
            GLASS = 0x1FFFFFFF;
            GLASS_HI = 0x33FFFFFF;
            GLASS_LO = 0x12FFFFFF;
            TILE = 0x1FFFFFFF;
            FOCUS_BG = 0xFFF2F2F5;
            FOCUS_FG = 0xFF111114;
            RING = 0xF2FFFFFF;
            ACCENT = 0xFFD8D8DE;
            ACCENT2 = 0xFF9A9AA2;
            SCRIM = 0xFF0E0F12;
        } else {
            BG = 0xFFE6E7EB;
            BG_TOP = 0xFFF8F8FA;
            INK = 0xFF1D1D1F;
            GLASS = 0xB3FFFFFF;
            GLASS_HI = 0xFFFFFFFF;
            GLASS_LO = 0x80FFFFFF;
            TILE = 0xFF3A3A3D;
            FOCUS_BG = 0xFF1D1D1F;
            FOCUS_FG = 0xFFFFFFFF;
            RING = 0xFF1D1D1F;
            ACCENT = 0xFF6E6E76;
            ACCENT2 = 0xFFB4B4BC;
            SCRIM = 0xFFE6E7EB;
        }
        TEXT = INK;
        TEXT2 = ink(0xA6);
        TEXT3 = ink(darkMode ? 0x66 : 0x80);
    }

    /** Textfarbe mit Deckkraft (0–255). */
    public static int ink(int alpha) {
        return (alpha << 24) | (INK & 0x00FFFFFF);
    }

    /** Schriftfarbe auf fokussierten Flächen mit Deckkraft. */
    public static int focusInk(int alpha) {
        return (alpha << 24) | (FOCUS_FG & 0x00FFFFFF);
    }

    /** Hintergrundfarbe mit Deckkraft (für Verläufe über Bildern). */
    public static int scrim(int alpha) {
        return (alpha << 24) | (SCRIM & 0x00FFFFFF);
    }

    /** Hintergrund eines Bildschirms (ruhiger Verlauf). */
    public static android.graphics.drawable.Drawable background() {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{BG_TOP, BG, BG});
    }

    /** Silberner Verlauf (Profilkreise, Akzente). */
    public static GradientDrawable silver(float radius) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFFF4F4F7, 0xFFC9C9CF, 0xFF9C9CA4});
        g.setCornerRadius(radius);
        return g;
    }

    private static Typeface regular, medium, semibold, bold;

    private Ui() {}

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    private static Typeface load(Context c, int res, int fallbackStyle) {
        try {
            return c.getResources().getFont(res);
        } catch (Exception e) {
            return Typeface.create(Typeface.SANS_SERIF, fallbackStyle);
        }
    }

    public static Typeface regular(Context c) {
        if (regular == null) regular = load(c, R.font.inter_regular, Typeface.NORMAL);
        return regular;
    }

    public static Typeface medium(Context c) {
        if (medium == null) medium = load(c, R.font.inter_medium, Typeface.NORMAL);
        return medium;
    }

    public static Typeface semibold(Context c) {
        if (semibold == null) semibold = load(c, R.font.inter_semibold, Typeface.BOLD);
        return semibold;
    }

    public static Typeface bold(Context c) {
        if (bold == null) bold = load(c, R.font.inter_bold, Typeface.BOLD);
        return bold;
    }

    public static TextView text(Context c, float sp, int color, Typeface tf) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setIncludeFontPadding(false);
        return t;
    }

    public static TextView lines(TextView t, int max) {
        t.setMaxLines(max);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    public static GradientDrawable round(int color, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        return g;
    }

    public static GradientDrawable ring(int strokeColor, int strokePx, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.TRANSPARENT);
        g.setStroke(strokePx, strokeColor);
        g.setCornerRadius(radiusPx);
        return g;
    }

    public static GradientDrawable vgrad(int top, int bottom) {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
    }

    public static GradientDrawable hgrad(int left, int right) {
        return new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{left, right});
    }

    public static void clipRound(View v, float radiusPx) {
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radiusPx);
            }
        });
        v.setClipToOutline(true);
    }

    public static void clipCircle(View v) {
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        v.setClipToOutline(true);
    }

    public static FrameLayout.LayoutParams frame(int w, int h, int gravity) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
        lp.gravity = gravity;
        return lp;
    }

    public static LinearLayout.LayoutParams lin(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    public static LinearLayout.LayoutParams lin(int w, int h, float weight) {
        return new LinearLayout.LayoutParams(w, h, weight);
    }

    public static LinearLayout.LayoutParams margins(LinearLayout.LayoutParams lp, int l, int t, int r, int b) {
        lp.setMargins(l, t, r, b);
        return lp;
    }

    public static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    public static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    /**
     * tvOS-Fokus: Karte wird größer, hebt sich mit Schatten ab und bekommt einen hellen Rand.
     * Zusätzliche Reaktion (z. B. Hintergrund ändern) über {@code extra}.
     */
    public static void lift(View v, float scale, float radiusPx, FocusExtra extra) {
        v.setFocusable(true);
        Drawable ringD = ring(RING, dp(v.getContext(), 2.5f), radiusPx);
        v.setOnFocusChangeListener((view, has) -> {
            view.animate().scaleX(has ? scale : 1f).scaleY(has ? scale : 1f)
                    .translationZ(has ? dp(view.getContext(), 14) : 0f)
                    .setDuration(160).start();
            if (android.os.Build.VERSION.SDK_INT >= 23) view.setForeground(has ? ringD : null);
            if (extra != null) extra.onFocus(view, has);
        });
    }

    public interface FocusExtra {
        void onFocus(View v, boolean has);
    }

    /** Glas-Pille (Navigation, Buttons): grau, mit Fokus weiß mit dunkler Schrift. */
    public static TextView pill(Context c, String label, float sp) {
        TextView t = text(c, sp, TEXT, semibold(c));
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        int h = dp(c, 16), v = dp(c, 9);
        t.setPadding(h, v, h, v);
        t.setFocusable(true);
        t.setClickable(true);
        t.setSingleLine(true);
        stylePill(t, false, false);
        t.setOnFocusChangeListener((view, has) -> stylePill(t, has, Boolean.TRUE.equals(t.getTag(R.id.tag_selected))));
        return t;
    }

    /** Runde Pille nur mit Symbol (Suche, Einstellungen) – wie in der tvOS-Leiste. */
    public static TextView iconPill(Context c, int drawableRes) {
        TextView t = pill(c, "", 15);
        android.graphics.drawable.Drawable d = c.getResources().getDrawable(drawableRes, c.getTheme()).mutate();
        int s = dp(c, 20);
        d.setBounds(0, 0, s, s);
        t.setCompoundDrawables(d, null, null, null);
        t.setPadding(dp(c, 11), dp(c, 8), dp(c, 11), dp(c, 8));
        stylePill(t, false, false);
        return t;
    }

    public static void setPillSelected(TextView t, boolean selected) {
        t.setTag(R.id.tag_selected, selected);
        stylePill(t, t.hasFocus(), selected);
    }

    private static void stylePill(TextView t, boolean focused, boolean selected) {
        Context c = t.getContext();
        float r = dp(c, 100);
        int fg;
        if (focused) {
            t.setBackground(round(FOCUS_BG, r));
            fg = FOCUS_FG;
            t.animate().scaleX(1.06f).scaleY(1.06f).setDuration(140).start();
        } else if (selected) {
            t.setBackground(round(GLASS_HI, r));
            fg = TEXT;
            t.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
        } else {
            t.setBackground(null);
            fg = TEXT2;
            t.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
        }
        t.setTextColor(fg);
        t.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(fg));
    }

    /** Großer Aktionsknopf (Abspielen usw.). */
    public static TextView button(Context c, String label, boolean primary) {
        TextView t = text(c, 17, TEXT, semibold(c));
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 28), dp(c, 13), dp(c, 28), dp(c, 13));
        t.setFocusable(true);
        t.setClickable(true);
        t.setSingleLine(true);
        float r = dp(c, 14);
        styleButton(t, false, primary, r);
        t.setOnFocusChangeListener((view, has) -> styleButton(t, has, primary, r));
        return t;
    }

    private static void styleButton(TextView t, boolean focused, boolean primary, float r) {
        if (focused) {
            t.setBackground(round(FOCUS_BG, r));
            t.setTextColor(FOCUS_FG);
            t.animate().scaleX(1.06f).scaleY(1.06f).setDuration(140).start();
        } else {
            t.setBackground(round(primary ? ink(0x30) : GLASS, r));
            t.setTextColor(TEXT);
            t.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
        }
    }

    /** Kleines Etikett, z. B. „LIVE“, „HD“, Jahreszahl. */
    public static TextView badge(Context c, String label, int bg, int fg) {
        TextView t = text(c, 12, fg, bold(c));
        t.setText(label);
        t.setPadding(dp(c, 8), dp(c, 4), dp(c, 8), dp(c, 4));
        t.setBackground(round(bg, dp(c, 6)));
        t.setLetterSpacing(0.06f);
        return t;
    }

    /** Dünner abgerundeter Fortschrittsbalken. */
    public static final class Bar extends View {
        private final android.graphics.Paint bg = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Paint fg = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF r = new android.graphics.RectF();
        private float progress;

        public Bar(Context c, int track, int fill) {
            super(c);
            bg.setColor(track);
            fg.setColor(fill);
        }

        public void setProgress(float p) {
            float np = Math.max(0f, Math.min(1f, p));
            if (np != progress) {
                progress = np;
                invalidate();
            }
        }

        public void setColors(int track, int fill) {
            bg.setColor(track);
            fg.setColor(fill);
            invalidate();
        }

        @Override
        protected void onDraw(android.graphics.Canvas canvas) {
            float h = getHeight();
            float rad = h / 2f;
            r.set(0, 0, getWidth(), h);
            canvas.drawRoundRect(r, rad, rad, bg);
            if (progress > 0) {
                r.set(0, 0, Math.max(h, getWidth() * progress), h);
                canvas.drawRoundRect(r, rad, rad, fg);
            }
        }
    }

    /** Kurze Einblendung unten (statt Toast, passt besser zum Design). */
    public static void toast(android.app.Activity a, String msg) {
        ViewGroup root = a.findViewById(android.R.id.content);
        if (root == null) return;
        Context c = a;
        TextView t = text(c, 17, TEXT, semibold(c));
        t.setText(msg);
        t.setPadding(dp(c, 24), dp(c, 14), dp(c, 24), dp(c, 14));
        t.setBackground(round(dark ? 0xF02C2C30 : 0xF2FFFFFF, dp(c, 100)));
        t.setElevation(dp(c, 20));
        FrameLayout.LayoutParams lp = frame(WRAP, WRAP, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = dp(c, 48);
        t.setAlpha(0f);
        t.setTranslationY(dp(c, 20));
        root.addView(t, lp);
        t.animate().alpha(1f).translationY(0).setDuration(200).start();
        t.postDelayed(() -> t.animate().alpha(0f).setDuration(250).withEndAction(() -> root.removeView(t)).start(), 2200);
    }

    public interface Choice {
        void onChoice(int index);
    }

    /**
     * Auswahlfenster im App-Stil (statt grauem Android-Dialog): Titel, Optionen, Häkchen bei der
     * aktuellen Wahl. Bedienung nur mit Pfeiltasten und OK.
     */
    public static android.app.Dialog choose(android.app.Activity a, String title, String subtitle,
                                            String[] options, int selected, Choice cb) {
        android.app.Dialog d = new android.app.Dialog(a, android.R.style.Theme_Translucent_NoTitleBar);
        Context c = a;
        FrameLayout root = new FrameLayout(c);
        root.setBackgroundColor(dark ? 0x99000000 : 0x66000000);
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        int p = dp(c, 24);
        card.setPadding(p, p, p, dp(c, 18));
        card.setBackground(round(dark ? 0xFA232327 : 0xFAF7F7F9, dp(c, 26)));
        card.setElevation(dp(c, 24));
        TextView t = text(c, 22, TEXT, bold(c));
        t.setText(title);
        card.addView(t);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView st = text(c, 14, TEXT2, regular(c));
            st.setText(subtitle);
            st.setLineSpacing(dp(c, 3), 1f);
            card.addView(st, margins(lin(MATCH, WRAP), 0, dp(c, 8), 0, 0));
        }
        android.widget.ScrollView sv = new android.widget.ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(c, 14), 0, dp(c, 4));
        View first = null;
        for (int i = 0; i < options.length; i++) {
            final int idx = i;
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));
            row.setFocusable(true);
            row.setClickable(true);
            TextView check = text(c, 16, TEXT, bold(c));
            check.setText(i == selected ? "✓" : "");
            row.addView(check, lin(dp(c, 26), WRAP));
            TextView l = lines(text(c, 17, TEXT, semibold(c)), 2);
            l.setText(options[i]);
            row.addView(l, lin(0, WRAP, 1f));
            float r = dp(c, 14);
            row.setOnFocusChangeListener((v, has) -> {
                v.setBackground(has ? round(FOCUS_BG, r) : null);
                l.setTextColor(has ? FOCUS_FG : TEXT);
                check.setTextColor(has ? FOCUS_FG : TEXT);
            });
            row.setOnClickListener(v -> {
                d.dismiss();
                cb.onChoice(idx);
            });
            list.addView(row, margins(lin(MATCH, WRAP), 0, 0, 0, dp(c, 4)));
            if (first == null || i == selected) first = row;
        }
        sv.addView(list);
        card.addView(sv, lin(MATCH, WRAP));
        FrameLayout.LayoutParams lp = frame(dp(c, 460), WRAP, Gravity.CENTER);
        lp.topMargin = dp(c, 30);
        lp.bottomMargin = dp(c, 30);
        root.addView(card, lp);
        d.setContentView(root);
        View f = first;
        d.setOnShowListener(x -> {
            if (f != null) f.requestFocus();
        });
        d.show();
        return d;
    }

    public static String time(long ms) {
        return new java.text.SimpleDateFormat("HH:mm", java.util.Locale.GERMANY).format(new java.util.Date(ms));
    }

    public static String duration(long ms) {
        long s = Math.max(0, ms / 1000);
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        if (h > 0) return String.format(java.util.Locale.ROOT, "%d:%02d:%02d", h, m, sec);
        return String.format(java.util.Locale.ROOT, "%d:%02d", m, sec);
    }

    public static String minutes(long ms) {
        long m = Math.max(0, Math.round(ms / 60000.0));
        if (m >= 60) return (m / 60) + " Std. " + (m % 60 > 0 ? (m % 60) + " Min." : "");
        return m + " Min.";
    }
}
