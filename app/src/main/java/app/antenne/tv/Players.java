package app.antenne.tv;

import android.content.Context;
import android.net.Uri;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory;

import java.util.Locale;

/** Erstellt passend eingestellte Player für IPTV-Streams (HTTP, Weiterleitungen, TS/HLS). */
public final class Players {

    private Players() {}

    public static ExoPlayer create(Context c, boolean live) {
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent(Prefs.userAgent())
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(20_000);
        // Viele IPTV-Streams markieren ihre Schlüsselbilder nicht sauber: ohne diese
        // Einstellungen wartet der Player endlos auf ein Startbild (Dauerladen).
        DefaultExtractorsFactory extractors = new DefaultExtractorsFactory()
                .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES
                        | DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS)
                .setConstantBitrateSeekingEnabled(true);
        DefaultMediaSourceFactory msf = new DefaultMediaSourceFactory(http, extractors);
        DefaultLoadControl lc = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(live ? 8_000 : 15_000, live ? 30_000 : 60_000, 1_500, 3_000)
                .build();
        DefaultRenderersFactory rf = new DefaultRenderersFactory(c)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                .setEnableDecoderFallback(true);
        ExoPlayer p = new ExoPlayer.Builder(c, rf)
                .setMediaSourceFactory(msf)
                .setLoadControl(lc)
                .build();
        p.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(), true);
        return p;
    }

    /** MediaItem mit Formathinweis, damit HLS/TS sicher erkannt werden. */
    public static MediaItem item(String url) {
        MediaItem.Builder b = new MediaItem.Builder().setUri(Uri.parse(url));
        String path = url.toLowerCase(Locale.ROOT);
        int q = path.indexOf('?');
        if (q >= 0) path = path.substring(0, q);
        if (path.endsWith(".m3u8")) b.setMimeType(MimeTypes.APPLICATION_M3U8);
        else if (path.endsWith(".ts")) b.setMimeType(MimeTypes.VIDEO_MP2T);
        return b.build();
    }
}
