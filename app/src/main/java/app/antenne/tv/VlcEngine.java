package app.antenne.tv;

import android.content.Context;
import android.net.Uri;
import android.view.View;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;

import java.util.ArrayList;
import java.util.List;

/** VLC als Player: spielt auch Streams, mit denen ExoPlayer oder der TV-Dekoder Mühe haben. */
final class VlcEngine implements Engine {

    private final LibVLC lib;
    private final MediaPlayer mp;
    private final VLCVideoLayout layout;
    private final Callback cb;
    private final int cachingMs;
    private boolean attached, ready, released;

    VlcEngine(Context c, boolean live, Callback cb) {
        this.cb = cb;
        int[] live_ = {800, 1800, 3500, 7000};
        int[] vod = {1500, 3000, 6000, 12000};
        cachingMs = (live ? live_ : vod)[Math.max(0, Math.min(3, Prefs.buffer()))];
        ArrayList<String> opts = new ArrayList<>();
        opts.add("--network-caching=" + cachingMs);
        opts.add("--http-reconnect");
        opts.add("--audio-time-stretch");
        opts.add("--no-stats");
        lib = new LibVLC(c, opts);
        mp = new MediaPlayer(lib);
        layout = new VLCVideoLayout(c);
        layout.setKeepScreenOn(true);
        mp.setEventListener(ev -> {
            if (released) return;
            switch (ev.type) {
                case MediaPlayer.Event.Buffering:
                    if (ev.getBuffering() >= 100f) {
                        cb.onBuffering(false);
                        if (!ready && mp.isPlaying()) {
                            ready = true;
                            cb.onReady();
                        }
                    } else {
                        cb.onBuffering(true);
                    }
                    break;
                case MediaPlayer.Event.Playing:
                    ready = true;
                    cb.onPlaying(true);
                    cb.onBuffering(false);
                    cb.onReady();
                    break;
                case MediaPlayer.Event.Paused:
                    cb.onPlaying(false);
                    break;
                case MediaPlayer.Event.Vout:
                    if (ev.getVoutCount() > 0) cb.onFirstFrame();
                    break;
                case MediaPlayer.Event.EndReached:
                    cb.onEnded();
                    break;
                case MediaPlayer.Event.EncounteredError:
                    cb.onError("VLC konnte den Stream nicht öffnen.\nDer Sender ist vielleicht offline.", false, true);
                    break;
                default:
                    break;
            }
        });
    }

    @Override
    public View view() {
        return layout;
    }

    @Override
    public void play(String url, long startMs) {
        if (!layout.isAttachedToWindow()) {
            layout.post(() -> {
                if (!released) play(url, startMs);
            });
            return;
        }
        if (!attached) {
            mp.attachViews(layout, null, false, false);
            mp.setVideoScale(MediaPlayer.ScaleType.SURFACE_BEST_FIT);
            attached = true;
        }
        ready = false;
        Media m = new Media(lib, Uri.parse(url));
        m.setHWDecoderEnabled(true, false);
        m.addOption(":network-caching=" + cachingMs);
        m.addOption(":http-user-agent=" + Prefs.userAgent());
        if (startMs > 1000) m.addOption(":start-time=" + (startMs / 1000));
        mp.setMedia(m);
        m.release();
        cb.onBuffering(true);
        mp.play();
    }

    @Override
    public void stop() {
        mp.stop();
    }

    @Override
    public void pause() {
        mp.pause();
    }

    @Override
    public void resume() {
        mp.play();
    }

    @Override
    public boolean isPlaying() {
        return mp.isPlaying();
    }

    @Override
    public boolean isReady() {
        return ready;
    }

    @Override
    public long position() {
        return Math.max(0, mp.getTime());
    }

    @Override
    public long duration() {
        long l = mp.getLength();
        return l > 0 ? l : -1;
    }

    @Override
    public void seekTo(long ms) {
        mp.setTime(ms);
    }

    @Override
    public List<Track> tracks(int type) {
        List<Track> out = new ArrayList<>();
        if (type == VIDEO) return out;
        MediaPlayer.TrackDescription[] list = type == AUDIO ? mp.getAudioTracks() : mp.getSpuTracks();
        int cur = type == AUDIO ? mp.getAudioTrack() : mp.getSpuTrack();
        if (list == null) return out;
        for (MediaPlayer.TrackDescription t : list) {
            if (t.id < 0) continue; // „Deaktivieren“-Eintrag
            final int id = t.id;
            String name = t.name == null || t.name.isEmpty() ? "Spur " + (out.size() + 1) : t.name;
            out.add(new Track(name, id == cur, () -> {
                if (type == AUDIO) mp.setAudioTrack(id);
                else mp.setSpuTrack(id);
            }));
        }
        return out;
    }

    @Override
    public boolean subtitlesOff() {
        return mp.getSpuTrack() < 0;
    }

    @Override
    public void setSubtitlesOff() {
        mp.setSpuTrack(-1);
    }

    @Override
    public boolean autoQuality() {
        return true;
    }

    @Override
    public void setAutoQuality() {
    }

    @Override
    public void release() {
        released = true;
        try {
            mp.setEventListener(null);
            mp.stop();
            if (attached) mp.detachViews();
            mp.release();
            lib.release();
        } catch (Exception ignored) {
        }
    }
}
