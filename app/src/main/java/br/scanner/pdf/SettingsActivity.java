package br.scanner.pdf;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/** Chaves (Gemini, GitHub) e cópia em Downloads. */
public class SettingsActivity extends Activity {

    private EditText gemini;
    private EditText token;
    private Switch downloads;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences sp = Store.prefs(this);
        LinearLayout col = Ui.scrollScreen(this);
        Ui.header(this, col, "Configurações");

        gemini = secret(col, "Chave do Gemini (LaTeX)", sp.getString("gemini_key", ""), "AIza…",
                "aistudio.google.com/apikey", "https://aistudio.google.com/apikey");
        token = secret(col, "Token do GitHub", sp.getString("gh_token", ""), "ghp_… ou github_pat_…",
                "Criar token (permissão Contents)", "https://github.com/settings/personal-access-tokens/new");

        downloads = new Switch(this);
        downloads.setText("Cópia em Download/Scanabla");
        downloads.setTypeface(Ui.font(this, Typeface.NORMAL));
        downloads.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        downloads.setTextColor(Ui.INK);
        downloads.setThumbTintList(ColorStateList.valueOf(Ui.INK));
        downloads.setTrackTintList(ColorStateList.valueOf(0xFFBDBDBD));
        downloads.setChecked(sp.getBoolean("downloads", false));
        if (Build.VERSION.SDK_INT < 29) {
            downloads.setEnabled(false);
            downloads.setText("Cópia em Downloads (Android 10+)");
        }
        col.addView(downloads, Ui.full(this, 0, 28));

        col.addView(Ui.button(this, R.drawable.ic_check, "Salvar", true, v -> {
            save();
            Ui.toast(this, "Salvo.");
            finish();
        }), Ui.full(this, 56, 28));

        TextView about = Ui.centered(this, "Scanabla 2.0 · fonte Tinos (medidas da Times New Roman)", 13, Ui.GREY, Typeface.ITALIC);
        about.setPadding(0, Ui.dp(this, 28), 0, 0);
        col.addView(about);
    }

    private EditText secret(LinearLayout col, String label, String value, String hint, String linkText, String url) {
        TextView l = Ui.text(this, label, 18, Ui.INK, Typeface.NORMAL);
        l.setPadding(0, Ui.dp(this, 22), 0, Ui.dp(this, 6));
        col.addView(l);
        EditText e = Ui.field(this, value, hint);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        col.addView(e, Ui.full(this, 0, 0));
        TextView link = Ui.text(this, linkText, 14.5f, Ui.GREY, Typeface.ITALIC);
        link.setPaintFlags(link.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        link.setPadding(0, Ui.dp(this, 6), 0, 0);
        link.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))));
        col.addView(link);
        return e;
    }

    @Override
    protected void onPause() {
        super.onPause();
        save();
    }

    private void save() {
        Store.prefs(this).edit()
                .putString("gemini_key", gemini.getText().toString().trim())
                .putString("gh_token", token.getText().toString().trim())
                .putBoolean("downloads", downloads.isChecked())
                .apply();
    }
}
