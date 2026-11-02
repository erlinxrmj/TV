package app.antenne.tv;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Das TV-Programm als Raster, komplett selbst gezeichnet (flüssig auch auf alten Fernsehern).
 * Links Senderlogos, oben die Zeitleiste, eine Linie markiert „jetzt“.
 */
public class GuideView extends View {

    public interface Listener {
        void onSelect(Model.Channel c, @Nullable Model.Programme p);

        void onPlay(Model.Channel c);

        void onFavorite(Model.Channel c);

        /** Pfeil hoch in der ersten Zeile. */
        void onLeaveTop();
    }

    private static final long MIN = 60_000L;
    private static final long HALF_HOUR = 30 * MIN;
    private static final long VISIBLE_SPAN = 150 * MIN; // 2,5 Stunden sichtbar

    private List<Model.Channel> channels = new ArrayList<>();
    private Listener listener;
    private int selRow;
    private long cursor;          // Zeitpunkt, an dem die Auswahl „steht“
    private long windowStart;     // Ziel-Fensteranfang
    private long windowAnim;      // animierter Fensteranfang (ms)
    private float scrollY, scrollTarget;
    private ValueAnimator scrollAnim, windowAnimator;

    private final float logoCol, header, rowH, rowGap, radius, pad;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint title = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint small = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint headerText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint nameText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint star = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    private final Map<String, Bitmap> logos = new HashMap<>();
    private final Set<String> logoLoading = new HashSet<>();
    private final Map<String, Drawable> monograms = new HashMap<>();
    private boolean focusedNow;

    public GuideView(Context c) {
        super(c);
        setFocusable(true);
        setFocusableInTouchMode(true);
        logoCol = Ui.dp(c, 176);
        header = Ui.dp(c, 34);
        rowH = Ui.dp(c, 60);
        rowGap = Ui.dp(c, 6);
        radius = Ui.dp(c, 12);
        pad = Ui.dp(c, 12);
        title.setTypeface(Ui.semibold(c));
        title.setTextSize(Ui.dp(c, 15));
        small.setTypeface(Ui.medium(c));
        small.setTextSize(Ui.dp(c, 12));
        headerText.setTypeface(Ui.semibold(c));
        headerText.setTextSize(Ui.dp(c, 13));
        nameText.setTypeface(Ui.semibold(c));
        nameText.setTextSize(Ui.dp(c, 13));
        star.setTypeface(Ui.bold(c));
        star.setTextSize(Ui.dp(c, 13));
        star.setColor(Ui.FAV);
        long now = System.currentTimeMillis();
        cursor = now;
        windowStart = floorHalf(now) - HALF_HOUR; // eine halbe Stunde Rückblick
        windowAnim = windowStart;
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public void setChannels(List<Model.Channel> list, int focusRow) {
        channels = list == null ? new ArrayList<>() : list;
        selRow = Math.max(0, Math.min(focusRow, channels.size() - 1));
        if (channels.isEmpty()) selRow = 0;
        long now = System.currentTimeMillis();
        cursor = now;
        windowStart = floorHalf(now) - HALF_HOUR;
        windowAnim = windowStart;
        scrollY = scrollTarget = targetScrollFor(selRow);
        invalidate();
        notifySelect();
    }

    public Model.Channel selectedChannel() {
        return channels.isEmpty() ? null : channels.get(selRow);
    }

    public int selectedRow() {
        return selRow;
    }

    private static long floorHalf(long t) {
        return t - (t % HALF_HOUR);
    }

    private float pxPerMs() {
        return (getWidth() - logoCol) / (float) VISIBLE_SPAN;
    }

    private float rowStep() {
        return rowH + rowGap;
    }

    private float targetScrollFor(int row) {
        float viewport = getHeight() - header;
        if (viewport <= 0) return 0;
        // Auswahl bleibt etwa im oberen Drittel stehen
        float t = row * rowStep() - viewport * 0.3f;
        float max = Math.max(0, channels.size() * rowStep() - viewport + rowGap);
        return Math.max(0, Math.min(t, max));
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        scrollY = scrollTarget = targetScrollFor(selRow);
    }

    // ---------- Zeichnen ----------

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Context c = getContext();
        long now = System.currentTimeMillis();
        float w = getWidth();
        float h = getHeight();
        float ppm = pxPerMs();
        long winStart = windowAnim;
        long winEnd = winStart + (long) ((w - logoCol) / ppm);
        focusedNow = hasFocus();

        // Zeitleiste
        headerText.setColor(Ui.TEXT2);
        for (long t = floorHalf(winStart); t <= winEnd; t += HALF_HOUR) {
            float x = logoCol + (t - winStart) * ppm;
            if (x < logoCol - 1) continue;
            canvas.drawText(Ui.time(t), x + Ui.dp(c, 6), header - Ui.dp(c, 12), headerText);
            fill.setColor(0x33FFFFFF);
            canvas.drawRect(x, header - Ui.dp(c, 8), x + Ui.dp(c, 1), header - Ui.dp(c, 2), fill);
        }

        // Zeilen
        int first = Math.max(0, (int) (scrollY / rowStep()));
        int last = Math.min(channels.size() - 1, (int) ((scrollY + h - header) / rowStep()) + 1);
        canvas.save();
        canvas.clipRect(0, header, w, h);
        for (int i = first; i <= last; i++) {
            float top = header + i * rowStep() - scrollY;
            drawRow(canvas, i, top, winStart, winEnd, ppm, now);
        }
        canvas.restore();

        // Jetzt-Linie
        float nx = logoCol + (now - winStart) * ppm;
        if (nx >= logoCol && nx <= w) {
            fill.setColor(Ui.ACCENT);
            canvas.drawRect(nx - Ui.dp(c, 1), header - Ui.dp(c, 4), nx + Ui.dp(c, 1), h, fill);
            float pw = Ui.dp(c, 8);
            canvas.drawCircle(nx, header - Ui.dp(c, 4), pw / 2f, fill);
        }

        // Weiche Ränder oben/unten
        if (channels.isEmpty()) {
            title.setColor(Ui.TEXT2);
            canvas.drawText("Keine Sender in dieser Auswahl", logoCol, header + Ui.dp(c, 60), title);
        }
    }

    private void drawRow(Canvas canvas, int i, float top, long winStart, long winEnd, float ppm, long now) {
        Context c = getContext();
        Model.Channel ch = channels.get(i);
        boolean sel = i == selRow && focusedNow;
        float bottom = top + rowH;

        // Logo-Kachel
        r.set(0, top, logoCol - Ui.dp(c, 10), bottom);
        fill.setColor(sel ? 0x40FFFFFF : (i == selRow ? 0x26FFFFFF : 0x14FFFFFF));
        canvas.drawRoundRect(r, radius, radius, fill);
        float tileW = Ui.dp(c, 64), tileH = rowH - Ui.dp(c, 16);
        float tx = Ui.dp(c, 8), ty = top + (rowH - tileH) / 2f;
        drawLogo(canvas, ch, tx, ty, tileW, tileH);
        float nameX = tx + tileW + Ui.dp(c, 10);
        float nameW = r.right - nameX - Ui.dp(c, 8);
        nameText.setColor(i == selRow ? Ui.TEXT : Ui.TEXT2);
        boolean fav = Store.isFavorite(ch.key());
        String nm = TextUtils.ellipsize(ch.name, nameText, nameW, TextUtils.TruncateAt.END).toString();
        float nameY = top + rowH / 2f - (fav ? Ui.dp(c, 1) : -Ui.dp(c, 5));
        canvas.drawText(nm, nameX, nameY, nameText);
        if (fav) canvas.drawText("★", nameX, nameY + Ui.dp(c, 17), star);

        // Sendungen
        List<Model.Programme> list = Epg.get(ch);
        float gridL = logoCol;
        float gridR = getWidth();
        if (list == null || list.isEmpty()) {
            r.set(gridL + Ui.dp(c, 2), top, gridR, bottom);
            boolean focusBlock = sel;
            fill.setColor(focusBlock ? 0xFFFFFFFF : 0x10FFFFFF);
            canvas.drawRoundRect(r, radius, radius, fill);
            title.setColor(focusBlock ? 0xFF111111 : Ui.TEXT3);
            String t = list == null ? "Programm wird geladen …" : "Keine Programminfos";
            canvas.drawText(t, r.left + pad, top + rowH / 2f + Ui.dp(c, 5), title);
            return;
        }
        Model.Programme focusP = sel ? programmeAt(list, cursor) : null;
        for (Model.Programme p : list) {
            if (p.end <= winStart || p.start >= winEnd) continue;
            float x0 = Math.max(gridL, gridL + (p.start - winStart) * ppm) + Ui.dp(c, 2);
            float x1 = Math.min(gridR, gridL + (p.end - winStart) * ppm) - Ui.dp(c, 2);
            if (x1 - x0 < Ui.dp(c, 3)) continue;
            r.set(x0, top, x1, bottom);
            boolean isFocus = p == focusP;
            boolean live = p.isNow(now);
            boolean past = p.end <= now;
            int bg = isFocus ? 0xFFFFFFFF : live ? 0x2EFFFFFF : past ? 0x0DFFFFFF : 0x1AFFFFFF;
            fill.setColor(bg);
            canvas.drawRoundRect(r, radius, radius, fill);
            if (live && !isFocus) {
                // Fortschritt der laufenden Sendung als feine Linie unten
                float px = x0 + (x1 - x0) * p.progress(now);
                fill.setColor(0x66FFFFFF);
                canvas.drawRect(x0 + radius / 2, bottom - Ui.dp(c, 3), Math.max(x0 + radius / 2, px), bottom - Ui.dp(c, 1), fill);
            }
            float avail = x1 - x0 - pad * 2;
            if (avail < Ui.dp(c, 14)) continue;
            canvas.save();
            canvas.clipRect(r);
            String prefix = p.start < winStart ? "‹ " : "";
            title.setColor(isFocus ? 0xFF111111 : past ? Ui.TEXT3 : Ui.TEXT);
            String tt = TextUtils.ellipsize(prefix + p.title, title, avail, TextUtils.TruncateAt.END).toString();
            canvas.drawText(tt, x0 + pad, top + Ui.dp(c, 25), title);
            small.setColor(isFocus ? 0x99111111 : Ui.TEXT3);
            String st = TextUtils.ellipsize(Ui.time(p.start) + " – " + Ui.time(p.end), small, avail, TextUtils.TruncateAt.END).toString();
            canvas.drawText(st, x0 + pad, top + Ui.dp(c, 45), small);
            canvas.restore();
        }
        // Lücke ohne Sendung, aber Auswahl steht dort
        if (sel && focusP == null) {
            float x0 = Math.max(gridL, gridL + (floorHalf(cursor) - winStart) * ppm) + Ui.dp(c, 2);
            float x1 = Math.min(gridR, x0 + HALF_HOUR * ppm) - Ui.dp(c, 2);
            r.set(x0, top, x1, bottom);
            fill.setColor(0xFFFFFFFF);
            canvas.drawRoundRect(r, radius, radius, fill);
            title.setColor(0xFF111111);
            canvas.drawText("—", x0 + pad, top + rowH / 2f + Ui.dp(c, 5), title);
        }
    }

    private void drawLogo(Canvas canvas, Model.Channel ch, float x, float y, float w, float h) {
        String url = ch.logo;
        Bitmap b = url == null ? null : logos.get(url);
        if (b != null && !b.isRecycled()) {
            float s = Math.min(w / b.getWidth(), h / b.getHeight());
            float bw = b.getWidth() * s, bh = b.getHeight() * s;
            r.set(x + (w - bw) / 2f, y + (h - bh) / 2f, x + (w + bw) / 2f, y + (h + bh) / 2f);
            canvas.drawBitmap(b, null, r, null);
            return;
        }
        if (url != null && !logoLoading.contains(url)) loadLogo(url);
        Drawable d = monograms.get(ch.name);
        if (d == null) {
            d = new Img.Monogram(ch.name, Ui.bold(getContext()));
            monograms.put(ch.name, d);
        }
        d.setBounds((int) x, (int) y, (int) (x + w), (int) (y + h));
        d.draw(canvas);
    }

    private void loadLogo(String url) {
        Object model = Img.model(url);
        logoLoading.add(url);
        if (model == null) return;
        Context c = getContext();
        if (c instanceof android.app.Activity && ((android.app.Activity) c).isDestroyed()) return;
        Glide.with(c).asBitmap().load(model)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .override(200, 120)
                .fitCenter()
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                        logos.put(url, resource);
                        invalidate();
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                        logos.remove(url);
                    }
                });
    }

    /** Nach dem Laden der Logos (automatisch gefundene) neu zeichnen. */
    public void refreshLogos() {
        logoLoading.clear();
        invalidate();
    }

    // ---------- Steuerung ----------

    private static Model.Programme programmeAt(List<Model.Programme> list, long t) {
        if (list == null) return null;
        for (Model.Programme p : list) if (p.start <= t && t < p.end) return p;
        return null;
    }

    public Model.Programme selectedProgramme() {
        Model.Channel c = selectedChannel();
        return c == null ? null : programmeAt(Epg.get(c), cursor);
    }

    private void notifySelect() {
        if (listener != null && !channels.isEmpty()) listener.onSelect(selectedChannel(), selectedProgramme());
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (channels.isEmpty()) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && listener != null) {
                listener.onLeaveTop();
                return true;
            }
            return super.onKeyDown(keyCode, event);
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (selRow == 0) {
                    if (listener != null) listener.onLeaveTop();
                    return true;
                }
                moveRow(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                moveRow(1);
                return true;
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_PAGE_UP:
                moveRow(-6);
                return true;
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                moveRow(6);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                moveTime(true);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return moveTime(false);
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                event.startTracking();
                return true;
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_BOOKMARK:
                if (listener != null) listener.onFavorite(selectedChannel());
                invalidate();
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            if (listener != null && !channels.isEmpty()) listener.onFavorite(selectedChannel());
            invalidate();
            return true;
        }
        return super.onKeyLongPress(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            if (event.isTracking() && !event.isCanceled() && !channels.isEmpty() && listener != null) {
                listener.onPlay(selectedChannel());
            }
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private void moveRow(int d) {
        int n = Math.max(0, Math.min(channels.size() - 1, selRow + d));
        if (n == selRow) return;
        selRow = n;
        // Beim Wechseln der Zeile mindestens am Fensteranfang bleiben
        if (cursor < windowStart) cursor = windowStart;
        animateScroll(targetScrollFor(selRow));
        invalidate();
        notifySelect();
    }

    private boolean moveTime(boolean forward) {
        Model.Channel ch = selectedChannel();
        List<Model.Programme> list = Epg.get(ch);
        long now = System.currentTimeMillis();
        long minStart = floorHalf(now) - 3 * 3600_000L;
        Model.Programme cur = programmeAt(list, cursor);
        if (forward) {
            Model.Programme np = null;
            if (list != null) {
                long from = cur != null ? cur.end : cursor + 1;
                for (Model.Programme p : list) {
                    if (p.start >= from) {
                        np = p;
                        break;
                    }
                }
            }
            long next = np != null ? np.start : floorHalf(cursor) + HALF_HOUR;
            if (next - now > 7L * 86400_000L) return true;
            cursor = next;
            if (next > windowStart + VISIBLE_SPAN - HALF_HOUR) setWindow(floorHalf(next) - HALF_HOUR);
        } else {
            if (cur != null && cur.start < windowStart && windowStart > minStart) {
                // Laufende Auswahl beginnt links außerhalb: erst das Fenster zurückschieben
                setWindow(Math.max(minStart, floorHalf(cur.start)));
            } else {
                Model.Programme pp = null;
                if (list != null) {
                    long until = cur != null ? cur.start : cursor;
                    for (Model.Programme p : list) {
                        if (p.end <= until) pp = p;
                        else break;
                    }
                }
                long next;
                if (pp != null) next = Math.max(pp.start, minStart);
                else next = floorHalf(cursor - 1);
                if (next < minStart || (pp != null && pp.end <= minStart)) return true;
                cursor = next;
                if (cursor < windowStart) setWindow(Math.max(minStart, floorHalf(cursor)));
            }
        }
        invalidate();
        notifySelect();
        return true;
    }

    /** Springt zurück auf „jetzt“. */
    public void jumpNow() {
        cursor = System.currentTimeMillis();
        setWindow(floorHalf(cursor) - HALF_HOUR);
        invalidate();
        notifySelect();
    }

    private void setWindow(long start) {
        if (start == windowStart) return;
        windowStart = start;
        if (windowAnimator != null) windowAnimator.cancel();
        final long from = windowAnim;
        windowAnimator = ValueAnimator.ofFloat(0f, 1f);
        windowAnimator.setDuration(220);
        windowAnimator.setInterpolator(new DecelerateInterpolator());
        windowAnimator.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            windowAnim = from + (long) ((start - from) * (double) f);
            invalidate();
        });
        windowAnimator.start();
    }

    private void animateScroll(float target) {
        scrollTarget = target;
        if (scrollAnim != null) scrollAnim.cancel();
        scrollAnim = ValueAnimator.ofFloat(scrollY, target);
        scrollAnim.setDuration(180);
        scrollAnim.setInterpolator(new DecelerateInterpolator());
        scrollAnim.addUpdateListener(a -> {
            scrollY = (float) a.getAnimatedValue();
            invalidate();
        });
        scrollAnim.start();
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, @Nullable android.graphics.Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        invalidate();
        if (gainFocus) notifySelect();
    }
}
