package br.scanner.pdf;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Visual do Scanabla: preto e branco, fonte serifada (Tinos = medidas da Times New Roman), pouco texto. */
final class Ui {

    static final int INK = 0xFF000000;
    static final int PAPER = 0xFFFFFFFF;
    static final int GREY = 0xFF707070;
    static final int LINE = 0xFFD9D9D9;
    static final int SOFT = 0xFFF4F4F4;

    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private static Typeface family;

    private Ui() { }

    static Typeface font(Context c, int style) {
        if (family == null) {
            try {
                family = c.getResources().getFont(R.font.tinos);
            } catch (Exception e) {
                family = Typeface.SERIF;
            }
        }
        return Typeface.create(family, style);
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static boolean alive(Activity a) {
        return !a.isFinishing() && !a.isDestroyed();
    }

    // ---------------------------------------------------------------- blocos

    /** Tela com rolagem e margens; devolve a coluna de conteúdo. */
    static LinearLayout scrollScreen(Activity a) {
        ScrollView sv = new ScrollView(a);
        sv.setFitsSystemWindows(true);
        sv.setBackgroundColor(PAPER);
        sv.setFillViewport(true);
        LinearLayout col = column(a);
        col.setPadding(dp(a, 20), dp(a, 6), dp(a, 20), dp(a, 32));
        sv.addView(col, new ScrollView.LayoutParams(MATCH, WRAP));
        a.setContentView(sv);
        return col;
    }

    static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static TextView text(Context c, String s, float sp, int color, int style) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(font(c, style));
        return t;
    }

    static TextView centered(Context c, String s, float sp, int color, int style) {
        TextView t = text(c, s, sp, color, style);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        return t;
    }

    static ImageView icon(Context c, int res, int color, int sizeDp) {
        ImageView i = new ImageView(c);
        i.setImageResource(res);
        i.setImageTintList(ColorStateList.valueOf(color));
        i.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return i;
    }

    static LinearLayout.LayoutParams full(Context c, int heightDp, int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, heightDp > 0 ? dp(c, heightDp) : WRAP);
        lp.topMargin = dp(c, topDp);
        return lp;
    }

    static LinearLayout.LayoutParams weight(Context c, int heightDp, int sideDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, heightDp > 0 ? dp(c, heightDp) : WRAP, 1f);
        lp.leftMargin = dp(c, sideDp);
        lp.rightMargin = dp(c, sideDp);
        return lp;
    }

    static GradientDrawable box(Context c, int fill, int stroke, float strokeDp, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(c, radiusDp));
        if (strokeDp > 0) g.setStroke(Math.max(1, dp(c, strokeDp)), stroke);
        return g;
    }

    /** Botão grande: preto cheio (principal) ou contorno preto. */
    static LinearLayout button(Context c, int iconRes, String label, boolean primary, View.OnClickListener l) {
        LinearLayout b = row(c);
        b.setGravity(Gravity.CENTER);
        int fg = primary ? PAPER : INK;
        GradientDrawable bg = box(c, primary ? INK : PAPER, INK, 1, 10);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(primary ? 0x40FFFFFF : 0x1F000000), bg, null));
        b.setPadding(dp(c, 12), 0, dp(c, 12), 0);
        b.setMinimumHeight(dp(c, 48));
        if (iconRes != 0) {
            ImageView i = icon(c, iconRes, fg, 22);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(c, 22), dp(c, 22));
            if (label != null && !label.isEmpty()) lp.rightMargin = dp(c, 10);
            b.addView(i, lp);
        }
        if (label != null && !label.isEmpty()) {
            TextView t = text(c, label, 18, fg, Typeface.NORMAL);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            b.addView(t);
        }
        b.setOnClickListener(l);
        return b;
    }

    /** Linha de lista: ícone + rótulo. */
    static LinearLayout item(Context c, int iconRes, String label, View.OnClickListener l) {
        LinearLayout r = row(c);
        r.setPadding(dp(c, 4), dp(c, 11), dp(c, 4), dp(c, 11));
        r.setBackground(new RippleDrawable(ColorStateList.valueOf(0x1F000000), null, new ColorDrawable(INK)));
        ImageView i = icon(c, iconRes, INK, 24);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(c, 24), dp(c, 24));
        lp.rightMargin = dp(c, 18);
        r.addView(i, lp);
        r.addView(text(c, label, 19, INK, Typeface.NORMAL));
        r.setOnClickListener(l);
        return r;
    }

    /** Bloco de ação com ícone em cima e rótulo embaixo (grade da tela do documento). */
    static LinearLayout tile(Context c, int iconRes, String label, View.OnClickListener l) {
        LinearLayout t = column(c);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 4), dp(c, 14), dp(c, 4), dp(c, 12));
        GradientDrawable bg = box(c, PAPER, LINE, 1, 10);
        t.setBackground(new RippleDrawable(ColorStateList.valueOf(0x1F000000), bg, null));
        t.addView(icon(c, iconRes, INK, 26));
        TextView tv = centered(c, label, 15.5f, INK, Typeface.NORMAL);
        tv.setPadding(0, dp(c, 6), 0, 0);
        tv.setSingleLine(true);
        t.addView(tv);
        t.setOnClickListener(l);
        return t;
    }

    /** Divisória com título no meio: ──── Recursos ────. */
    static LinearLayout section(Context c, String label) {
        LinearLayout r = row(c);
        r.setPadding(0, dp(c, 26), 0, dp(c, 8));
        View a = new View(c);
        a.setBackgroundColor(LINE);
        r.addView(a, new LinearLayout.LayoutParams(0, Math.max(1, dp(c, 1)), 1f));
        TextView t = text(c, label, 17, INK, Typeface.NORMAL);
        t.setPadding(dp(c, 14), 0, dp(c, 14), 0);
        r.addView(t);
        View b = new View(c);
        b.setBackgroundColor(LINE);
        r.addView(b, new LinearLayout.LayoutParams(0, Math.max(1, dp(c, 1)), 1f));
        return r;
    }

    static View hairline(Context c) {
        View v = new View(c);
        v.setBackgroundColor(LINE);
        v.setLayoutParams(new LinearLayout.LayoutParams(MATCH, Math.max(1, dp(c, 1))));
        return v;
    }

    /** Barra de topo: ‹ voltar + título. Devolve a linha (para acrescentar botões à direita). */
    static LinearLayout header(Activity a, LinearLayout parent, String title) {
        LinearLayout r = row(a);
        ImageView back = icon(a, R.drawable.ic_back, INK, 24);
        int p = dp(a, 10);
        back.setPadding(p, p, p, p);
        back.setBackground(new RippleDrawable(ColorStateList.valueOf(0x1F000000), null, null));
        back.setContentDescription("Voltar");
        back.setOnClickListener(v -> a.onBackPressed());
        r.addView(back, new LinearLayout.LayoutParams(dp(a, 44), dp(a, 44)));
        TextView t = text(a, title, 21, INK, Typeface.NORMAL);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(dp(a, 4), 0, dp(a, 8), 0);
        r.addView(t, new LinearLayout.LayoutParams(0, WRAP, 1f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, dp(a, 56));
        lp.leftMargin = -dp(a, 10);
        parent.addView(r, lp);
        return r;
    }

    static EditText field(Context c, String value, String hint) {
        EditText e = new EditText(c);
        e.setText(value);
        e.setHint(hint);
        e.setTextColor(INK);
        e.setHintTextColor(0xFF9A9A9A);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        e.setTypeface(font(c, Typeface.NORMAL));
        e.setBackground(box(c, PAPER, INK, 1, 8));
        e.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
        e.setSingleLine(true);
        return e;
    }

    // ---------------------------------------------------------------- avisos

    static void toast(Context c, String s) {
        Toast.makeText(c, s, Toast.LENGTH_LONG).show();
    }

    static void alert(Activity a, String title, String msg) {
        if (!alive(a)) return;
        new AlertDialog.Builder(a).setTitle(title).setMessage(msg).setPositiveButton("OK", null).show();
    }

    static void confirm(Activity a, String msg, String ok, Runnable yes) {
        if (!alive(a)) return;
        new AlertDialog.Builder(a).setMessage(msg)
                .setPositiveButton(ok, (d, w) -> yes.run())
                .setNegativeButton("Cancelar", null).show();
    }

    static AlertDialog progress(Activity a, String msg) {
        LinearLayout r = row(a);
        r.setPadding(dp(a, 24), dp(a, 22), dp(a, 24), dp(a, 22));
        ProgressBar pb = new ProgressBar(a);
        pb.setIndeterminateTintList(ColorStateList.valueOf(INK));
        r.addView(pb, new LinearLayout.LayoutParams(dp(a, 30), dp(a, 30)));
        TextView t = text(a, msg, 18, INK, Typeface.NORMAL);
        t.setPadding(dp(a, 18), 0, 0, 0);
        t.setTag("msg");
        r.addView(t);
        AlertDialog d = new AlertDialog.Builder(a).setView(r).setCancelable(false).create();
        if (alive(a)) d.show();
        return d;
    }

    static void progressText(AlertDialog d, String msg) {
        View v = d.getWindow() == null ? null : d.getWindow().getDecorView().findViewWithTag("msg");
        if (v instanceof TextView) ((TextView) v).setText(msg);
    }

    static void dismiss(Activity a, AlertDialog d) {
        try {
            if (d != null && d.isShowing() && !a.isDestroyed()) d.dismiss();
        } catch (Exception ignored) {
        }
    }

    static String error(Throwable e) {
        String m = e.getMessage();
        return m == null || m.isEmpty() ? e.getClass().getSimpleName() : m;
    }
}
