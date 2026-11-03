package app.antenne.tv;

import android.content.Context;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Liste mit tvOS-artigem Fokus: Die Auswahl bleibt an einer festen Stelle stehen und die
 * Liste gleitet darunter durch. Der Fokus kann nicht mehr „wegspringen“, auch wenn man
 * die Taste gedrückt hält, und schnelles Dauerdrücken wird gebremst, damit alte Fernseher
 * mitkommen.
 */
public class TvList extends RecyclerView {

    private static final Interpolator EASE = new DecelerateInterpolator(1.6f);
    private long lastMove;
    private final int minRepeatMs;

    /**
     * @param anchor Position des Fokus im sichtbaren Bereich (0 = Anfang, 0.5 = Mitte)
     */
    public TvList(Context c, boolean horizontal, float anchor) {
        super(c);
        setLayoutManager(new Manager(c, horizontal, anchor));
        setItemAnimator(null);
        setClipToPadding(false);
        setClipChildren(false);
        setHasFixedSize(true);
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setVerticalScrollBarEnabled(false);
        setHorizontalScrollBarEnabled(false);
        setItemViewCacheSize(8);
        minRepeatMs = horizontal ? 110 : 80;
    }

    /** Raster (z. B. Senderkacheln) mit derselben ruhigen Fokusführung. */
    public TvList(Context c, int spanCount, float anchor) {
        super(c);
        setLayoutManager(new Grid(c, spanCount, anchor));
        setItemAnimator(null);
        setClipToPadding(false);
        setClipChildren(false);
        setHasFixedSize(true);
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setVerticalScrollBarEnabled(false);
        setItemViewCacheSize(12);
        minRepeatMs = 90;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        boolean dpad = k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN
                || k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT;
        if (dpad && e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() > 0) {
            long now = SystemClock.uptimeMillis();
            if (now - lastMove < minRepeatMs) return true; // gedrückt halten: gleichmässiges Tempo
            lastMove = now;
        } else if (dpad && e.getAction() == KeyEvent.ACTION_DOWN) {
            lastMove = SystemClock.uptimeMillis();
        }
        return super.dispatchKeyEvent(e);
    }

    /** Fokus auf eine Position setzen, auch wenn sie gerade nicht sichtbar ist. */
    public void focusPosition(int pos) {
        if (pos < 0 || getAdapter() == null || pos >= getAdapter().getItemCount()) return;
        ViewHolder vh = findViewHolderForAdapterPosition(pos);
        if (vh != null) {
            vh.itemView.requestFocus();
            return;
        }
        LinearLayoutManager m = (LinearLayoutManager) getLayoutManager();
        int off = (int) (((m.getOrientation() == LinearLayoutManager.HORIZONTAL ? getWidth() : getHeight())) * anchorOf(m));
        m.scrollToPositionWithOffset(pos, Math.max(0, off - 40));
        post(() -> {
            ViewHolder v2 = findViewHolderForAdapterPosition(pos);
            if (v2 != null) v2.itemView.requestFocus();
        });
    }

    private static float anchorOf(LinearLayoutManager m) {
        if (m instanceof Manager) return ((Manager) m).anchor;
        if (m instanceof Grid) return ((Grid) m).anchor;
        return 0f;
    }

    /** Gemeinsame Logik: Element an feste Stelle gleiten lassen. */
    static boolean glide(LinearLayoutManager m, float anchor, RecyclerView parent, View child, boolean immediate) {
        boolean h = m.getOrientation() == HORIZONTAL;
        int size = h ? child.getWidth() : child.getHeight();
        int pos = h ? child.getLeft() : child.getTop();
        int start = h ? parent.getPaddingLeft() : parent.getPaddingTop();
        int end = h ? parent.getWidth() - parent.getPaddingRight() : parent.getHeight() - parent.getPaddingBottom();
        int target = start + (int) ((end - start) * anchor) - (int) (size * anchor);
        int delta = pos - target;
        if (delta == 0) return false;
        if (immediate) {
            if (h) parent.scrollBy(delta, 0);
            else parent.scrollBy(0, delta);
        } else {
            if (h) parent.smoothScrollBy(delta, 0, EASE);
            else parent.smoothScrollBy(0, delta, EASE);
        }
        return true;
    }

    public static final class Grid extends GridLayoutManager {
        private final float anchor;

        Grid(Context c, int span, float anchor) {
            super(c, span);
            this.anchor = anchor;
        }

        @Override
        public boolean requestChildRectangleOnScreen(@NonNull RecyclerView parent, @NonNull View child,
                                                     @NonNull Rect rect, boolean immediate, boolean focusedChildVisible) {
            return glide(this, anchor, parent, child, immediate);
        }

        @Nullable
        @Override
        public View onInterceptFocusSearch(@NonNull View focused, int direction) {
            View item = findContainingItemView(focused);
            if (item == null) return null;
            int pos = getPosition(item);
            int span = getSpanCount();
            int col = pos % span;
            int next;
            switch (direction) {
                case View.FOCUS_DOWN:
                    if (pos + span >= getItemCount()) {
                        // letzte Reihe: auf das letzte Element springen, falls die Reihe darunter kürzer ist
                        int lastRowStart = (getItemCount() - 1) / span * span;
                        if (pos >= lastRowStart) return focused;
                        next = getItemCount() - 1;
                    } else next = pos + span;
                    break;
                case View.FOCUS_UP:
                    if (pos - span < 0) return null; // oberste Reihe: raus zur Navigation
                    next = pos - span;
                    break;
                case View.FOCUS_RIGHT:
                    if (col == span - 1 || pos + 1 >= getItemCount()) return focused;
                    next = pos + 1;
                    break;
                case View.FOCUS_LEFT:
                    if (col == 0) return null; // linke Spalte: raus zu den Kategorien
                    next = pos - 1;
                    break;
                default:
                    return null;
            }
            View v = findViewByPosition(next);
            if (v == null) {
                scrollToPosition(next);
                return focused;
            }
            return v;
        }
    }

    public static final class Manager extends LinearLayoutManager {
        private final float anchor;

        Manager(Context c, boolean horizontal, float anchor) {
            super(c, horizontal ? HORIZONTAL : VERTICAL, false);
            this.anchor = anchor;
            setItemPrefetchEnabled(true);
        }

        int anchorOffset(RecyclerView rv, int childSize) {
            boolean h = getOrientation() == HORIZONTAL;
            int start = h ? rv.getPaddingLeft() : rv.getPaddingTop();
            int end = h ? rv.getWidth() - rv.getPaddingRight() : rv.getHeight() - rv.getPaddingBottom();
            int space = end - start;
            return (int) (space * anchor) - (int) (childSize * anchor);
        }

        /** Statt minimal zu scrollen: fokussiertes Element an die feste Stelle gleiten lassen. */
        @Override
        public boolean requestChildRectangleOnScreen(@NonNull RecyclerView parent, @NonNull View child,
                                                     @NonNull Rect rect, boolean immediate, boolean focusedChildVisible) {
            return glide(this, anchor, parent, child, immediate);
        }

        /**
         * Liegt das nächste Element noch ausserhalb (nicht geladen), bleibt der Fokus hier und die
         * Liste rückt nach. So springt der Fokus nie in eine andere Spalte oder in die Navigation.
         */
        @Nullable
        @Override
        public View onInterceptFocusSearch(@NonNull View focused, int direction) {
            View item = findContainingItemView(focused);
            if (item == null) return null;
            int pos = getPosition(item);
            boolean h = getOrientation() == HORIZONTAL;
            int step = 0;
            if (h && direction == View.FOCUS_RIGHT) step = 1;
            else if (h && direction == View.FOCUS_LEFT) step = -1;
            else if (!h && direction == View.FOCUS_DOWN) step = 1;
            else if (!h && direction == View.FOCUS_UP) step = -1;
            if (step == 0) return null;
            int next = pos + step;
            if (next < 0 || next >= getItemCount()) {
                // Am Ende einer Reihe nicht nach draussen springen (nur am Anfang darf man raus)
                return step > 0 ? focused : null;
            }
            if (findViewByPosition(next) == null) {
                scrollToPosition(next);
                return focused;
            }
            return null; // Nachbar ist da: normale Fokussuche (findet auch Karten in Reihen)
        }
    }
}
