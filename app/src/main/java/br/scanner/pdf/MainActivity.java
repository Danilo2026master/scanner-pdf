package br.scanner.pdf;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.InputType;
import android.text.format.Formatter;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.mlkit.vision.documentscanner.GmsDocumentScanner;
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

/** Scanner PDF: escaneia com a câmera (recorte e melhoria automáticos), salva em PDF, extrai texto. */
public class MainActivity extends Activity {

    private static final int REQ_SCAN = 10;
    private static final int BG = Color.rgb(13, 17, 23);
    private static final int CARD = Color.rgb(22, 27, 36);
    private static final int TEXT = Color.rgb(238, 242, 248);
    private static final int MUTED = Color.rgb(147, 160, 180);
    private static final int ACCENT = Color.rgb(37, 99, 235);

    private LinearLayout list;
    private TextView empty;
    private Switch pagesSwitch;
    private Switch downloadsSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        scroll.setFitsSystemWindows(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(32));
        scroll.addView(root);

        TextView title = text("📄 Scanner PDF", 24, TEXT, true);
        root.addView(title);
        root.addView(text("Fotografe documentos, cadernos e livros. O app corta as bordas, endireita e melhora a imagem e gera o PDF.", 14, MUTED, false));

        Button scan = button("📷  Escanear documento", true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(60));
        lp.setMargins(0, dp(16), 0, dp(4));
        scan.setLayoutParams(lp);
        scan.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        scan.setOnClickListener(v -> startScan());
        root.addView(scan);
        root.addView(text("Dá para escanear várias páginas, importar fotos da galeria, girar, recortar e aplicar filtros antes de salvar.", 12, MUTED, false));

        LinearLayout opts = card();
        pagesSwitch = new Switch(this);
        pagesSwitch.setText("Muitas páginas por documento (até 100)");
        pagesSwitch.setTextColor(TEXT);
        pagesSwitch.setChecked(prefs().getBoolean("many", true));
        pagesSwitch.setOnCheckedChangeListener((b, on) -> prefs().edit().putBoolean("many", on).apply());
        opts.addView(pagesSwitch);
        downloadsSwitch = new Switch(this);
        downloadsSwitch.setText("Salvar uma cópia em Downloads/Scanner PDF");
        downloadsSwitch.setTextColor(TEXT);
        downloadsSwitch.setChecked(prefs().getBoolean("downloads", true));
        downloadsSwitch.setOnCheckedChangeListener((b, on) -> prefs().edit().putBoolean("downloads", on).apply());
        if (Build.VERSION.SDK_INT < 29) {
            downloadsSwitch.setEnabled(false);
            downloadsSwitch.setText("Cópia em Downloads (Android 10+). Use \"Compartilhar\" para salvar.");
        }
        opts.addView(downloadsSwitch);
        root.addView(opts);

        TextView h = text("MEUS DOCUMENTOS", 12, MUTED, true);
        h.setPadding(0, dp(18), 0, dp(4));
        root.addView(h);
        empty = text("Nenhum documento ainda. Toque em Escanear documento.", 14, MUTED, false);
        root.addView(empty);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);

        setContentView(scroll);
        renderList();
    }

    private android.content.SharedPreferences prefs() {
        return getSharedPreferences("scanner", MODE_PRIVATE);
    }

    static File docsDir(Context c) {
        File d = new File(c.getFilesDir(), "docs");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    // ---------------------------------------------------------------- escanear
    private void startScan() {
        GmsDocumentScannerOptions options = new GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(pagesSwitch.isChecked() ? 100 : 1)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build();
        GmsDocumentScanner scanner = GmsDocumentScanning.getClient(options);
        scanner.getStartScanIntent(this)
                .addOnSuccessListener(sender -> {
                    try {
                        startIntentSenderForResult(sender, REQ_SCAN, null, 0, 0, 0);
                    } catch (IntentSender.SendIntentException e) {
                        toast("Não consegui abrir o scanner: " + e.getMessage());
                    }
                })
                .addOnFailureListener(e -> new AlertDialog.Builder(this)
                        .setTitle("Scanner indisponível")
                        .setMessage("O scanner usa o Google Play Services. Atualize o \"Google Play Services\" pela Play Store e tente de novo.\n\nDetalhe: " + e.getMessage())
                        .setPositiveButton("OK", null).show());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_SCAN || resultCode != RESULT_OK || data == null) return;
        GmsDocumentScanningResult result = GmsDocumentScanningResult.fromActivityResultIntent(data);
        if (result == null || result.getPdf() == null) {
            toast("O scanner não devolveu um PDF.");
            return;
        }
        Uri pdfUri = result.getPdf().getUri();
        int pages = result.getPdf().getPageCount();
        String name = "Scan " + new SimpleDateFormat("yyyy-MM-dd HH-mm-ss", new Locale("pt", "BR")).format(new Date()) + ".pdf";
        File out = new File(docsDir(this), name);
        try {
            copy(getContentResolver().openInputStream(pdfUri), new FileOutputStream(out));
        } catch (Exception e) {
            toast("Não consegui salvar: " + e.getMessage());
            return;
        }
        prefs().edit().putInt("pages:" + name, pages).apply();
        String extra = "";
        if (Build.VERSION.SDK_INT >= 29 && downloadsSwitch.isChecked()) {
            extra = exportToDownloads(out) ? " Cópia em Downloads/Scanner PDF." : "";
        }
        toast("✓ PDF salvo (" + pages + (pages == 1 ? " página)." : " páginas).") + extra);
        renderList();
        askRename(out);
    }

    private boolean exportToDownloads(File f) {
        try {
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, f.getName());
            v.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Scanner PDF");
            ContentResolver cr = getContentResolver();
            Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) return false;
            copy(new FileInputStream(f), cr.openOutputStream(uri));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static void copy(InputStream in, OutputStream out) throws Exception {
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        out.close();
    }

    // ---------------------------------------------------------------- lista
    private void renderList() {
        list.removeAllViews();
        File[] files = docsDir(this).listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".pdf"));
        if (files == null) files = new File[0];
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        empty.setVisibility(files.length == 0 ? View.VISIBLE : View.GONE);
        SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("pt", "BR"));
        for (final File f : files) {
            LinearLayout c = card();
            TextView name = text("📄 " + f.getName().replaceAll("\\.pdf$", ""), 16, TEXT, true);
            c.addView(name);
            int pages = prefs().getInt("pages:" + f.getName(), -1);
            c.addView(text((pages > 0 ? pages + (pages == 1 ? " página · " : " páginas · ") : "")
                    + Formatter.formatShortFileSize(this, f.length()) + " · " + df.format(new Date(f.lastModified())), 13, MUTED, false));
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(10), 0, 0);
            row.addView(small("Abrir", v -> open(f)));
            row.addView(small("Enviar", v -> share(f)));
            row.addView(small("Texto", v -> extractText(f)));
            row.addView(small("⋯", v -> more(f)));
            c.addView(row);
            list.addView(c);
        }
    }

    private void more(final File f) {
        new AlertDialog.Builder(this)
                .setTitle(f.getName())
                .setItems(new String[]{"Renomear", "Excluir"}, (d, which) -> {
                    if (which == 0) askRename(f);
                    else new AlertDialog.Builder(this)
                            .setMessage("Excluir \"" + f.getName() + "\"? (a cópia em Downloads, se houver, continua lá)")
                            .setPositiveButton("Excluir", (d2, w2) -> {
                                if (f.delete()) prefs().edit().remove("pages:" + f.getName()).apply();
                                renderList();
                            })
                            .setNegativeButton("Cancelar", null).show();
                }).show();
    }

    private void askRename(final File f) {
        final EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setText(f.getName().replaceAll("\\.pdf$", ""));
        e.selectAll();
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(e, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this)
                .setTitle("Nome do documento")
                .setView(box)
                .setPositiveButton("Salvar", (d, w) -> {
                    String n = e.getText().toString().replaceAll("[\\\\/:*?\"<>|]", " ").trim();
                    if (n.isEmpty()) return;
                    File to = new File(f.getParentFile(), n + ".pdf");
                    if (to.exists() && !to.equals(f)) { toast("Já existe um documento com esse nome."); return; }
                    int pages = prefs().getInt("pages:" + f.getName(), -1);
                    if (f.renameTo(to)) {
                        prefs().edit().remove("pages:" + f.getName()).putInt("pages:" + to.getName(), pages).apply();
                    }
                    renderList();
                })
                .setNegativeButton("Manter", null).show();
    }

    // ---------------------------------------------------------------- abrir / compartilhar / texto
    private void open(File f) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(DocsProvider.uriFor(this, f), "application/pdf");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            toast("Nenhum leitor de PDF instalado. Use \"Enviar\" para abrir em outro app.");
        }
    }

    private void share(File f) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("application/pdf");
        Uri uri = DocsProvider.uriFor(this, f);
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.putExtra(Intent.EXTRA_SUBJECT, f.getName());
        i.setClipData(ClipData.newRawUri(f.getName(), uri));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(i, "Enviar PDF"));
    }

    private void extractText(final File f) {
        final AlertDialog progress = new AlertDialog.Builder(this)
                .setTitle("Lendo o texto…")
                .setMessage("Reconhecendo o texto das páginas. Pode levar alguns segundos.")
                .setCancelable(false).show();
        new Thread(() -> {
            String text;
            try {
                text = Ocr.pdfToText(this, f, (page, total) -> runOnUiThread(() ->
                        progress.setMessage("Página " + page + " de " + total + "…")));
            } catch (Exception e) {
                text = null;
                final String msg = e.getMessage();
                runOnUiThread(() -> {
                    progress.dismiss();
                    new AlertDialog.Builder(this).setTitle("Não deu para ler o texto")
                            .setMessage("Na primeira vez o Google Play Services baixa o leitor de texto; confira a internet e tente de novo.\n\nDetalhe: " + msg)
                            .setPositiveButton("OK", null).show();
                });
                return;
            }
            final String result = text;
            runOnUiThread(() -> {
                progress.dismiss();
                showText(f, result);
            });
        }).start();
    }

    private void showText(File f, final String text) {
        TextView tv = new TextView(this);
        tv.setText(text.isEmpty() ? "(nenhum texto encontrado)" : text);
        tv.setTextIsSelectable(true);
        tv.setPadding(dp(20), dp(8), dp(20), dp(8));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        ScrollView sv = new ScrollView(this);
        sv.addView(tv);
        new AlertDialog.Builder(this)
                .setTitle("Texto de " + f.getName().replaceAll("\\.pdf$", ""))
                .setView(sv)
                .setPositiveButton("Copiar", (d, w) -> {
                    ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("texto", text));
                    toast("Texto copiado.");
                })
                .setNeutralButton("Compartilhar", (d, w) -> {
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("text/plain");
                    i.putExtra(Intent.EXTRA_TEXT, text);
                    startActivity(Intent.createChooser(i, "Enviar texto"));
                })
                .setNegativeButton("Fechar", null).show();
    }

    // ---------------------------------------------------------------- visual
    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(14));
        bg.setStroke(1, Color.argb(30, 255, 255, 255));
        c.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(10), 0, 0);
        c.setLayoutParams(lp);
        return c;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(2), 0, dp(2));
        return t;
    }

    private Button button(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setStateListAnimator(null);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(primary ? ACCENT : Color.TRANSPARENT);
        bg.setCornerRadius(dp(14));
        if (!primary) bg.setStroke(dp(1), Color.argb(60, 255, 255, 255));
        b.setBackground(bg);
        return b;
    }

    private Button small(String label, View.OnClickListener l) {
        Button b = button(label, false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        b.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(42), 1f);
        lp.setMargins(dp(3), 0, dp(3), 0);
        b.setLayoutParams(lp);
        b.setOnClickListener(l);
        return b;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
