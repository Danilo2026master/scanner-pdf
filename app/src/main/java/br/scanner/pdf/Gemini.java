package br.scanner.pdf;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Lê fotos de escrita à mão e devolve LaTeX (Gemini, com a chave grátis do usuário). */
final class Gemini {

    interface Status {
        void say(String s);
    }

    /** Desenho recortado da foto: página (1…) e caixa [ymin, xmin, ymax, xmax] em 0–1000. */
    static final class Figure {
        String name;
        int page;
        int[] box;
    }

    static final class Result {
        String latex;
        final List<Figure> figures = new ArrayList<>();
    }

    private static final String ORGANIZED =
            "You turn photos of handwritten study notes (usually in Portuguese) into a clean, well organized LaTeX "
            + "document that compiles with pdflatex.\n"
            + "CONTENT: keep every piece of information and the author's language and terminology. Never invent facts, "
            + "never translate, never drop content. You may fix obvious spelling slips and expand obvious abbreviations.\n"
            + "ORGANIZATION: start the body with \\title{...} (a short title inferred from the content), \\author{}, "
            + "\\date{} and \\maketitle. Mirror the topics with \\section and \\subsection. Turn loose lines that are "
            + "lists into itemize or enumerate. Put definitions, laws, theorems and key concepts in "
            + "\\begin{caixa}{Definição: ...} ... \\end{caixa} (adapt the label: Definição, Lei, Teorema, Propriedade, "
            + "Observação, Resumo). Put the most important formulas in \\begin{destaque} ... \\end{destaque}. "
            + "Put worked examples and exercises in \\begin{exemplo} ... \\end{exemplo}. Keep the logical order of the "
            + "notes, following arrows and numbering.\n";

    private static final String FAITHFUL =
            "You transcribe photos of handwritten (or printed) pages into LaTeX that compiles with pdflatex, as "
            + "faithfully as possible.\n"
            + "Keep the original wording, order and language exactly (usually Portuguese): do not summarize, reorder, "
            + "translate or add content. Keep headings, lists and emphasis as written (underlined or boxed words become "
            + "\\textbf). Separate the pages with \\newpage.\n";

    private static final String COMMON =
            "MATH: write all mathematics in proper LaTeX: inline $...$, displayed \\[...\\] or align*; use \\frac, "
            + "\\sqrt, \\vec, \\int, \\sum, \\lim, pmatrix/bmatrix, \\cdot, \\times, \\Rightarrow, \\to. Chemistry with "
            + "\\ce{...}. Units upright, e.g. 5\\,\\mathrm{m/s}. Never leave Unicode math symbols as raw characters "
            + "(→ ≥ ± ° Δ ∞ ...): use LaTeX commands.\n"
            + "TABLES: tabular with \\toprule, \\midrule, \\bottomrule.\n"
            + "DRAWINGS: diagrams, graphs, sketches, schemes or anything that cannot be written as text or math must NOT "
            + "be redrawn. For each one add an item to \"figures\" with name fig1.jpg, fig2.jpg ... (in order), the "
            + "1-based page number of the photo and box = [ymin, xmin, ymax, xmax] normalized to 0-1000, covering the "
            + "whole drawing with its labels. Insert it where it belongs with "
            + "\\begin{figure}[H]\\centering\\includegraphics[width=0.7\\linewidth]{figuras/fig1.jpg}"
            + "\\caption{short description}\\end{figure}.\n"
            + "ILLEGIBLE words: \\ilegivel{}.\n"
            + "PREAMBLE: use exactly this preamble, unchanged, and do not load other packages:\n"
            + Latex.HEAD
            + "OUTPUT: JSON with \"figures\" (may be empty) and \"latex\", the complete document from \\documentclass "
            + "to \\end{document}.";

    private static final String FIX =
            "You fix LaTeX documents so that they compile with pdflatex (full TeX Live). You receive the pdflatex error "
            + "and the complete document. Change only what is needed to fix the error and anything else that would "
            + "obviously fail, keeping content, structure and preamble (remove a package only if it causes the error). "
            + "Images live in figuras/. Return only the complete corrected document: no markdown fences, no comments.";

    /** Melhor leitura primeiro; se não houver cota ou demorar, cai para os modelos rápidos. */
    private static final String[] VISION = {"gemini-pro-latest", "gemini-flash-latest", "gemini-flash-lite-latest"};
    private static final String[] TEXT = {"gemini-flash-latest", "gemini-flash-lite-latest"};

    private Gemini() { }

    static Result transcribe(String key, List<byte[]> jpegs, boolean organized, Status st) throws Exception {
        JSONArray parts = new JSONArray();
        for (int i = 0; i < jpegs.size(); i++) {
            parts.put(new JSONObject().put("text", "Página " + (i + 1) + ":"));
            parts.put(new JSONObject().put("inlineData", new JSONObject()
                    .put("mimeType", "image/jpeg")
                    .put("data", Base64.encodeToString(jpegs.get(i), Base64.NO_WRAP))));
        }
        parts.put(new JSONObject().put("text", organized
                ? "Organize estas anotações num documento LaTeX."
                : "Transcreva estas páginas para LaTeX."));

        JSONObject fig = new JSONObject().put("type", "OBJECT").put("properties", new JSONObject()
                        .put("name", new JSONObject().put("type", "STRING"))
                        .put("page", new JSONObject().put("type", "INTEGER"))
                        .put("box", new JSONObject().put("type", "ARRAY").put("items", new JSONObject().put("type", "INTEGER"))))
                .put("required", new JSONArray().put("name").put("page").put("box"));
        JSONObject schema = new JSONObject().put("type", "OBJECT").put("properties", new JSONObject()
                        .put("figures", new JSONObject().put("type", "ARRAY").put("items", fig))
                        .put("latex", new JSONObject().put("type", "STRING")))
                .put("required", new JSONArray().put("figures").put("latex"))
                .put("propertyOrdering", new JSONArray().put("figures").put("latex"));

        JSONObject body = new JSONObject();
        body.put("systemInstruction", new JSONObject().put("parts", new JSONArray()
                .put(new JSONObject().put("text", (organized ? ORGANIZED : FAITHFUL) + COMMON))));
        body.put("contents", new JSONArray().put(new JSONObject().put("role", "user").put("parts", parts)));
        body.put("generationConfig", new JSONObject().put("temperature", 0.2)
                .put("responseMimeType", "application/json").put("responseSchema", schema));

        JSONObject out = new JSONObject(textOf(call(key, VISION, body, st, 150000)));
        Result r = new Result();
        r.latex = tidy(out.optString("latex", ""));
        JSONArray figs = out.optJSONArray("figures");
        if (figs != null) {
            for (int i = 0; i < figs.length(); i++) {
                JSONObject o = figs.optJSONObject(i);
                JSONArray b = o == null ? null : o.optJSONArray("box");
                if (b == null || b.length() != 4) continue;
                Figure f = new Figure();
                f.name = o.optString("name", "fig" + (i + 1) + ".jpg");
                f.page = o.optInt("page", 1);
                f.box = new int[]{b.optInt(0), b.optInt(1), b.optInt(2), b.optInt(3)};
                r.figures.add(f);
            }
        }
        return r;
    }

    /** Corrige o documento a partir do erro do pdflatex. */
    static String fix(String key, String tex, String log) throws Exception {
        JSONObject body = new JSONObject();
        body.put("systemInstruction", new JSONObject().put("parts", new JSONArray().put(new JSONObject().put("text", FIX))));
        body.put("contents", new JSONArray().put(new JSONObject().put("role", "user").put("parts", new JSONArray()
                .put(new JSONObject().put("text", "pdflatex error:\n" + log + "\n\nDocument:\n" + tex)))));
        body.put("generationConfig", new JSONObject().put("temperature", 0));
        return tidy(textOf(call(key, TEXT, body, null, 120000)));
    }

    private static JSONObject call(String key, String[] models, JSONObject body, Status st, int timeout) throws Exception {
        if (key == null || key.trim().isEmpty()) throw new Exception("Falta a chave do Gemini (Configurações).");
        byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
        Exception last = new Exception("Gemini indisponível.");
        for (int i = 0; i < models.length; i++) {
            if (st != null && i > 0) st.say("Tentando modelo rápido…");
            try {
                HttpURLConnection c = Net.open("https://generativelanguage.googleapis.com/v1beta/models/"
                        + URLEncoder.encode(models[i], "UTF-8") + ":generateContent", "POST", timeout);
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                c.setRequestProperty("x-goog-api-key", key.trim());
                Net.Response r = Net.send(c, payload);
                if (r.ok()) return new JSONObject(r.text());
                String msg = r.text();
                try {
                    msg = new JSONObject(msg).getJSONObject("error").optString("message", msg);
                } catch (Exception ignored) {
                }
                last = new Exception("Gemini (" + r.code + "): " + msg);
                if (msg.toLowerCase(Locale.ROOT).contains("api key")) throw last; // chave errada: não adianta trocar de modelo
            } catch (SocketTimeoutException e) {
                last = new Exception("O Gemini demorou demais. Tente com menos páginas.");
            }
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
