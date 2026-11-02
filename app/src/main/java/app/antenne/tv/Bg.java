package app.antenne.tv;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Hintergrund-Threads und Rückkehr auf den Haupt-Thread. */
public final class Bg {

    private static final ExecutorService POOL = Executors.newFixedThreadPool(4);
    private static final ExecutorService EPG = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Bg() {}

    public static void run(Runnable r) {
        POOL.execute(r);
    }

    public static void epg(Runnable r) {
        EPG.execute(r);
    }

    public static void ui(Runnable r) {
        MAIN.post(r);
    }

    public static void later(Runnable r, long ms) {
        MAIN.postDelayed(r, ms);
    }

    public static void cancel(Runnable r) {
        MAIN.removeCallbacks(r);
    }
}
