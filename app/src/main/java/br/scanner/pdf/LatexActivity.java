package br.scanner.pdf;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Rect;
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
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/** Escrita à mão → LaTeX organizado → PDF, com abas Fotos / Código / PDF. */
public class LatexActivity extends Activity {

    private static final int REQ_CAM = 1;
    private static final int REQ_GAL = 2;
    private static final int MAX_PAGES = 10;
    private static final int PAGE_PX = 2048;

    private final ArrayList<byte[]> pages = new ArrayList<>();
    private File session;
    private volatile File doc;
    private boolean docFromSession;
    private boolean organized;

    private final TextView[] tabs = new TextView[3];
    private final View[] panes = new View[3];
    private LinearLayout photoStrip;
    private TextView photoEmpty;
    private TextView photoHint;
    private EditText code;
    private LinearLayout pdfList;
    private TextView pdfEmpty;
    private LinearLayout pdfActions;
    private int pdfGen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new File(Store.work(this), "latex-" + System.currentTimeMillis());
        session.mkdirs();
        organized = !"fiel".equals(Store.prefs(this).getString("tex_mode", "org"));

        LinearLayout col = Ui.scrollScreen(this);
        Ui.header(this, col, "Converter para LaTeX");
        col.addView(segmented(new String[]{"Fotos", "Código", "PDF"}, 0, this::showTab, tabs), Ui.full(this, 46, 4));

        panes[0] = buildPhotos();
        panes[1] = buildCode();
        panes[2] = buildPdf();
        for (View p : panes) col.addView(p, Ui.full(this, 0, 16));

        int start = 0;
        String path = getIntent().getStringExtra(DocActivity.EXTRA);
        if (path != null) {
            File f = new File(path);
            if (Store.hasTex(f)) {
                doc = f;
                docFromSession = true;
                String t = Store.readTex(f);
                if (t != null) code.setText(t);
                File figs = new File(Store.project(f), "figuras");
                try {
                    if (figs.isDirectory()) Store.copyDir(figs, new File(session, "figuras"));
                } catch (Exception ignored) {
                }
                start = 2;
            } else {
                loadPdfAsPhotos(f);
            }
        }
        renderPhotos();
        renderPdf();
        showTab(start);
    }

    // ---------------------------------------------------------------- abas

    /** Seletor preto e branco (ex.: Fotos | Código | PDF). */
    private LinearLayout segmented(String[] labels, int selected, IntConsumer onPick, TextView[] out) {
        LinearLayout row = Ui.row(this);
        int p = Ui.dp(this, 3);
        row.setPadding(p, p, p, p);
        row.setBackground(Ui.box(this, Ui.PAPER, Ui.INK, 1, 10));
        for (int i = 0; i < labels.length; i++) {
            TextView t = Ui.centered(this, labels[i], 17, Ui.INK, Typeface.NORMAL);
            t.setGravity(Gravity.CENTER);
            final int k = i;
            t.setOnClickListener(v -> {
                mark(out, k);
                onPick.accept(k);
            });
            out[i] = t;
            row.addView(t, new LinearLayout.LayoutParams(0, Ui.MATCH, 1f));
        }
        mark(out, selected);
        return row;
    }

    private void mark(TextView[] items, int sel) {
        for (int i = 0; i < items.length; i++) {
            boolean on = i == sel;
            items[i].setTextColor(on ? Ui.PAPER : Ui.INK);
            items[i].setBackground(on ? Ui.box(this, Ui.INK, Ui.INK, 0, 8) : null);
        }
    }

    private void showTab(int k) {
        mark(tabs, k);
        for (int i = 0; i < panes.length; i++) panes[i].setVisibility(i == k ? View.VISIBLE : View.GONE);
    }

    // ---------------------------------------------------------------- Fotos

    private View buildPhotos() {
        LinearLayout p = Ui.column(this);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout holder = Ui.row(this);
        photoEmpty = Ui.centered(this, "Fotos da escrita", 18, Ui.GREY, Typeface.ITALIC);
        photoEmpty.setGravity(Gravity.CENTER);
        photoEmpty.setBackground(Ui.box(this, Ui.SOFT, Ui.LINE, 1, 10));
        holder.addView(photoEmpty, new LinearLayout.LayoutParams(
                getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 40), Ui.dp(this, 300)));
        photoStrip = Ui.row(this);
        holder.addView(photoStrip);
        hs.addView(holder);
        p.addView(hs, Ui.full(this, 300, 0));
        photoHint = Ui.text(this, "Toque para ampliar · segure para remover", 14, Ui.GREY, Typeface.ITALIC);
        photoHint.setPadding(0, Ui.dp(this, 6), 0, 0);
        p.addView(photoHint);

        LinearLayout src = Ui.row(this);
        src.addView(Ui.button(this, R.drawable.ic_camera, "Câmera", false,
                v -> Scan.start(this, REQ_CAM, true)), Ui.weight(this, 50, 4));
        src.addView(Ui.button(this, R.drawable.ic_photo, "Galeria", false, v -> gallery()), Ui.weight(this, 50, 4));
        src.addView(Ui.button(this, R.drawable.ic_doc, "PDF", false, v -> pickDoc()), Ui.weight(this, 50, 4));
        p.addView(src, wide(12));

        TextView[] modes = new TextView[2];
        p.addView(segmented(new String[]{"Organizado", "Fiel"}, organized ? 0 : 1, k -> {
            organized = k == 0;
            Store.prefs(this).edit().putString("tex_mode", organized ? "org" : "fiel").apply();
        }, modes), Ui.full(this, 46, 14));

        p.addView(Ui.button(this, R.drawable.ic_tex, "Gerar LaTeX", true, v -> generate()), Ui.full(this, 56, 12));
        return p;
    }

    private LinearLayout.LayoutParams wide(int topDp) {
        LinearLayout.LayoutParams lp = Ui.full(this, 0, topDp);
        lp.leftMargin = -Ui.dp(this, 4);
        lp.rightMargin = -Ui.dp(this, 4);
        return lp;
    }

    private void renderPhotos() {
        photoStrip.removeAllViews();
        photoEmpty.setVisibility(pages.isEmpty() ? View.VISIBLE : View.GONE);
        photoHint.setVisibility(pages.isEmpty() ? View.GONE : View.VISIBLE);
        for (int i = 0; i < pages.size(); i++) {
            final byte[] j = pages.get(i);
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(Img.fromJpeg(j, 700));
            iv.setAdjustViewBounds(true);
            iv.setBackground(Ui.box(this, Ui.PAPER, Ui.LINE, 1, 0));
            int pad = Math.max(1, Ui.dp(this, 1));
            iv.setPadding(pad, pad, pad, pad);
            iv.setOnClickListener(v -> ZoomImage.show(this, Img.fromJpeg(j, PAGE_PX)));
            iv.setOnLongClickListener(v -> {
                Ui.confirm(this, "Remover esta foto?", "Remover", () -> {
                    pages.remove(j);
                    renderPhotos();
                });
                return true;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.WRAP, Ui.dp(this, 300));
            lp.rightMargin = Ui.dp(this, 10);
            photoStrip.addView(iv, lp);
        }
    }

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
        new AlertDialog.Builder(this).setTitle("PDF").setItems(names, (d, w) -> loadPdfAsPhotos(docs[w])).show();
    }

    private void loadPdfAsPhotos(File f) {
        AlertDialog pd = Ui.progress(this, "Abrindo…");
        final int room = MAX_PAGES - pages.size();
        Thumbs.EXEC.execute(() -> {
            ArrayList<byte[]> got = new ArrayList<>();
            String err = null;
            try (Thumbs t = new Thumbs(f)) {
                int n = Math.min(t.count(), room);
                for (int i = 0; i < n; i++) {
                    Bitmap b = t.render(i, PAGE_PX);
                    got.add(Img.jpeg(b, 88));
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
        final int room = MAX_PAGES - pages.size();
        Store.IO.execute(() -> {
            ArrayList<byte[]> got = new ArrayList<>();
            String err = null;
            for (Uri u : uris) {
                if (got.size() >= room) break;
                try {
                    Bitmap b = Img.decode(this, u, PAGE_PX);
                    got.add(Img.jpeg(b, 88));
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
        renderPhotos();
        showTab(0);
    }

    // ---------------------------------------------------------------- Código

    private View buildCode() {
        LinearLayout p = Ui.column(this);
        code = new EditText(this);
        code.setTypeface(Typeface.MONOSPACE);
        code.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        code.setTextColor(Ui.INK);
        code.setHint("\\documentclass{article}\n\\begin{document}\n…\n\\end{document}");
        code.setHintTextColor(0xFF9A9A9A);
        code.setGravity(Gravity.TOP | Gravity.START);
        code.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        code.setMinLines(18);
        code.setBackground(Ui.box(this, Ui.SOFT, Ui.LINE, 1, 10));
        int pad = Ui.dp(this, 12);
        code.setPadding(pad, pad, pad, pad);
        TexHighlight.attach(code);
        p.addView(code);

        LinearLayout actions = Ui.row(this);
        actions.addView(Ui.button(this, R.drawable.ic_copy, "Copiar", false, v -> copy()), Ui.weight(this, 54, 4));
        actions.addView(Ui.button(this, R.drawable.ic_doc, "Gerar PDF", true, v -> compileCode()), Ui.weight(this, 54, 4));
        p.addView(actions, wide(12));
        return p;
    }

    private void copy() {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("LaTeX", code.getText().toString()));
        Ui.toast(this, "Copiado.");
    }

    // ---------------------------------------------------------------- PDF

    private View buildPdf() {
        LinearLayout p = Ui.column(this);
        pdfEmpty = Ui.centered(this, "Ainda sem PDF.", 18, Ui.GREY, Typeface.ITALIC);
        pdfEmpty.setPadding(0, Ui.dp(this, 40), 0, Ui.dp(this, 40));
        p.addView(pdfEmpty);
        pdfList = Ui.column(this);
        p.addView(pdfList);
        pdfActions = Ui.row(this);
        pdfActions.addView(Ui.button(this, R.drawable.ic_open, "Abrir", true, v -> openPdf()), Ui.weight(this, 52, 4));
        pdfActions.addView(Ui.button(this, R.drawable.ic_chev, "Opções", false, v -> openDocScreen()), Ui.weight(this, 52, 4));
        p.addView(pdfActions, wide(6));
        return p;
    }

    /** Mostra as páginas do PDF gerado aqui mesmo (toque para ampliar). */
    private void renderPdf() {
        pdfList.removeAllViews();
        final File f = doc;
        boolean has = f != null && f.exists();
        pdfEmpty.setVisibility(has ? View.GONE : View.VISIBLE);
        pdfActions.setVisibility(has ? View.VISIBLE : View.GONE);
        if (!has) return;
        final int gen = ++pdfGen;
        final int width = Math.min(1080, getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 40));
        Thumbs.EXEC.execute(() -> {
            try (Thumbs t = new Thumbs(f)) {
                int total = t.count();
                int n = Math.min(total, 15);
                for (int i = 0; i < n; i++) {
                    if (gen != pdfGen || isDestroyed()) return;
                    Bitmap full = t.render(i, width);
                    Bitmap small = full.copy(Bitmap.Config.RGB_565, false);
                    full.recycle();
                    final int idx = i;
                    runOnUiThread(() -> addPdfPage(gen, small, f, idx));
                }
                if (total > n) runOnUiThread(() -> {
                    if (gen != pdfGen) return;
                    pdfList.addView(Ui.centered(this, "+" + (total - n) + " páginas: toque em Abrir", 15, Ui.GREY, Typeface.ITALIC));
                });
            } catch (Exception e) {
                runOnUiThread(() -> Ui.toast(this, Ui.error(e)));
            }
        });
    }

    private void addPdfPage(int gen, Bitmap b, File f, int idx) {
        if (gen != pdfGen) return;
        ImageView iv = new ImageView(this);
        iv.setImageBitmap(b);
        iv.setAdjustViewBounds(true);
        iv.setBackground(Ui.box(this, Ui.PAPER, Ui.LINE, 1, 0));
        int pad = Math.max(1, Ui.dp(this, 1));
        iv.setPadding(pad, pad, pad, pad);
        iv.setOnClickListener(v -> Thumbs.EXEC.execute(() -> {
            try (Thumbs t = new Thumbs(f)) {
                Bitmap big = t.render(idx, 2000);
                runOnUiThread(() -> ZoomImage.show(this, big));
            } catch (Exception ignored) {
            }
        }));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP);
        lp.bottomMargin = Ui.dp(this, 12);
        pdfList.addView(iv, lp);
    }

    private void openPdf() {
        File f = doc;
        if (f == null || !f.exists()) return;
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(DocsProvider.uriFor(this, f), "application/pdf");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            openDocScreen();
        }
    }

    private void openDocScreen() {
        File f = doc;
        if (f == null || !f.exists()) return;
        startActivity(new Intent(this, DocActivity.class).putExtra(DocActivity.EXTRA, f.getAbsolutePath()));
    }

    // ---------------------------------------------------------------- gerar

    private String key() {
        return Store.prefs(this).getString("gemini_key", "").trim();
    }

    /** Fotos → (Gemini) LaTeX organizado + figuras recortadas → PDF, corrigindo erros sozinho. */
    private void generate() {
        if (pages.isEmpty()) {
            Ui.toast(this, "Adicione uma foto.");
            showTab(0);
            return;
        }
        String key = key();
        if (key.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Sem chave do Gemini")
                    .setMessage("Sem a chave, só o texto simples é lido (sem fórmulas nem organização).")
                    .setPositiveButton("Texto simples", (d, w) -> plain())
                    .setNeutralButton("Configurar", (d, w) -> startActivity(new Intent(this, SettingsActivity.class)))
                    .setNegativeButton("Cancelar", null).show();
            return;
        }
        if (!docFromSession) doc = null; // fotos novas viram um documento novo
        final ArrayList<byte[]> imgs = new ArrayList<>(pages);
        final boolean org = organized;
        AlertDialog pd = Ui.progress(this, org ? "Organizando as anotações…" : "Lendo a escrita…");
        Store.IO.execute(() -> {
            String err = null;
            boolean texErr = false;
            try {
                Gemini.Result r = Gemini.transcribe(key, imgs, org, s -> runOnUiThread(() -> Ui.progressText(pd, s)));
                String tex = prepareFigures(r, imgs);
                setCode(tex);
                compileAndSave(tex, key, pd);
            } catch (Latex.CompileError e) {
                texErr = true;
                err = Ui.error(e);
            } catch (Exception e) {
                err = Ui.error(e);
            }
            report(pd, err, texErr);
        });
    }

    /** Sem chave: OCR do celular → LaTeX simples → PDF. */
    private void plain() {
        if (!docFromSession) doc = null;
        final ArrayList<byte[]> imgs = new ArrayList<>(pages);
        AlertDialog pd = Ui.progress(this, "Lendo…");
        Store.IO.execute(() -> {
            String err = null;
            boolean texErr = false;
            try {
                Store.deleteTree(new File(session, "figuras"));
                String tex = Latex.fromPlain(Ocr.jpegsToText(imgs));
                setCode(tex);
                compileAndSave(tex, "", pd);
            } catch (Latex.CompileError e) {
                texErr = true;
                err = Ui.error(e);
            } catch (Exception e) {
                err = Ui.error(e);
            }
            report(pd, err, texErr);
        });
    }

    /** Botão "Gerar PDF" da aba Código: compila o que está escrito (sem mexer no código). */
    private void compileCode() {
        final String tex = code.getText().toString();
        if (tex.trim().isEmpty()) {
            Ui.toast(this, "Sem código.");
            return;
        }
        AlertDialog pd = Ui.progress(this, "Gerando PDF…");
        Store.IO.execute(() -> {
            String err = null;
            boolean texErr = false;
            try {
                String t = Latex.fixFigures(tex, session);
                saveDoc(Latex.compile(t, session), t);
            } catch (Latex.CompileError e) {
                texErr = true;
                err = Ui.error(e);
            } catch (Exception e) {
                err = Ui.error(e);
            }
            report(pd, err, texErr);
        });
    }

    private void fixWithAi(String log) {
        final String tex = code.getText().toString();
        final String key = key();
        AlertDialog pd = Ui.progress(this, "Corrigindo o LaTeX…");
        Store.IO.execute(() -> {
            String err = null;
            boolean texErr = false;
            try {
                String fixed = Latex.fixFigures(Gemini.fix(key, tex, log), session);
                setCode(fixed);
                compileAndSave(fixed, key, pd);
            } catch (Latex.CompileError e) {
                texErr = true;
                err = Ui.error(e);
            } catch (Exception e) {
                err = Ui.error(e);
            }
            report(pd, err, texErr);
        });
    }

    /** Compila; se der erro de LaTeX e houver chave, pede a correção ao Gemini (até 2 vezes). */
    private void compileAndSave(String tex, String key, AlertDialog pd) throws Exception {
        String cur = tex;
        for (int attempt = 0; ; attempt++) {
            say(pd, "Gerando PDF…");
            try {
                saveDoc(Latex.compile(cur, session), cur);
                return;
            } catch (Latex.CompileError e) {
                if (key == null || key.isEmpty() || attempt >= 2) throw e;
                say(pd, "Corrigindo o LaTeX…");
                cur = Latex.fixFigures(Gemini.fix(key, cur, e.getMessage()), session);
                setCode(cur);
            }
        }
    }

    /** Recorta os desenhos das fotos para figuras/ e acerta os nomes no código. */
    private String prepareFigures(Gemini.Result r, List<byte[]> imgs) throws Exception {
        File figs = new File(session, "figuras");
        Store.deleteTree(figs);
        figs.mkdirs();
        String tex = r.latex;
        int n = 0;
        for (Gemini.Figure f : r.figures) {
            n++;
            String name = f.name == null ? "" : f.name.replaceAll(".*/", "");
            String stem = name.replaceAll("\\.[A-Za-z]+$", "");
            if (!stem.matches("[A-Za-z0-9_-]{1,40}")) stem = "fig" + n;
            String jpg = stem + ".jpg";
            if (!jpg.equals(name) && !name.isEmpty()) tex = tex.replace("figuras/" + name, "figuras/" + jpg);
            int pg = f.page - 1;
            if (pg < 0 || pg >= imgs.size()) continue;
            crop(imgs.get(pg), f.box, new File(figs, jpg));
        }
        tex = Latex.fixFigures(tex, session);
        Store.writeText(new File(session, "main.tex"), tex);
        return tex;
    }

    @SuppressWarnings("deprecation")
    private static void crop(byte[] jpeg, int[] box, File out) {
        try {
            int y0 = clamp(box[0]), x0 = clamp(box[1]), y1 = clamp(box[2]), x1 = clamp(box[3]);
            if (y1 - y0 < 10 || x1 - x0 < 10) return;
            BitmapRegionDecoder dec = BitmapRegionDecoder.newInstance(jpeg, 0, jpeg.length, false);
            int w = dec.getWidth();
            int h = dec.getHeight();
            int padX = w * 15 / 1000;
            int padY = h * 15 / 1000;
            Rect rect = new Rect(Math.max(0, x0 * w / 1000 - padX), Math.max(0, y0 * h / 1000 - padY),
                    Math.min(w, x1 * w / 1000 + padX), Math.min(h, y1 * h / 1000 + padY));
            Bitmap b = dec.decodeRegion(rect, null);
            dec.recycle();
            if (b == null) return;
            try (FileOutputStream os = new FileOutputStream(out)) {
                b.compress(Bitmap.CompressFormat.JPEG, 90, os);
            }
            b.recycle();
        } catch (Exception ignored) {
            // sem a figura, o código troca por \fbox{Figura}
        }
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(1000, v));
    }

    /** Grava o PDF (no mesmo documento desta sessão) e o projeto LaTeX ao lado. */
    private void saveDoc(byte[] pdf, String tex) throws Exception {
        File target = doc;
        if (target == null) {
            String t = Latex.titleOf(tex);
            target = Store.newDoc(this, t != null ? t : "LaTeX");
        }
        File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
        try (FileOutputStream os = new FileOutputStream(tmp)) {
            os.write(pdf);
        }
        Store.replace(tmp, target);
        Store.writeText(new File(session, "main.tex"), tex);
        Store.saveProject(target, tex, session);
        Store.changed(this, target);
        doc = target;
        docFromSession = true;
    }

    private void setCode(String tex) {
        runOnUiThread(() -> code.setText(tex));
    }

    private void say(AlertDialog pd, String s) {
        runOnUiThread(() -> Ui.progressText(pd, s));
    }

    private void report(AlertDialog pd, String err, boolean texErr) {
        runOnUiThread(() -> {
            Ui.dismiss(this, pd);
            if (err == null) {
                renderPdf();
                showTab(2);
                Ui.toast(this, "PDF salvo");
                return;
            }
            if (!Ui.alive(this)) return;
            if (texErr) {
                showTab(1);
                AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle("Erro no LaTeX").setMessage(err)
                        .setNegativeButton("OK", null);
                if (!key().isEmpty()) b.setPositiveButton("Corrigir com IA", (d, w) -> fixWithAi(err));
                b.show();
            } else {
                Ui.alert(this, "Não consegui", err);
            }
        });
    }
}
