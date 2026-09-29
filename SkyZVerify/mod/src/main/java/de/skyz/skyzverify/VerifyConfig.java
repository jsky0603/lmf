package de.skyz.skyzverify;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Properties;

public final class VerifyConfig {
    public final String bridgeUrl;
    public final String bridgeSecret;
    public final String discordInvite;

    private VerifyConfig(String url, String secret, String invite) {
        this.bridgeUrl = url;
        this.bridgeSecret = secret;
        this.discordInvite = invite;
    }

    public static VerifyConfig load(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Properties properties = new Properties();
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                properties.load(in);
            }
        } else {
            properties.setProperty("bridge.url", "http://127.0.0.1:8787");
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            StringBuilder secret = new StringBuilder(64);
            for (byte part : random) secret.append(String.format("%02x", part & 0xff));
            properties.setProperty("bridge.secret", secret.toString());
            properties.setProperty("discord.invite", "");
            try (OutputStream out = Files.newOutputStream(file)) {
                properties.store(out, "SkyZVerify - secret belongs only on the server and bot");
            }
        }
        String url = properties.getProperty("bridge.url", "").trim();
        String secret = properties.getProperty("bridge.secret", "").trim();
        String invite = properties.getProperty("discord.invite", "").trim();
        try {
            URI parsed = new URI(url);
            if (!("http".equals(parsed.getScheme()) || "https".equals(parsed.getScheme()))
                    || parsed.getHost() == null || parsed.getRawQuery() != null || parsed.getRawFragment() != null
                    || (parsed.getRawPath() != null && !parsed.getRawPath().isEmpty() && !"/".equals(parsed.getRawPath()))) {
                throw new IllegalArgumentException("Ungueltige Bridge-URL");
            }
            if (!secret.matches("[a-fA-F0-9]{64}")) throw new IllegalArgumentException("Bridge-Secret muss 64 Hex-Zeichen haben");
        } catch (Exception exception) {
            throw new IOException("SkyZVerify: bridge.url oder bridge.secret in " + file + " ist ungueltig.", exception);
        }
        return new VerifyConfig(url.replaceAll("/+$", ""), secret, invite);
    }
}
