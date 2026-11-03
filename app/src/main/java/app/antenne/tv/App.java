package app.antenne.tv;

import android.app.Application;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

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
        // Absturzursache merken, damit sie beim nächsten Start angezeigt werden kann
        Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                String txt = "Thread: " + t.getName() + "\n" + sw;
                if (txt.length() > 6000) txt = txt.substring(0, 6000);
                try (FileOutputStream out = new FileOutputStream(crashFile())) {
                    out.write(txt.getBytes(StandardCharsets.UTF_8));
                }
            } catch (Throwable ignored) {
            }
            if (prev != null) prev.uncaughtException(t, e);
        });
        Prefs.init(this);
        Ui.apply(Prefs.darkMode());
        Store.init(this);
    }

    static File crashFile() {
        return new File(instance.getFilesDir(), "crash.txt");
    }

    /** Liefert die letzte Absturzmeldung (und löscht sie), oder null. */
    static String takeCrash() {
        try {
            File f = crashFile();
            if (!f.exists()) return null;
            byte[] b = new byte[(int) Math.min(f.length(), 8000)];
            int n;
            try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
                n = in.read(b);
            }
            f.delete();
            return n > 0 ? new String(b, 0, n, StandardCharsets.UTF_8) : null;
        } catch (Throwable e) {
            return null;
        }
    }
}
