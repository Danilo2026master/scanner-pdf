package br.scanner.pdf;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/** Entrega os PDFs (e projetos .zip) para outros apps (abrir, enviar pelo WhatsApp, e-mail…) sem pedir permissão de armazenamento. */
public class DocsProvider extends ContentProvider {

    static Uri uriFor(Context c, File f) {
        String base = "content://" + c.getPackageName() + ".docs/";
        if (f.getParentFile() != null && f.getParentFile().equals(Store.work(c))) {
            return Uri.parse(base + "work/" + Uri.encode(f.getName()));
        }
        return Uri.parse(base + Uri.encode(f.getName()));
    }

    /** Só arquivos da pasta de documentos ou da pasta de trabalho (ZIPs para enviar). */
    private File fileFor(Uri uri) throws FileNotFoundException {
        java.util.List<String> seg = uri.getPathSegments();
        String name = uri.getLastPathSegment();
        if (name == null || name.contains("/") || name.contains("..")) throw new FileNotFoundException();
        File dir;
        if (seg.size() == 1) dir = Store.dir(getContext());
        else if (seg.size() == 2 && "work".equals(seg.get(0))) dir = Store.work(getContext());
        else throw new FileNotFoundException();
        File f = new File(dir, name);
        if (!f.isFile()) throw new FileNotFoundException(name);
        return f;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(fileFor(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        File f;
        try {
            f = fileFor(uri);
        } catch (FileNotFoundException e) {
            return null;
        }
        String[] cols = projection != null ? projection : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor c = new MatrixCursor(cols, 1);
        Object[] row = new Object[cols.length];
        for (int i = 0; i < cols.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) row[i] = f.getName();
            else if (OpenableColumns.SIZE.equals(cols[i])) row[i] = f.length();
        }
        c.addRow(row);
        return c;
    }

    @Override
    public String getType(Uri uri) {
        String n = uri.getLastPathSegment() == null ? "" : uri.getLastPathSegment().toLowerCase(java.util.Locale.ROOT);
        if (n.endsWith(".tex")) return "text/x-tex";
        if (n.endsWith(".zip")) return "application/zip";
        return "application/pdf";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
