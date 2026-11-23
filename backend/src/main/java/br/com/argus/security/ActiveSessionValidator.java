package br.com.argus.security;

import java.time.Clock;
import java.util.UUID;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import br.com.argus.auth.AuthSessionRepository;

/**
 * Além de assinatura e expiração, exige que a sessão do token ({@code sid}) continue ativa no banco.
 * É o que torna logout e revogação efetivos imediatamente, sem esperar o access token expirar.
 */
public class ActiveSessionValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error SESSION_INACTIVE = new OAuth2Error(
            OAuth2ErrorCodes.INVALID_TOKEN, "Sessão encerrada ou expirada.", null);

    private final AuthSessionRepository sessions;
    private final Clock clock;

    public ActiveSessionValidator(AuthSessionRepository sessions, Clock clock) {
        this.sessions = sessions;
        this.clock = clock;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        String sid = jwt.getClaimAsString(AccessTokenService.CLAIM_SESSION_ID);
        if (sid == null) {
            return OAuth2TokenValidatorResult.failure(SESSION_INACTIVE);
        }
        try {
            return sessions.isActive(UUID.fromString(sid), clock.instant())
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(SESSION_INACTIVE);
        } catch (IllegalArgumentException e) {
            return OAuth2TokenValidatorResult.failure(SESSION_INACTIVE);
        }
    }
}
