package br.scanner.pdf;

import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.widget.EditText;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Destaque do código LaTeX em preto e branco: comandos em negrito, comentários em cinza. */
final class TexHighlight implements TextWatcher {

    private static final Pattern CMD = Pattern.compile("\\\\(?:[A-Za-z@]+\\*?|.)");
    private static final Pattern COMMENT = Pattern.compile("(?<!\\\\)%.*$", Pattern.MULTILINE);
    private static final Pattern MATH = Pattern.compile("\\$[^$]+\\$");

    private static final class Cmd extends StyleSpan {
        Cmd() {
            super(Typeface.BOLD);
        }
    }

    private static final class Grey extends ForegroundColorSpan {
        Grey(int c) {
            super(c);
        }
    }

    private final EditText edit;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable run = this::apply;

    private TexHighlight(EditText e) {
        edit = e;
    }

    static void attach(EditText e) {
        TexHighlight h = new TexHighlight(e);
        e.addTextChangedListener(h);
        h.apply();
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) { }

    @Override
    public void afterTextChanged(Editable s) {
        handler.removeCallbacks(run);
        handler.postDelayed(run, 300);
    }

    private void apply() {
        Editable s = edit.getText();
        for (Cmd c : s.getSpans(0, s.length(), Cmd.class)) s.removeSpan(c);
        for (Grey g : s.getSpans(0, s.length(), Grey.class)) s.removeSpan(g);
        if (s.length() > 200000) return;
        Matcher m = MATH.matcher(s);
        while (m.find()) s.setSpan(new Grey(0xFF3D3D3D), m.start(), m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        m = CMD.matcher(s);
        while (m.find()) s.setSpan(new Cmd(), m.start(), m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        m = COMMENT.matcher(s);
        while (m.find()) s.setSpan(new Grey(0xFF8A8A8A), m.start(), m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
}
