package app.antenne.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Vollbild-Player. Live: hoch/runter schaltet um, OK zeigt die Infoleiste, links öffnet die
 * Senderliste, OK lang = Favorit. Filme/Serien: links/rechts spulen, OK Pause, Menü = Tonspur/Untertitel.
 */
public class PlayerActivity extends Activity {

    /** Folgen der Serie, aus der gestartet wurde (für „Nächste Folge“). */
    static List<Model.Episode> queue;
    static Model.Series queueSeries;

    private String type;
    private ExoPlayer player;
    private PlayerView playerView;
    private ProgressBar spinner;
    private TextView errorView;

    // Live
    private List<Model.Channel> channels = new ArrayList<>();
    private int index;
    private String catKey;
    private boolean triedAltFormat;
    private int retries;

    // VOD
    private Model.Movie movie;
    private Model.Episode episode;
    private long startPos;

    // Overlays
    private LinearLayout liveBar, vodBar;
    private ImageView lbLogo;
    private TextView lbNum, lbName, lbStar, lbNow, lbTime, lbNext, lbClock, lbCat;
    private Ui.Bar lbProgress;
    private TextView vbTitle, vbSub, vbPos, vbDur, vbState;
    private Ui.Bar vbProgress;
    private FrameLayout listPanel;
    private RecyclerView listView;
    private ListAdapter listAdapter;
    private TextView nextCard;
    private boolean nextCancelled;

    private final Runnable hideBars = this::hideOverlays;
    private final Runnable saveTick = new Runnable() {
        @Override
        public void run() {
            savePosition();
            updateVod();
            checkNext();
            Bg.later(this, 1000);
        }
    };
    private final Runnable zapRunnable = this::playCurrentChannel;
    /** Wenn ein Sender nach 12 s noch lädt: anderes Format probieren bzw. Meldung zeigen. */
    private final Runnable stallCheck = () -> {
        if (player == null || !"live".equals(type)) return;
        if (player.getPlaybackState() == Player.STATE_READY) return;
        if (tryAltFormat()) {
            Bg.later(this.stallCheckRef(), 12_000);
            return;
        }
        spinner.setVisibility(View.GONE);
        errorView.setText("Dieser Sender antwortet gerade nicht.\nOK = nochmal versuchen   ·   ▲▼ = anderer Sender");
        errorView.setVisibility(View.VISIBLE);
    };

    private Runnable stallCheckRef() {
        return stallCheck;
    }
    private int seekRepeat;
    private boolean wasPaused;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Library.get().profile == null) {
            finish();
            return;
        }
        type = getIntent().getStringExtra("type");
        buildUi();
        if ("live".equals(type)) {
            catKey = getIntent().getStringExtra("cat");
            channels = Library.get().channelsFor(catKey);
            String key = getIntent().getStringExtra("key");
            Model.Channel c = Library.get().channel(key);
            index = channels.indexOf(c);
            if (index < 0) {
                channels = Library.get().channelsFor("all");
                catKey = "all";
                index = Math.max(0, channels.indexOf(c));
            }
            if (channels.isEmpty()) {
                finish();
                return;
            }
            playCurrentChannel();
        } else if ("movie".equals(type)) {
            movie = Library.get().movie(getIntent().getStringExtra("id"));
            if (movie == null) {
                finish();
                return;
            }
            startPos = getIntent().getLongExtra("pos", Store.position(movie.key()));
            playVod(Library.get().streamUrl(movie), startPos);
        } else {
            String id = getIntent().getStringExtra("id");
            if (queue != null) for (Model.Episode e : queue) if (e.id.equals(id)) episode = e;
            if (episode == null) {
                finish();
                return;
            }
            startPos = getIntent().getLongExtra("pos", 0);
            playVod(Library.get().streamUrl(episode), startPos);
        }
    }

    // ---------------- Oberfläche ----------------

    private void buildUi() {
        Context c = this;
        FrameLayout root = new FrameLayout(c);
        root.setBackgroundColor(0xFF000000);
        playerView = new PlayerView(c);
        playerView.setUseController(false);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        playerView.setShutterBackgroundColor(0xFF000000);
        playerView.setKeepScreenOn(true);
        root.addView(playerView, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        spinner = new ProgressBar(c);
        root.addView(spinner, Ui.frame(Ui.dp(c, 52), Ui.dp(c, 52), Gravity.CENTER));

        errorView = Ui.text(c, 18, Ui.TEXT, Ui.semibold(c));
        errorView.setGravity(Gravity.CENTER);
        errorView.setPadding(Ui.dp(c, 28), Ui.dp(c, 18), Ui.dp(c, 28), Ui.dp(c, 18));
        errorView.setBackground(Ui.round(0xE61C1E28, Ui.dp(c, 20)));
        errorView.setVisibility(View.GONE);
        root.addView(errorView, Ui.frame(Ui.WRAP, Ui.WRAP, Gravity.CENTER));

        // Untere Verdunkelung für die Leisten
        View shade = new View(c);
        shade.setBackground(Ui.vgrad(0x00000000, 0xCC000000));
        shade.setTag("shade");
        root.addView(shade, Ui.frame(Ui.MATCH, Ui.dp(c, 260), Gravity.BOTTOM));

        if ("live".equals(type)) buildLiveBar(root);
        else buildVodBar(root);

        nextCard = Ui.text(c, 16, 0xFF111111, Ui.semibold(c));
        nextCard.setPadding(Ui.dp(c, 22), Ui.dp(c, 14), Ui.dp(c, 22), Ui.dp(c, 14));
        nextCard.setBackground(Ui.round(0xFFFFFFFF, Ui.dp(c, 16)));
        nextCard.setVisibility(View.GONE);
        FrameLayout.LayoutParams nl = Ui.frame(Ui.WRAP, Ui.WRAP, Gravity.BOTTOM | Gravity.END);
        nl.setMargins(0, 0, Ui.dp(c, 48), Ui.dp(c, 170));
        root.addView(nextCard, nl);

        setContentView(root);
    }

    private View shade() {
        ViewGroup root = (ViewGroup) playerView.getParent();
        return root.findViewWithTag("shade");
    }

    private void buildLiveBar(FrameLayout root) {
        Context c = this;
        liveBar = new LinearLayout(c);
        liveBar.setOrientation(LinearLayout.HORIZONTAL);
        liveBar.setGravity(Gravity.CENTER_VERTICAL);
        int p = Ui.dp(c, 22);
        liveBar.setPadding(p, p, Ui.dp(c, 28), p);
        liveBar.setBackground(Ui.round(0xD91C1E28, Ui.dp(c, 26)));

        FrameLayout tile = new FrameLayout(c);
        tile.setBackground(Ui.round(0x1AFFFFFF, Ui.dp(c, 16)));
        tile.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 10));
        lbLogo = new ImageView(c);
        tile.addView(lbLogo, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        liveBar.addView(tile, Ui.lin(Ui.dp(c, 140), Ui.dp(c, 88)));

        LinearLayout mid = new LinearLayout(c);
        mid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout top = new LinearLayout(c);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        lbNum = Ui.text(c, 15, Ui.TEXT3, Ui.bold(c));
        top.addView(lbNum, Ui.margins(Ui.lin(Ui.WRAP, Ui.WRAP), 0, 0, Ui.dp(c, 10), 0));
        lbName = Ui.lines(Ui.text(c, 22, Ui.TEXT, Ui.bold(c)), 1);
        top.addView(lbName, Ui.lin(Ui.WRAP, Ui.WRAP));
        lbStar = Ui.text(c, 20, Ui.FAV, Ui.bold(c));
        lbStar.setText("  ★");
        top.addView(lbStar);
        View spacer = new View(c);
        top.addView(spacer, Ui.lin(0, 1, 1f));
        lbCat = Ui.lines(Ui.text(c, 13, Ui.TEXT3, Ui.semibold(c)), 1);
        lbCat.setMaxWidth(Ui.dp(c, 220));
        top.addView(lbCat, Ui.margins(Ui.lin(Ui.WRAP, Ui.WRAP), Ui.dp(c, 12), 0, Ui.dp(c, 16), 0));
        lbClock = Ui.text(c, 20, Ui.TEXT, Ui.semibold(c));
        top.addView(lbClock);
        mid.addView(top, Ui.lin(Ui.MATCH, Ui.WRAP));

        LinearLayout nowRow = new LinearLayout(c);
        nowRow.setOrientation(LinearLayout.HORIZONTAL);
        nowRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView live = Ui.badge(c, "JETZT", Ui.ACCENT, 0xFF0B0C12);
        nowRow.addView(live);
        lbNow = Ui.lines(Ui.text(c, 17, Ui.TEXT, Ui.semibold(c)), 1);
        nowRow.addView(lbNow, Ui.margins(Ui.lin(0, Ui.WRAP, 1f), Ui.dp(c, 10), 0, 0, 0));
        lbTime = Ui.text(c, 14, Ui.TEXT2, Ui.medium(c));
        nowRow.addView(lbTime, Ui.margins(Ui.lin(Ui.WRAP, Ui.WRAP), Ui.dp(c, 12), 0, 0, 0));
        mid.addView(nowRow, Ui.margins(Ui.lin(Ui.MATCH, Ui.WRAP), 0, Ui.dp(c, 12), 0, 0));
        lbProgress = new Ui.Bar(c, 0x33FFFFFF, Ui.TEXT);
        mid.addView(lbProgress, Ui.margins(Ui.lin(Ui.MATCH, Ui.dp(c, 4)), 0, Ui.dp(c, 10), 0, 0));
        lbNext = Ui.lines(Ui.text(c, 14, Ui.TEXT2, Ui.medium(c)), 1);
        mid.addView(lbNext, Ui.margins(Ui.lin(Ui.MATCH, Ui.WRAP), 0, Ui.dp(c, 10), 0, 0));
        liveBar.addView(mid, Ui.margins(Ui.lin(0, Ui.WRAP, 1f), Ui.dp(c, 22), 0, 0, 0));

        FrameLayout.LayoutParams lp = Ui.frame(Ui.MATCH, Ui.WRAP, Gravity.BOTTOM);
        lp.setMargins(Ui.dp(c, 48), 0, Ui.dp(c, 48), Ui.dp(c, 36));
        liveBar.setElevation(Ui.dp(c, 12));
        root.addView(liveBar, lp);

        TextView help = Ui.text(c, 12, 0x99FFFFFF, Ui.medium(c));
        help.setText("▲▼ Sender wechseln   ◀ Senderliste   OK lang ★ Favorit   Menü: Tonspur");
        help.setTag("help");
        FrameLayout.LayoutParams hl = Ui.frame(Ui.WRAP, Ui.WRAP, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        hl.bottomMargin = Ui.dp(c, 12);
        root.addView(help, hl);

        // Senderliste links
        listPanel = new FrameLayout(c);
        listPanel.setBackground(Ui.hgrad(0xF00B0C12, 0x000B0C12));
        listPanel.setVisibility(View.GONE);
        listView = new RecyclerView(c);
        listView.setLayoutManager(new LinearLayoutManager(c));
        listView.setClipToPadding(false);
        listView.setClipChildren(false);
        listView.setPadding(Ui.dp(c, 36), Ui.dp(c, 40), Ui.dp(c, 60), Ui.dp(c, 40));
        listView.setItemAnimator(null);
        listAdapter = new ListAdapter();
        listView.setAdapter(listAdapter);
        listPanel.addView(listView, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        root.addView(listPanel, Ui.frame(Ui.dp(c, 470), Ui.MATCH, Gravity.START));
    }

    private void buildVodBar(FrameLayout root) {
        Context c = this;
        vodBar = new LinearLayout(c);
        vodBar.setOrientation(LinearLayout.VERTICAL);
        vbTitle = Ui.lines(Ui.text(c, 28, Ui.TEXT, Ui.bold(c)), 1);
        vodBar.addView(vbTitle);
        vbSub = Ui.lines(Ui.text(c, 15, Ui.TEXT2, Ui.medium(c)), 1);
        vodBar.addView(vbSub, Ui.margins(Ui.lin(Ui.MATCH, Ui.WRAP), 0, Ui.dp(c, 6), 0, 0));
        vbProgress = new Ui.Bar(c, 0x40FFFFFF, Ui.TEXT);
        vodBar.addView(vbProgress, Ui.margins(Ui.lin(Ui.MATCH, Ui.dp(c, 6)), 0, Ui.dp(c, 20), 0, 0));
        LinearLayout times = new LinearLayout(c);
        times.setOrientation(LinearLayout.HORIZONTAL);
        vbPos = Ui.text(c, 14, Ui.TEXT2, Ui.semibold(c));
        times.addView(vbPos);
        vbState = Ui.text(c, 14, Ui.TEXT, Ui.bold(c));
        vbState.setGravity(Gravity.CENTER);
        times.addView(vbState, Ui.lin(0, Ui.WRAP, 1f));
        vbDur = Ui.text(c, 14, Ui.TEXT2, Ui.semibold(c));
        times.addView(vbDur);
        vodBar.addView(times, Ui.margins(Ui.lin(Ui.MATCH, Ui.WRAP), 0, Ui.dp(c, 10), 0, 0));
        TextView help = Ui.text(c, 12, 0x80FFFFFF, Ui.medium(c));
        help.setText("◀ ▶ spulen   OK Pause   Menü / ▼ Tonspur & Untertitel");
        vodBar.addView(help, Ui.margins(Ui.lin(Ui.WRAP, Ui.WRAP), 0, Ui.dp(c, 12), 0, 0));
        FrameLayout.LayoutParams lp = Ui.frame(Ui.MATCH, Ui.WRAP, Gravity.BOTTOM);
        lp.setMargins(Ui.dp(c, 56), 0, Ui.dp(c, 56), Ui.dp(c, 32));
        root.addView(vodBar, lp);
    }

    // ---------------- Wiedergabe ----------------

    private ExoPlayer ensurePlayer(boolean live) {
        if (player == null) {
            player = Players.create(this, live);
            playerView.setPlayer(player);
            player.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int state) {
                    spinner.setVisibility(state == Player.STATE_BUFFERING ? View.VISIBLE : View.GONE);
                    if (state == Player.STATE_READY) {
                        errorView.setVisibility(View.GONE);
                        retries = 0;
                    }
                    if (state == Player.STATE_ENDED && !"live".equals(type)) onEnded();
                }

                @Override
                public void onIsPlayingChanged(boolean isPlaying) {
                    if (vbState != null) vbState.setText(isPlaying ? "" : "❚❚  Pausiert");
                }

                @Override
                public void onPlayerError(@NonNull PlaybackException error) {
                    handleError(error);
                }
            });
        }
        return player;
    }

    private void handleError(PlaybackException error) {
        spinner.setVisibility(View.GONE);
        if ("live".equals(type)) {
            Model.Channel ch = channels.get(index);
            // 1) Kurz neu versuchen (Server bricht Live-Streams gerne ab)
            if (retries < 2 && error.errorCode != PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) {
                retries++;
                Bg.later(this::playCurrentChannel, 1200);
                return;
            }
            // 2) Anderes Format probieren (TS ↔ HLS)
            if (tryAltFormat()) return;
        } else if (retries < 1) {
            retries++;
            long pos = Math.max(0, player.getCurrentPosition());
            player.prepare();
            player.seekTo(pos);
            player.play();
            return;
        }
        errorView.setText(errorText(error));
        errorView.setVisibility(View.VISIBLE);
    }

    /** Probiert einmal das andere Live-Format (TS ↔ HLS). */
    private boolean tryAltFormat() {
        if (triedAltFormat || player == null) return false;
        Model.Channel ch = channels.get(index);
        Xtream x = Library.get().xtream;
        if (ch.url != null || x == null) return false;
        triedAltFormat = true;
        String ext = "ts".equals(Prefs.liveFormat()) ? ".m3u8" : ".ts";
        String alt = x.base + "/live/" + android.net.Uri.encode(x.user) + "/" + android.net.Uri.encode(x.pass) + "/" + ch.id + ext;
        player.setMediaItem(Players.item(alt));
        player.prepare();
        player.play();
        return true;
    }

    private static String errorText(PlaybackException e) {
        switch (e.errorCode) {
            case PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS:
                return "Der Anbieter liefert diesen Stream gerade nicht.\n(Zu viele Verbindungen oder Sender offline)";
            case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED:
            case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT:
                return "Keine Verbindung zum Stream.\nBitte Internet prüfen.";
            case PlaybackException.ERROR_CODE_DECODER_INIT_FAILED:
            case PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED:
                return "Dieses Videoformat kann dein Fernseher nicht abspielen.";
            case PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED:
            case PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED:
                return "Der Stream ist beschädigt oder hat ein unbekanntes Format.";
            default:
                return "Wiedergabe nicht möglich.\n" + e.getErrorCodeName();
        }
    }

    private void playCurrentChannel() {
        Model.Channel ch = channels.get(index);
        String url = Library.get().streamUrl(ch);
        if (url == null) return;
        ensurePlayer(true);
        errorView.setVisibility(View.GONE);
        spinner.setVisibility(View.VISIBLE);
        player.setMediaItem(Players.item(url));
        player.prepare();
        player.play();
        Store.addRecent(ch.key());
        showLiveBar();
        Bg.cancel(stallCheck);
        Bg.later(stallCheck, 12_000);
    }

    private void zap(int d) {
        if (channels.isEmpty()) return;
        index = (index + d + channels.size()) % channels.size();
        triedAltFormat = false;
        retries = 0;
        if (player != null) player.stop();
        Bg.cancel(stallCheck);
        // Kurz warten: beim schnellen Durchschalten nicht jeden Sender laden
        showLiveBar();
        Bg.cancel(zapRunnable);
        Bg.later(zapRunnable, 350);
    }

    private void playVod(String url, long from) {
        if (url == null) {
            finish();
            return;
        }
        ensurePlayer(false);
        player.setMediaItem(Players.item(url), Math.max(0, from));
        player.prepare();
        player.play();
        if (movie != null) {
            vbTitle.setText(movie.name);
            vbSub.setText(movie.year == null ? "Film" : "Film  ·  " + movie.year);
        } else if (episode != null) {
            String sName = queueSeries != null ? queueSeries.name : "";
            String t = episode.title == null ? "" : episode.title.replaceFirst("(?i)^.*?S\\d{1,2}\\s*E\\d{1,3}\\s*[-:.]*\\s*", "");
            vbTitle.setText(sName);
            vbSub.setText("Staffel " + episode.season + ", Folge " + episode.number + (t.isEmpty() ? "" : "  ·  " + t));
        }
        nextCancelled = false;
        nextCard.setVisibility(View.GONE);
        showVodBar();
        Bg.cancel(saveTick);
        Bg.later(saveTick, 1000);
    }

    private void onEnded() {
        savePosition();
        Model.Episode n = nextEpisode();
        if (episode != null && n != null && Prefs.autoNext() && !nextCancelled) {
            playEpisode(n);
        } else {
            finish();
        }
    }

    private Model.Episode nextEpisode() {
        if (episode == null || queue == null) return null;
        int i = queue.indexOf(episode);
        return i >= 0 && i + 1 < queue.size() ? queue.get(i + 1) : null;
    }

    private void playEpisode(Model.Episode n) {
        savePosition();
        episode = n;
        retries = 0;
        playVod(Library.get().streamUrl(n), Store.position(n.key()));
    }

    private void checkNext() {
        if (episode == null || player == null || !Prefs.autoNext() || nextCancelled) return;
        long dur = player.getDuration();
        long pos = player.getCurrentPosition();
        Model.Episode n = nextEpisode();
        if (n == null || dur <= 0 || dur == C.TIME_UNSET) return;
        long left = dur - pos;
        if (left < 20_000 && left > 0) {
            nextCard.setText("▶  Nächste Folge in " + Math.max(1, left / 1000) + " s   ·   S" + n.season + " F" + n.number
                    + "      (OK = jetzt, Zurück = abbrechen)");
            nextCard.setVisibility(View.VISIBLE);
        } else {
            nextCard.setVisibility(View.GONE);
        }
    }

    private void savePosition() {
        if (player == null || "live".equals(type)) return;
        long pos = player.getCurrentPosition();
        long dur = player.getDuration();
        if (dur <= 0 || dur == C.TIME_UNSET) return;
        Store.Resume r = new Store.Resume();
        r.pos = pos;
        r.dur = dur;
        r.time = System.currentTimeMillis();
        if (movie != null) {
            r.key = movie.key();
            r.title = movie.name;
            r.subtitle = movie.year == null ? "Film" : movie.year;
            r.image = movie.poster;
        } else if (episode != null) {
            r.key = episode.key();
            r.title = queueSeries != null ? queueSeries.name : "Serie";
            r.subtitle = "S" + episode.season + " F" + episode.number;
            r.image = queueSeries != null ? queueSeries.poster : episode.image;
            r.seriesId = episode.seriesId;
        } else return;
        Store.saveResume(r);
    }

    // ---------------- Leisten ----------------

    private void showLiveBar() {
        if (liveBar == null) return;
        Model.Channel ch = channels.get(index);
        lbNum.setText(String.valueOf(ch.num > 0 && "all".equals(catKey) ? ch.num : index + 1));
        lbName.setText(ch.name);
        lbStar.setVisibility(Store.isFavorite(ch.key()) ? View.VISIBLE : View.GONE);
        lbCat.setText(Library.get().categoryName(catKey));
        lbClock.setText(Ui.time(System.currentTimeMillis()));
        Img.logo(lbLogo, ch.logo, ch.name);
        Model.Programme p = Epg.now(ch);
        if (p != null) {
            lbNow.setText(p.title);
            lbTime.setText(Ui.time(p.start) + " – " + Ui.time(p.end) + "  ·  noch " + Ui.minutes(p.end - System.currentTimeMillis()));
            lbProgress.setProgress(p.progress(System.currentTimeMillis()));
            lbProgress.setVisibility(View.VISIBLE);
        } else {
            lbNow.setText(Epg.get(ch) == null ? "Programm wird geladen …" : "Keine Programminfos");
            lbTime.setText("");
            lbProgress.setVisibility(View.INVISIBLE);
        }
        Model.Programme n = Epg.next(ch);
        lbNext.setText(n == null ? "" : "Danach   " + Ui.time(n.start) + "   " + n.title);
        showOverlay(liveBar);
        View help = ((ViewGroup) liveBar.getParent()).findViewWithTag("help");
        if (help != null) showOverlay(help);
        Bg.cancel(hideBars);
        Bg.later(hideBars, 5000);
    }

    private void showVodBar() {
        if (vodBar == null) return;
        updateVod();
        showOverlay(vodBar);
        Bg.cancel(hideBars);
        if (player != null && player.isPlaying()) Bg.later(hideBars, 4000);
    }

    private void updateVod() {
        if (vodBar == null || player == null) return;
        long dur = player.getDuration();
        long pos = player.getCurrentPosition();
        if (dur > 0 && dur != C.TIME_UNSET) {
            vbProgress.setProgress(pos / (float) dur);
            vbPos.setText(Ui.duration(pos));
            vbDur.setText("-" + Ui.duration(dur - pos));
        } else {
            vbPos.setText(Ui.duration(pos));
            vbDur.setText("");
        }
    }

    private void showOverlay(View v) {
        View s = shade();
        if (s != null && s.getAlpha() < 1f) s.animate().alpha(1f).setDuration(200).start();
        if (v.getVisibility() != View.VISIBLE || v.getAlpha() < 1f) {
            v.setVisibility(View.VISIBLE);
            v.setTranslationY(Ui.dp(this, 24));
            v.animate().alpha(1f).translationY(0).setDuration(220).start();
        }
    }

    private void hideOverlays() {
        View s = shade();
        if (s != null) s.animate().alpha(0f).setDuration(300).start();
        for (View v : new View[]{liveBar, vodBar}) {
            if (v == null) continue;
            v.animate().alpha(0f).translationY(Ui.dp(this, 24)).setDuration(260).start();
        }
        if (liveBar != null) {
            View help = ((ViewGroup) liveBar.getParent()).findViewWithTag("help");
            if (help != null) help.animate().alpha(0f).setDuration(260).start();
        }
    }

    private boolean overlayVisible() {
        View v = liveBar != null ? liveBar : vodBar;
        return v != null && v.getAlpha() > 0.5f;
    }

    // ---------------- Senderliste ----------------

    private void openList() {
        listPanel.setVisibility(View.VISIBLE);
        listPanel.setAlpha(0f);
        listPanel.setTranslationX(-Ui.dp(this, 40));
        listPanel.animate().alpha(1f).translationX(0).setDuration(200).start();
        hideOverlays();
        listAdapter.notifyDataSetChanged();
        ((LinearLayoutManager) listView.getLayoutManager()).scrollToPositionWithOffset(index, Ui.dp(this, 160));
        listView.post(() -> {
            RecyclerView.ViewHolder vh = listView.findViewHolderForAdapterPosition(index);
            if (vh != null) vh.itemView.requestFocus();
        });
    }

    private void closeList() {
        listPanel.animate().alpha(0f).translationX(-Ui.dp(this, 40)).setDuration(180)
                .withEndAction(() -> listPanel.setVisibility(View.GONE)).start();
        playerView.requestFocus();
    }

    private boolean listOpen() {
        return listPanel != null && listPanel.getVisibility() == View.VISIBLE;
    }

    private final class ListAdapter extends RecyclerView.Adapter<ListAdapter.VH> {
        final class VH extends RecyclerView.ViewHolder {
            final LinearLayout root;
            final ImageView logo;
            final TextView name, now;

            VH(LinearLayout root, ImageView logo, TextView name, TextView now) {
                super(root);
                this.root = root;
                this.logo = logo;
                this.name = name;
                this.now = now;
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context c = parent.getContext();
            LinearLayout v = new LinearLayout(c);
            v.setOrientation(LinearLayout.HORIZONTAL);
            v.setGravity(Gravity.CENTER_VERTICAL);
            v.setPadding(Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 16), Ui.dp(c, 8));
            v.setFocusable(true);
            v.setClickable(true);
            ImageView logo = new ImageView(c);
            v.addView(logo, Ui.lin(Ui.dp(c, 64), Ui.dp(c, 40)));
            LinearLayout t = new LinearLayout(c);
            t.setOrientation(LinearLayout.VERTICAL);
            TextView name = Ui.lines(Ui.text(c, 16, Ui.TEXT, Ui.semibold(c)), 1);
            t.addView(name);
            TextView now = Ui.lines(Ui.text(c, 13, Ui.TEXT2, Ui.medium(c)), 1);
            t.addView(now, Ui.margins(Ui.lin(Ui.MATCH, Ui.WRAP), 0, Ui.dp(c, 3), 0, 0));
            v.addView(t, Ui.margins(Ui.lin(0, Ui.WRAP, 1f), Ui.dp(c, 14), 0, 0, 0));
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(Ui.MATCH, Ui.WRAP);
            lp.bottomMargin = Ui.dp(c, 4);
            v.setLayoutParams(lp);
            VH vh = new VH(v, logo, name, now);
            float r = Ui.dp(c, 14);
            v.setOnFocusChangeListener((view, has) -> {
                view.setBackground(has ? Ui.round(0xFFFFFFFF, r) : null);
                name.setTextColor(has ? 0xFF111111 : Ui.TEXT);
                now.setTextColor(has ? 0xB3111111 : Ui.TEXT2);
                view.animate().scaleX(has ? 1.03f : 1f).scaleY(has ? 1.03f : 1f).setDuration(120).start();
            });
            v.setOnClickListener(view -> {
                int pos = vh.getBindingAdapterPosition();
                if (pos < 0) return;
                closeList();
                if (pos != index) {
                    index = pos;
                    triedAltFormat = false;
                    retries = 0;
                    playCurrentChannel();
                } else showLiveBar();
            });
            return vh;
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Model.Channel c = channels.get(position);
            h.name.setText((Store.isFavorite(c.key()) ? "★ " : "") + c.name);
            Img.logo(h.logo, c.logo, c.name);
            Model.Programme p = Epg.now(c);
            h.now.setText(p != null ? p.title : "");
            h.now.setVisibility(p != null ? View.VISIBLE : View.GONE);
        }

        @Override
        public int getItemCount() {
            return channels.size();
        }
    }

    // ---------------- Tonspur & Untertitel ----------------

    private void showTracks() {
        if (player == null) return;
        Tracks tracks = player.getCurrentTracks();
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        int audioN = 0;
        for (Tracks.Group g : tracks.getGroups()) {
            if (g.getType() != C.TRACK_TYPE_AUDIO) continue;
            for (int i = 0; i < g.length; i++) {
                if (!g.isTrackSupported(i)) continue;
                audioN++;
                Format f = g.getTrackFormat(i);
                String l = (g.isTrackSelected(i) ? "✓  " : "     ") + "Ton: " + trackName(f, audioN);
                final int fi = i;
                labels.add(l);
                actions.add(() -> player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                        .setOverrideForType(new TrackSelectionOverride(g.getMediaTrackGroup(), fi)).build()));
            }
        }
        boolean textOff = player.getTrackSelectionParameters().disabledTrackTypes.contains(C.TRACK_TYPE_TEXT);
        int subN = 0;
        List<String> subLabels = new ArrayList<>();
        List<Runnable> subActions = new ArrayList<>();
        for (Tracks.Group g : tracks.getGroups()) {
            if (g.getType() != C.TRACK_TYPE_TEXT) continue;
            for (int i = 0; i < g.length; i++) {
                if (!g.isTrackSupported(i)) continue;
                subN++;
                Format f = g.getTrackFormat(i);
                String l = (!textOff && g.isTrackSelected(i) ? "✓  " : "     ") + "Untertitel: " + trackName(f, subN);
                final int fi = i;
                subLabels.add(l);
                subActions.add(() -> player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setOverrideForType(new TrackSelectionOverride(g.getMediaTrackGroup(), fi)).build()));
            }
        }
        if (subN > 0) {
            labels.add((textOff ? "✓  " : "     ") + "Untertitel: Aus");
            actions.add(() -> player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()));
            labels.addAll(subLabels);
            actions.addAll(subActions);
        }
        if (labels.isEmpty()) {
            Ui.toast(this, "Keine weiteren Tonspuren oder Untertitel");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Tonspur & Untertitel")
                .setItems(labels.toArray(new String[0]), (d, w) -> actions.get(w).run())
                .show();
    }

    private static String trackName(Format f, int n) {
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

    // ---------------- Tasten ----------------

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e) {
        if (listOpen()) {
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                closeList();
                return true;
            }
            return super.onKeyDown(keyCode, e);
        }
        if ("live".equals(type)) return liveKey(keyCode, e) || super.onKeyDown(keyCode, e);
        return vodKey(keyCode, e) || super.onKeyDown(keyCode, e);
    }

    private boolean liveKey(int keyCode, KeyEvent e) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_CHANNEL_UP:
                zap(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
                zap(1);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                openList();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                showLiveBar();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                e.startTracking();
                return true;
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_INFO:
                showTracks();
                return true;
            case KeyEvent.KEYCODE_BOOKMARK:
                toggleFav();
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if ("live".equals(type) && !listOpen() && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
            toggleFav();
            return true;
        }
        return super.onKeyLongPress(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent e) {
        if ("live".equals(type) && !listOpen() && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
            if (e.isTracking() && !e.isCanceled()) {
                if (errorView.getVisibility() == View.VISIBLE) {
                    retries = 0;
                    triedAltFormat = false;
                    playCurrentChannel();
                } else if (overlayVisible()) hideOverlays();
                else showLiveBar();
            }
            return true;
        }
        if (!"live".equals(type) && (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            seekRepeat = 0;
        }
        return super.onKeyUp(keyCode, e);
    }

    private void toggleFav() {
        Model.Channel ch = channels.get(index);
        boolean fav = Store.toggleFavorite(ch.key());
        Ui.toast(this, fav ? "★  " + ch.name + " zu Favoriten hinzugefügt" : ch.name + " aus Favoriten entfernt");
        showLiveBar();
    }

    private boolean vodKey(int keyCode, KeyEvent e) {
        if (player == null) return false;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_SPACE:
                if (nextCard.getVisibility() == View.VISIBLE && keyCode != KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                    Model.Episode n = nextEpisode();
                    if (n != null) {
                        playEpisode(n);
                        return true;
                    }
                }
                if (errorView.getVisibility() == View.VISIBLE) {
                    errorView.setVisibility(View.GONE);
                    retries = 0;
                    long pos = player.getCurrentPosition();
                    player.prepare();
                    player.seekTo(pos);
                    player.play();
                    return true;
                }
                if (player.isPlaying()) player.pause();
                else player.play();
                showVodBar();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                player.play();
                showVodBar();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                player.pause();
                showVodBar();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                seek(1, e.getRepeatCount());
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                seek(-1, e.getRepeatCount());
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                showVodBar();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_MENU:
                showTracks();
                return true;
            case KeyEvent.KEYCODE_MEDIA_NEXT: {
                Model.Episode n = nextEpisode();
                if (n != null) playEpisode(n);
                return true;
            }
            default:
                return false;
        }
    }

    private void seek(int dir, int repeat) {
        long step = repeat < 4 ? 10_000 : repeat < 12 ? 30_000 : 90_000;
        long dur = player.getDuration();
        long target = player.getCurrentPosition() + dir * step;
        if (target < 0) target = 0;
        if (dur > 0 && dur != C.TIME_UNSET && target > dur - 2000) target = dur - 2000;
        player.seekTo(target);
        showVodBar();
        updateVod();
    }

    @Override
    public void onBackPressed() {
        if (listOpen()) {
            closeList();
            return;
        }
        if (nextCard != null && nextCard.getVisibility() == View.VISIBLE) {
            nextCancelled = true;
            nextCard.setVisibility(View.GONE);
            return;
        }
        if (!"live".equals(type) && overlayVisible() && player != null && player.isPlaying()) {
            hideOverlays();
            return;
        }
        super.onBackPressed();
    }

    // ---------------- Lebenszyklus ----------------

    @Override
    protected void onPause() {
        super.onPause();
        savePosition();
        Bg.cancel(saveTick);
        Bg.cancel(stallCheck);
        if (player != null) player.pause();
        wasPaused = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (player != null && wasPaused) {
            wasPaused = false;
            if ("live".equals(type)) {
                // Live nach Pause neu starten, sonst hängt man zeitlich hinterher
                playCurrentChannel();
            } else {
                player.play();
                Bg.later(saveTick, 1000);
            }
        }
    }

    @Override
    protected void onDestroy() {
        Bg.cancel(saveTick);
        Bg.cancel(hideBars);
        Bg.cancel(zapRunnable);
        Bg.cancel(stallCheck);
        if (player != null) {
            playerView.setPlayer(null);
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}
