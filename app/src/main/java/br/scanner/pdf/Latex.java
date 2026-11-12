package br.scanner.pdf;

import org.json.JSONObject;

import android.util.Base64;

import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** LaTeX: montar o documento e compilar para PDF em serviços online grátis. */
final class Latex {

    static final String PREAMBLE = "\\documentclass[12pt]{article}\n"
            + "\\usepackage[utf8]{inputenc}\n"
            + "\\usepackage[T1]{fontenc}\n"
            + "\\usepackage{amsmath,amssymb}\n"
            + "\\usepackage[margin=2.5cm]{geometry}\n\n"
            + "\\begin{document}\n\n";

    /** Erro de compilação (código LaTeX com problema): mostra as linhas do log que importam. */
    static final class CompileError extends Exception {
        CompileError(String log) {
            super(log);
        }
    }

    private Latex() { }

    static String wrapRaw(String body) {
        return PREAMBLE + body.trim() + "\n\n\\end{document}\n";
    }

    /** Texto simples (sem fórmulas) vindo do OCR: escapa caracteres especiais. */
    static String fromPlain(String[] pages) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pages.length; i++) {
            if (i > 0) sb.append("\n\\newpage\n\n");
            for (String para : pages[i].split("\\n\\s*\\n")) {
                String p = para.trim();
                if (p.isEmpty()) continue;
                sb.append(escape(p).replace("\n", "\\\\\n")).append("\n\n");
            }
        }
        return wrapRaw(sb.toString());
    }

    static String escape(String s) {
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '\\': b.append("\\textbackslash{}"); break;
                case '&': case '%': case '$': case '#': case '_': case '{': case '}':
                    b.append('\\').append(ch); break;
                case '~': b.append("\\textasciitilde{}"); break;
                case '^': b.append("\\textasciicircum{}"); break;
                default: b.append(ch);
            }
        }
        return b.toString();
    }

    // ---------------------------------------------------------------- compilar

    static byte[] compile(String tex) throws Exception {
        String net;
        try {
            return ytotech(tex);
        } catch (CompileError e) {
            throw e;
        } catch (Exception e) {
            net = Ui.error(e);
        }
        try {
            return latexOnline(tex);
        } catch (CompileError e) {
            throw e;
        } catch (Exception e) {
            throw new Exception("Serviços de LaTeX fora do ar ou sem internet.\n" + net + "\n" + Ui.error(e));
        }
    }

    /** latex.ytotech.com — POST /builds/sync com o .tex em base64. */
    private static byte[] ytotech(String tex) throws Exception {
        JSONObject res = new JSONObject().put("main", true)
                .put("file", Base64.encodeToString(tex.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
        JSONObject body = new JSONObject().put("compiler", "pdflatex")
                .put("resources", new org.json.JSONArray().put(res));
        HttpURLConnection c = Net.open("https://latex.ytotech.com/builds/sync", "POST", 120000);
        c.setRequestProperty("Content-Type", "application/json");
        Net.Response r = Net.send(c, body.toString().getBytes(StandardCharsets.UTF_8));
        if (r.ok() && Net.isPdf(r.body)) return r.body;
        if (r.code >= 400 && r.code < 500) {
            String logs = r.text();
            try {
                JSONObject j = new JSONObject(logs);
                logs = j.optString("logs", j.optString("error", logs));
            } catch (Exception ignored) {
            }
            throw new CompileError(excerpt(logs));
        }
        throw new Exception("ytotech " + r.code);
    }

    /** latexonline.cc — GET /compile?text=… (reserva, para documentos não muito longos). */
    private static byte[] latexOnline(String tex) throws Exception {
        String url = "https://latexonline.cc/compile?text=" + URLEncoder.encode(tex, "UTF-8");
        if (url.length() > 7800) throw new Exception("documento longo demais para o serviço reserva");
        Net.Response r = Net.finish(Net.open(url, "GET", 120000));
        if (r.ok() && Net.isPdf(r.body)) return r.body;
        if (r.code >= 400 && r.code < 500) throw new CompileError(excerpt(r.text()));
        throw new Exception("latexonline " + r.code);
    }

    /** As linhas "! erro" e "l.N" do log; se não houver, o fim do log. */
    static String excerpt(String log) {
        if (log == null) return "";
        StringBuilder sb = new StringBuilder();
        String[] lines = log.split("\\r?\\n");
        int found = 0;
        for (int i = 0; i < lines.length && found < 4; i++) {
            if (lines[i].startsWith("!")) {
                sb.append(lines[i]).append('\n');
                for (int k = i + 1; k < Math.min(lines.length, i + 6); k++) {
                    if (lines[k].startsWith("l.")) {
                        sb.append(lines[k]).append('\n');
                        break;
                    }
                }
                found++;
            }
        }
        if (sb.length() > 0) return sb.toString().trim();
        String t = log.trim();
        return t.length() > 700 ? "…" + t.substring(t.length() - 700) : t;
    }
}
