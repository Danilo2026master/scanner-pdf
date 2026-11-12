package br.scanner.pdf;

import com.tom_roush.pdfbox.io.MemoryUsageSetting;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Monta um PDF novo a partir de páginas escolhidas (de um ou vários PDFs), na ordem e giro pedidos. */
final class Pdf {

    /** Uma página: de qual arquivo (src), qual página (index) e quanto girar a mais (rot). */
    static final class Page {
        final int src;
        final int index;
        int rot;

        Page(int src, int index) {
            this.src = src;
            this.index = index;
        }
    }

    private Pdf() { }

    static void build(List<File> sources, List<Page> pages, File out) throws IOException {
        File tmp = new File(out.getParentFile(), out.getName() + ".tmp");
        List<PDDocument> docs = new ArrayList<>();
        try {
            for (File f : sources) {
                PDDocument d = PDDocument.load(f, MemoryUsageSetting.setupTempFileOnly());
                d.setAllSecurityToBeRemoved(true);
                docs.add(d);
            }
            try (PDDocument dest = new PDDocument(MemoryUsageSetting.setupTempFileOnly())) {
                for (Page p : pages) {
                    PDPage from = docs.get(p.src).getPage(p.index);
                    PDPage to = dest.importPage(from);
                    to.setResources(from.getResources());
                    to.setRotation((((from.getRotation() + p.rot) % 360) + 360) % 360);
                }
                dest.save(tmp);
            }
        } finally {
            for (PDDocument d : docs) {
                try {
                    d.close();
                } catch (IOException ignored) {
                }
            }
        }
        Store.replace(tmp, out);
    }
}
