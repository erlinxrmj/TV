package app.antenne.tv;

import android.app.Application;

/** Startpunkt der App: hält den Kontext für Einstellungen, Cache und Netzwerk bereit. */
public class App extends Application {

    private static App instance;

    public static App get() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Prefs.init(this);
        Store.init(this);
    }
}
