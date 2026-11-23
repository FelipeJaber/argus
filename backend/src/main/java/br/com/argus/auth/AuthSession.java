package br.com.argus.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import br.com.argus.user.User;

/**
 * Sessão de login (um dispositivo/navegador). É a fonte da verdade para o access token
 * (claim {@code sid}) e guarda o hash do refresh token vigente.
 */
@Entity
@Table(name = "auth_sessions")
public class AuthSession {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "refresh_token_hash", nullable = false, length = 64)
    private String refreshTokenHash;

    @Column(name = "previous_token_hash", length = 64)
    private String previousTokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "rotated_at")
    private Instant rotatedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "max_expires_at", nullable = false, updatable = false)
    private Instant maxExpiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "revoked_reason", length = 30)
    private RevocationReason revokedReason;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected AuthSession() {
        // JPA
    }

    private AuthSession(UUID id, User user, Instant now, Duration ttl, Duration maxLifetime, ClientInfo client) {
        this.id = id;
        this.user = user;
        this.createdAt = now;
        this.maxExpiresAt = now.plus(maxLifetime);
        this.expiresAt = min(now.plus(ttl), maxExpiresAt);
        this.userAgent = truncate(client.userAgent(), 255);
        this.ipAddress = truncate(client.ipAddress(), 45);
    }

    /** Abre uma sessão e devolve o refresh token em texto puro (única vez em que ele existe fora do cliente). */
    public static Opened open(User user, Instant now, Duration ttl, Duration maxLifetime, ClientInfo client) {
        AuthSession session = new AuthSession(UUID.randomUUID(), user, now, ttl, maxLifetime, client);
        RefreshToken token = RefreshToken.generate(session.id);
        session.refreshTokenHash = token.hash();
        return new Opened(session, token);
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public boolean matchesCurrent(RefreshToken token) {
        return constantTimeEquals(refreshTokenHash, token.hash());
    }

    /** O token apresentado é o imediatamente anterior e a rotação aconteceu há pouco (requisições concorrentes). */
    public boolean matchesPreviousWithin(RefreshToken token, Instant now, Duration grace) {
        return previousTokenHash != null
                && rotatedAt != null
                && !rotatedAt.plus(grace).isBefore(now)
                && constantTimeEquals(previousTokenHash, token.hash());
    }

    /** Rotação: o token atual vira "anterior", um novo é emitido e a validade desliza (até o limite absoluto). */
    public RefreshToken rotate(Instant now, Duration ttl) {
        RefreshToken next = RefreshToken.generate(id);
        previousTokenHash = refreshTokenHash;
        refreshTokenHash = next.hash();
        rotatedAt = now;
        expiresAt = min(now.plus(ttl), maxExpiresAt);
        return next;
    }

    public void revoke(Instant now, RevocationReason reason) {
        if (revokedAt == null) {
            revokedAt = now;
            revokedReason = Objects.requireNonNull(reason);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return a != null && b != null
                && MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII), b.getBytes(StandardCharsets.US_ASCII));
    }

    private static Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getRotatedAt() {
        return rotatedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public RevocationReason getRevokedReason() {
        return revokedReason;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public record Opened(AuthSession session, RefreshToken refreshToken) {
    }
}
