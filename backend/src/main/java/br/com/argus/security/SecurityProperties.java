package br.com.argus.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configurações de segurança ({@code argus.security.*}). A aplicação não sobe sem um segredo JWT válido.
 *
 * @param jwt          assinatura e validade do access token
 * @param refreshToken validade da sessão/refresh token e o cookie onde ele trafega
 * @param login        proteção contra força bruta
 * @param cors         origens do frontend autorizadas
 */
@ConfigurationProperties("argus.security")
public record SecurityProperties(
        @DefaultValue Jwt jwt,
        @DefaultValue RefreshToken refreshToken,
        @DefaultValue Login login,
        @DefaultValue Cors cors) {

    public SecurityProperties {
        if (jwt.secret() == null || jwt.secret().getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "argus.security.jwt.secret (env ARGUS_JWT_SECRET) é obrigatório e deve ter ao menos 32 bytes.");
        }
    }

    /**
     * @param issuer         valor do claim {@code iss}
     * @param secret         chave HMAC-SHA256 (mínimo 32 bytes)
     * @param accessTokenTtl validade do access token (curta, pois ele circula no cliente)
     */
    public record Jwt(
            @DefaultValue("argus-api") String issuer,
            String secret,
            @DefaultValue("15m") Duration accessTokenTtl) {
    }

    /**
     * @param ttl                validade deslizante: cada renovação estende a sessão por este período
     * @param sessionMaxLifetime validade absoluta da sessão, independentemente das renovações
     * @param reuseGracePeriod   janela em que o token imediatamente anterior recebe 409 (corrida entre abas)
     *                           em vez de ser tratado como roubo
     * @param cookie             cookie HttpOnly que transporta o refresh token
     */
    public record RefreshToken(
            @DefaultValue("7d") Duration ttl,
            @DefaultValue("30d") Duration sessionMaxLifetime,
            @DefaultValue("10s") Duration reuseGracePeriod,
            @DefaultValue Cookie cookie) {
    }

    public record Cookie(
            @DefaultValue("argus_refresh") String name,
            @DefaultValue("/api/v1/auth") String path,
            @DefaultValue("true") boolean secure,
            @DefaultValue("Strict") String sameSite) {
    }

    public record Login(
            @DefaultValue("5") int maxFailedAttempts,
            @DefaultValue("15m") Duration lockDuration) {
    }

    public record Cors(@DefaultValue List<String> allowedOrigins) {
    }
}
