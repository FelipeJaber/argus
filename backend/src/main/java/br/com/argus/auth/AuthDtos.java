package br.com.argus.auth;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import br.com.argus.user.UserResponse;

/** DTOs do módulo de autenticação. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank @Email @Size(max = 160) String email,
            @NotBlank @Size(max = 72) String password) {

        @Override
        public String toString() {
            return "LoginRequest[email=" + email + ", password=***]";
        }
    }

    /**
     * Corpo das respostas de login/refresh. O refresh token NÃO aparece aqui: vai no cookie HttpOnly.
     *
     * @param expiresIn segundos até o access token expirar
     */
    public record TokenResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {
    }

    public record SessionResponse(
            UUID id,
            Instant createdAt,
            Instant lastRefreshedAt,
            Instant expiresAt,
            String userAgent,
            String ipAddress,
            boolean current) {

        static SessionResponse from(AuthSession session, UUID currentSessionId) {
            return new SessionResponse(session.getId(), session.getCreatedAt(), session.getRotatedAt(),
                    session.getExpiresAt(), session.getUserAgent(), session.getIpAddress(),
                    session.getId().equals(currentSessionId));
        }
    }
}
