package br.scanner.pdf;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;

/** Lê o texto das páginas do PDF (reconhecimento no celular, pelo Google Play Services). */
final class Ocr {

    interface Progress {
        void onPage(int page, int total);
    }

    private Ocr() { }

    static String pdfToText(Context c, File pdf, Progress progress) throws Exception {
        TextRecognizer rec = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        StringBuilder out = new StringBuilder();
        try (ParcelFileDescriptor fd = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY);
             PdfRenderer renderer = new PdfRenderer(fd)) {
            int total = renderer.getPageCount();
            for (int i = 0; i < total; i++) {
                progress.onPage(i + 1, total);
                Bitmap bmp;
                try (PdfRenderer.Page page = renderer.openPage(i)) {
                    float scale = 1600f / Math.max(1, page.getWidth());
                    int w = Math.max(1, Math.round(page.getWidth() * scale));
                    int h = Math.max(1, Math.round(page.getHeight() * scale));
                    bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    bmp.eraseColor(Color.WHITE);
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                }
                Text t = Tasks.await(rec.process(InputImage.fromBitmap(bmp, 0)));
                bmp.recycle();
                if (total > 1) out.append("— Página ").append(i + 1).append(" —\n");
                out.append(t.getText().trim()).append("\n\n");
            }
        } finally {
            rec.close();
        }
        return out.toString().trim();
    }
}
