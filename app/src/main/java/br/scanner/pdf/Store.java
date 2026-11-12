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
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
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

    /** Código LaTeX que gerou o PDF (quando houver). */
    static File tex(File pdf) {
        return new File(pdf.getParentFile(), base(pdf) + ".tex");
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
        File oldTex = tex(f);
        if (!f.renameTo(to)) return null;
        if (oldTex.exists()) oldTex.renameTo(tex(to));
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
        tex(f).delete();
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

    /** PDF + código .tex (se existir) num ZIP. */
    static File zip(Context c, File pdf) throws IOException {
        File out = new File(work(c), slug(base(pdf)) + ".zip");
        try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(out))) {
            addZip(z, pdf);
            File t = tex(pdf);
            if (t.exists()) addZip(z, t);
        }
        return out;
    }

    private static void addZip(ZipOutputStream z, File f) throws IOException {
        z.putNextEntry(new ZipEntry(f.getName()));
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) z.write(buf, 0, n);
        }
        z.closeEntry();
    }
}
