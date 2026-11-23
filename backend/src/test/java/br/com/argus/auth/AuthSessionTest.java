package br.com.argus.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import br.com.argus.user.Role;
import br.com.argus.user.User;

class AuthSessionTest {

    private static final Duration TTL = Duration.ofDays(7);
    private static final Duration MAX = Duration.ofDays(30);
    private static final Duration GRACE = Duration.ofSeconds(10);
    private static final Instant T0 = Instant.parse("2026-01-01T12:00:00Z");

    private final User user = new User("Teste", "teste@teste.local", "{noop}x", Role.COLLABORATOR, null);
    private final ClientInfo client = new ClientInfo("JUnit", "127.0.0.1");

    @Test
    void openedSessionAcceptsOnlyItsOwnRefreshToken() {
        AuthSession.Opened opened = AuthSession.open(user, T0, TTL, MAX, client);

        assertThat(opened.session().isActive(T0)).isTrue();
        assertThat(opened.session().matchesCurrent(opened.refreshToken())).isTrue();
        assertThat(opened.session().matchesCurrent(RefreshToken.generate(opened.session().getId()))).isFalse();
        assertThat(opened.refreshToken().sessionId()).isEqualTo(opened.session().getId());
    }

    @Test
    void rotationReplacesTokenAndSlidesExpiration() {
        AuthSession.Opened opened = AuthSession.open(user, T0, TTL, MAX, client);
        AuthSession session = opened.session();
        Instant later = T0.plus(Duration.ofDays(3));

        RefreshToken next = session.rotate(later, TTL);

        assertThat(session.matchesCurrent(next)).isTrue();
        assertThat(session.matchesCurrent(opened.refreshToken())).isFalse();
        assertThat(session.getExpiresAt()).isEqualTo(later.plus(TTL));
    }

    @Test
    void previousTokenIsToleratedOnlyWithinGracePeriod() {
        AuthSession.Opened opened = AuthSession.open(user, T0, TTL, MAX, client);
        opened.session().rotate(T0, TTL);

        assertThat(opened.session().matchesPreviousWithin(opened.refreshToken(), T0.plusSeconds(5), GRACE)).isTrue();
        assertThat(opened.session().matchesPreviousWithin(opened.refreshToken(), T0.plusSeconds(11), GRACE)).isFalse();
    }

    @Test
    void slidingExpirationNeverExceedsAbsoluteLifetime() {
        AuthSession session = AuthSession.open(user, T0, TTL, MAX, client).session();

        session.rotate(T0.plus(Duration.ofDays(28)), TTL);

        assertThat(session.getExpiresAt()).isEqualTo(T0.plus(MAX));
        assertThat(session.isActive(T0.plus(MAX))).isFalse();
    }

    @Test
    void revokedOrExpiredSessionIsInactive() {
        AuthSession session = AuthSession.open(user, T0, TTL, MAX, client).session();
        assertThat(session.isActive(T0.plus(TTL))).isFalse();

        session.revoke(T0, RevocationReason.LOGOUT);
        session.revoke(T0.plusSeconds(1), RevocationReason.TOKEN_REUSE_DETECTED);

        assertThat(session.isActive(T0)).isFalse();
        assertThat(session.getRevokedReason()).isEqualTo(RevocationReason.LOGOUT);
    }

    @Test
    void refreshTokenParsingRejectsMalformedValues() {
        RefreshToken token = RefreshToken.generate(java.util.UUID.randomUUID());

        assertThat(RefreshToken.parse(token.value())).contains(token);
        assertThat(RefreshToken.parse(null)).isEmpty();
        assertThat(RefreshToken.parse("sem-ponto")).isEmpty();
        assertThat(RefreshToken.parse("nao-uuid.segredo")).isEmpty();
        assertThat(RefreshToken.parse(token.sessionId() + ".")).isEmpty();
        assertThat(token.toString()).doesNotContain(token.secret());
    }
}
