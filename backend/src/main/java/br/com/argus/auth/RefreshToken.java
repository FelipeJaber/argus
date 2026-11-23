package br.com.argus.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Refresh token opaco no formato {@code <sessionId>.<segredo>}.
 * <p>
 * Apenas o hash SHA-256 do segredo é persistido. Carregar o id da sessão no próprio token permite
 * reconhecer um token antigo (já rotacionado) e revogar a sessão inteira em caso de reuso.
 */
public record RefreshToken(UUID sessionId, String secret) {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int SECRET_BYTES = 32;

    public static RefreshToken generate(UUID sessionId) {
        byte[] bytes = new byte[SECRET_BYTES];
        RANDOM.nextBytes(bytes);
        return new RefreshToken(sessionId, Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
    }

    public static Optional<RefreshToken> parse(String raw) {
        if (raw == null || raw.length() > 200) {
            return Optional.empty();
        }
        int dot = raw.indexOf('.');
        if (dot <= 0 || dot == raw.length() - 1) {
            return Optional.empty();
        }
        try {
            return Optional.of(new RefreshToken(UUID.fromString(raw.substring(0, dot)), raw.substring(dot + 1)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public String value() {
        return sessionId + "." + secret;
    }

    public String hash() {
        return sha256Hex(secret);
    }

    static String sha256Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    @Override
    public String toString() {
        return "RefreshToken[sessionId=" + sessionId + ", secret=***]";
    }
}
