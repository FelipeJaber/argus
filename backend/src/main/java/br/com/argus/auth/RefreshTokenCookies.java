package br.com.argus.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

import br.com.argus.security.SecurityProperties;

/**
 * O refresh token trafega apenas em cookie HttpOnly + SameSite=Strict, restrito ao caminho
 * {@code /api/v1/auth}: JavaScript não consegue lê-lo (XSS) e outros sites não conseguem enviá-lo (CSRF).
 */
@Component
public class RefreshTokenCookies {

    private final SecurityProperties.Cookie config;
    private final Clock clock;

    public RefreshTokenCookies(SecurityProperties properties, Clock clock) {
        this.config = properties.refreshToken().cookie();
        this.clock = clock;
    }

    public Optional<String> read(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, config.name());
        return Optional.ofNullable(cookie).map(Cookie::getValue).filter(v -> !v.isBlank());
    }

    public ResponseCookie create(RefreshToken token, Instant expiresAt) {
        Duration maxAge = Duration.between(clock.instant(), expiresAt);
        return base(token.value()).maxAge(maxAge.isNegative() ? Duration.ZERO : maxAge).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(config.name(), value)
                .httpOnly(true)
                .secure(config.secure())
                .sameSite(config.sameSite())
                .path(config.path());
    }
}
