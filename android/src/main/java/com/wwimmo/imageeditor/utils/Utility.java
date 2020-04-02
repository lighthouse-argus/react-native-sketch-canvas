package com.wwimmo.imageeditor.utils;

import java.lang.IllegalArgumentException;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.util.DisplayMetrics;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.PointF;
import android.graphics.drawable.VectorDrawable;

import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;

public final class Utility {
    public static RectF fillImage(float imgWidth, float imgHeight, float targetWidth, float targetHeight, String mode) {
        float imageAspectRatio = imgWidth / imgHeight;
        float targetAspectRatio = targetWidth / targetHeight;
        switch (mode) {
            case "AspectFill": {
                float scaleFactor = targetAspectRatio < imageAspectRatio ? targetHeight / imgHeight : targetWidth / imgWidth;
                float w = imgWidth * scaleFactor, h = imgHeight * scaleFactor;
                return new RectF((targetWidth - w) / 2, (targetHeight - h) / 2,
                    w + (targetWidth - w) / 2, h + (targetHeight - h) / 2);
            }
            case "AspectFit":
            default: {
                float scaleFactor = targetAspectRatio > imageAspectRatio ? targetHeight / imgHeight : targetWidth / imgWidth;
                float w = imgWidth * scaleFactor, h = imgHeight * scaleFactor;
                return new RectF((targetWidth - w) / 2, (targetHeight - h) / 2,
                    w + (targetWidth - w) / 2, h + (targetHeight - h) / 2);
            }
            case "ScaleToFill": {
                return  new RectF(0, 0, targetWidth, targetHeight);
            }
        }
    }

    public static int convertDpToPx(DisplayMetrics displayMetrics, float dp) {
        return (int) (dp * displayMetrics.density);
    }

    public static float convertDpToPxAsFloat(DisplayMetrics displayMetrics, float dp) {
      return dp * displayMetrics.density;
    }

    public static int convertPxToDp(DisplayMetrics displayMetrics, float px) {
        return (int) (px / displayMetrics.density);
    }

    public static float convertPxToDpAsFloat(DisplayMetrics displayMetrics, float px) {
        return px / displayMetrics.density;
    }

    /**
     * For more info:
     * <a href="http://math.stackexchange.com/questions/190111/how-to-check-if-a-point-is-inside-a-rectangle">StackOverflow: How to check point is in rectangle</a>
     *
     * @param pt point to check
     * @param v1 vertex 1 of the triangle
     * @param v2 vertex 2 of the triangle
     * @param v3 vertex 3 of the triangle
     * @return true if point (x, y) is inside the triangle
     */
    public static boolean pointInTriangle(@NonNull PointF pt, @NonNull PointF v1,
                                          @NonNull PointF v2, @NonNull PointF v3) {

        boolean b1 = crossProduct(pt, v1, v2) < 0.0f;
        boolean b2 = crossProduct(pt, v2, v3) < 0.0f;
        boolean b3 = crossProduct(pt, v3, v1) < 0.0f;

        return (b1 == b2) && (b2 == b3);
    }

    /**
     * calculates cross product of vectors AB and AC
     *
     * @param a beginning of 2 vectors
     * @param b end of vector 1
     * @param c enf of vector 2
     * @return cross product AB * AC
     */
    private static float crossProduct(@NonNull PointF a, @NonNull PointF b, @NonNull PointF c) {
        return crossProduct(a.x, a.y, b.x, b.y, c.x, c.y);
    }

    /**
     * calculates cross product of vectors AB and AC
     *
     * @param ax X coordinate of point A
     * @param ay Y coordinate of point A
     * @param bx X coordinate of point B
     * @param by Y coordinate of point B
     * @param cx X coordinate of point C
     * @param cy Y coordinate of point C
     * @return cross product AB * AC
     */
    private static float crossProduct(float ax, float ay, float bx, float by, float cx, float cy) {
        return (ax - cx) * (by - cy) - (bx - cx) * (ay - cy);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static Bitmap getBitmap(VectorDrawable vectorDrawable) {
        Log.w("sketch: vector height", String.valueOf(vectorDrawable.getIntrinsicHeight()));
        Log.w("sketch: vector width", String.valueOf(vectorDrawable.getIntrinsicWidth()));
        Bitmap bitmap = Bitmap.createBitmap(vectorDrawable.getIntrinsicWidth(),
                vectorDrawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        vectorDrawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        vectorDrawable.draw(canvas);
        return bitmap;
    }

    public static Bitmap getBitmap(Context context, int drawableId) {
        Drawable drawable = ContextCompat.getDrawable(context, drawableId);
        Log.w("SKETCH: drawable", drawable.toString());
        if (drawable instanceof BitmapDrawable) {
            Log.w("SKETCH: returning drawable", drawable.toString());
            return BitmapFactory.decodeResource(context.getResources(), drawableId);
        } else if (drawable instanceof VectorDrawable) {
            Log.w("SKETCH: returning vector", "");
            return getBitmap((VectorDrawable) drawable);
        } else {
            Log.w("SKETCH: oops", "not allowed buckaroo");
            throw new IllegalArgumentException("unsupported drawable type");
        }
    }
}
