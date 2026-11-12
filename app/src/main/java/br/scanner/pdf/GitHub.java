package br.scanner.pdf;

import android.net.Uri;
import android.util.Base64;
import android.util.Base64OutputStream;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Envio para o GitHub pela API oficial, com o token pessoal do usuário. */
final class GitHub {

    private static final String API = "https://api.github.com";

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

    /** Repositórios do usuário: "dono/nome". */
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

    /** Cria um repositório privado e devolve "dono/nome". */
    static String createRepo(String token, String name) throws Exception {
        JSONObject body = new JSONObject().put("name", name).put("private", true)
                .put("description", "PDFs do Scanabla").put("auto_init", true);
        HttpURLConnection c = open(token, "/user/repos", "POST", 30000);
        c.setRequestProperty("Content-Type", "application/json");
        Net.Response r = Net.send(c, body.toString().getBytes(StandardCharsets.UTF_8));
        if (!r.ok()) throw fail(r);
        return new JSONObject(r.text()).getString("full_name");
    }

    private static String encodePath(String path) {
        StringBuilder sb = new StringBuilder();
        for (String seg : path.split("/")) {
            if (seg.isEmpty()) continue;
            if (sb.length() > 0) sb.append('/');
            sb.append(Uri.encode(seg));
        }
        return sb.toString();
    }

    /** Se o caminho já existir, acrescenta -2, -3… antes da extensão. */
    static String freePath(String token, String repo, String path) throws Exception {
        int dot = path.lastIndexOf('.');
        String stem = dot > 0 ? path.substring(0, dot) : path;
        String ext = dot > 0 ? path.substring(dot) : "";
        String p = path;
        for (int n = 2; n < 50; n++) {
            Net.Response r = Net.finish(open(token, "/repos/" + repo + "/contents/" + encodePath(p), "GET", 30000));
            if (r.code == 404) return p;
            if (!r.ok()) throw fail(r);
            p = stem + "-" + n + ext;
        }
        return stem + "-" + System.currentTimeMillis() + ext;
    }

    /** Envia o arquivo (sem carregar tudo na memória) e devolve o link dele no GitHub. */
    static String upload(String token, String repo, String path, File file, String message) throws Exception {
        long n = file.length();
        long b64 = 4 * ((n + 2) / 3);
        byte[] head = ("{\"message\":" + JSONObject.quote(message) + ",\"content\":\"").getBytes(StandardCharsets.UTF_8);
        byte[] tail = "\"}".getBytes(StandardCharsets.UTF_8);
        HttpURLConnection c = open(token, "/repos/" + repo + "/contents/" + encodePath(path), "PUT", 300000);
        c.setRequestProperty("Content-Type", "application/json");
        c.setDoOutput(true);
        c.setFixedLengthStreamingMode(head.length + b64 + tail.length);
        OutputStream os = c.getOutputStream();
        os.write(head);
        try (InputStream in = new FileInputStream(file);
             Base64OutputStream enc = new Base64OutputStream(os, Base64.NO_WRAP | Base64.NO_CLOSE)) {
            byte[] buf = new byte[48 * 1024];
            int k;
            while ((k = in.read(buf)) > 0) enc.write(buf, 0, k);
        }
        os.write(tail);
        os.close();
        Net.Response r = Net.finish(c);
        if (!r.ok()) throw fail(r);
        JSONObject content = new JSONObject(r.text()).optJSONObject("content");
        return content == null ? "https://github.com/" + repo : content.optString("html_url", "https://github.com/" + repo);
    }
}
