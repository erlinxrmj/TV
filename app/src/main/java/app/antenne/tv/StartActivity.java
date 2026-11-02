package app.antenne.tv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/** Startbildschirm: lädt das aktive Profil und öffnet dann die App (oder das Login). */
public class StartActivity extends Activity {

    private TextView status;
    private ProgressBar spinner;
    private LinearLayout actions;
    private boolean destroyed;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Prefs.Profile p = Prefs.active();
        if (p == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        buildUi();
        load(p, getIntent().getBooleanExtra("force", false));
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        super.onDestroy();
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF1C1E2C, 0xFF0B0C12, 0xFF120C1A}));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.mipmap.ic_launcher);
        col.addView(logo, Ui.lin(Ui.dp(this, 112), Ui.dp(this, 112)));

        TextView title = Ui.text(this, 40, Ui.TEXT, Ui.bold(this));
        title.setText("Antenne");
        title.setLetterSpacing(-0.02f);
        col.addView(title, Ui.margins(Ui.lin(Ui.WRAP, Ui.WRAP), 0, Ui.dp(this, 18), 0, 0));

        spinner = new ProgressBar(this);
        spinner.setIndeterminate(true);
        col.addView(spinner, Ui.margins(Ui.lin(Ui.dp(this, 36), Ui.dp(this, 36)), 0, Ui.dp(this, 36), 0, 0));

        status = Ui.text(this, 17, Ui.TEXT2, Ui.medium(this));
        status.setGravity(Gravity.CENTER);
        status.setText("Wird geladen …");
        col.addView(status, Ui.margins(Ui.lin(Ui.dp(this, 620), Ui.WRAP), 0, Ui.dp(this, 16), 0, 0));

        actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        actions.setVisibility(View.GONE);
        col.addView(actions, Ui.margins(Ui.lin(Ui.WRAP, Ui.WRAP), 0, Ui.dp(this, 28), 0, 0));

        root.addView(col, Ui.frame(Ui.WRAP, Ui.WRAP, Gravity.CENTER));

        TextView by = Ui.text(this, 13, Ui.TEXT3, Ui.medium(this));
        by.setText("Entwickelt von Erlin Ramaj");
        FrameLayout.LayoutParams lp = Ui.frame(Ui.WRAP, Ui.WRAP, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = Ui.dp(this, 28);
        root.addView(by, lp);

        logo.setAlpha(0f);
        logo.setScaleX(0.8f);
        logo.setScaleY(0.8f);
        logo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(500).start();
        setContentView(root);
    }

    private void load(Prefs.Profile p, boolean force) {
        actions.setVisibility(View.GONE);
        spinner.setVisibility(View.VISIBLE);
        Bg.run(() -> {
            try {
                Library.get().load(p, force, msg -> Bg.ui(() -> {
                    if (!destroyed) status.setText(msg);
                }));
                Bg.ui(() -> {
                    if (destroyed) return;
                    startActivity(new Intent(this, MainActivity.class));
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                    finish();
                });
            } catch (Exception e) {
                String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                Bg.ui(() -> {
                    if (!destroyed) showError(p, msg);
                });
            }
        });
    }

    private void showError(Prefs.Profile p, String msg) {
        spinner.setVisibility(View.GONE);
        status.setText("Verbindung fehlgeschlagen\n" + friendly(msg));
        status.setTextColor(Ui.TEXT);
        actions.removeAllViews();
        TextView retry = Ui.button(this, "Erneut versuchen", true);
        retry.setOnClickListener(v -> {
            status.setTextColor(Ui.TEXT2);
            load(p, true);
        });
        TextView edit = Ui.button(this, "Login bearbeiten", false);
        edit.setOnClickListener(v -> {
            Intent i = new Intent(this, LoginActivity.class);
            i.putExtra("edit", p.id);
            startActivity(i);
            finish();
        });
        TextView other = Ui.button(this, "Anderes Profil", false);
        other.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class).putExtra("pick", true));
            finish();
        });
        int m = Ui.dp(this, 8);
        actions.addView(retry, Ui.margins(Ui.lin(Ui.WRAP, Ui.WRAP), m, 0, m, 0));
        actions.addView(edit, Ui.margins(Ui.lin(Ui.WRAP, Ui.WRAP), m, 0, m, 0));
        if (Prefs.profiles().size() > 1) actions.addView(other, Ui.margins(Ui.lin(Ui.WRAP, Ui.WRAP), m, 0, m, 0));
        actions.setVisibility(View.VISIBLE);
        retry.requestFocus();
    }

    static String friendly(String msg) {
        if (msg == null) return "";
        if (msg.contains("Unable to resolve host") || msg.contains("UnknownHost")) return "Server nicht gefunden – stimmt die Adresse?";
        if (msg.contains("timeout") || msg.contains("timed out")) return "Der Server antwortet nicht (Zeitüberschreitung).";
        if (msg.contains("HTTP 401") || msg.contains("HTTP 403")) return "Zugang verweigert – Benutzername/Passwort prüfen.";
        if (msg.contains("HTTP 404")) return "Adresse nicht gefunden (404).";
        if (msg.contains("Failed to connect") || msg.contains("ECONNREFUSED")) return "Keine Verbindung zum Server möglich.";
        return msg;
    }
}
