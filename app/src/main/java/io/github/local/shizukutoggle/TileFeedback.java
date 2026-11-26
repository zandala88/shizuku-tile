package io.github.local.shizukutoggle;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Icon;

/** Quick Settings owns the icon drawable, so animate by publishing cached icon frames. */
final class TileFeedback {
    static final long FRAME_MILLIS = 120;
    private static Icon[] frames;

    static Icon icon(Context context, long uptime) {
        if (frames == null) {
            frames = new Icon[12];
            int size = Math.max(24, Math.min(96,
                    Math.round(24 * context.getResources().getDisplayMetrics().density)));
            for (int i = 0; i < frames.length; i++) {
                Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                canvas.scale(size / 24f, size / 24f);
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeWidth(2.5f);
                paint.setColor(Color.WHITE);
                RectF bounds = new RectF(3.5f, 3.5f, 20.5f, 20.5f);
                paint.setAlpha(55);
                canvas.drawOval(bounds, paint);
                paint.setAlpha(255);
                canvas.drawArc(bounds, i * 30f - 90f, 250f, false, paint);
                frames[i] = Icon.createWithBitmap(bitmap);
            }
        }
        return frames[(int) ((uptime / FRAME_MILLIS) % frames.length)];
    }
}
