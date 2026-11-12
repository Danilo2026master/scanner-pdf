package br.scanner.pdf;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.net.Uri;

import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult;

import java.util.ArrayList;
import java.util.List;

/** Scanner de documentos do Google: bordas, recorte, filtros, várias páginas numa sessão. */
final class Scan {

    private Scan() { }

    /** images=false: devolve um único PDF com todas as páginas da sessão. images=true: fotos (para LaTeX). */
    static void start(Activity a, int req, boolean images) {
        GmsDocumentScannerOptions.Builder b = new GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .setResultFormats(images ? GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
                        : GmsDocumentScannerOptions.RESULT_FORMAT_PDF);
        if (images) b.setPageLimit(10);
        GmsDocumentScanning.getClient(b.build()).getStartScanIntent(a)
                .addOnSuccessListener(sender -> {
                    try {
                        a.startIntentSenderForResult(sender, req, null, 0, 0, 0);
                    } catch (IntentSender.SendIntentException e) {
                        Ui.toast(a, Ui.error(e));
                    }
                })
                .addOnFailureListener(e -> Ui.alert(a, "Scanner indisponível",
                        "Atualize o Google Play Services e tente de novo.\n\n" + Ui.error(e)));
    }

    static GmsDocumentScanningResult result(Intent data) {
        return data == null ? null : GmsDocumentScanningResult.fromActivityResultIntent(data);
    }

    static Uri pdf(Intent data) {
        GmsDocumentScanningResult r = result(data);
        return r == null || r.getPdf() == null ? null : r.getPdf().getUri();
    }

    static int pdfPages(Intent data) {
        GmsDocumentScanningResult r = result(data);
        return r == null || r.getPdf() == null ? 0 : r.getPdf().getPageCount();
    }

    static List<Uri> images(Intent data) {
        List<Uri> out = new ArrayList<>();
        GmsDocumentScanningResult r = result(data);
        if (r != null && r.getPages() != null) {
            for (GmsDocumentScanningResult.Page p : r.getPages()) out.add(p.getImageUri());
        }
        return out;
    }

    /** Uris escolhidas num seletor de arquivos (um ou vários). */
    static List<Uri> picked(Intent data) {
        List<Uri> out = new ArrayList<>();
        if (data == null) return out;
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                Uri u = data.getClipData().getItemAt(i).getUri();
                if (u != null) out.add(u);
            }
        } else if (data.getData() != null) {
            out.add(data.getData());
        }
        return out;
    }

    static Intent pickPdf(boolean multiple) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/pdf");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple);
        return i;
    }
}
