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
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Erstellt passend eingestellte Player für IPTV-Streams (HTTP, Weiterleitungen, TS/HLS). */
public final class Players {

    private Players() {}

    /** Puffer je Stufe: {min, max, zumStart, nachHänger} in ms. */
    private static final int[][] LIVE_BUF = {
            {4_000, 15_000, 1_000, 2_000}, {10_000, 30_000, 1_500, 3_000},
            {20_000, 60_000, 2_500, 5_000}, {40_000, 120_000, 5_000, 10_000}};
    private static final int[][] VOD_BUF = {
            {10_000, 30_000, 1_000, 2_000}, {15_000, 60_000, 1_500, 3_000},
            {30_000, 90_000, 2_500, 5_000}, {60_000, 180_000, 5_000, 10_000}};

    /** Software-Dekoder zuerst: hilft bei Fernsehern, deren Hardware-Dekoder streikt. */
    private static final MediaCodecSelector SOFTWARE_FIRST = (mimeType, secure, tunneling) -> {
        List<MediaCodecInfo> all = MediaCodecUtil.getDecoderInfos(mimeType, secure, tunneling);
        List<MediaCodecInfo> out = new ArrayList<>();
        for (MediaCodecInfo i : all) if (i.softwareOnly) out.add(i);
        for (MediaCodecInfo i : all) if (!i.softwareOnly) out.add(i);
        return out;
    };

    public static ExoPlayer create(Context c, boolean live, boolean software) {
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
        int[] b = (live ? LIVE_BUF : VOD_BUF)[Math.max(0, Math.min(3, Prefs.buffer()))];
        DefaultLoadControl lc = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(b[0], b[1], b[2], b[3])
                .setPrioritizeTimeOverSizeThresholds(true)
                .build();
        DefaultRenderersFactory rf = new DefaultRenderersFactory(c)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                .setEnableDecoderFallback(true);
        if (software) rf.setMediaCodecSelector(SOFTWARE_FIRST);
        ExoPlayer p = new ExoPlayer.Builder(c, rf)
                .setMediaSourceFactory(msf)
                .setLoadControl(lc)
                .build();
        p.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(), true);
        // Automatische Qualität: ExoPlayer wählt laufend die passende Stufe (bei HLS mit mehreren
        // Stufen); optional mit Obergrenze aus den Einstellungen.
        int maxH = Prefs.maxQuality();
        if (maxH > 0) {
            p.setTrackSelectionParameters(p.getTrackSelectionParameters().buildUpon()
                    .setMaxVideoSize(Integer.MAX_VALUE, maxH).build());
        }
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
