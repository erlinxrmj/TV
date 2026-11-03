package app.antenne.tv;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** ExoPlayer (Media3) – „Standard“ mit Hardware-Dekoder oder „Kompatibel“ mit Software-Dekoder. */
final class ExoEngine implements Engine {

    private final PlayerView view;
    private final ExoPlayer p;
    private final Callback cb;

    ExoEngine(Context c, boolean live, boolean software, Callback cb) {
        this.cb = cb;
        view = new PlayerView(c);
        view.setUseController(false);
        view.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        view.setShutterBackgroundColor(0xFF000000);
        view.setKeepScreenOn(true);
        p = Players.create(c, live, software);
        view.setPlayer(p);
        p.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_BUFFERING) cb.onBuffering(true);
                else if (state == Player.STATE_READY) {
                    cb.onBuffering(false);
                    cb.onReady();
                } else if (state == Player.STATE_ENDED) cb.onEnded();
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                cb.onPlaying(isPlaying);
            }

            @Override
            public void onRenderedFirstFrame() {
                cb.onFirstFrame();
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException e) {
                cb.onError(errorText(e), e.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                        e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                                || e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                                || e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW
                                || e.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
            }
        });
    }

    static String errorText(PlaybackException e) {
        switch (e.errorCode) {
            case PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS:
                return "Der Anbieter liefert diesen Stream gerade nicht.\n(Zu viele Verbindungen oder Sender offline)";
            case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED:
            case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT:
                return "Keine Verbindung zum Stream.\nBitte Internet prüfen.";
            case PlaybackException.ERROR_CODE_DECODER_INIT_FAILED:
            case PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED:
            case PlaybackException.ERROR_CODE_DECODING_FAILED:
                return "Dein Fernseher kann dieses Videoformat nicht abspielen.\nTipp: Im Menü den Player auf „VLC-Engine“ stellen.";
            case PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED:
            case PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED:
                return "Unbekanntes Stream-Format.\nTipp: Im Menü den Player auf „VLC-Engine“ stellen.";
            default:
                return "Wiedergabe nicht möglich (" + e.getErrorCodeName() + ")";
        }
    }

    @Override
    public View view() {
        return view;
    }

    @Override
    public void play(String url, long startMs) {
        p.setMediaItem(Players.item(url), Math.max(0, startMs));
        p.prepare();
        p.play();
    }

    @Override
    public void stop() {
        p.stop();
    }

    @Override
    public void pause() {
        p.pause();
    }

    @Override
    public void resume() {
        if (p.getPlaybackState() == Player.STATE_IDLE) p.prepare();
        p.play();
    }

    @Override
    public boolean isPlaying() {
        return p.isPlaying();
    }

    @Override
    public boolean isReady() {
        return p.getPlaybackState() == Player.STATE_READY;
    }

    @Override
    public long position() {
        return Math.max(0, p.getCurrentPosition());
    }

    @Override
    public long duration() {
        long d = p.getDuration();
        return d == C.TIME_UNSET || d <= 0 || p.isCurrentMediaItemLive() ? -1 : d;
    }

    @Override
    public void seekTo(long ms) {
        p.seekTo(ms);
    }

    @Override
    public List<Track> tracks(int type) {
        List<Track> out = new ArrayList<>();
        int t = type == AUDIO ? C.TRACK_TYPE_AUDIO : type == TEXT ? C.TRACK_TYPE_TEXT : C.TRACK_TYPE_VIDEO;
        boolean textOff = p.getTrackSelectionParameters().disabledTrackTypes.contains(C.TRACK_TYPE_TEXT);
        boolean videoOverride = !p.getTrackSelectionParameters().overrides.isEmpty() && hasVideoOverride();
        int n = 0;
        for (Tracks.Group g : p.getCurrentTracks().getGroups()) {
            if (g.getType() != t) continue;
            for (int i = 0; i < g.length; i++) {
                if (!g.isTrackSupported(i)) continue;
                n++;
                Format f = g.getTrackFormat(i);
                final int fi = i;
                boolean sel = g.isTrackSelected(i);
                String label;
                if (type == VIDEO) {
                    if (f.height <= 0) continue;
                    label = f.height + "p" + (f.bitrate > 0 ? String.format(Locale.GERMANY, "  ·  %.1f Mbit/s", f.bitrate / 1_000_000f) : "");
                    sel = sel && videoOverride;
                } else {
                    label = trackName(f, n);
                    if (type == TEXT) sel = sel && !textOff;
                }
                out.add(new Track(label, sel, () -> p.setTrackSelectionParameters(p.getTrackSelectionParameters().buildUpon()
                        .setTrackTypeDisabled(t, false)
                        .setOverrideForType(new TrackSelectionOverride(g.getMediaTrackGroup(), fi)).build())));
            }
        }
        if (type == VIDEO && out.size() < 2) out.clear(); // nur eine Stufe: nichts zu wählen
        return out;
    }

    private boolean hasVideoOverride() {
        for (TrackSelectionOverride o : p.getTrackSelectionParameters().overrides.values()) {
            if (o.getType() == C.TRACK_TYPE_VIDEO) return true;
        }
        return false;
    }

    static String trackName(Format f, int n) {
        String lang = f.language;
        String name = null;
        if (lang != null && !lang.equals("und")) {
            try {
                name = new Locale(lang).getDisplayLanguage(Locale.GERMANY);
            } catch (Exception ignored) {
            }
        }
        if (f.label != null && !f.label.isEmpty()) name = name == null ? f.label : name + " (" + f.label + ")";
        if (name == null || name.isEmpty()) name = "Spur " + n;
        if (f.channelCount == 6) name += "  5.1";
        else if (f.channelCount == 8) name += "  7.1";
        return name;
    }

    @Override
    public boolean subtitlesOff() {
        return p.getTrackSelectionParameters().disabledTrackTypes.contains(C.TRACK_TYPE_TEXT);
    }

    @Override
    public void setSubtitlesOff() {
        p.setTrackSelectionParameters(p.getTrackSelectionParameters().buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build());
    }

    @Override
    public boolean autoQuality() {
        return !hasVideoOverride();
    }

    @Override
    public void setAutoQuality() {
        p.setTrackSelectionParameters(p.getTrackSelectionParameters().buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO).build());
    }

    @Override
    public void release() {
        view.setPlayer(null);
        p.release();
    }
}
