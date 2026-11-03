package app.antenne.tv;

import android.view.View;

import java.util.List;

/** Gemeinsame Schnittstelle der Player (ExoPlayer „Standard/Kompatibel“ und VLC). */
public interface Engine {

    int AUDIO = 1, TEXT = 2, VIDEO = 3;

    interface Callback {
        void onBuffering(boolean buffering);

        void onReady();

        void onFirstFrame();

        void onPlaying(boolean playing);

        void onEnded();

        /**
         * @param message  verständliche Meldung
         * @param http     Server hat den Stream verweigert (z. B. zu viele Verbindungen)
         * @param retryable kurzer Abbruch, neuer Versuch lohnt sich
         */
        void onError(String message, boolean http, boolean retryable);
    }

    final class Track {
        public final String label;
        public final boolean selected;
        public final Runnable apply;

        public Track(String label, boolean selected, Runnable apply) {
            this.label = label;
            this.selected = selected;
            this.apply = apply;
        }
    }

    View view();

    void play(String url, long startMs);

    void stop();

    void pause();

    void resume();

    boolean isPlaying();

    boolean isReady();

    long position();

    /** Länge in ms, oder -1 bei Live/unbekannt. */
    long duration();

    void seekTo(long ms);

    List<Track> tracks(int type);

    boolean subtitlesOff();

    void setSubtitlesOff();

    boolean autoQuality();

    void setAutoQuality();

    void release();
}
