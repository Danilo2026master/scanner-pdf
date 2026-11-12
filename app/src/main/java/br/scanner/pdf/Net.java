package br.scanner.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** HTTP simples (sem bibliotecas extras). */
final class Net {

    static final class Response {
        int code;
        byte[] body;

        String text() {
            return body == null ? "" : new String(body, StandardCharsets.UTF_8);
        }

        boolean ok() {
            return code >= 200 && code < 300;
        }
    }

    private Net() { }

    static HttpURLConnection open(String url, String method, int readTimeoutMs) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(15000);
        c.setReadTimeout(readTimeoutMs);
        c.setRequestProperty("User-Agent", "Scanabla/2.1 (Android)");
        return c;
    }

    static Response send(HttpURLConnection c, byte[] body) throws IOException {
        if (body != null) {
            c.setDoOutput(true);
            c.setFixedLengthStreamingMode(body.length);
            c.getOutputStream().write(body);
            c.getOutputStream().close();
        }
        return finish(c);
    }

    static Response finish(HttpURLConnection c) throws IOException {
        Response r = new Response();
        try {
            r.code = c.getResponseCode();
            InputStream in = r.code >= 400 ? c.getErrorStream() : c.getInputStream();
            r.body = in == null ? new byte[0] : readAll(in);
        } finally {
            c.disconnect();
        }
        return r;
    }

    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[32 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return out.toByteArray();
    }

    static boolean isPdf(byte[] b) {
        return b != null && b.length > 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F';
    }
}
