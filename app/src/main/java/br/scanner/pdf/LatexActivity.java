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
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Escrita à mão → LaTeX → PDF. */
public class LatexActivity extends Activity {

    private static final int REQ_CAM = 1;
    private static final int REQ_GAL = 2;
    private static final int MAX_PAGES = 10;

    private final ArrayList<byte[]> pages = new ArrayList<>();
    private LinearLayout strip;
    private TextView empty;
    private EditText code;
    private LinearLayout done;
    private File lastPdf;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout col = Ui.scrollScreen(this);
        Ui.header(this, col, "Converter para LaTeX");

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout holder = Ui.row(this);
        empty = Ui.centered(this, "Fotos da escrita", 17, Ui.GREY, Typeface.ITALIC);
        empty.setGravity(Gravity.CENTER);
        empty.setBackground(Ui.box(this, Ui.SOFT, Ui.LINE, 1, 10));
        holder.addView(empty, new LinearLayout.LayoutParams(getResources().getDisplayMetrics().widthPixels
                - Ui.dp(this, 40), Ui.dp(this, 150)));
        strip = Ui.row(this);
        holder.addView(strip);
        hs.addView(holder);
        col.addView(hs, Ui.full(this, 150, 8));

        LinearLayout src = Ui.row(this);
        src.addView(Ui.button(this, R.drawable.ic_camera, "Câmera", false,
                v -> Scan.start(this, REQ_CAM, true)), Ui.weight(this, 50, 4));
        src.addView(Ui.button(this, R.drawable.ic_photo, "Galeria", false, v -> gallery()), Ui.weight(this, 50, 4));
        src.addView(Ui.button(this, R.drawable.ic_doc, "PDF", false, v -> pickDoc()), Ui.weight(this, 50, 4));
        LinearLayout.LayoutParams slp = Ui.full(this, 0, 12);
        slp.leftMargin = -Ui.dp(this, 4);
        slp.rightMargin = -Ui.dp(this, 4);
        col.addView(src, slp);

        col.addView(Ui.button(this, R.drawable.ic_tex, "Gerar LaTeX", true, v -> transcribe()), Ui.full(this, 56, 12));

        code = new EditText(this);
        code.setTypeface(Typeface.MONOSPACE);
        code.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        code.setTextColor(Ui.INK);
        code.setHint("\\documentclass{article}\n\\begin{document}\n…\n\\end{document}");
        code.setHintTextColor(0xFF9A9A9A);
        code.setGravity(Gravity.TOP | Gravity.START);
        code.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        code.setMinLines(12);
        code.setBackground(Ui.box(this, Ui.SOFT, Ui.LINE, 1, 10));
        int p = Ui.dp(this, 12);
        code.setPadding(p, p, p, p);
        col.addView(code, Ui.full(this, 0, 16));

        LinearLayout actions = Ui.row(this);
        actions.addView(Ui.button(this, R.drawable.ic_copy, "Copiar", false, v -> copy()), Ui.weight(this, 54, 4));
        actions.addView(Ui.button(this, R.drawable.ic_doc, "Gerar PDF", true, v -> compile()), Ui.weight(this, 54, 4));
        LinearLayout.LayoutParams alp = Ui.full(this, 0, 12);
        alp.leftMargin = -Ui.dp(this, 4);
        alp.rightMargin = -Ui.dp(this, 4);
        col.addView(actions, alp);

        done = Ui.column(this);
        done.setVisibility(View.GONE);
        LinearLayout ok = Ui.row(this);
        ok.setGravity(Gravity.CENTER);
        ok.addView(Ui.icon(this, R.drawable.ic_check, Ui.INK, 22));
        TextView okt = Ui.text(this, "PDF gerado", 18, Ui.INK, Typeface.NORMAL);
        okt.setPadding(Ui.dp(this, 8), 0, 0, 0);
        ok.addView(okt);
        done.addView(ok, Ui.full(this, 0, 18));
        LinearLayout after = Ui.row(this);
        after.addView(Ui.button(this, R.drawable.ic_open, "Abrir PDF", true, v -> openPdf()), Ui.weight(this, 52, 4));
        after.addView(Ui.button(this, R.drawable.ic_chev, "Opções", false, v -> openDocScreen()), Ui.weight(this, 52, 4));
        LinearLayout.LayoutParams dlp = Ui.full(this, 0, 10);
        dlp.leftMargin = -Ui.dp(this, 4);
        dlp.rightMargin = -Ui.dp(this, 4);
        done.addView(after, dlp);
        col.addView(done);

        String path = getIntent().getStringExtra(DocActivity.EXTRA);
        if (path != null) {
            File f = new File(path);
            File tex = Store.tex(f);
            if (tex.exists()) {
                try {
                    code.setText(new String(java.nio.file.Files.readAllBytes(tex.toPath()), StandardCharsets.UTF_8));
                } catch (Exception ignored) {
                }
            }
            loadDoc(f);
        }
    }

    // ---------------------------------------------------------------- imagens

    private void gallery() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(Intent.createChooser(i, "Fotos"), REQ_GAL);
    }

    private void pickDoc() {
        File[] docs = Store.docs(this);
        if (docs.length == 0) {
            Ui.toast(this, "Nenhum PDF salvo.");
            return;
        }
        String[] names = new String[docs.length];
        for (int i = 0; i < docs.length; i++) names[i] = Store.base(docs[i]);
        new AlertDialog.Builder(this).setTitle("PDF").setItems(names, (d, w) -> loadDoc(docs[w])).show();
    }

    private void loadDoc(File f) {
        AlertDialog pd = Ui.progress(this, "Abrindo…");
        Thumbs.EXEC.execute(() -> {
            ArrayList<byte[]> got = new ArrayList<>();
            String err = null;
            try (Thumbs t = new Thumbs(f)) {
                int n = Math.min(t.count(), MAX_PAGES - pages.size());
                for (int i = 0; i < n; i++) {
                    Bitmap b = t.render(i, 1600);
                    got.add(Img.jpeg(b, 85));
                    b.recycle();
                }
            } catch (Exception e) {
                err = Ui.error(e);
            }
            final String fe = err;
            runOnUiThread(() -> {
                Ui.dismiss(this, pd);
                if (fe != null) Ui.toast(this, fe);
                addPages(got);
            });
        });
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null) return;
        List<Uri> uris = req == REQ_CAM ? Scan.images(data) : Scan.picked(data);
        if (uris.isEmpty()) return;
        AlertDialog pd = Ui.progress(this, "Preparando…");
        Store.IO.execute(() -> {
            ArrayList<byte[]> got = new ArrayList<>();
            String err = null;
            for (Uri u : uris) {
                if (pages.size() + got.size() >= MAX_PAGES) break;
                try {
                    Bitmap b = Img.decode(this, u, 1600);
                    got.add(Img.jpeg(b, 85));
                    b.recycle();
                } catch (Exception e) {
                    err = Ui.error(e);
                }
            }
            final String fe = err;
            runOnUiThread(() -> {
                Ui.dismiss(this, pd);
                if (fe != null) Ui.toast(this, fe);
                addPages(got);
            });
        });
    }

    private void addPages(List<byte[]> got) {
        for (byte[] j : got) {
            if (pages.size() >= MAX_PAGES) {
                Ui.toast(this, "Máximo de " + MAX_PAGES + " páginas.");
                break;
            }
            pages.add(j);
        }
        renderStrip();
    }

    private void renderStrip() {
        strip.removeAllViews();
        empty.setVisibility(pages.isEmpty() ? View.VISIBLE : View.GONE);
        for (int i = 0; i < pages.size(); i++) {
            final byte[] j = pages.get(i);
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(Img.fromJpeg(j, 400));
            iv.setAdjustViewBounds(true);
            iv.setBackground(Ui.box(this, Ui.PAPER, Ui.LINE, 1, 0));
            int pad = Math.max(1, Ui.dp(this, 1));
            iv.setPadding(pad, pad, pad, pad);
            iv.setOnClickListener(v -> Ui.confirm(this, "Remover esta foto?", "Remover", () -> {
                pages.remove(j);
                renderStrip();
            }));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.WRAP, Ui.dp(this, 150));
            lp.rightMargin = Ui.dp(this, 10);
            strip.addView(iv, lp);
        }
    }

    // ---------------------------------------------------------------- transcrever

    private void transcribe() {
        if (pages.isEmpty()) {
            Ui.toast(this, "Adicione uma foto.");
            return;
        }
        String key = Store.prefs(this).getString("gemini_key", "");
        if (key.trim().isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Sem chave do Gemini")
                    .setMessage("Sem a chave, só o texto simples é lido (sem fórmulas).")
                    .setPositiveButton("Texto simples", (d, w) -> plain())
                    .setNeutralButton("Configurar", (d, w) -> startActivity(new Intent(this, SettingsActivity.class)))
                    .setNegativeButton("Cancelar", null).show();
            return;
        }
        final ArrayList<byte[]> imgs = new ArrayList<>(pages);
        AlertDialog pd = Ui.progress(this, "Lendo a escrita…");
        Store.IO.execute(() -> {
            String tex = null;
            String err = null;
            try {
                tex = Gemini.latex(key, imgs);
            } catch (Exception e) {
                err = Ui.error(e);
            }
            final String ft = tex;
            final String fe = err;
            runOnUiThread(() -> {
                Ui.dismiss(this, pd);
                if (ft != null) {
                    code.setText(ft);
                    done.setVisibility(View.GONE);
                } else {
                    Ui.alert(this, "Não consegui ler", fe);
                }
            });
        });
    }

    private void plain() {
        final ArrayList<byte[]> imgs = new ArrayList<>(pages);
        AlertDialog pd = Ui.progress(this, "Lendo…");
        Store.IO.execute(() -> {
            String tex = null;
            String err = null;
            try {
                tex = Latex.fromPlain(Ocr.jpegsToText(imgs));
            } catch (Exception e) {
                err = Ui.error(e);
            }
            final String ft = tex;
            final String fe = err;
            runOnUiThread(() -> {
                Ui.dismiss(this, pd);
                if (ft != null) code.setText(ft);
                else Ui.alert(this, "Não consegui ler", fe);
            });
        });
    }

    // ---------------------------------------------------------------- PDF

    private void compile() {
        final String tex = code.getText().toString();
        if (tex.trim().isEmpty()) {
            Ui.toast(this, "Sem código.");
            return;
        }
        AlertDialog pd = Ui.progress(this, "Gerando PDF…");
        Store.IO.execute(() -> {
            File out = null;
            String err = null;
            boolean texError = false;
            try {
                byte[] pdf = Latex.compile(tex);
                out = Store.newDoc(this, "LaTeX");
                try (java.io.FileOutputStream os = new java.io.FileOutputStream(out)) {
                    os.write(pdf);
                }
                try (java.io.FileOutputStream os = new java.io.FileOutputStream(Store.tex(out))) {
                    os.write(tex.getBytes(StandardCharsets.UTF_8));
                }
                Store.changed(this, out);
            } catch (Latex.CompileError e) {
                texError = true;
                err = Ui.error(e);
            } catch (Exception e) {
                err = Ui.error(e);
            }
            final File fo = out;
            final String fe = err;
            final boolean fte = texError;
            runOnUiThread(() -> {
                Ui.dismiss(this, pd);
                if (fe != null) {
                    Ui.alert(this, fte ? "Erro no código LaTeX" : "Não consegui gerar", fe);
                    return;
                }
                lastPdf = fo;
                done.setVisibility(View.VISIBLE);
            });
        });
    }

    private void copy() {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("LaTeX", code.getText().toString()));
        Ui.toast(this, "Copiado.");
    }

    private void openPdf() {
        if (lastPdf == null || !lastPdf.exists()) return;
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(DocsProvider.uriFor(this, lastPdf), "application/pdf");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            openDocScreen();
        }
    }

    private void openDocScreen() {
        if (lastPdf == null || !lastPdf.exists()) return;
        startActivity(new Intent(this, DocActivity.class).putExtra(DocActivity.EXTRA, lastPdf.getAbsolutePath()));
    }
}
