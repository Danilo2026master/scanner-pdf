package br.scanner.pdf;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Desenha páginas de PDF (miniaturas e imagens para OCR/LaTeX). Um desenho por vez, por segurança. */
final class Thumbs implements Closeable {

    static final Object LOCK = new Object();
    static final ExecutorService EXEC = Executors.newSingleThreadExecutor();

    private final ParcelFileDescriptor fd;
    private final PdfRenderer renderer;

    Thumbs(File f) throws IOException {
        synchronized (LOCK) {
            fd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
            try {
                renderer = new PdfRenderer(fd);
            } catch (IOException | RuntimeException e) {
                fd.close();
                throw new IOException(e instanceof SecurityException
                        ? "PDF protegido por senha." : "Não consegui ler este PDF.");
            }
        }
    }

    int count() {
        synchronized (LOCK) {
            return renderer.getPageCount();
        }
    }

    /** Página "i" com a largura pedida (em pixels), fundo branco. */
    Bitmap render(int i, int width) {
        synchronized (LOCK) {
            try (PdfRenderer.Page p = renderer.openPage(i)) {
                float k = width / (float) Math.max(1, p.getWidth());
                int w = Math.max(1, Math.round(p.getWidth() * k));
                int h = Math.max(1, Math.min(width * 4, Math.round(p.getHeight() * k)));
                Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                b.eraseColor(Color.WHITE);
                p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                return b;
            }
        }
    }

    @Override
    public void close() {
        synchronized (LOCK) {
            try {
                renderer.close();
            } catch (Exception ignored) {
            }
            try {
                fd.close();
            } catch (Exception ignored) {
            }
        }
    }
}
