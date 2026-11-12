package br.scanner.pdf;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;

import com.tom_roush.pdfbox.io.MemoryUsageSetting;
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Documentos guardados no app: cada PDF é um arquivo separado em files/docs. */
final class Store {

    static final ExecutorService IO = Executors.newCachedThreadPool();
    private static final Map<String, Integer> PAGES = new HashMap<>();

    private Store() { }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("scanabla", Context.MODE_PRIVATE);
    }

    static File dir(Context c) {
        File d = new File(c.getFilesDir(), "docs");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** Pasta de trabalho temporária (importações, ZIPs). */
    static File work(Context c) {
        File d = new File(c.getCacheDir(), "work");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    static File[] docs(Context c) {
        File[] fs = dir(c).listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".pdf"));
        if (fs == null) return new File[0];
        Arrays.sort(fs, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return fs;
    }

    static String base(File f) {
        return f.getName().replaceAll("(?i)\\.pdf$", "");
    }

    // ---------------------------------------------------------------- projeto LaTeX

    /** Pasta do projeto LaTeX de um PDF: main.tex + figuras/. */
    static File project(File pdf) {
        return new File(pdf.getParentFile(), base(pdf) + ".projeto");
    }

    /** Versão 2.0 guardava só um .tex ao lado do PDF. */
    private static File legacyTex(File pdf) {
        return new File(pdf.getParentFile(), base(pdf) + ".tex");
    }

    static boolean hasTex(File pdf) {
        return new File(project(pdf), "main.tex").exists() || legacyTex(pdf).exists();
    }

    static String readTex(File pdf) {
        File t = new File(project(pdf), "main.tex");
        if (!t.exists()) t = legacyTex(pdf);
        if (!t.exists()) return null;
        try {
            return new String(java.nio.file.Files.readAllBytes(t.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    static void writeText(File f, String text) throws IOException {
        File parent = f.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (OutputStream os = new FileOutputStream(f)) {
            os.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    /** Copia o conteúdo de uma pasta (recursivo). */
    static void copyDir(File from, File to) throws IOException {
        File[] kids = from.listFiles();
        if (kids == null) return;
        to.mkdirs();
        for (File k : kids) {
            File dest = new File(to, k.getName());
            if (k.isDirectory()) {
                copyDir(k, dest);
            } else {
                try (InputStream in = new FileInputStream(k); OutputStream out = new FileOutputStream(dest)) {
                    copy(in, out);
                }
            }
        }
    }

    /** Grava o projeto do PDF: main.tex + figuras/ (copiadas da pasta de trabalho). */
    static void saveProject(File pdf, String tex, File workProject) throws IOException {
        File p = project(pdf);
        deleteTree(p);
        p.mkdirs();
        writeText(new File(p, "main.tex"), tex);
        File figs = new File(workProject, "figuras");
        if (figs.isDirectory()) copyDir(figs, new File(p, "figuras"));
        legacyTex(pdf).delete();
    }

    /** Um arquivo do projeto: caminho dentro do projeto + conteúdo (arquivo ou bytes). */
    static final class ProjFile {
        final String path;
        final File file;
        final byte[] data;

        ProjFile(String path, File file, byte[] data) {
            this.path = path;
            this.file = file;
            this.data = data;
        }

        long length() {
            return data != null ? data.length : file.length();
        }

        InputStream open() throws IOException {
            return data != null ? new java.io.ByteArrayInputStream(data) : new FileInputStream(file);
        }
    }

    /**
     * Projeto para editar no Overleaf/TeXmaker: main.tex, figuras/ e o PDF.
     * PDF escaneado (sem LaTeX): main.tex que inclui as páginas do PDF.
     */
    static List<ProjFile> projectFiles(File pdf) {
        List<ProjFile> out = new ArrayList<>();
        String tex = readTex(pdf);
        if (tex != null) {
            out.add(new ProjFile("main.tex", null, tex.getBytes(StandardCharsets.UTF_8)));
            File[] figs = new File(project(pdf), "figuras").listFiles();
            if (figs != null) {
                Arrays.sort(figs);
                for (File f : figs) if (f.isFile()) out.add(new ProjFile("figuras/" + f.getName(), f, null));
            }
            out.add(new ProjFile(slug(base(pdf)) + ".pdf", pdf, null));
        } else {
            out.add(new ProjFile("main.tex", null, Latex.pdfpagesWrapper("documento.pdf").getBytes(StandardCharsets.UTF_8)));
            out.add(new ProjFile("documento.pdf", pdf, null));
        }
        return out;
    }

    static String stamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH'h'mm", Locale.ROOT).format(new Date());
    }

    static File newDoc(Context c, String prefix) {
        return unique(dir(c), prefix + " " + stamp());
    }

    static String clean(String s) {
        return s.replaceAll("[\\\\/:*?\"<>|\\n\\r\\t]", " ").replaceAll("\\s+", " ").trim();
    }

    static File unique(File dir, String name) {
        String b = clean(name);
        if (b.isEmpty()) b = "Documento";
        File f = new File(dir, b + ".pdf");
        int n = 2;
        while (f.exists()) f = new File(dir, b + " (" + (n++) + ").pdf");
        return f;
    }

    /** Nome seguro para GitHub/ZIP: sem acentos nem espaços. */
    static String slug(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        n = n.replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("-{2,}", "-").replaceAll("^[-.]+|[-.]+$", "");
        return n.isEmpty() ? "documento" : n;
    }

    static int pages(File f) {
        String k = f.getAbsolutePath() + "|" + f.lastModified() + "|" + f.length();
        synchronized (PAGES) {
            Integer n = PAGES.get(k);
            if (n != null) return n;
        }
        int n = -1;
        try (Thumbs t = new Thumbs(f)) {
            n = t.count();
        } catch (Exception ignored) {
        }
        synchronized (PAGES) {
            PAGES.put(k, n);
        }
        return n;
    }

    static String pagesLabel(int n) {
        return n == 1 ? "1 página" : n + " páginas";
    }

    // ---------------------------------------------------------------- arquivos

    static void copy(InputStream in, OutputStream out) throws IOException {
        if (in == null || out == null) throw new IOException("Arquivo indisponível");
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        out.flush();
    }

    static void copyUri(Context c, Uri uri, File to) throws IOException {
        try (InputStream in = c.getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(to)) {
            copy(in, out);
        }
    }

    static String displayName(Context c, Uri uri) {
        try (Cursor cur = c.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cur != null && cur.moveToFirst()) {
                String n = cur.getString(0);
                if (n != null && !n.isEmpty()) return n;
            }
        } catch (Exception ignored) {
        }
        String last = uri.getLastPathSegment();
        return last == null ? "Documento" : last.replaceAll(".*/", "");
    }

    /** Copia um PDF de outro app para a pasta indicada (docs ou trabalho). */
    static File importPdf(Context c, Uri uri, File dir) throws IOException {
        String name = displayName(c, uri).replaceAll("(?i)\\.pdf$", "");
        File out = unique(dir, name);
        copyUri(c, uri, out);
        if (!isPdf(out)) {
            out.delete();
            throw new IOException("Este arquivo não é um PDF.");
        }
        return out;
    }

    static boolean isPdf(File f) {
        try (InputStream in = new FileInputStream(f)) {
            byte[] h = new byte[1024];
            int n = in.read(h);
            String s = n > 0 ? new String(h, 0, n, "ISO-8859-1") : "";
            return s.contains("%PDF");
        } catch (IOException e) {
            return false;
        }
    }

    /** Junta "extra" no fim de "target" (mesmo PDF, páginas em sequência). */
    static void append(File target, File extra) throws IOException {
        PDFMergerUtility m = new PDFMergerUtility();
        m.addSource(target);
        m.addSource(extra);
        File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
        m.setDestinationFileName(tmp.getAbsolutePath());
        m.mergeDocuments(MemoryUsageSetting.setupTempFileOnly());
        replace(tmp, target);
    }

    static void replace(File tmp, File target) throws IOException {
        if (!tmp.renameTo(target)) {
            tmp.delete();
            throw new IOException("Não consegui gravar o arquivo.");
        }
    }

    static File rename(Context c, File f, String name) {
        String n = clean(name);
        if (n.isEmpty()) return null;
        File to = new File(f.getParentFile(), n + ".pdf");
        if (to.equals(f)) return f;
        if (to.exists()) return null;
        File oldProject = project(f);
        File oldTex = legacyTex(f);
        if (!f.renameTo(to)) return null;
        if (oldProject.exists()) oldProject.renameTo(project(to));
        if (oldTex.exists()) oldTex.renameTo(legacyTex(to));
        SharedPreferences p = prefs(c);
        String dl = p.getString("dl:" + f.getName(), null);
        if (dl != null) {
            p.edit().remove("dl:" + f.getName()).putString("dl:" + to.getName(), dl).apply();
            try {
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, to.getName());
                c.getContentResolver().update(Uri.parse(dl), v, null, null);
            } catch (Exception ignored) {
            }
        }
        return to;
    }

    static void delete(Context c, File f) {
        f.delete();
        deleteTree(project(f));
        legacyTex(f).delete();
        prefs(c).edit().remove("dl:" + f.getName()).apply();
    }

    static void deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        f.delete();
    }

    // ---------------------------------------------------------------- Downloads

    static boolean autoDownloads(Context c) {
        return Build.VERSION.SDK_INT >= 29 && prefs(c).getBoolean("downloads", false);
    }

    /** Chamar depois de criar/alterar um PDF (fora da thread principal). */
    static void changed(Context c, File f) {
        if (autoDownloads(c)) toDownloads(c, f);
    }

    /** Grava (ou atualiza) a cópia em Download/Scanabla. */
    static boolean toDownloads(Context c, File f) {
        if (Build.VERSION.SDK_INT < 29) return false;
        ContentResolver cr = c.getContentResolver();
        SharedPreferences p = prefs(c);
        String saved = p.getString("dl:" + f.getName(), null);
        if (saved != null) {
            try (OutputStream os = cr.openOutputStream(Uri.parse(saved), "wt");
                 InputStream in = new FileInputStream(f)) {
                if (os != null) {
                    copy(in, os);
                    return true;
                }
            } catch (Exception ignored) {
                // cópia apagada pelo usuário: cria outra
            }
        }
        try {
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, f.getName());
            v.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Scanabla");
            Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) return false;
            try (OutputStream os = cr.openOutputStream(uri);
                 InputStream in = new FileInputStream(f)) {
                copy(in, os);
            }
            p.edit().putString("dl:" + f.getName(), uri.toString()).apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------------------------------------------------------------- ZIP

    /** Projeto LaTeX inteiro num ZIP (é o formato que o Overleaf importa em "Upload project"). */
    static File projectZip(Context c, File pdf) throws IOException {
        File out = new File(work(c), slug(base(pdf)) + ".zip");
        try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(out))) {
            for (ProjFile pf : projectFiles(pdf)) {
                z.putNextEntry(new ZipEntry(pf.path));
                try (InputStream in = pf.open()) {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) z.write(buf, 0, n);
                }
                z.closeEntry();
            }
        }
        return out;
    }
}
