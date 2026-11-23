package br.com.argus.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.argus.audit.AuditAction;
import br.com.argus.audit.AuditEntry;
import br.com.argus.audit.AuditOutcome;
import br.com.argus.audit.AuditService;
import br.com.argus.auth.AuthDtos.SessionResponse;
import br.com.argus.security.AccessTokenService;
import br.com.argus.security.AccessTokenService.IssuedAccessToken;
import br.com.argus.security.SecurityProperties;
import br.com.argus.shared.ApiException;
import br.com.argus.user.User;
import br.com.argus.user.UserRepository;

/**
 * Fluxo de autenticação:
 * <ol>
 * <li><b>login</b>: valida credenciais, abre uma {@link AuthSession} e emite access token (JWT) + refresh token;</li>
 * <li><b>refresh</b>: troca o refresh token por um novo par (rotação). Apresentar um token já rotacionado
 * fora da janela de tolerância é tratado como roubo e revoga a sessão;</li>
 * <li><b>logout</b>: revoga a sessão; o access token deixa de valer na próxima requisição.</li>
 * </ol>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository users;
    private final AuthSessionRepository sessions;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService accessTokens;
    private final AuditService audit;
    private final SecurityProperties properties;
    private final Clock clock;
    /** Hash usado quando o e-mail não existe, para o tempo de resposta não revelar contas válidas. */
    private final String dummyHash;

    public AuthService(UserRepository users, AuthSessionRepository sessions, PasswordEncoder passwordEncoder,
            AccessTokenService accessTokens, AuditService audit, SecurityProperties properties, Clock clock) {
        this.users = users;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.accessTokens = accessTokens;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("argus-timing-protection");
    }

    /** {@code noRollbackFor}: falhas de login precisam persistir o contador de tentativas e a auditoria. */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResult login(String email, String password, ClientInfo client) {
        Instant now = clock.instant();
        Optional<User> found = users.findByEmail(User.normalizeEmail(email));
        if (found.isEmpty()) {
            passwordEncoder.matches(password, dummyHash);
            audit.record(AuditEntry.of(AuditAction.LOGIN, AuditOutcome.BLOCKED).detail("Usuário inexistente"));
            throw invalidCredentials();
        }
        User user = found.get();
        if (user.isLocked(now)) {
            audit.record(AuditEntry.of(AuditAction.LOGIN, AuditOutcome.BLOCKED)
                    .by(user.getId(), user.getName()).detail("Conta temporariamente bloqueada"));
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "ACCOUNT_LOCKED",
                    "Muitas tentativas sem sucesso. Tente novamente em alguns minutos.");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            user.registerFailedLogin(now, properties.login().maxFailedAttempts(), properties.login().lockDuration());
            audit.record(AuditEntry.of(AuditAction.LOGIN, AuditOutcome.BLOCKED)
                    .by(user.getId(), user.getName()).detail("Senha inválida"));
            throw invalidCredentials();
        }
        if (!user.isActive()) {
            audit.record(AuditEntry.of(AuditAction.LOGIN, AuditOutcome.BLOCKED)
                    .by(user.getId(), user.getName()).detail("Usuário inativo"));
            throw invalidCredentials();
        }

        user.registerSuccessfulLogin(now);
        var refresh = properties.refreshToken();
        AuthSession.Opened opened = AuthSession.open(user, now, refresh.ttl(), refresh.sessionMaxLifetime(), client);
        sessions.save(opened.session());
        audit.record(AuditEntry.of(AuditAction.LOGIN, AuditOutcome.ALLOWED).by(user.getId(), user.getName()));
        return result(user, opened.session(), opened.refreshToken());
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResult refresh(String rawRefreshToken, ClientInfo client) {
        RefreshToken presented = RefreshToken.parse(rawRefreshToken).orElseThrow(AuthService::invalidRefreshToken);
        AuthSession session = sessions.findWithUserById(presented.sessionId()).orElseThrow(AuthService::invalidRefreshToken);
        Instant now = clock.instant();

        if (!session.isActive(now)) {
            throw invalidRefreshToken();
        }
        User user = session.getUser();
        if (!user.isActive()) {
            session.revoke(now, RevocationReason.USER_DEACTIVATED);
            throw invalidRefreshToken();
        }
        if (session.matchesCurrent(presented)) {
            RefreshToken next = session.rotate(now, properties.refreshToken().ttl());
            return result(user, session, next);
        }
        if (session.matchesPreviousWithin(presented, now, properties.refreshToken().reuseGracePeriod())) {
            // Duas abas renovaram ao mesmo tempo: a outra já recebeu o token novo no cookie.
            throw ApiException.conflict("REFRESH_TOKEN_ROTATED",
                    "O token já foi renovado por outra requisição. Tente novamente.");
        }

        // Token antigo reapresentado fora da janela: possível roubo. Revoga a sessão inteira.
        session.revoke(now, RevocationReason.TOKEN_REUSE_DETECTED);
        log.warn("Reuso de refresh token detectado na sessão {} (usuário {})", session.getId(), user.getId());
        audit.record(AuditEntry.of(AuditAction.REFRESH_TOKEN_REUSE, AuditOutcome.BLOCKED)
                .by(user.getId(), user.getName()).detail("Sessão revogada por reuso de refresh token"));
        throw invalidRefreshToken();
    }

    /** Revoga a sessão do refresh token apresentado. Idempotente: token inválido/ausente não gera erro. */
    @Transactional
    public void logout(String rawRefreshToken) {
        RefreshToken.parse(rawRefreshToken)
                .flatMap(token -> sessions.findWithUserById(token.sessionId())
                        .filter(session -> session.matchesCurrent(token)))
                .ifPresent(session -> {
                    session.revoke(clock.instant(), RevocationReason.LOGOUT);
                    User user = session.getUser();
                    audit.record(AuditEntry.of(AuditAction.LOGOUT, AuditOutcome.ALLOWED)
                            .by(user.getId(), user.getName()));
                });
    }

    @Transactional
    public void logoutAll(UUID userId, String userName) {
        int revoked = sessions.revokeAllByUser(userId, clock.instant(), RevocationReason.LOGOUT_ALL);
        audit.record(AuditEntry.of(AuditAction.LOGOUT, AuditOutcome.ALLOWED)
                .by(userId, userName).detail("Encerradas " + revoked + " sessões"));
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> activeSessions(UUID userId, UUID currentSessionId) {
        return sessions.findActiveByUser(userId, clock.instant()).stream()
                .map(s -> SessionResponse.from(s, currentSessionId))
                .toList();
    }

    @Transactional
    public void revokeSession(UUID userId, String userName, UUID sessionId) {
        AuthSession session = sessions.findWithUserById(sessionId)
                .filter(s -> s.getUser().getId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Sessão"));
        session.revoke(clock.instant(), RevocationReason.REVOKED_BY_USER);
        audit.record(AuditEntry.of(AuditAction.SESSION_REVOKED, AuditOutcome.ALLOWED).by(userId, userName));
    }

    /** Usado quando senha, perfil ou status do usuário mudam: derruba sessões existentes. */
    @Transactional
    public void revokeAllSessions(UUID userId, RevocationReason reason) {
        sessions.revokeAllByUser(userId, clock.instant(), reason);
    }

    @Transactional
    public void revokeOtherSessions(UUID userId, UUID keepSessionId, RevocationReason reason) {
        sessions.revokeAllByUserExcept(userId, keepSessionId, clock.instant(), reason);
    }

    private static ApiException invalidCredentials() {
        return ApiException.unauthorized("INVALID_CREDENTIALS", "E-mail ou senha inválidos.");
    }

    private static ApiException invalidRefreshToken() {
        return ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Sessão expirada. Entre novamente.");
    }

    private AuthResult result(User user, AuthSession session, RefreshToken refreshToken) {
        IssuedAccessToken access = accessTokens.issue(user, session.getId());
        return new AuthResult(user, access, refreshToken, session.getExpiresAt());
    }

    public record AuthResult(User user, IssuedAccessToken accessToken, RefreshToken refreshToken,
            Instant refreshExpiresAt) {
    }
}
