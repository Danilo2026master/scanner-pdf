package br.scanner.pdf;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Lê a escrita à mão das fotos e devolve um documento LaTeX (Gemini, com a chave grátis do usuário). */
final class Gemini {

    private static final String SYSTEM =
            "You transcribe photos of handwritten (or printed) pages into LaTeX. "
            + "Output ONE complete document that compiles with pdflatex, and nothing else: no markdown fences, no notes. "
            + "Use exactly this preamble: \\documentclass[12pt]{article} \\usepackage[utf8]{inputenc} "
            + "\\usepackage[T1]{fontenc} \\usepackage{amsmath,amssymb} \\usepackage[margin=2.5cm]{geometry}. "
            + "Keep the original language (usually Portuguese) and the exact wording: do not translate, summarize, "
            + "correct or add content. Write all mathematics in proper LaTeX (inline $...$, displayed \\[...\\] or align*). "
            + "Titles as \\section*{...}, lists as itemize/enumerate, tables as tabular. "
            + "For drawings or diagrams write \\fbox{Figura} with a few words of what it shows. "
            + "Write [?] for illegible words. Keep pages in order, separated by \\newpage.";

    private static final String[] MODELS = {"gemini-flash-latest", "gemini-flash-lite-latest"};

    private Gemini() { }

    static String latex(String key, List<byte[]> jpegs) throws Exception {
        if (key == null || key.trim().isEmpty()) throw new Exception("Falta a chave do Gemini (Configurações).");
        JSONArray parts = new JSONArray();
        for (byte[] j : jpegs) {
            parts.put(new JSONObject().put("inlineData", new JSONObject()
                    .put("mimeType", "image/jpeg")
                    .put("data", Base64.encodeToString(j, Base64.NO_WRAP))));
        }
        parts.put(new JSONObject().put("text", "Transcreva " + (jpegs.size() == 1 ? "esta página" : "estas "
                + jpegs.size() + " páginas") + " para um documento LaTeX completo."));
        JSONObject body = new JSONObject();
        body.put("systemInstruction", new JSONObject().put("parts", new JSONArray().put(new JSONObject().put("text", SYSTEM))));
        body.put("contents", new JSONArray().put(new JSONObject().put("role", "user").put("parts", parts)));
        body.put("generationConfig", new JSONObject().put("temperature", 0.1));
        byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);

        Exception last = null;
        for (String m : MODELS) {
            HttpURLConnection c = Net.open("https://generativelanguage.googleapis.com/v1beta/models/"
                    + URLEncoder.encode(m, "UTF-8") + ":generateContent", "POST", 180000);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            c.setRequestProperty("x-goog-api-key", key.trim());
            Net.Response r = Net.send(c, payload);
            if (r.ok()) return tidy(textOf(new JSONObject(r.text())));
            String msg = r.text();
            try {
                msg = new JSONObject(msg).getJSONObject("error").optString("message", msg);
            } catch (Exception ignored) {
            }
            last = new Exception("Gemini (" + r.code + "): " + msg);
            if (r.code == 400 || r.code == 401 || r.code == 403) break; // chave errada: não adianta tentar outro modelo
        }
        throw last;
    }

    private static String textOf(JSONObject r) throws Exception {
        JSONArray cands = r.optJSONArray("candidates");
        if (cands == null || cands.length() == 0) throw new Exception("O Gemini não devolveu texto.");
        JSONObject content = cands.getJSONObject(0).optJSONObject("content");
        JSONArray ps = content == null ? null : content.optJSONArray("parts");
        StringBuilder sb = new StringBuilder();
        if (ps != null) {
            for (int i = 0; i < ps.length(); i++) {
                JSONObject p = ps.getJSONObject(i);
                if (!p.optBoolean("thought", false)) sb.append(p.optString("text", ""));
            }
        }
        if (sb.length() == 0) throw new Exception("O Gemini não devolveu texto.");
        return sb.toString();
    }

    /** Remove cercas ```latex e garante um documento completo. */
    static String tidy(String s) {
        String t = s.trim();
        if (t.startsWith("```")) {
            int nl = t.indexOf('\n');
            t = nl > 0 ? t.substring(nl + 1) : "";
            int end = t.lastIndexOf("```");
            if (end >= 0) t = t.substring(0, end);
            t = t.trim();
        }
        if (!t.contains("\\begin{document}")) t = Latex.wrapRaw(t);
        return t + "\n";
    }
}
