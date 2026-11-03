package app.antenne.tv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
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
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Vollbild-Player. Live: ▲▼ schaltet um, OK zeigt die Infoleiste, ◀ öffnet die Senderliste,
 * OK lang = Favorit, Menü = Optionen (Qualität, Ton, Untertitel, Puffer, Player).
 * Filme/Serien: ◀▶ spulen, OK Pause, Menü/▼ = Optionen.
 *
 * Qualität „Automatisch“: Ruckelt ein Sender, wechselt der Player selbst auf eine leichtere
 * Fassung desselben Senders (z. B. FHD → HD → SD). Bei HLS passt ExoPlayer die Stufe laufend an.
 */
public class PlayerActivity extends Activity implements Engine.Callback {

    /** Folgen der Serie, aus der gestartet wurde (für „Nächste Folge“). */
    static List<Model.Episode> queue;
    static Model.Series queueSeries;
    /** Für diese Sitzung gemerkte, automatisch gesenkte Qualität je Sender. */
    private static final Map<String, Integer> AUTO_STEP = new HashMap<>();

    // Player-Oberfläche ist immer dunkel (wie bei Apple TV), unabhängig vom Hell-Modus
    private static final int W = 0xFFFFFFFF, W2 = 0xB3FFFFFF, W3 = 0x80FFFFFF;
    private static final int PANEL = 0xE61C1C20;

    private String type;
    private Engine engine;
    private String engineId;
    private FrameLayout root;
    private ProgressBar spinner;
    private TextView errorView;

    // Live
    private List<Model.Channel> channels = new ArrayList<>();
    private int index;
    private String catKey;
    private boolean triedAltFormat;
    private int retries;
    private List<Model.Channel> variants = new ArrayList<>();
    private int variant;          // aktuelle Fassung in variants
    private boolean manualQuality; // Qualität von Hand gewählt → keine Automatik
    private String currentUrl;

    // Hänger-Erkennung für die automatische Qualität
    private boolean firstFrame;
    private long bufferingSince;
    private final List<Long> stalls = new ArrayList<>();
    private boolean hintShown;

    // VOD
    private Model.Movie movie;
    private Model.Episode episode;

    // Overlays
    private LinearLayout liveBar, vodBar;
    private ImageView lbLogo;
    private TextView lbNum, lbName, lbStar, lbNow, lbTime, lbNext, lbClock, lbCat, lbQuality;
    private Ui.Bar lbProgress;
    private TextView vbTitle, vbSub, vbPos, vbDur, vbState;
    private Ui.Bar vbProgress;
    private View shade, help;
    private FrameLayout listPanel;
    private TvList listView;
    private ListAdapter listAdapter;
    private TextView nextCard;
    private boolean nextCancelled;
    private boolean wasPaused;

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
    /** Startet ein Sender nach 12 s nicht: Format/Qualität wechseln oder Meldung zeigen. */
    private final Runnable stallCheck = this::onStartTimeout;
    /** Hängt der Stream länger als 6 s: als Hänger zählen. */
    private final Runnable longStall = () -> registerStall(true);

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Library.get().profile == null) {
            finish();
            return;
        }
        type = getIntent().getStringExtra("type");
        engineId = Prefs.engine();
        buildUi();
        if ("live".equals(type)) {
            catKey = getIntent().getStringExtra("cat");
            channels = Library.get().channelsFor(catKey);
            Model.Channel c = Library.get().channel(getIntent().getStringExtra("key"));
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
            playVod(Library.get().streamUrl(movie), getIntent().getLongExtra("pos", Store.position(movie.key())));
        } else {
            String id = getIntent().getStringExtra("id");
            if (queue != null) for (Model.Episode e : queue) if (e.id.equals(id)) episode = e;
            if (episode == null) {
                finish();
                return;
            }
            playVod(Library.get().streamUrl(episode), getIntent().getLongExtra("pos", 0));
        }
    }

    // ---------------- Player-Engine ----------------

    private boolean isLive() {
        return "live".equals(type);
    }

    private void ensureEngine() {
        if (engine != null) return;
        String id = "ext".equals(engineId) ? "exo" : engineId;
        try {
            if ("vlc".equals(id)) engine = new VlcEngine(this, isLive(), this);
            else engine = new ExoEngine(this, isLive(), "exo_sw".equals(id), this);
        } catch (Throwable t) {
            // VLC nicht verfügbar (z. B. Prozessor nicht unterstützt) → Standard-Player
            engineId = "exo";
            engine = new ExoEngine(this, isLive(), false, this);
            Ui.toast(this, "VLC-Engine nicht verfügbar – Standard-Player wird verwendet");
        }
        root.addView(engine.view(), 0, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
    }

    private void switchEngine(String id) {
        long pos = engine != null && !isLive() ? engine.position() : 0;
        if ("ext".equals(id)) {
            openExternal(currentUrl);
            return;
        }
        engineId = id;
        Prefs.setEngine(id);
        if (engine != null) {
            root.removeView(engine.view());
            engine.release();
            engine = null;
        }
        retries = 0;
        if (isLive()) playCurrentChannel();
        else playVod(currentUrl, pos);
        Ui.toast(this, "Player: " + Prefs.engineName(id));
    }

    private void openExternal(String url) {
        if (url == null) return;
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(Uri.parse(url), "video/*");
        String title = isLive() ? channels.get(index).name : movie != null ? movie.name : episode != null ? episode.title : "";
        i.putExtra("title", title);
        try {
            startActivity(Intent.createChooser(i, "Abspielen mit …"));
        } catch (Exception e) {
            Ui.toast(this, "Keine passende Player-App installiert (z. B. VLC)");
        }
    }

    // ---------------- Engine.Callback ----------------

    @Override
    public void onBuffering(boolean buffering) {
        spinner.setVisibility(buffering ? View.VISIBLE : View.GONE);
        if (buffering) {
            if (firstFrame && bufferingSince == 0) {
                bufferingSince = SystemClock.uptimeMillis();
                Bg.cancel(longStall);
                Bg.later(longStall, 6000);
            }
        } else {
            if (bufferingSince > 0) {
                Bg.cancel(longStall);
                long d = SystemClock.uptimeMillis() - bufferingSince;
                bufferingSince = 0;
                if (d > 700) registerStall(false);
            }
        }
    }

    @Override
    public void onReady() {
        errorView.setVisibility(View.GONE);
        retries = 0;
        Bg.cancel(stallCheck);
    }

    @Override
    public void onFirstFrame() {
        firstFrame = true;
        Bg.cancel(stallCheck);
    }

    @Override
    public void onPlaying(boolean playing) {
        if (vbState != null) vbState.setText(playing ? "" : "❚❚  Pausiert");
    }

    @Override
    public void onEnded() {
        if (!isLive()) {
            onVodEnded();
        } else if (retries < 3) {
            retries++; // Live-Stream abgebrochen → neu verbinden
            Bg.later(this::playCurrentChannel, 800);
        } else {
            onError("Der Sender hat die Verbindung beendet.", false, false);
        }
    }

    @Override
    public void onError(String message, boolean http, boolean retryable) {
        spinner.setVisibility(View.GONE);
        Bg.cancel(longStall);
        if (isLive()) {
            if (retries < 2 && !http) {
                retries++;
                Bg.later(this::playCurrentChannel, 1200);
                return;
            }
            if (tryAltFormat()) return;
            if (stepDownQuality("Sender lief nicht – leichtere Fassung wird versucht")) return;
        } else if (retries < 1 && retryable) {
            retries++;
            long pos = engine.position();
            engine.play(currentUrl, pos);
            return;
        }
        errorView.setText(message + "\n\nOK = nochmal versuchen   ·   Menü = Player wechseln");
        errorView.setVisibility(View.VISIBLE);
    }

    // ---------------- Automatische Qualität ----------------

    /** Zählt Hänger; ab 2 Hängern in 45 s (oder einem langen) wird die Qualität gesenkt. */
    private void registerStall(boolean longOne) {
        if (!firstFrame || manualQuality) return;
        long now = SystemClock.uptimeMillis();
        stalls.add(now);
        while (!stalls.isEmpty() && now - stalls.get(0) > 45_000) stalls.remove(0);
        if (longOne || stalls.size() >= 2) {
            stalls.clear();
            if (isLive() && stepDownQuality("Qualität automatisch gesenkt, damit es flüssig läuft")) return;
            if (!hintShown) {
                hintShown = true;
                Ui.toast(this, "Stream ruckelt – im Menü Puffer erhöhen oder Player wechseln");
            }
        }
    }

    /** Wechselt auf die nächstleichtere Fassung desselben Senders. */
    private boolean stepDownQuality(String msg) {
        if (!isLive() || manualQuality || variant + 1 >= variants.size()) return false;
        variant++;
        AUTO_STEP.put(Names.key(channels.get(index).rawName), variant);
        Ui.toast(this, msg + " (" + Library.qualityLabel(Library.qualityRank(variants.get(variant).rawName)) + ")");
        triedAltFormat = false;
        retries = 0;
        startChannelUrl(Library.get().streamUrl(variants.get(variant)));
        return true;
    }

    private void onStartTimeout() {
        if (engine == null || !isLive() || engine.isReady()) return;
        if (tryAltFormat()) {
            Bg.later(stallCheck, 12_000);
            return;
        }
        if (stepDownQuality("Sender startet nicht – leichtere Fassung wird versucht")) return;
        spinner.setVisibility(View.GONE);
        errorView.setText("Dieser Sender antwortet gerade nicht.\nOK = nochmal versuchen   ·   ▲▼ = anderer Sender   ·   Menü = Player wechseln");
        errorView.setVisibility(View.VISIBLE);
    }

    /** Probiert einmal das andere Live-Format (TS ↔ HLS). */
    private boolean tryAltFormat() {
        if (triedAltFormat || engine == null) return false;
        Model.Channel ch = variants.isEmpty() ? channels.get(index) : variants.get(variant);
        Xtream x = Library.get().xtream;
        if (ch.url != null || x == null) return false;
        triedAltFormat = true;
        String ext = "ts".equals(Prefs.liveFormat()) ? ".m3u8" : ".ts";
        startChannelUrl(x.base + "/live/" + Uri.encode(x.user) + "/" + Uri.encode(x.pass) + "/" + ch.id + ext);
        return true;
    }

    // ---------------- Oberfläche ----------------

    private void buildUi() {
        Context c = this;
        root = new FrameLayout(c);
        root.setBackgroundColor(0xFF000000);

        spinner = new ProgressBar(c);
        root.addView(spinner, Ui.frame(Ui.dp(c, 52), Ui.dp(c, 52), Gravity.CENTER));

        errorView = Ui.text(c, 17, W, Ui.semibold(c));
        errorView.setGravity(Gravity.CENTER);
        errorView.setLineSpacing(Ui.dp(c, 3), 1f);
        errorView.setPadding(Ui.dp(c, 28), Ui.dp(c, 20), Ui.dp(c, 28), Ui.dp(c, 20));
        errorView.setBackground(Ui.round(PANEL, Ui.dp(c, 22)));
        errorView.setVisibility(View.GONE);
        root.addView(errorView, Ui.frame(Ui.dp(c, 620), Ui.WRAP, Gravity.CENTER));

        shade = new View(c);
        shade.setBackground(Ui.vgrad(0x00000000, 0xD9000000));
        root.addView(shade, Ui.frame(Ui.MATCH, Ui.dp(c, 280), Gravity.BOTTOM));

        if (isLive()) buildLiveBar(root);
        else buildVodBar(root);

        nextCard = Ui.text(c, 15, 0xFF111111, Ui.semibold(c));
        nextCard.setPadding(Ui.dp(c, 22), Ui.dp(c, 14), Ui.dp(c, 22), Ui.dp(c, 14));
        nextCard.setBackground(Ui.round(0xFFF2F2F5, Ui.dp(c, 16)));
        nextCard.setVisibility(View.GONE);
        FrameLayout.LayoutParams nl = Ui.frame(Ui.WRAP, Ui.WRAP, Gravity.BOTTOM | Gravity.END);
        nl.setMargins(0, 0, Ui.dp(c, 48), Ui.dp(c, 170));
        root.addView(nextCard, nl);

        setContentView(root);
    }

    private TextView pText(float sp, int color, boolean bold) {
        return Ui.text(this, sp, color, bold ? Ui.bold(this) : Ui.medium(this));
    }

    private void buildLiveBar(FrameLayout root) {
        Context c = this;
        liveBar = new LinearLayout(c);
        liveBar.setOrientation(LinearLayout.HORIZONTAL);
        liveBar.setGravity(Gravity.CENTER_VERTICAL);
        int p = Ui.dp(c, 20);
        liveBar.setPadding(p, p, Ui.dp(c, 26), p);
        liveBar.setBackground(Ui.round(PANEL, Ui.dp(c, 26)));

        FrameLayout tile = new FrameLayout(c);
        tile.setBackground(Ui.round(0x1FFFFFFF, Ui.dp(c, 14)));
        tile.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 10));
        lbLogo = new ImageView(c);
        tile.addView(lbLogo, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        liveBar.addView(tile, Ui.lin(Ui.dp(c, 132), Ui.dp(c, 80)));

        LinearLayout mid = new LinearLayout(c);
        mid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout top = new LinearLayout(c);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        lbNum = pText(14, W3, true);
        top.addView(lbNum, Ui.margins(Ui.lin(Ui.WRAP, Ui.WRAP), 0, 0, Ui.dp(c, 10), 0));
        lbName = Ui.lines(pText(21, W, true), 1);
        top.addView(lbName, Ui.lin(0, Ui.WRAP, 1f));
        lbStar = pText(18, Ui.FAV, true);
        lbStar.setText("★");
        top.addView(lbStar, Ui.margins(Ui.lin(Ui.WRAP, Ui.WRAP), Ui.dp(c, 8), 0, 0, 0));
        lbQuality = Ui.badge(c, "", 0x33FFFFFF, W);
        top.addView(lbQuality, Ui.margins(Ui.lin(Ui.WRAP, Ui.WRAP), Ui.dp(c, 10), 0, 0, 0));
        lbCat = Ui.lines(pText(13, W3, false), 1);
        lbCat.setMaxWidth(Ui.dp(c, 200));
        top.addView(lbCat, Ui.margins(Ui.lin(Ui.WRAP, Ui.WRAP), Ui.dp(c, 12), 0, Ui.dp(c, 14), 0));
        lbClock = pText(19, W, true);
        top.addView(lbClock);
        mid.addView(top, Ui.lin(Ui.MATCH, Ui.WRAP));

        LinearLayout nowRow = new LinearLayout(c);
        nowRow.setOrientation(LinearLayout.HORIZONTAL);
        nowRow.setGravity(Gravity.CENTER_VERTICAL);
        nowRow.addView(Ui.badge(c, "JETZT", 0xFFE5E5EA, 0xFF111111));
        lbNow = Ui.lines(pText(16, W, true), 1);
        nowRow.addView(lbNow, Ui.margins(Ui.lin(0, Ui.WRAP, 1f), Ui.dp(c, 10), 0, 0, 0));
        lbTime = pText(13, W2, false);
        nowRow.addView(lbTime, Ui.margins(Ui.lin(Ui.WRAP, Ui.WRAP), Ui.dp(c, 12), 0, 0, 0));
        mid.addView(nowRow, Ui.margins(Ui.lin(Ui.MATCH, Ui.WRAP), 0, Ui.dp(c, 12), 0, 0));
        lbProgress = new Ui.Bar(c, 0x33FFFFFF, W);
        mid.addView(lbProgress, Ui.margins(Ui.lin(Ui.MATCH, Ui.dp(c, 4)), 0, Ui.dp(c, 10), 0, 0));
        lbNext = Ui.lines(pText(13, W2, false), 1);
        mid.addView(lbNext, Ui.margins(Ui.lin(Ui.MATCH, Ui.WRAP), 0, Ui.dp(c, 9), 0, 0));
        liveBar.addView(mid, Ui.margins(Ui.lin(0, Ui.WRAP, 1f), Ui.dp(c, 20), 0, 0, 0));

        FrameLayout.LayoutParams lp = Ui.frame(Ui.MATCH, Ui.WRAP, Gravity.BOTTOM);
        lp.setMargins(Ui.dp(c, 44), 0, Ui.dp(c, 44), Ui.dp(c, 38));
        root.addView(liveBar, lp);

        TextView h = pText(12, W3, false);
        h.setText("▲▼ Sender   ◀ Senderliste   OK lang ★ Favorit   Menü: Qualität · Ton · Player");
        FrameLayout.LayoutParams hl = Ui.frame(Ui.WRAP, Ui.WRAP, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        hl.bottomMargin = Ui.dp(c, 12);
        root.addView(h, hl);
        help = h;

        // Senderliste links
        listPanel = new FrameLayout(c);
        listPanel.setBackground(Ui.hgrad(0xF5101013, 0x00101013));
        listPanel.setVisibility(View.GONE);
        listView = new TvList(c, false, 0.4f);
        listView.setPadding(Ui.dp(c, 32), Ui.dp(c, 30), Ui.dp(c, 70), Ui.dp(c, 30));
        listAdapter = new ListAdapter();
        listView.setAdapter(listAdapter);
        listPanel.addView(listView, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        root.addView(listPanel, Ui.frame(Ui.dp(c, 460), Ui.MATCH, Gravity.START));
    }

    private void buildVodBar(FrameLayout root) {
        Context c = this;
        vodBar = new LinearLayout(c);
        vodBar.setOrientation(LinearLayout.VERTICAL);
        vbTitle = Ui.lines(pText(26, W, true), 1);
        vodBar.addView(vbTitle);
        vbSub = Ui.lines(pText(14, W2, false), 1);
        vodBar.addView(vbSub, Ui.margins(Ui.lin(Ui.MATCH, Ui.WRAP), 0, Ui.dp(c, 6), 0, 0));
        vbProgress = new Ui.Bar(c, 0x40FFFFFF, W);
        vodBar.addView(vbProgress, Ui.margins(Ui.lin(Ui.MATCH, Ui.dp(c, 6)), 0, Ui.dp(c, 18), 0, 0));
        LinearLayout times = new LinearLayout(c);
        times.setOrientation(LinearLayout.HORIZONTAL);
        vbPos = pText(13, W2, true);
        times.addView(vbPos);
        vbState = pText(13, W, true);
        vbState.setGravity(Gravity.CENTER);
        times.addView(vbState, Ui.lin(0, Ui.WRAP, 1f));
        vbDur = pText(13, W2, true);
        times.addView(vbDur);
        vodBar.addView(times, Ui.margins(Ui.lin(Ui.MATCH, Ui.WRAP), 0, Ui.dp(c, 10), 0, 0));
        TextView h = pText(12, W3, false);
        h.setText("◀ ▶ spulen   OK Pause   Menü / ▼ Qualität · Ton · Untertitel · Player");
        vodBar.addView(h, Ui.margins(Ui.lin(Ui.WRAP, Ui.WRAP), 0, Ui.dp(c, 12), 0, 0));
        FrameLayout.LayoutParams lp = Ui.frame(Ui.MATCH, Ui.WRAP, Gravity.BOTTOM);
        lp.setMargins(Ui.dp(c, 56), 0, Ui.dp(c, 56), Ui.dp(c, 30));
        root.addView(vodBar, lp);
    }

    // ---------------- Wiedergabe ----------------

    private void playCurrentChannel() {
        Model.Channel ch = channels.get(index);
        int wanted = manualQuality && variant < variants.size() ? Library.qualityRank(variants.get(variant).rawName) : 2;
        variants = Library.get().variants(ch);
        if (!manualQuality) {
            // Mit genau der Fassung starten, die gewählt wurde; gesenkt wird nur bei Rucklern
            variant = Math.max(0, variants.indexOf(ch));
            int cap = Prefs.maxQuality();
            if (cap > 0) {
                int capRank = cap >= 2160 ? 4 : cap >= 1080 ? 3 : cap >= 720 ? 2 : 1;
                if (Library.qualityRank(variants.get(variant).rawName) > capRank) {
                    for (int i = variant; i < variants.size(); i++) {
                        if (Library.qualityRank(variants.get(i).rawName) <= capRank) {
                            variant = i;
                            break;
                        }
                    }
                }
            }
            Integer remembered = AUTO_STEP.get(Names.key(ch.rawName));
            if (remembered != null && remembered < variants.size()) variant = Math.max(variant, remembered);
        } else {
            // Bei manueller Wahl dieselbe Stufe auch beim nächsten Sender versuchen
            variant = 0;
            for (int i = 0; i < variants.size(); i++) {
                if (Library.qualityRank(variants.get(i).rawName) <= wanted) {
                    variant = i;
                    break;
                }
            }
        }
        if (variant >= variants.size()) variant = 0;
        String url = Library.get().streamUrl(variants.get(variant));
        if (url == null) return;
        Store.addRecent(ch.key());
        if ("ext".equals(engineId)) {
            openExternal(url);
            return;
        }
        startChannelUrl(url);
        showLiveBar();
    }

    private void startChannelUrl(String url) {
        ensureEngine();
        currentUrl = url;
        firstFrame = false;
        bufferingSince = 0;
        stalls.clear();
        errorView.setVisibility(View.GONE);
        spinner.setVisibility(View.VISIBLE);
        engine.play(url, 0);
        Bg.cancel(stallCheck);
        Bg.later(stallCheck, 12_000);
        updateQualityBadge();
    }

    private void zap(int d) {
        if (channels.isEmpty()) return;
        index = (index + d + channels.size()) % channels.size();
        triedAltFormat = false;
        retries = 0;
        if (engine != null) engine.stop();
        Bg.cancel(stallCheck);
        Bg.cancel(longStall);
        showLiveBar();
        // Kurz warten: beim schnellen Durchschalten nicht jeden Sender laden
        Bg.cancel(zapRunnable);
        Bg.later(zapRunnable, 380);
    }

    private void playVod(String url, long from) {
        if (url == null) {
            finish();
            return;
        }
        currentUrl = url;
        if ("ext".equals(engineId)) {
            openExternal(url);
            finish();
            return;
        }
        ensureEngine();
        firstFrame = false;
        engine.play(url, Math.max(0, from));
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

    private void onVodEnded() {
        savePosition();
        Model.Episode n = nextEpisode();
        if (episode != null && n != null && Prefs.autoNext() && !nextCancelled) playEpisode(n);
        else finish();
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
        if (episode == null || engine == null || !Prefs.autoNext() || nextCancelled) return;
        long dur = engine.duration();
        Model.Episode n = nextEpisode();
        if (n == null || dur <= 0) return;
        long left = dur - engine.position();
        if (left < 20_000 && left > 0) {
            nextCard.setText("▶  Nächste Folge in " + Math.max(1, left / 1000) + " s  ·  S" + n.season + " F" + n.number
                    + "     OK = jetzt · Zurück = abbrechen");
            nextCard.setVisibility(View.VISIBLE);
        } else {
            nextCard.setVisibility(View.GONE);
        }
    }

    private void savePosition() {
        if (engine == null || isLive()) return;
        long dur = engine.duration();
        if (dur <= 0) return;
        Store.Resume r = new Store.Resume();
        r.pos = engine.position();
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

    private void updateQualityBadge() {
        if (lbQuality == null) return;
        if (variants.size() > 1) {
            String q = Library.qualityLabel(Library.qualityRank(variants.get(variant).rawName));
            lbQuality.setText(manualQuality ? q : "AUTO · " + q);
            lbQuality.setVisibility(View.VISIBLE);
        } else {
            lbQuality.setVisibility(View.GONE);
        }
    }

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
        long now = System.currentTimeMillis();
        if (p != null) {
            lbNow.setText(p.title);
            lbTime.setText(Ui.time(p.start) + " – " + Ui.time(p.end) + "  ·  noch " + Ui.minutes(p.end - now));
            lbProgress.setProgress(p.progress(now));
            lbProgress.setVisibility(View.VISIBLE);
        } else {
            lbNow.setText(Epg.get(ch) == null ? "Programm wird geladen …" : "Keine Programminfos");
            lbTime.setText("");
            lbProgress.setVisibility(View.INVISIBLE);
        }
        Model.Programme n = Epg.next(ch);
        lbNext.setText(n == null ? "" : "Danach   " + Ui.time(n.start) + "   " + n.title);
        updateQualityBadge();
        showOverlay(liveBar);
        if (help != null) showOverlay(help);
        Bg.cancel(hideBars);
        Bg.later(hideBars, 5000);
    }

    private void showVodBar() {
        if (vodBar == null) return;
        updateVod();
        showOverlay(vodBar);
        Bg.cancel(hideBars);
        if (engine != null && engine.isPlaying()) Bg.later(hideBars, 4000);
    }

    private void updateVod() {
        if (vodBar == null || engine == null) return;
        long dur = engine.duration();
        long pos = engine.position();
        if (dur > 0) {
            vbProgress.setProgress(pos / (float) dur);
            vbPos.setText(Ui.duration(pos));
            vbDur.setText("-" + Ui.duration(dur - pos));
        } else {
            vbPos.setText(Ui.duration(pos));
            vbDur.setText("");
        }
    }

    private void showOverlay(View v) {
        if (shade.getAlpha() < 1f) shade.animate().alpha(1f).setDuration(200).start();
        if (v.getVisibility() != View.VISIBLE || v.getAlpha() < 1f) {
            v.setVisibility(View.VISIBLE);
            v.setTranslationY(Ui.dp(this, 20));
            v.animate().alpha(1f).translationY(0).setDuration(220).start();
        }
    }

    private void hideOverlays() {
        shade.animate().alpha(0f).setDuration(300).start();
        for (View v : new View[]{liveBar, vodBar, help}) {
            if (v == null) continue;
            v.animate().alpha(0f).translationY(Ui.dp(this, 20)).setDuration(260).start();
        }
    }

    private boolean overlayVisible() {
        View v = liveBar != null ? liveBar : vodBar;
        return v != null && v.getAlpha() > 0.5f;
    }

    // ---------------- Optionen (Qualität, Ton, Untertitel, Puffer, Player) ----------------

    private void showOptions() {
        if (engine == null) return;
        hideOverlays();
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        // Qualität
        List<Engine.Track> video = engine.tracks(Engine.VIDEO);
        boolean hasVariants = isLive() && variants.size() > 1;
        String q;
        if (hasVariants) {
            String cur = Library.qualityLabel(Library.qualityRank(variants.get(variant).rawName));
            q = manualQuality ? cur : "Automatisch (" + cur + ")";
        } else if (!video.isEmpty()) {
            q = engine.autoQuality() ? "Automatisch" : selectedLabel(video);
        } else {
            q = "Automatisch";
        }
        labels.add("Qualität:  " + q);
        actions.add(() -> chooseQuality(video, hasVariants));

        List<Engine.Track> audio = engine.tracks(Engine.AUDIO);
        if (audio.size() > 1) {
            labels.add("Tonspur:  " + selectedLabel(audio));
            actions.add(() -> chooseTrack("Tonspur", audio, false));
        }
        List<Engine.Track> subs = engine.tracks(Engine.TEXT);
        if (!subs.isEmpty()) {
            labels.add("Untertitel:  " + (engine.subtitlesOff() ? "Aus" : selectedLabel(subs)));
            actions.add(() -> chooseTrack("Untertitel", subs, true));
        }
        labels.add("Puffer:  " + Prefs.BUFFER_NAMES[Prefs.buffer()].split(" ·")[0]);
        actions.add(this::chooseBuffer);
        labels.add("Player:  " + Prefs.engineName(engineId));
        actions.add(this::chooseEngine);
        if (isLive() && Library.get().xtream != null) {
            labels.add("Stream-Format:  " + ("ts".equals(Prefs.liveFormat()) ? "MPEG-TS" : "HLS"));
            actions.add(() -> {
                Prefs.setLiveFormat("ts".equals(Prefs.liveFormat()) ? "m3u8" : "ts");
                triedAltFormat = false;
                playCurrentChannel();
            });
        }
        Ui.choose(this, "Optionen", null, labels.toArray(new String[0]), -1, i -> actions.get(i).run());
    }

    private static String selectedLabel(List<Engine.Track> l) {
        for (Engine.Track t : l) if (t.selected) return t.label;
        return l.isEmpty() ? "–" : l.get(0).label;
    }

    private void chooseQuality(List<Engine.Track> video, boolean hasVariants) {
        List<String> names = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        int sel = 0;
        names.add("Automatisch – passt sich an, damit es flüssig läuft");
        acts.add(() -> {
            manualQuality = false;
            engine.setAutoQuality();
            if (hasVariants) {
                variant = 0;
                AUTO_STEP.remove(Names.key(channels.get(index).rawName));
                startChannelUrl(Library.get().streamUrl(variants.get(0)));
            }
            Ui.toast(this, "Qualität: Automatisch");
        });
        boolean auto = hasVariants ? !manualQuality : engine.autoQuality();
        if (hasVariants) {
            for (int i = 0; i < variants.size(); i++) {
                final int vi = i;
                Model.Channel v = variants.get(i);
                names.add(Library.qualityLabel(Library.qualityRank(v.rawName)) + "   ·   " + v.name);
                if (!auto && i == variant) sel = names.size() - 1;
                acts.add(() -> {
                    manualQuality = true;
                    variant = vi;
                    triedAltFormat = false;
                    startChannelUrl(Library.get().streamUrl(variants.get(vi)));
                });
            }
        }
        for (Engine.Track t : video) {
            names.add(t.label);
            if (!auto && t.selected) sel = names.size() - 1;
            acts.add(() -> {
                manualQuality = true;
                t.apply.run();
            });
        }
        String sub = names.size() == 1
                ? "Dieser Stream wird nur in einer Qualität gesendet. „Automatisch“ hält ihn trotzdem so flüssig wie möglich."
                : "Bei „Automatisch“ wechselt der Player selbst auf eine leichtere Stufe, wenn es ruckelt.";
        Ui.choose(this, "Qualität", sub, names.toArray(new String[0]), sel, i -> acts.get(i).run());
    }

    private void chooseTrack(String title, List<Engine.Track> tracks, boolean allowOff) {
        List<String> names = new ArrayList<>();
        int sel = -1;
        if (allowOff) {
            names.add("Aus");
            if (engine.subtitlesOff()) sel = 0;
        }
        for (Engine.Track t : tracks) {
            names.add(t.label);
            if (t.selected && !(allowOff && engine.subtitlesOff())) sel = names.size() - 1;
        }
        Ui.choose(this, title, null, names.toArray(new String[0]), sel, i -> {
            if (allowOff && i == 0) engine.setSubtitlesOff();
            else tracks.get(allowOff ? i - 1 : i).apply.run();
        });
    }

    private void chooseBuffer() {
        Ui.choose(this, "Puffer", "Grösserer Puffer = etwas längerer Start, dafür weniger Hänger. Der Stream startet dafür kurz neu.",
                Prefs.BUFFER_NAMES, Prefs.buffer(), i -> {
                    if (i == Prefs.buffer()) return;
                    Prefs.setBuffer(i);
                    switchEngine(engineId); // neu aufbauen, damit der Puffer gilt
                });
    }

    private void chooseEngine() {
        String[] ids = Prefs.ENGINE_IDS;
        int sel = java.util.Arrays.asList(ids).indexOf(engineId);
        Ui.choose(this, "Player", "Ruckelt ein Sender oder startet er nicht, probier einen anderen Player. "
                        + "„VLC-Engine“ spielt fast jedes Format ab, „Externe App“ öffnet z. B. VLC oder MX Player.",
                Prefs.ENGINE_NAMES, sel, i -> {
                    if (!ids[i].equals(engineId)) switchEngine(ids[i]);
                });
    }

    // ---------------- Senderliste ----------------

    private void openList() {
        listPanel.setVisibility(View.VISIBLE);
        listPanel.setAlpha(0f);
        listPanel.setTranslationX(-Ui.dp(this, 40));
        listPanel.animate().alpha(1f).translationX(0).setDuration(200).start();
        hideOverlays();
        listAdapter.notifyDataSetChanged();
        listView.post(() -> listView.focusPosition(index));
    }

    private void closeList() {
        listPanel.animate().alpha(0f).translationX(-Ui.dp(this, 40)).setDuration(180)
                .withEndAction(() -> listPanel.setVisibility(View.GONE)).start();
        root.requestFocus();
    }

    private boolean listOpen() {
        return listPanel != null && listPanel.getVisibility() == View.VISIBLE;
    }

    private final class ListAdapter extends RecyclerView.Adapter<ListAdapter.VH> {
        final class VH extends RecyclerView.ViewHolder {
            final ImageView logo;
            final TextView name, now;

            VH(LinearLayout root, ImageView logo, TextView name, TextView now) {
                super(root);
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
            v.addView(logo, Ui.lin(Ui.dp(c, 64), Ui.dp(c, 38)));
            LinearLayout t = new LinearLayout(c);
            t.setOrientation(LinearLayout.VERTICAL);
            TextView name = Ui.lines(Ui.text(c, 16, W, Ui.semibold(c)), 1);
            t.addView(name);
            TextView now = Ui.lines(Ui.text(c, 13, W2, Ui.medium(c)), 1);
            t.addView(now, Ui.margins(Ui.lin(Ui.MATCH, Ui.WRAP), 0, Ui.dp(c, 3), 0, 0));
            v.addView(t, Ui.margins(Ui.lin(0, Ui.WRAP, 1f), Ui.dp(c, 14), 0, 0, 0));
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(Ui.MATCH, Ui.WRAP);
            lp.bottomMargin = Ui.dp(c, 4);
            v.setLayoutParams(lp);
            VH vh = new VH(v, logo, name, now);
            float r = Ui.dp(c, 14);
            v.setOnFocusChangeListener((view, has) -> {
                view.setBackground(has ? Ui.round(0xFFF2F2F5, r) : null);
                name.setTextColor(has ? 0xFF111111 : W);
                now.setTextColor(has ? 0xB3111111 : W2);
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
        if (isLive()) return liveKey(keyCode, e) || super.onKeyDown(keyCode, e);
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
            case KeyEvent.KEYCODE_SETTINGS:
                showOptions();
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
        if (isLive() && !listOpen() && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
            toggleFav();
            return true;
        }
        return super.onKeyLongPress(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent e) {
        if (isLive() && !listOpen() && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
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
        return super.onKeyUp(keyCode, e);
    }

    private void toggleFav() {
        Model.Channel ch = channels.get(index);
        boolean fav = Store.toggleFavorite(ch.key());
        Ui.toast(this, fav ? "★  " + ch.name + " zu Favoriten hinzugefügt" : ch.name + " aus Favoriten entfernt");
        showLiveBar();
    }

    private boolean vodKey(int keyCode, KeyEvent e) {
        if (engine == null) return false;
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
                    engine.play(currentUrl, engine.position());
                    return true;
                }
                if (engine.isPlaying()) engine.pause();
                else engine.resume();
                showVodBar();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                engine.resume();
                showVodBar();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                engine.pause();
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
            case KeyEvent.KEYCODE_INFO:
            case KeyEvent.KEYCODE_SETTINGS:
                showOptions();
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
        long dur = engine.duration();
        long target = engine.position() + dir * step;
        if (target < 0) target = 0;
        if (dur > 0 && target > dur - 2000) target = dur - 2000;
        engine.seekTo(target);
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
        if (!isLive() && overlayVisible() && engine != null && engine.isPlaying()) {
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
        Bg.cancel(longStall);
        if (engine != null) engine.pause();
        wasPaused = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (engine != null && wasPaused) {
            wasPaused = false;
            if (isLive()) {
                // Live nach Pause neu starten, sonst hängt man zeitlich hinterher
                playCurrentChannel();
            } else {
                engine.resume();
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
        Bg.cancel(longStall);
        if (engine != null) {
            engine.release();
            engine = null;
        }
        super.onDestroy();
    }
}
