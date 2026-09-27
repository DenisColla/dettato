package it.migelino.dettato;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Trascrizione (Whisper) e pulizia del testo (LLM). Da chiamare su un thread in background. */
final class Ai {
    /** Avviso da mostrare dopo l'incolla (es. pulizia non riuscita). */
    static volatile String lastWarning;

    static String process(File f, Cfg c) throws Exception {
        lastWarning = null;
        byte[] audio = Files.readAllBytes(f.toPath());
        if (audio.length < 2000) throw new Exception("registrazione vuota");

        String raw = (c.isOpenRouter() ? sttOpenRouter(audio, c) : sttGroq(audio, c)).trim();
        if (raw.isEmpty()) throw new Exception("nessuna voce riconosciuta");

        try {
            String clean = cleanup(raw, c).trim();
            if (!clean.isEmpty()) return clean;
            lastWarning = "pulizia vuota: incollata la trascrizione grezza";
        } catch (Exception e) {
            lastWarning = "pulizia non riuscita (" + e.getMessage() + "): incollata la trascrizione grezza";
        }
        return raw;
    }

    // ---------- Trascrizione ----------

    private static String sttGroq(byte[] audio, Cfg c) throws Exception {
        String b = "----dettato" + System.nanoTime();
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        part(o, b, "model", c.stt);
        if (!c.lang.isEmpty()) part(o, b, "language", c.lang);
        part(o, b, "response_format", "json");
        part(o, b, "temperature", "0");
        o.write(("--" + b + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"audio.m4a\"\r\n"
                + "Content-Type: audio/mp4\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        o.write(audio);
        o.write(("\r\n--" + b + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpURLConnection con = open(c.base() + "/audio/transcriptions", c.key);
        con.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + b);
        return str(new JSONObject(send(con, o.toByteArray())), "text");
    }

    private static void part(ByteArrayOutputStream o, String b, String name, String value) throws IOException {
        o.write(("--" + b + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n")
                .getBytes(StandardCharsets.UTF_8));
    }

    private static String sttOpenRouter(byte[] audio, Cfg c) throws Exception {
        JSONObject input = new JSONObject();
        input.put("data", Base64.encodeToString(audio, Base64.NO_WRAP));
        input.put("format", "mp4");
        JSONObject body = new JSONObject();
        body.put("model", c.stt);
        body.put("input_audio", input);

        HttpURLConnection con = open(c.base() + "/audio/transcriptions", c.key);
        con.setRequestProperty("Content-Type", "application/json");
        return str(new JSONObject(send(con, body.toString().getBytes(StandardCharsets.UTF_8))), "text");
    }

    // ---------- Pulizia ----------

    private static String cleanup(String raw, Cfg c) throws Exception {
        JSONArray msgs = new JSONArray();
        msgs.put(new JSONObject().put("role", "system").put("content", c.prompt));
        msgs.put(new JSONObject().put("role", "user").put("content", "<dettato>\n" + raw + "\n</dettato>"));

        JSONObject body = new JSONObject();
        body.put("model", c.llm);
        body.put("temperature", 0.3);
        body.put("messages", msgs);
        if (c.isOpenRouter()) {
            body.put("max_tokens", 4096);
            body.put("reasoning", new JSONObject().put("effort", "low").put("exclude", true));
        } else {
            body.put("max_completion_tokens", 4096);
            if (c.llm.contains("gpt-oss")) {
                body.put("reasoning_effort", "low");
                body.put("include_reasoning", false);
            }
        }

        HttpURLConnection con = open(c.base() + "/chat/completions", c.key);
        con.setRequestProperty("Content-Type", "application/json");
        JSONObject r = new JSONObject(send(con, body.toString().getBytes(StandardCharsets.UTF_8)));
        JSONObject msg = r.getJSONArray("choices").getJSONObject(0).getJSONObject("message");
        return str(msg, "content");
    }

    // ---------- HTTP ----------

    private static HttpURLConnection open(String url, String key) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(15000);
        con.setReadTimeout(90000);
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setRequestProperty("Authorization", "Bearer " + key);
        return con;
    }

    private static String send(HttpURLConnection con, byte[] body) throws Exception {
        con.setFixedLengthStreamingMode(body.length);
        try (OutputStream out = con.getOutputStream()) {
            out.write(body);
        }
        int code = con.getResponseCode();
        InputStream in = code >= 400 ? con.getErrorStream() : con.getInputStream();
        String s = in == null ? "" : read(in);
        con.disconnect();
        if (code >= 400) {
            throw new Exception("HTTP " + code + " " + s.substring(0, Math.min(200, s.length())));
        }
        return s;
    }

    static String read(InputStream in) throws IOException {
        try (InputStream is = in) {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) o.write(buf, 0, n);
            return new String(o.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String str(JSONObject o, String name) {
        return (o.has(name) && !o.isNull(name)) ? o.optString(name, "") : "";
    }
}
