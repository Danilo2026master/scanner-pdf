package br.scanner.pdf;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.format.Formatter;
import android.util.TypedValue;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;

/** Um documento (um PDF): miniaturas, + páginas no mesmo arquivo e o que fazer com ele. */
public class DocActivity extends Activity {

    static final String EXTRA = "path";
    private static final int REQ_MORE = 1;
    private static final int MAX_THUMBS = 40;

    private File file;
    private TextView title;
    private TextView meta;
    private LinearLayout strip;
    private int generation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String p = getIntent().getStringExtra(EXTRA);
        file = p == null ? null : new File(p);
        if (file == null || !file.exists()) {
            finish();
            return;
        }
        LinearLayout col = Ui.scrollScreen(this);
        Ui.header(this, col, "");

        title = Ui.text(this, "", 28, Ui.INK, Typeface.NORMAL);
        title.setOnClickListener(v -> rename());
        col.addView(title);
        meta = Ui.text(this, "", 16, Ui.GREY, Typeface.ITALIC);
        meta.setPadding(0, Ui.dp(this, 2), 0, 0);
        col.addView(meta);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        strip = Ui.row(this);
        hs.addView(strip);
        col.addView(hs, Ui.full(this, 170, 18));

        col.addView(Ui.button(this, R.drawable.ic_plus, "Páginas", true,
                v -> Scan.start(this, REQ_MORE, false)), Ui.full(this, 56, 18));

        int[][] icons = {
                {R.drawable.ic_open, R.drawable.ic_share, R.drawable.ic_text},
                {R.drawable.ic_grid, R.drawable.ic_tex, R.drawable.ic_upload},
                {R.drawable.ic_download, R.drawable.ic_edit, R.drawable.ic_trash}};
        String[][] labels = {
                {"Abrir", "Enviar", "Texto"},
                {"Organizar", "LaTeX", "GitHub"},
                {"Downloads", "Renomear", "Excluir"}};
        Runnable[][] actions = {
                {this::open, this::share, this::extractText},
                {this::organize, this::latex, this::github},
                {this::downloads, this::rename, this::delete}};
        for (int r = 0; r < 3; r++) {
            LinearLayout row = Ui.row(this);
            for (int c = 0; c < 3; c++) {
                final Runnable a = actions[r][c];
                row.addView(Ui.tile(this, icons[r][c], labels[r][c], v -> a.run()), Ui.weight(this, 0, 5));
            }
            LinearLayout.LayoutParams lp = Ui.full(this, 0, r == 0 ? 16 : 10);
            lp.leftMargin = -Ui.dp(this, 5);
            lp.rightMargin = -Ui.dp(this, 5);
            col.addView(row, lp);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (file == null) return;
        if (!file.exists()) {
            finish();
            return;
        }
        refresh();
    }

    private void refresh() {
        title.setText(Store.base(file));
        final String size = Formatter.formatShortFileSize(this, file.length());
        meta.setText(size);
        strip.removeAllViews();
        final int gen = ++generation;
        final File f = file;
        final int thumbW = Ui.dp(this, 120);
        Thumbs.EXEC.execute(() -> {
            try (Thumbs t = new Thumbs(f)) {
                int n = t.count();
                runOnUiThread(() -> {
                    if (gen == generation) meta.setText(Store.pagesLabel(n) + " · " + size);
                });
                for (int i = 0; i < Math.min(n, MAX_THUMBS); i++) {
                    if (gen != generation || isDestroyed()) return;
                    Bitmap b = t.render(i, thumbW);
                    final int idx = i;
                    runOnUiThread(() -> addThumb(gen, b, idx));
                }
                if (n > MAX_THUMBS) runOnUiThread(() -> {
                    if (gen != generation) return;
                    TextView more = Ui.text(this, "+" + (n - MAX_THUMBS), 18, Ui.GREY, Typeface.ITALIC);
                    more.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
                    strip.addView(more);
                });
            } catch (Exception e) {
                runOnUiThread(() -> meta.setText(Ui.error(e)));
            }
        });
    }

    private void addThumb(int gen, Bitmap b, int idx) {
        if (gen != generation) return;
        LinearLayout box = Ui.column(this);
        ImageView iv = new ImageView(this);
        iv.setImageBitmap(b);
        iv.setAdjustViewBounds(true);
        iv.setBackground(Ui.box(this, Ui.PAPER, Ui.LINE, 1, 0));
        int pad = Math.max(1, Ui.dp(this, 1));
        iv.setPadding(pad, pad, pad, pad);
        iv.setOnClickListener(v -> open());
        box.addView(iv, new LinearLayout.LayoutParams(Ui.WRAP, Ui.dp(this, 146)));
        TextView n = Ui.centered(this, String.valueOf(idx + 1), 14, Ui.GREY, Typeface.NORMAL);
        box.addView(n, new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
        lp.rightMargin = Ui.dp(this, 10);
        strip.addView(box, lp);
    }

    // ---------------------------------------------------------------- + páginas no mesmo PDF

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_MORE || res != RESULT_OK) return;
        Uri pdf = Scan.pdf(data);
        int added = Scan.pdfPages(data);
        if (pdf == null) return;
        AlertDialog pd = Ui.progress(this, "Juntando…");
        Store.IO.execute(() -> {
            String err = null;
            File tmp = new File(Store.work(this), "mais-" + System.currentTimeMillis() + ".pdf");
            try {
                Store.copyUri(this, pdf, tmp);
                Store.append(file, tmp);
                Store.changed(this, file);
            } catch (Throwable e) {
                err = Ui.error(e);
            } finally {
                tmp.delete();
            }
            final String fe = err;
            runOnUiThread(() -> {
                Ui.dismiss(this, pd);
                if (fe != null) Ui.alert(this, "Não consegui juntar", fe);
                else Ui.toast(this, "+" + Store.pagesLabel(Math.max(1, added)));
                refresh();
            });
        });
    }

    // ---------------------------------------------------------------- ações

    private void open() {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(DocsProvider.uriFor(this, file), "application/pdf");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Ui.toast(this, "Nenhum leitor de PDF. Use Enviar.");
        }
    }

    private void share() {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("application/pdf");
        Uri uri = DocsProvider.uriFor(this, file);
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.putExtra(Intent.EXTRA_SUBJECT, Store.base(file));
        i.setClipData(ClipData.newRawUri(file.getName(), uri));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(i, "Enviar"));
    }

    private void organize() {
        startActivity(new Intent(this, OrganizeActivity.class)
                .putExtra(OrganizeActivity.EXTRA_PATHS, new String[]{file.getAbsolutePath()}));
    }

    private void latex() {
        startActivity(new Intent(this, LatexActivity.class).putExtra(EXTRA, file.getAbsolutePath()));
    }

    private void github() {
        startActivity(new Intent(this, GitHubActivity.class).putExtra(EXTRA, file.getAbsolutePath()));
    }

    private void downloads() {
        if (Build.VERSION.SDK_INT < 29) {
            share();
            return;
        }
        Store.IO.execute(() -> {
            boolean ok = Store.toDownloads(this, file);
            runOnUiThread(() -> Ui.toast(this, ok ? "Salvo em Download/Scanabla" : "Não consegui salvar."));
        });
    }

    private void rename() {
        EditText e = Ui.field(this, Store.base(file), "Nome");
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.selectAll();
        LinearLayout box = Ui.column(this);
        box.setPadding(Ui.dp(this, 22), Ui.dp(this, 12), Ui.dp(this, 22), 0);
        box.addView(e);
        new AlertDialog.Builder(this).setTitle("Renomear").setView(box)
                .setPositiveButton("Salvar", (d, w) -> {
                    File to = Store.rename(this, file, e.getText().toString());
                    if (to == null) {
                        Ui.toast(this, "Nome inválido ou já usado.");
                        return;
                    }
                    file = to;
                    refresh();
                })
                .setNegativeButton("Cancelar", null).show();
    }

    private void delete() {
        Ui.confirm(this, "Excluir \"" + Store.base(file) + "\"?", "Excluir", () -> {
            Store.delete(this, file);
            finish();
        });
    }

    private void extractText() {
        AlertDialog pd = Ui.progress(this, "Lendo…");
        final File f = file;
        Store.IO.execute(() -> {
            String text = null;
            String err = null;
            try {
                text = Ocr.pdfToText(f, (page, total) -> runOnUiThread(() ->
                        Ui.progressText(pd, "Página " + page + " de " + total)));
            } catch (Exception e) {
                err = Ui.error(e);
            }
            final String ft = text;
            final String fe = err;
            runOnUiThread(() -> {
                Ui.dismiss(this, pd);
                if (ft == null) Ui.alert(this, "Não deu para ler", "Na 1ª vez o leitor é baixado: confira a internet.\n\n" + fe);
                else showText(ft);
            });
        });
    }

    private void showText(String text) {
        if (!Ui.alive(this)) return;
        TextView tv = Ui.text(this, text.isEmpty() ? "(sem texto)" : text, 16, Ui.INK, Typeface.NORMAL);
        tv.setTextIsSelectable(true);
        tv.setPadding(Ui.dp(this, 22), Ui.dp(this, 10), Ui.dp(this, 22), Ui.dp(this, 10));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        ScrollView sv = new ScrollView(this);
        sv.addView(tv);
        new AlertDialog.Builder(this).setTitle("Texto").setView(sv)
                .setPositiveButton("Copiar", (d, w) -> {
                    ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("texto", text));
                    Ui.toast(this, "Copiado.");
                })
                .setNeutralButton("Enviar", (d, w) -> {
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("text/plain");
                    i.putExtra(Intent.EXTRA_TEXT, text);
                    startActivity(Intent.createChooser(i, "Enviar texto"));
                })
                .setNegativeButton("Fechar", null).show();
    }
}
