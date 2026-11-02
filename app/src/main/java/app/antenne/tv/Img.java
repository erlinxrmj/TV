package app.antenne.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

/** Bilder laden: Senderlogos mit schönem Ersatz, Poster, weichgezeichnete Hintergründe. */
public final class Img {

    private Img() {}

    private static boolean dead(Context c) {
        if (c instanceof android.app.Activity) {
            android.app.Activity a = (android.app.Activity) c;
            return a.isFinishing() || a.isDestroyed();
        }
        return false;
    }

    static Object model(String url) {
        if (url == null || url.trim().isEmpty()) return null;
        String u = url.trim();
        if (!u.startsWith("http")) return null;
        return new GlideUrl(u, new LazyHeaders.Builder().addHeader("User-Agent", Prefs.userAgent()).build());
    }

    /** Senderlogo; ohne Logo oder bei Fehler ein farbiges Monogramm. */
    public static void logo(ImageView v, String url, String name) {
        Context c = v.getContext();
        if (dead(c)) return;
        Drawable fallback = new Monogram(name, Ui.bold(c));
        Object m = model(url);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (m == null) {
            Glide.with(c).clear(v);
            v.setImageDrawable(fallback);
            return;
        }
        Glide.with(c).load(m)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .override(320, 180)
                .fitCenter()
                .error(fallback)
                .into(v);
    }

    public static void poster(ImageView v, String url) {
        Context c = v.getContext();
        if (dead(c)) return;
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Object m = model(url);
        if (m == null) {
            Glide.with(c).clear(v);
            v.setImageDrawable(null);
            return;
        }
        Glide.with(c).load(m)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .override(360, 540)
                .centerCrop()
                .into(v);
    }

    public static void wide(ImageView v, String url) {
        Context c = v.getContext();
        if (dead(c)) return;
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Object m = model(url);
        if (m == null) {
            Glide.with(c).clear(v);
            v.setImageDrawable(null);
            return;
        }
        Glide.with(c).load(m)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .override(960, 540)
                .centerCrop()
                .into(v);
    }

    /**
     * Weich gezeichneter Hintergrund: Bild sehr klein laden und groß anzeigen –
     * auf alten Fernsehern viel schneller als ein echter Blur.
     */
    public static void blur(ImageView v, String url) {
        Context c = v.getContext();
        if (dead(c)) return;
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Object m = model(url);
        if (m == null) {
            v.animate().alpha(0f).setDuration(300).start();
            return;
        }
        Glide.with(c).load(m)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .override(48, 27)
                .centerCrop()
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, @NonNull Target<Drawable> target, boolean isFirstResource) {
                        v.animate().alpha(0f).setDuration(300).start();
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(@NonNull Drawable resource, @NonNull Object model, Target<Drawable> target, @NonNull DataSource dataSource, boolean isFirstResource) {
                        v.animate().alpha(1f).setDuration(400).start();
                        return false;
                    }
                })
                .into(v);
    }

    /** Ersatzlogo: Initialen auf einem Farbverlauf, der aus dem Namen abgeleitet ist. */
    public static final class Monogram extends Drawable {
        private final String text;
        private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fg = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int c1, c2;

        private static final int[][] PALETTE = {
                {0xFF5AC8FA, 0xFF007AFF}, {0xFFAF52DE, 0xFF5856D6}, {0xFFFF375F, 0xFFFF9F0A},
                {0xFF30D158, 0xFF00C7BE}, {0xFFFF9F0A, 0xFFFF453A}, {0xFF64D2FF, 0xFF5E5CE6},
                {0xFFBF5AF2, 0xFFFF375F}, {0xFF32ADE6, 0xFF30D158},
        };

        public Monogram(String name, Typeface tf) {
            text = Names.initials(name);
            int h = name == null ? 0 : Math.abs(name.hashCode());
            int[] p = PALETTE[h % PALETTE.length];
            c1 = p[0];
            c2 = p[1];
            fg.setColor(0xFFFFFFFF);
            fg.setTypeface(tf);
            fg.setTextAlign(Paint.Align.CENTER);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect b = getBounds();
            float w = b.width(), h = b.height();
            float size = Math.min(w, h) * 0.8f;
            float left = b.left + (w - size * 1.5f) / 2f;
            float top = b.top + (h - size) / 2f;
            float right = left + size * 1.5f;
            if (right - left > w) {
                left = b.left;
                right = b.right;
            }
            bg.setShader(new LinearGradient(left, top, right, top + size, c1, c2, Shader.TileMode.CLAMP));
            float r = size * 0.22f;
            canvas.drawRoundRect(left, top, right, top + size, r, r, bg);
            float ts = size * (text.length() <= 2 ? 0.46f : text.length() <= 3 ? 0.38f : 0.3f);
            fg.setTextSize(ts);
            float cy = top + size / 2f - (fg.descent() + fg.ascent()) / 2f;
            canvas.drawText(text, (left + right) / 2f, cy, fg);
        }

        @Override
        public void setAlpha(int alpha) {
            bg.setAlpha(alpha);
            fg.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return 240;
        }

        @Override
        public int getIntrinsicHeight() {
            return 160;
        }
    }
}
