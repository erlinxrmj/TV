package app.antenne.tv;

import android.view.KeyEvent;
import android.view.View;

/** Ein Bereich der Hauptansicht (Live-TV, TV-Programm, Filme, …). */
public abstract class Screen {

    protected final MainActivity a;
    private View view;

    protected Screen(MainActivity a) {
        this.a = a;
    }

    public final View view() {
        if (view == null) view = build();
        return view;
    }

    public final boolean isBuilt() {
        return view != null;
    }

    protected abstract View build();

    /** Fokus in den Inhalt setzen (z. B. nach „runter“ aus der Navigation). */
    public abstract void focusFirst();

    public void onShow() {}

    public void onHide() {}

    public void onPause() {}

    public void onResume() {}

    /** @return true, wenn „Zurück“ verbraucht wurde */
    public boolean onBack() {
        return false;
    }

    public boolean onKey(int keyCode, KeyEvent e) {
        return false;
    }

    public void onLibraryChanged(String what) {}
}
