package br.scanner.pdf;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.FrameLayout;
import android.widget.ImageView;

/** Imagem com zoom de pinça, arrastar e toque duplo. */
final class ZoomImage extends ImageView {

    private final Matrix m = new Matrix();
    private final float[] vals = new float[9];
    private float minScale = 1f;
    private final ScaleGestureDetector scale;
    private final GestureDetector gestures;

    ZoomImage(Context c) {
        super(c);
        setScaleType(ScaleType.MATRIX);
        scale = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                float cur = current();
                float target = Math.max(minScale, Math.min(minScale * 8f, cur * d.getScaleFactor()));
                float k = target / cur;
                m.postScale(k, k, d.getFocusX(), d.getFocusY());
                apply();
                return true;
            }
        });
        gestures = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                m.postTranslate(-dx, -dy);
                apply();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                float cur = current();
                float k = cur > minScale * 1.5f ? minScale / cur : (minScale * 2.5f) / cur;
                m.postScale(k, k, e.getX(), e.getY());
                apply();
                return true;
            }
        });
    }

    private float current() {
        m.getValues(vals);
        return vals[Matrix.MSCALE_X];
    }

    @Override
    public void setImageBitmap(Bitmap bm) {
        super.setImageBitmap(bm);
        fit();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        fit();
    }

    private void fit() {
        Drawable d = getDrawable();
        if (d == null || getWidth() == 0 || getHeight() == 0) return;
        float dw = d.getIntrinsicWidth();
        float dh = d.getIntrinsicHeight();
        minScale = Math.min(getWidth() / dw, getHeight() / dh);
        m.reset();
        m.postScale(minScale, minScale);
        m.postTranslate((getWidth() - dw * minScale) / 2f, (getHeight() - dh * minScale) / 2f);
        setImageMatrix(m);
    }

    /** Mantém a imagem centralizada quando menor que a tela e sem sobrar borda quando maior. */
    private void apply() {
        Drawable d = getDrawable();
        if (d == null) return;
        RectF r = new RectF(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
        m.mapRect(r);
        float w = getWidth();
        float h = getHeight();
        float dx = 0;
        float dy = 0;
        if (r.width() <= w) dx = (w - r.width()) / 2f - r.left;
        else if (r.left > 0) dx = -r.left;
        else if (r.right < w) dx = w - r.right;
        if (r.height() <= h) dy = (h - r.height()) / 2f - r.top;
        else if (r.top > 0) dy = -r.top;
        else if (r.bottom < h) dy = h - r.bottom;
        m.postTranslate(dx, dy);
        setImageMatrix(m);
    }

    @Override
    @SuppressWarnings("ClickableViewAccessibility")
    public boolean onTouchEvent(MotionEvent e) {
        scale.onTouchEvent(e);
        gestures.onTouchEvent(e);
        return true;
    }

    /** Abre a imagem em tela cheia (fundo branco, botão voltar). */
    static void show(Activity a, Bitmap b) {
        if (b == null || !Ui.alive(a)) return;
        Dialog d = new Dialog(a, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen);
        FrameLayout root = new FrameLayout(a);
        root.setBackgroundColor(Ui.PAPER);
        ZoomImage z = new ZoomImage(a);
        root.addView(z, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        ImageView close = Ui.icon(a, R.drawable.ic_back, Ui.INK, 24);
        int p = Ui.dp(a, 12);
        close.setPadding(p, p, p, p);
        close.setBackground(Ui.box(a, 0xE6FFFFFF, Ui.LINE, 1, 24));
        close.setOnClickListener(v -> d.dismiss());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.dp(a, 48), Ui.dp(a, 48), Gravity.TOP | Gravity.START);
        lp.setMargins(Ui.dp(a, 12), Ui.dp(a, 28), 0, 0);
        root.addView(close, lp);
        d.setContentView(root);
        z.setImageBitmap(b);
        d.show();
    }
}
