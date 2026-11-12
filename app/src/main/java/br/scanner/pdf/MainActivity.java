package br.scanner.pdf;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Início: escanear, abrir, recursos e a lista de documentos (cada um é um PDF separado). */
public class MainActivity extends Activity {

    private static final int REQ_SCAN = 1;
    private static final int REQ_OPEN = 2;
    private static final int REQ_MERGE = 3;
    private static final int REQ_PICK = 4;

    private interface OnDoc {
        void run(File f);
    }

    private LinearLayout docsBox;
    private TextView docsEmpty;
    private OnDoc pending;
    private final ArrayList<String> mergeChosen = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout col = Ui.scrollScreen(this);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.nabla);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 82), Ui.dp(this, 85));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.topMargin = Ui.dp(this, 34);
        col.addView(logo, lp);
        TextView name = Ui.centered(this, "Scanabla", 42, Ui.INK, Typeface.NORMAL);
        name.setPadding(0, Ui.dp(this, 12), 0, 0);
        col.addView(name);
        col.addView(Ui.centered(this, "Escanear. Organizar. Exportar.", 19, Ui.INK, Typeface.ITALIC));

        col.addView(Ui.button(this, R.drawable.ic_camera, "Escanear", true,
                v -> Scan.start(this, REQ_SCAN, false)), Ui.full(this, 58, 30));
        col.addView(Ui.button(this, R.drawable.ic_folder, "Abrir PDF", false,
                v -> startActivityForResult(Scan.pickPdf(false), REQ_OPEN)), Ui.full(this, 58, 12));

        col.addView(Ui.section(this, "Recursos"));
        col.addView(Ui.item(this, R.drawable.ic_merge, "Unir PDFs", v -> merge()));
        col.addView(Ui.item(this, R.drawable.ic_grid, "Organizar páginas", v -> chooseDoc(this::organize)));
        col.addView(Ui.item(this, R.drawable.ic_trash, "Excluir páginas", v -> chooseDoc(this::organize)));
        col.addView(Ui.item(this, R.drawable.ic_tex, "Converter para LaTeX",
                v -> startActivity(new Intent(this, LatexActivity.class))));
        col.addView(Ui.item(this, R.drawable.ic_upload, "Enviar para GitHub", v -> chooseDoc(f ->
                startActivity(new Intent(this, GitHubActivity.class).putExtra(DocActivity.EXTRA, f.getAbsolutePath())))));

        col.addView(Ui.section(this, "Documentos"));
        docsEmpty = Ui.centered(this, "Nenhum ainda.", 17, Ui.GREY, Typeface.ITALIC);
        docsEmpty.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
        col.addView(docsEmpty);
        docsBox = Ui.column(this);
        col.addView(docsBox);

        LinearLayout settings = Ui.row(this);
        settings.setGravity(Gravity.CENTER);
        settings.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 14));
        ImageView gear = Ui.icon(this, R.drawable.ic_gear, Ui.INK, 22);
        settings.addView(gear);
        TextView st = Ui.text(this, "Configurações", 18, Ui.INK, Typeface.NORMAL);
        st.setPadding(Ui.dp(this, 10), 0, 0, 0);
        settings.addView(st);
        settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        LinearLayout.LayoutParams slp = Ui.full(this, 0, 18);
        col.addView(settings, slp);

        handleIncoming(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIncoming(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderDocs();
    }

    /** PDF aberto a partir de outro app ("Abrir com" / "Enviar para"). */
    @SuppressWarnings("deprecation")
    private void handleIncoming(Intent i) {
        if (i == null) return;
        Uri u = null;
        if (Intent.ACTION_VIEW.equals(i.getAction())) u = i.getData();
        else if (Intent.ACTION_SEND.equals(i.getAction())) u = i.getParcelableExtra(Intent.EXTRA_STREAM);
        setIntent(new Intent(this, MainActivity.class));
        if (u != null) importDoc(u, this::openDoc);
    }

    // ---------------------------------------------------------------- ações

    private void openDoc(File f) {
        startActivity(new Intent(this, DocActivity.class).putExtra(DocActivity.EXTRA, f.getAbsolutePath()));
    }

    private void organize(File f) {
        startActivity(new Intent(this, OrganizeActivity.class)
                .putExtra(OrganizeActivity.EXTRA_PATHS, new String[]{f.getAbsolutePath()}));
    }

    /** Escolher um documento salvo, ou um PDF do celular. */
    private void chooseDoc(OnDoc cb) {
        File[] fs = Store.docs(this);
        if (fs.length == 0) {
            pending = cb;
            startActivityForResult(Scan.pickPdf(false), REQ_PICK);
            return;
        }
        String[] names = new String[fs.length + 1];
        for (int i = 0; i < fs.length; i++) names[i] = Store.base(fs[i]);
        names[fs.length] = "Do celular…";
        new AlertDialog.Builder(this).setTitle("Qual PDF?").setItems(names, (d, w) -> {
            if (w < fs.length) {
                cb.run(fs[w]);
            } else {
                pending = cb;
                startActivityForResult(Scan.pickPdf(false), REQ_PICK);
            }
        }).show();
    }

    private void merge() {
        File[] fs = Store.docs(this);
        mergeChosen.clear();
        if (fs.length == 0) {
            startActivityForResult(Scan.pickPdf(true), REQ_MERGE);
            return;
        }
        String[] names = new String[fs.length];
        for (int i = 0; i < fs.length; i++) names[i] = Store.base(fs[i]);
        boolean[] checked = new boolean[fs.length];
        new AlertDialog.Builder(this).setTitle("Unir PDFs")
                .setMultiChoiceItems(names, checked, (d, w, on) -> checked[w] = on)
                .setPositiveButton("Unir", (d, w) -> {
                    for (int i = 0; i < fs.length; i++) if (checked[i]) mergeChosen.add(fs[i].getAbsolutePath());
                    if (mergeChosen.size() < 2) Ui.toast(this, "Escolha 2 ou mais.");
                    else openMerge();
                })
                .setNeutralButton("+ Do celular", (d, w) -> {
                    for (int i = 0; i < fs.length; i++) if (checked[i]) mergeChosen.add(fs[i].getAbsolutePath());
                    startActivityForResult(Scan.pickPdf(true), REQ_MERGE);
                })
                .setNegativeButton("Cancelar", null).show();
    }

    private void openMerge() {
        startActivity(new Intent(this, OrganizeActivity.class)
                .putExtra(OrganizeActivity.EXTRA_PATHS, mergeChosen.toArray(new String[0]))
                .putExtra(OrganizeActivity.EXTRA_MERGE, true));
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null) return;
        if (req == REQ_SCAN) {
            Uri pdf = Scan.pdf(data);
            if (pdf == null) {
                Ui.toast(this, "O scanner não devolveu um PDF.");
                return;
            }
            saveScan(pdf, Scan.pdfPages(data));
        } else if (req == REQ_OPEN) {
            Uri u = data.getData();
            if (u != null) importDoc(u, this::openDoc);
        } else if (req == REQ_PICK) {
            Uri u = data.getData();
            OnDoc cb = pending;
            pending = null;
            if (u != null && cb != null) importDoc(u, cb);
        } else if (req == REQ_MERGE) {
            List<Uri> uris = Scan.picked(data);
            AlertDialog pd = Ui.progress(this, "Abrindo…");
            Store.IO.execute(() -> {
                String err = null;
                for (Uri u : uris) {
                    try {
                        mergeChosen.add(Store.importPdf(this, u, Store.work(this)).getAbsolutePath());
                    } catch (Exception e) {
                        err = Ui.error(e);
                    }
                }
                final String fe = err;
                runOnUiThread(() -> {
                    Ui.dismiss(this, pd);
                    if (fe != null) Ui.toast(this, fe);
                    if (!mergeChosen.isEmpty()) openMerge();
                });
            });
        }
    }

    /** Uma sessão do scanner = um PDF novo, salvo na hora, separado dos outros. */
    private void saveScan(Uri pdf, int pages) {
        AlertDialog pd = Ui.progress(this, "Salvando…");
        Store.IO.execute(() -> {
            File out = Store.newDoc(this, "Scan");
            String err = null;
            try {
                Store.copyUri(this, pdf, out);
                Store.changed(this, out);
            } catch (Exception e) {
                out.delete();
                err = Ui.error(e);
            }
            final String fe = err;
            runOnUiThread(() -> {
                Ui.dismiss(this, pd);
                if (fe != null) {
                    Ui.alert(this, "Não consegui salvar", fe);
                    return;
                }
                Ui.toast(this, "Salvo · " + Store.pagesLabel(Math.max(1, pages)));
                openDoc(out);
            });
        });
    }

    /** PDF de outro app vira um documento do Scanabla. */
    private void importDoc(Uri u, OnDoc then) {
        AlertDialog pd = Ui.progress(this, "Abrindo…");
        Store.IO.execute(() -> {
            File f = null;
            String err = null;
            try {
                f = Store.importPdf(this, u, Store.dir(this));
            } catch (Exception e) {
                err = Ui.error(e);
            }
            final File ff = f;
            final String fe = err;
            runOnUiThread(() -> {
                Ui.dismiss(this, pd);
                if (ff == null) Ui.alert(this, "Não consegui abrir", fe);
                else then.run(ff);
            });
        });
    }

    // ---------------------------------------------------------------- lista

    private void renderDocs() {
        docsBox.removeAllViews();
        File[] fs = Store.docs(this);
        docsEmpty.setVisibility(fs.length == 0 ? View.VISIBLE : View.GONE);
        SimpleDateFormat df = new SimpleDateFormat("dd/MM/yy HH:mm", new Locale("pt", "BR"));
        for (int i = 0; i < fs.length; i++) {
            final File f = fs[i];
            if (i > 0) docsBox.addView(Ui.hairline(this));
            LinearLayout r = Ui.item(this, R.drawable.ic_doc, "", v -> openDoc(f));
            r.removeViewAt(1);
            LinearLayout texts = Ui.column(this);
            TextView t = Ui.text(this, Store.base(f), 18, Ui.INK, Typeface.NORMAL);
            t.setSingleLine(true);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            texts.addView(t);
            String tail = Formatter.formatShortFileSize(this, f.length()) + " · " + df.format(new Date(f.lastModified()));
            TextView meta = Ui.text(this, tail, 14, Ui.GREY, Typeface.ITALIC);
            texts.addView(meta);
            r.addView(texts, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
            r.addView(Ui.icon(this, R.drawable.ic_chev, Ui.GREY, 18));
            docsBox.addView(r);
            Thumbs.EXEC.execute(() -> {
                int n = Store.pages(f);
                if (n > 0) runOnUiThread(() -> meta.setText(Store.pagesLabel(n) + " · " + tail));
            });
        }
    }
}
