package br.scanner.pdf;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Fotos: abre já reduzidas (economiza memória) e converte para JPEG. */
final class Img {

    private Img() { }

    static Bitmap decode(Context c, Uri uri, int max) throws IOException {
        if (Build.VERSION.SDK_INT >= 28) {
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(c.getContentResolver(), uri), (dec, info, src) -> {
                int w = info.getSize().getWidth();
                int h = info.getSize().getHeight();
                float k = Math.min(1f, max / (float) Math.max(1, Math.max(w, h)));
                dec.setTargetSize(Math.max(1, Math.round(w * k)), Math.max(1, Math.round(h * k)));
                dec.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            });
        }
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, o);
        }
        int s = 1;
        while (Math.max(o.outWidth, o.outHeight) / (s * 2) >= max) s *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = s;
        Bitmap b;
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            b = BitmapFactory.decodeStream(in, null, o2);
        }
        if (b == null) throw new IOException("Imagem inválida.");
        return fit(b, max);
    }

    static Bitmap fit(Bitmap b, int max) {
        int big = Math.max(b.getWidth(), b.getHeight());
        if (big <= max) return b;
        float k = max / (float) big;
        Bitmap s = Bitmap.createScaledBitmap(b, Math.max(1, Math.round(b.getWidth() * k)),
                Math.max(1, Math.round(b.getHeight() * k)), true);
        if (s != b) b.recycle();
        return s;
    }

    static byte[] jpeg(Bitmap b, int quality) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.JPEG, quality, out);
        return out.toByteArray();
    }

    static Bitmap fromJpeg(byte[] jpeg, int max) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, o);
        int s = 1;
        while (Math.max(o.outWidth, o.outHeight) / (s * 2) >= max) s *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = s;
        Bitmap b = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, o2);
        return b == null ? null : fit(b, max);
    }
}
