package br.scanner.pdf;

import android.net.Uri;
import android.util.Base64;
import android.util.Base64OutputStream;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Envio para o GitHub pela API oficial, com o token pessoal do usuário. */
final class GitHub {

    private static final String API = "https://api.github.com";

    /** Resultado do envio: página no GitHub e (para repositórios públicos) link direto do arquivo. */
    static final class Sent {
        String html;
        String download;
    }

    private GitHub() { }

    private static HttpURLConnection open(String token, String path, String method, int timeout) throws Exception {
        HttpURLConnection c = Net.open(API + path, method, timeout);
        c.setRequestProperty("Authorization", "Bearer " + token.trim());
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        return c;
    }

    private static Exception fail(Net.Response r) {
        String msg = r.text();
        try {
            msg = new JSONObject(msg).optString("message", msg);
        } catch (Exception ignored) {
        }
        if (r.code == 401) return new Exception("Token do GitHub inválido ou vencido.");
        if (r.code == 403) return new Exception("O token não tem permissão para isso.\n" + msg);
        if (r.code == 404) return new Exception("Repositório não encontrado (ou o token não tem acesso a ele).");
        return new Exception("GitHub (" + r.code + "): " + msg);
    }

    private static JSONObject getJson(String token, String path) throws Exception {
        Net.Response r = Net.finish(open(token, path, "GET", 30000));
        if (!r.ok()) throw fail(r);
        return new JSONObject(r.text());
    }

    private static JSONObject sendJson(String token, String path, String method, JSONObject body) throws Exception {
        HttpURLConnection c = open(token, path, method, 60000);
        c.setRequestProperty("Content-Type", "application/json");
        Net.Response r = Net.send(c, body.toString().getBytes(StandardCharsets.UTF_8));
        if (!r.ok()) throw fail(r);
        return new JSONObject(r.text());
    }

    /** Envia {head}{base64 do arquivo}{tail} sem carregar o arquivo inteiro na memória. */
    private static JSONObject sendBase64(String token, String path, String method, String head,
                                         Store.ProjFile f, String tail) throws Exception {
        byte[] h = head.getBytes(StandardCharsets.UTF_8);
        byte[] t = tail.getBytes(StandardCharsets.UTF_8);
        long n = f.length();
        long b64 = 4 * ((n + 2) / 3);
        HttpURLConnection c = open(token, path, method, 300000);
        c.setRequestProperty("Content-Type", "application/json");
        c.setDoOutput(true);
        c.setFixedLengthStreamingMode(h.length + b64 + t.length);
        OutputStream os = c.getOutputStream();
        os.write(h);
        try (InputStream in = f.open();
             Base64OutputStream enc = new Base64OutputStream(os, Base64.NO_WRAP | Base64.NO_CLOSE)) {
            byte[] buf = new byte[48 * 1024];
            int k;
            while ((k = in.read(buf)) > 0) enc.write(buf, 0, k);
        }
        os.write(t);
        os.close();
        Net.Response r = Net.finish(c);
        if (!r.ok()) throw fail(r);
        return new JSONObject(r.text());
    }

    // ---------------------------------------------------------------- repositórios

    static List<String> repos(String token) throws Exception {
        List<String> out = new ArrayList<>();
        for (int page = 1; page <= 3; page++) {
            Net.Response r = Net.finish(open(token, "/user/repos?per_page=100&sort=updated&page=" + page, "GET", 30000));
            if (!r.ok()) throw fail(r);
            JSONArray a = new JSONArray(r.text());
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                JSONObject perms = o.optJSONObject("permissions");
                if (perms != null && !perms.optBoolean("push", true)) continue;
                out.add(o.getString("full_name"));
            }
            if (a.length() < 100) break;
        }
        return out;
    }

    static String createRepo(String token, String name) throws Exception {
        JSONObject body = new JSONObject().put("name", name).put("private", true)
                .put("description", "Projetos do Scanabla").put("auto_init", true);
        return sendJson(token, "/user/repos", "POST", body).getString("full_name");
    }

    static boolean isPublic(String token, String repo) {
        try {
            return !getJson(token, "/repos/" + repo).optBoolean("private", true);
        } catch (Exception e) {
            return false;
        }
    }

    static String encodePath(String path) {
        StringBuilder sb = new StringBuilder();
        for (String seg : path.split("/")) {
            if (seg.isEmpty()) continue;
            if (sb.length() > 0) sb.append('/');
            sb.append(Uri.encode(seg));
        }
        return sb.toString();
    }

    /** Se o caminho (arquivo ou pasta) já existir, acrescenta -2, -3… */
    static String freePath(String token, String repo, String path) throws Exception {
        int slash = path.lastIndexOf('/');
        int dot = path.lastIndexOf('.');
        boolean hasExt = dot > slash + 1;
        String stem = hasExt ? path.substring(0, dot) : path;
        String ext = hasExt ? path.substring(dot) : "";
        String p = path;
        for (int n = 2; n < 50; n++) {
            Net.Response r = Net.finish(open(token, "/repos/" + repo + "/contents/" + encodePath(p), "GET", 30000));
            if (r.code == 404) return p;
            if (!r.ok()) throw fail(r);
            p = stem + "-" + n + ext;
        }
        return stem + "-" + System.currentTimeMillis() + ext;
    }

    // ---------------------------------------------------------------- envio

    /** Um arquivo só (ex.: o ZIP do projeto). */
    static Sent putFile(String token, String repo, String path, Store.ProjFile f, String message) throws Exception {
        JSONObject r = sendBase64(token, "/repos/" + repo + "/contents/" + encodePath(path), "PUT",
                "{\"message\":" + JSONObject.quote(message) + ",\"content\":\"", f, "\"}");
        JSONObject content = r.optJSONObject("content");
        Sent s = new Sent();
        s.html = content == null ? "https://github.com/" + repo : content.optString("html_url", "https://github.com/" + repo);
        s.download = content == null ? null : content.optString("download_url", null);
        return s;
    }

    private static String headSha(String token, String repo, String branch) throws Exception {
        Net.Response r = Net.finish(open(token, "/repos/" + repo + "/git/ref/heads/" + encodePath(branch), "GET", 30000));
        if (r.code == 404 || r.code == 409) return null; // repositório vazio
        if (!r.ok()) throw fail(r);
        return new JSONObject(r.text()).getJSONObject("object").getString("sha");
    }

    /** Vários arquivos numa pasta, num único commit (projeto LaTeX aberto, sem ZIP). */
    static Sent commitFiles(String token, String repo, String dir, List<Store.ProjFile> files, String message)
            throws Exception {
        String branch = getJson(token, "/repos/" + repo).optString("default_branch", "main");
        List<Store.ProjFile> rest = new ArrayList<>(files);
        String head = headSha(token, repo, branch);
        if (head == null) {
            // Repositório vazio: o primeiro arquivo cria o branch.
            Store.ProjFile first = rest.remove(0);
            putFile(token, repo, dir + "/" + first.path, first, message);
            head = headSha(token, repo, branch);
            if (head == null) throw new Exception("Não consegui iniciar o repositório.");
        }
        if (!rest.isEmpty()) {
            String baseTree = getJson(token, "/repos/" + repo + "/git/commits/" + head)
                    .getJSONObject("tree").getString("sha");
            JSONArray tree = new JSONArray();
            for (Store.ProjFile f : rest) {
                String sha = sendBase64(token, "/repos/" + repo + "/git/blobs", "POST",
                        "{\"encoding\":\"base64\",\"content\":\"", f, "\"}").getString("sha");
                tree.put(new JSONObject().put("path", dir + "/" + f.path).put("mode", "100644")
                        .put("type", "blob").put("sha", sha));
            }
            String treeSha = sendJson(token, "/repos/" + repo + "/git/trees", "POST",
                    new JSONObject().put("base_tree", baseTree).put("tree", tree)).getString("sha");
            String commit = sendJson(token, "/repos/" + repo + "/git/commits", "POST", new JSONObject()
                    .put("message", message).put("tree", treeSha).put("parents", new JSONArray().put(head)))
                    .getString("sha");
            sendJson(token, "/repos/" + repo + "/git/refs/heads/" + encodePath(branch), "PATCH",
                    new JSONObject().put("sha", commit));
        }
        Sent s = new Sent();
        s.html = "https://github.com/" + repo + "/tree/" + encodePath(branch) + "/" + encodePath(dir);
        return s;
    }
}
