package br.scanner.pdf;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.List;

/** Envia o PDF (ou o ZIP com PDF + .tex) para um repositório do GitHub. */
public class GitHubActivity extends Activity {

    private File file;
    private TextView repoLabel;
    private EditText folder;
    private CheckBox zip;
    private LinearLayout done;
    private String link;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String p = getIntent().getStringExtra(DocActivity.EXTRA);
        file = p == null ? null : new File(p);
        if (file == null || !file.exists()) {
            finish();
            return;
        }
        SharedPreferences sp = Store.prefs(this);
        LinearLayout col = Ui.scrollScreen(this);
        Ui.header(this, col, "Enviar para GitHub");

        ImageView big = Ui.icon(this, R.drawable.ic_upload, Ui.INK, 64);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 64));
        blp.gravity = Gravity.CENTER_HORIZONTAL;
        blp.topMargin = Ui.dp(this, 18);
        col.addView(big, blp);
        TextView name = Ui.centered(this, Store.base(file), 21, Ui.INK, Typeface.NORMAL);
        name.setPadding(0, Ui.dp(this, 12), 0, 0);
        col.addView(name);

        LinearLayout repo = Ui.button(this, R.drawable.ic_folder, "", false, v -> chooseRepo());
        repoLabel = Ui.text(this, "", 18, Ui.INK, Typeface.NORMAL);
        repoLabel.setSingleLine(true);
        repoLabel.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        repo.addView(repoLabel);
        ((LinearLayout.LayoutParams) repo.getChildAt(0).getLayoutParams()).rightMargin = Ui.dp(this, 10);
        col.addView(repo, Ui.full(this, 54, 24));
        showRepo(sp.getString("gh_repo", ""));

        LinearLayout frow = Ui.row(this);
        frow.addView(Ui.text(this, "Pasta", 18, Ui.INK, Typeface.NORMAL));
        folder = Ui.field(this, sp.getString("gh_folder", "scanabla"), "(raiz)");
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(0, Ui.WRAP, 1f);
        flp.leftMargin = Ui.dp(this, 14);
        frow.addView(folder, flp);
        col.addView(frow, Ui.full(this, 0, 14));

        zip = new CheckBox(this);
        zip.setText("Compactar (ZIP)");
        zip.setTypeface(Ui.font(this, Typeface.NORMAL));
        zip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        zip.setTextColor(Ui.INK);
        zip.setButtonTintList(ColorStateList.valueOf(Ui.INK));
        zip.setChecked(sp.getBoolean("gh_zip", true));
        LinearLayout.LayoutParams zlp = Ui.full(this, 0, 10);
        zlp.gravity = Gravity.CENTER_HORIZONTAL;
        zlp.width = Ui.WRAP;
        col.addView(zip, zlp);

        col.addView(Ui.button(this, R.drawable.ic_upload, "Enviar agora", true, v -> send()), Ui.full(this, 56, 14));

        done = Ui.column(this);
        done.setVisibility(View.GONE);
        LinearLayout ok = Ui.row(this);
        ok.setGravity(Gravity.CENTER);
        ok.addView(Ui.icon(this, R.drawable.ic_check, Ui.INK, 22));
        TextView okt = Ui.text(this, "Enviado", 18, Ui.INK, Typeface.NORMAL);
        okt.setPadding(Ui.dp(this, 8), 0, 0, 0);
        ok.addView(okt);
        done.addView(ok, Ui.full(this, 0, 18));
        done.addView(Ui.button(this, R.drawable.ic_open, "Ver no GitHub", false, v -> {
            if (link != null) startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link)));
        }), Ui.full(this, 50, 10));
        col.addView(done);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (folder == null) return;
        Store.prefs(this).edit()
                .putString("gh_folder", folder.getText().toString().trim())
                .putBoolean("gh_zip", zip.isChecked()).apply();
    }

    private void showRepo(String r) {
        repoLabel.setText(r == null || r.isEmpty() ? "Selecionar repositório" : r);
    }

    private String token() {
        return Store.prefs(this).getString("gh_token", "").trim();
    }

    /** Pede o token na primeira vez (fica salvo só neste celular). */
    private void withToken(Runnable next) {
        if (!token().isEmpty()) {
            next.run();
            return;
        }
        EditText e = Ui.field(this, "", "ghp_… ou github_pat_…");
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout box = Ui.column(this);
        box.setPadding(Ui.dp(this, 22), Ui.dp(this, 6), Ui.dp(this, 22), 0);
        TextView hint = Ui.text(this, "github.com/settings/tokens · permissão Contents (leitura e escrita)", 14, Ui.GREY, Typeface.ITALIC);
        hint.setPadding(0, 0, 0, Ui.dp(this, 10));
        box.addView(hint);
        box.addView(e);
        new AlertDialog.Builder(this).setTitle("Token do GitHub").setView(box)
                .setPositiveButton("Salvar", (d, w) -> {
                    String t = e.getText().toString().trim();
                    if (t.isEmpty()) return;
                    Store.prefs(this).edit().putString("gh_token", t).apply();
                    next.run();
                })
                .setNeutralButton("Criar token", (d, w) -> startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/settings/personal-access-tokens/new"))))
                .setNegativeButton("Cancelar", null).show();
    }

    private void chooseRepo() {
        withToken(() -> {
            AlertDialog pd = Ui.progress(this, "Carregando…");
            final String tk = token();
            Store.IO.execute(() -> {
                List<String> list = null;
                String err = null;
                try {
                    list = GitHub.repos(tk);
                } catch (Exception e) {
                    err = Ui.error(e);
                }
                final List<String> fl = list;
                final String fe = err;
                runOnUiThread(() -> {
                    Ui.dismiss(this, pd);
                    if (fl == null) {
                        Ui.alert(this, "GitHub", fe);
                        return;
                    }
                    String[] items = new String[fl.size() + 1];
                    items[0] = "+ Novo repositório";
                    for (int i = 0; i < fl.size(); i++) items[i + 1] = fl.get(i);
                    if (!Ui.alive(this)) return;
                    new AlertDialog.Builder(this).setTitle("Repositório").setItems(items, (d, w) -> {
                        if (w == 0) newRepo();
                        else setRepo(fl.get(w - 1));
                    }).show();
                });
            });
        });
    }

    private void setRepo(String r) {
        Store.prefs(this).edit().putString("gh_repo", r).apply();
        showRepo(r);
    }

    private void newRepo() {
        EditText e = Ui.field(this, "scanabla", "nome");
        LinearLayout box = Ui.column(this);
        box.setPadding(Ui.dp(this, 22), Ui.dp(this, 6), Ui.dp(this, 22), 0);
        box.addView(e);
        new AlertDialog.Builder(this).setTitle("Novo repositório (privado)").setView(box)
                .setPositiveButton("Criar", (d, w) -> {
                    String n = Store.slug(e.getText().toString());
                    AlertDialog pd = Ui.progress(this, "Criando…");
                    final String tk = token();
                    Store.IO.execute(() -> {
                        String full = null;
                        String err = null;
                        try {
                            full = GitHub.createRepo(tk, n);
                        } catch (Exception ex) {
                            err = Ui.error(ex);
                        }
                        final String ff = full;
                        final String fe = err;
                        runOnUiThread(() -> {
                            Ui.dismiss(this, pd);
                            if (ff != null) setRepo(ff);
                            else Ui.alert(this, "GitHub", fe);
                        });
                    });
                })
                .setNegativeButton("Cancelar", null).show();
    }

    private void send() {
        String repo = Store.prefs(this).getString("gh_repo", "");
        if (repo.isEmpty()) {
            chooseRepo();
            return;
        }
        withToken(() -> {
            final String tk = token();
            final boolean asZip = zip.isChecked();
            String dir = folder.getText().toString().trim().replaceAll("^/+|/+$", "");
            final String folderPath = dir;
            AlertDialog pd = Ui.progress(this, "Enviando…");
            done.setVisibility(View.GONE);
            Store.IO.execute(() -> {
                String url = null;
                String err = null;
                File up = null;
                try {
                    up = asZip ? Store.zip(this, file) : file;
                    String name = Store.slug(Store.base(file)) + (asZip ? ".zip" : ".pdf");
                    String path = folderPath.isEmpty() ? name : folderPath + "/" + name;
                    path = GitHub.freePath(tk, repo, path);
                    url = GitHub.upload(tk, repo, path, up, "Scanabla: " + Store.base(file));
                } catch (Exception e) {
                    err = Ui.error(e);
                } finally {
                    if (asZip && up != null) up.delete();
                }
                final String fu = url;
                final String fe = err;
                runOnUiThread(() -> {
                    Ui.dismiss(this, pd);
                    if (fu == null) {
                        Ui.alert(this, "Não enviado", fe);
                        return;
                    }
                    link = fu;
                    done.setVisibility(View.VISIBLE);
                });
            });
        });
    }
}
