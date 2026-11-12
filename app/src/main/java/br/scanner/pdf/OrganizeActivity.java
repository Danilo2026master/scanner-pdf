package br.scanner.pdf;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/** Organizar um PDF (ou unir vários): reordenar, girar, excluir e adicionar páginas. */
public class OrganizeActivity extends Activity {

    static final String EXTRA_PATHS = "paths";
    static final String EXTRA_MERGE = "merge";
    private static final int REQ_SCAN = 1;
    private static final int REQ_PDF = 2;

    private final List<File> sources = Collections.synchronizedList(new ArrayList<>());
    private final ArrayList<Thumbs> thumbs = new ArrayList<>(); // só na thread de desenho
    private final ArrayList<Pdf.Page> pages = new ArrayList<>();
    private final HashSet<Pdf.Page> selected = new HashSet<>();
    private final HashSet<String> loading = new HashSet<>();
    private LruCache<String, Bitmap> cache;

    private boolean merge;
    private boolean dirty;
    private volatile boolean destroyed;

    private GridView grid;
    private Adapter adapter;
    private TextView info;
    private TextView selectAll;
    private int cellW;
    private int cellH;
    private int thumbPx;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String[] paths = getIntent().getStringArrayExtra(EXTRA_PATHS);
        merge = getIntent().getBooleanExtra(EXTRA_MERGE, false);
        if (paths == null || paths.length == 0) {
            finish();
            return;
        }
        int mem = (int) Math.min(Integer.MAX_VALUE, Runtime.getRuntime().maxMemory() / 6);
        cache = new LruCache<String, Bitmap>(mem) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount();
            }
        };

        LinearLayout root = Ui.column(this);
        root.setFitsSystemWindows(true);
        root.setBackgroundColor(Ui.PAPER);
        setContentView(root);

        LinearLayout top = Ui.column(this);
        top.setPadding(Ui.dp(this, 20), Ui.dp(this, 6), Ui.dp(this, 16), 0);
        root.addView(top);
        LinearLayout head = Ui.header(this, top, merge ? "Unir PDFs" : "Organizar");
        LinearLayout save = Ui.button(this, R.drawable.ic_check, "Salvar", true, v -> save());
        save.setMinimumHeight(Ui.dp(this, 40));
        head.addView(save, new LinearLayout.LayoutParams(Ui.WRAP, Ui.dp(this, 42)));

        LinearLayout infoRow = Ui.row(this);
        info = Ui.text(this, "", 16, Ui.GREY, Typeface.ITALIC);
        infoRow.addView(info, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        selectAll = Ui.text(this, "Todas", 16, Ui.INK, Typeface.NORMAL);
        selectAll.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 8));
        selectAll.setOnClickListener(v -> toggleAll());
        infoRow.addView(selectAll);
        top.addView(infoRow);

        int screen = getResources().getDisplayMetrics().widthPixels;
        int pad = Ui.dp(this, 14);
        int gap = Ui.dp(this, 10);
        cellW = (screen - 2 * pad - 2 * gap) / 3;
        cellH = Math.round(cellW * 1.42f);
        thumbPx = Math.min(360, cellW);

        grid = new GridView(this);
        grid.setNumColumns(3);
        grid.setHorizontalSpacing(gap);
        grid.setVerticalSpacing(gap);
        grid.setPadding(pad, Ui.dp(this, 6), pad, pad);
        grid.setClipToPadding(false);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setSelector(new android.graphics.drawable.ColorDrawable(0));
        adapter = new Adapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((p, v, pos, id) -> {
            Pdf.Page pg = pages.get(pos);
            if (!selected.remove(pg)) selected.add(pg);
            changedSelection();
        });
        root.addView(grid, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));

        root.addView(Ui.hairline(this));
        LinearLayout bar = Ui.row(this);
        bar.setPadding(Ui.dp(this, 6), Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 4));
        bar.addView(tool(R.drawable.ic_left, "Mover", v -> move(-1)), Ui.weight(this, 0, 0));
        bar.addView(tool(R.drawable.ic_right, "Mover", v -> move(1)), Ui.weight(this, 0, 0));
        bar.addView(tool(R.drawable.ic_rotate, "Girar", v -> rotate()), Ui.weight(this, 0, 0));
        bar.addView(tool(R.drawable.ic_trash, "Excluir", v -> deleteSelected()), Ui.weight(this, 0, 0));
        bar.addView(tool(R.drawable.ic_plus, "Adicionar", v -> add()), Ui.weight(this, 0, 0));
        root.addView(bar);

        AlertDialog pd = Ui.progress(this, "Abrindo…");
        Thumbs.EXEC.execute(() -> {
            ArrayList<Pdf.Page> found = new ArrayList<>();
            String err = null;
            for (String p : paths) {
                try {
                    int src = openSource(new File(p));
                    int n = thumbs.get(src).count();
                    for (int i = 0; i < n; i++) found.add(new Pdf.Page(src, i));
                } catch (Exception e) {
                    err = new File(p).getName() + ": " + Ui.error(e);
                }
            }
            final String fe = err;
            runOnUiThread(() -> {
                Ui.dismiss(this, pd);
                if (fe != null) Ui.toast(this, fe);
                if (found.isEmpty()) {
                    finish();
                    return;
                }
                pages.addAll(found);
                dirty = merge;
                changedSelection();
            });
        });
    }

    /** Abre um PDF de origem (na thread de desenho) e devolve o índice dele. */
    private int openSource(File f) throws Exception {
        Thumbs t = new Thumbs(f);
        thumbs.add(t);
        sources.add(f);
        return thumbs.size() - 1;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        destroyed = true;
        Thumbs.EXEC.execute(() -> {
            for (Thumbs t : thumbs) t.close();
            thumbs.clear();
        });
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (dirty && !pages.isEmpty()) {
            new AlertDialog.Builder(this).setMessage("Sair sem salvar?")
                    .setPositiveButton("Sair", (d, w) -> finish())
                    .setNegativeButton("Continuar", null).show();
        } else {
            super.onBackPressed();
        }
    }

    // ---------------------------------------------------------------- ferramentas

    private View tool(int icon, String label, View.OnClickListener l) {
        LinearLayout t = Ui.column(this);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 6));
        t.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x1F000000), null, new android.graphics.drawable.ColorDrawable(Ui.INK)));
        t.addView(Ui.icon(this, icon, Ui.INK, 24));
        TextView tv = Ui.centered(this, label, 13.5f, Ui.INK, Typeface.NORMAL);
        tv.setPadding(0, Ui.dp(this, 3), 0, 0);
        t.addView(tv);
        t.setOnClickListener(l);
        return t;
    }

    private boolean needSelection() {
        if (selected.isEmpty()) {
            Ui.toast(this, "Toque nas páginas.");
            return true;
        }
        return false;
    }

    private void move(int dir) {
        if (needSelection()) return;
        int n = pages.size();
        if (dir < 0) {
            for (int i = 1; i < n; i++) {
                if (selected.contains(pages.get(i)) && !selected.contains(pages.get(i - 1))) Collections.swap(pages, i, i - 1);
            }
        } else {
            for (int i = n - 2; i >= 0; i--) {
                if (selected.contains(pages.get(i)) && !selected.contains(pages.get(i + 1))) Collections.swap(pages, i, i + 1);
            }
        }
        dirty = true;
        changedSelection();
        int first = -1;
        for (int i = 0; i < pages.size(); i++) if (selected.contains(pages.get(i))) { first = i; break; }
        if (first >= 0) grid.smoothScrollToPosition(first);
    }

    private void rotate() {
        if (needSelection()) return;
        for (Pdf.Page p : selected) p.rot = (p.rot + 90) % 360;
        dirty = true;
        adapter.notifyDataSetChanged();
    }

    private void deleteSelected() {
        if (needSelection()) return;
        if (selected.size() >= pages.size()) {
            Ui.toast(this, "Deixe ao menos 1 página.");
            return;
        }
        int n = selected.size();
        Ui.confirm(this, "Excluir " + (n == 1 ? "1 página?" : n + " páginas?"), "Excluir", () -> {
            pages.removeAll(selected);
            selected.clear();
            dirty = true;
            changedSelection();
        });
    }

    private void toggleAll() {
        if (selected.size() == pages.size()) selected.clear();
        else selected.addAll(pages);
        changedSelection();
    }

    private void changedSelection() {
        int n = pages.size();
        String s = Store.pagesLabel(n);
        if (!selected.isEmpty()) s += " · " + selected.size() + (selected.size() == 1 ? " selecionada" : " selecionadas");
        info.setText(s);
        selectAll.setText(selected.size() == n && n > 0 ? "Nenhuma" : "Todas");
        adapter.notifyDataSetChanged();
    }

    // ---------------------------------------------------------------- adicionar páginas

    private void add() {
        File[] docs = Store.docs(this);
        String[] opts = docs.length > 0
                ? new String[]{"Escanear", "PDF do celular", "Documento salvo"}
                : new String[]{"Escanear", "PDF do celular"};
        new AlertDialog.Builder(this).setTitle("Adicionar").setItems(opts, (d, w) -> {
            if (w == 0) Scan.start(this, REQ_SCAN, false);
            else if (w == 1) startActivityForResult(Scan.pickPdf(true), REQ_PDF);
            else chooseSaved(docs);
        }).show();
    }

    private void chooseSaved(File[] docs) {
        String[] names = new String[docs.length];
        for (int i = 0; i < docs.length; i++) names[i] = Store.base(docs[i]);
        new AlertDialog.Builder(this).setTitle("Documento").setItems(names, (d, w) -> addFiles(
                Collections.singletonList(docs[w]))).show();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null) return;
        if (req == REQ_SCAN) {
            Uri u = Scan.pdf(data);
            if (u == null) return;
            Store.IO.execute(() -> {
                File f = new File(Store.work(this), "scan-" + System.currentTimeMillis() + ".pdf");
                try {
                    Store.copyUri(this, u, f);
                    runOnUiThread(() -> addFiles(Collections.singletonList(f)));
                } catch (Exception e) {
                    runOnUiThread(() -> Ui.toast(this, Ui.error(e)));
                }
            });
        } else if (req == REQ_PDF) {
            List<Uri> uris = Scan.picked(data);
            Store.IO.execute(() -> {
                ArrayList<File> fs = new ArrayList<>();
                for (Uri u : uris) {
                    try {
                        fs.add(Store.importPdf(this, u, Store.work(this)));
                    } catch (Exception e) {
                        runOnUiThread(() -> Ui.toast(this, Ui.error(e)));
                    }
                }
                runOnUiThread(() -> addFiles(fs));
            });
        }
    }

    private void addFiles(List<File> files) {
        if (files.isEmpty()) return;
        Thumbs.EXEC.execute(() -> {
            ArrayList<Pdf.Page> found = new ArrayList<>();
            for (File f : files) {
                try {
                    int src = openSource(f);
                    int n = thumbs.get(src).count();
                    for (int i = 0; i < n; i++) found.add(new Pdf.Page(src, i));
                } catch (Exception e) {
                    runOnUiThread(() -> Ui.toast(this, Ui.error(e)));
                }
            }
            runOnUiThread(() -> {
                if (found.isEmpty()) return;
                pages.addAll(found);
                dirty = true;
                changedSelection();
                grid.smoothScrollToPosition(pages.size() - 1);
                Ui.toast(this, "+" + Store.pagesLabel(found.size()));
            });
        });
    }

    // ---------------------------------------------------------------- salvar

    private void save() {
        if (pages.isEmpty()) return;
        final ArrayList<Pdf.Page> order = new ArrayList<>(pages);
        final ArrayList<File> srcs;
        synchronized (sources) {
            srcs = new ArrayList<>(sources);
        }
        final File target = merge ? Store.newDoc(this, "União") : srcs.get(0);
        AlertDialog pd = Ui.progress(this, "Salvando…");
        Store.IO.execute(() -> {
            String err = null;
            try {
                Pdf.build(srcs, order, target);
                Store.changed(this, target);
            } catch (Throwable e) {
                err = Ui.error(e);
            }
            final String fe = err;
            runOnUiThread(() -> {
                Ui.dismiss(this, pd);
                if (fe != null) {
                    Ui.alert(this, "Não consegui salvar", fe);
                    return;
                }
                dirty = false;
                Ui.toast(this, "Salvo · " + Store.pagesLabel(order.size()));
                if (merge) {
                    startActivity(new Intent(this, DocActivity.class).putExtra(DocActivity.EXTRA, target.getAbsolutePath()));
                }
                setResult(RESULT_OK);
                finish();
            });
        });
    }

    // ---------------------------------------------------------------- grade de miniaturas

    private static final class Cell {
        FrameLayout frame;
        ImageView img;
        TextView num;
    }

    private final class Adapter extends BaseAdapter {
        @Override
        public int getCount() {
            return pages.size();
        }

        @Override
        public Object getItem(int position) {
            return pages.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int pos, View v, ViewGroup parent) {
            Cell cell;
            if (v == null) {
                cell = new Cell();
                cell.frame = new FrameLayout(OrganizeActivity.this);
                cell.frame.setLayoutParams(new AbsListView.LayoutParams(Ui.MATCH, cellH));
                int pad = Ui.dp(OrganizeActivity.this, 6);
                cell.frame.setPadding(pad, pad, pad, Ui.dp(OrganizeActivity.this, 26));
                cell.img = new ImageView(OrganizeActivity.this);
                cell.img.setScaleType(ImageView.ScaleType.FIT_CENTER);
                cell.frame.addView(cell.img, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
                cell.num = Ui.centered(OrganizeActivity.this, "", 14, Ui.INK, Typeface.NORMAL);
                FrameLayout.LayoutParams nlp = new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP,
                        Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
                nlp.bottomMargin = -Ui.dp(OrganizeActivity.this, 22);
                cell.num.setMinWidth(Ui.dp(OrganizeActivity.this, 26));
                cell.num.setPadding(Ui.dp(OrganizeActivity.this, 6), 0, Ui.dp(OrganizeActivity.this, 6), 0);
                cell.frame.addView(cell.num, nlp);
                cell.frame.setClipToPadding(false);
                cell.frame.setTag(cell);
                v = cell.frame;
            } else {
                cell = (Cell) v.getTag();
            }
            Pdf.Page p = pages.get(pos);
            boolean sel = selected.contains(p);
            GradientDrawable bg = Ui.box(OrganizeActivity.this, sel ? 0xFFEDEDED : Ui.PAPER,
                    sel ? Ui.INK : Ui.LINE, sel ? 2.5f : 1, 6);
            cell.frame.setBackground(bg);
            cell.num.setText(String.valueOf(pos + 1));
            cell.num.setTextColor(sel ? Ui.PAPER : Ui.INK);
            cell.num.setBackground(sel ? Ui.box(OrganizeActivity.this, Ui.INK, Ui.INK, 0, 10) : null);

            String key = p.src + ":" + p.index;
            cell.img.setTag(key);
            Bitmap b = cache.get(key);
            cell.img.setImageBitmap(b);
            cell.img.setRotation(p.rot);
            float k = 1f;
            if (p.rot % 180 != 0) {
                float w = cellW - Ui.dp(OrganizeActivity.this, 12);
                float h = cellH - Ui.dp(OrganizeActivity.this, 32);
                k = Math.min(w / h, h / w);
            }
            cell.img.setScaleX(k);
            cell.img.setScaleY(k);
            if (b == null) request(key, p.src, p.index);
            return v;
        }
    }

    private void request(String key, int src, int index) {
        if (!loading.add(key)) return;
        Thumbs.EXEC.execute(() -> {
            Bitmap b = null;
            try {
                if (!destroyed && src < thumbs.size()) b = thumbs.get(src).render(index, thumbPx);
            } catch (Exception ignored) {
            }
            final Bitmap fb = b;
            runOnUiThread(() -> {
                loading.remove(key);
                if (fb == null || destroyed) return;
                cache.put(key, fb);
                for (int i = 0; i < grid.getChildCount(); i++) {
                    Object t = grid.getChildAt(i).getTag();
                    if (t instanceof Cell && key.equals(((Cell) t).img.getTag())) ((Cell) t).img.setImageBitmap(fb);
                }
            });
        });
    }
}
