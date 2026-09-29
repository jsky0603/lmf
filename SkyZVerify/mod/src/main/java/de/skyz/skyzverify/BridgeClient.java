package de.skyz.skyzverify;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Blocking HTTP client; always call on the background executor. */
public final class BridgeClient {
    private final VerifyConfig config;

    public BridgeClient(VerifyConfig config) {
        this.config = config;
    }

    public JsonObject create(String requestId, String uuid, String player, String discordUser) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("id", requestId);
        body.addProperty("mcUuid", uuid);
        body.addProperty("mcName", player);
        body.addProperty("discordUsername", discordUser);
        return send("POST", "/requests", body);
    }

    public JsonObject status(String requestId) throws IOException {
        return send("GET", "/requests/" + requestId, null);
    }

    public void dismiss(String requestId) throws IOException {
        send("DELETE", "/requests/" + requestId, null);
    }

    public static String field(JsonObject object, String key) throws IOException {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive()
                || !object.get(key).getAsJsonPrimitive().isString()) {
            throw new IOException("Ungueltige Antwort des Discord-Bots (" + key + ")");
        }
        return object.get(key).getAsString();
    }

    private JsonObject send(String method, String path, JsonObject body) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(config.bridgeUrl + path).openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(7000);
        connection.setRequestMethod(method);
        connection.setRequestProperty("X-SkyZ-Secret", config.bridgeSecret);
        connection.setRequestProperty("Accept", "application/json");
        try {
            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(bytes);
                }
            }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                String code = "";
                try (InputStream error = connection.getErrorStream()) {
                    if (error != null) {
                        byte[] chunk = new byte[512];
                        ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();
                        int read;
                        while ((read = error.read(chunk)) != -1 && errorBytes.size() + read <= 1024) {
                            errorBytes.write(chunk, 0, read);
                        }
                        if (errorBytes.size() > 0) {
                            JsonObject object = new JsonParser().parse(new String(errorBytes.toByteArray(),
                                    StandardCharsets.UTF_8)).getAsJsonObject();
                            code = field(object, "code");
                        }
                    }
                } catch (IOException | RuntimeException ignored) { /* Generic error below. */ }
                if ("USER_NOT_FOUND".equals(code)) throw new IOException("Discord-Username nicht auf dem Discord-Server gefunden.");
                if ("ALREADY_PENDING".equals(code)) throw new IOException("Für dieses Discord-Konto läuft bereits eine Anfrage.");
                if ("TOO_MANY_REQUESTS".equals(code)) throw new IOException("Zu viele Anfragen. Bitte später erneut versuchen.");
                if (status == 503) throw new IOException("Discord-Bot ist nicht bereit. Bitte später erneut versuchen.");
                throw new IOException("Discord-Bot meldet HTTP " + status + ". Bitte Admin informieren.");
            }
            try (InputStream in = connection.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] chunk = new byte[1024];
                int read;
                while ((read = in.read(chunk)) != -1) {
                    if (out.size() + read > 16384) throw new IOException("Antwort des Bots zu gross");
                    out.write(chunk, 0, read);
                }
                try {
                    return new JsonParser().parse(new String(out.toByteArray(), StandardCharsets.UTF_8)).getAsJsonObject();
                } catch (RuntimeException exception) {
                    throw new IOException("Bot antwortet mit ungueltigem JSON", exception);
                }
            }
        } finally {
            connection.disconnect();
        }
    }
}
