package br.scanner.pdf;

import android.graphics.Bitmap;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.util.List;

/** Lê o texto das páginas (reconhecimento no celular, pelo Google Play Services). */
final class Ocr {

    interface Progress {
        void onPage(int page, int total);
    }

    private Ocr() { }

    static String pdfToText(File pdf, Progress progress) throws Exception {
        TextRecognizer rec = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        StringBuilder out = new StringBuilder();
        try (Thumbs t = new Thumbs(pdf)) {
            int total = t.count();
            for (int i = 0; i < total; i++) {
                progress.onPage(i + 1, total);
                Bitmap bmp = t.render(i, 1600);
                Text r = Tasks.await(rec.process(InputImage.fromBitmap(bmp, 0)));
                bmp.recycle();
                if (total > 1) out.append("— Página ").append(i + 1).append(" —\n");
                out.append(r.getText().trim()).append("\n\n");
            }
        } finally {
            rec.close();
        }
        return out.toString().trim();
    }

    /** Texto de cada foto (JPEG), na ordem. */
    static String[] jpegsToText(List<byte[]> jpegs) throws Exception {
        TextRecognizer rec = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        String[] out = new String[jpegs.size()];
        try {
            for (int i = 0; i < jpegs.size(); i++) {
                Bitmap b = Img.fromJpeg(jpegs.get(i), 1600);
                if (b == null) {
                    out[i] = "";
                    continue;
                }
                out[i] = Tasks.await(rec.process(InputImage.fromBitmap(b, 0))).getText().trim();
                b.recycle();
            }
        } finally {
            rec.close();
        }
        return out;
    }
}
