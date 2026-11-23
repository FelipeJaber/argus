package br.com.argus.security;

import java.util.UUID;

import br.com.argus.user.Role;

/**
 * Usuário autenticado extraído do access token. Use nos controllers com
 * {@code @AuthenticationPrincipal AuthenticatedUser user}.
 *
 * @param id        id do usuário (claim {@code sub})
 * @param sessionId sessão de login à qual o token pertence (claim {@code sid})
 */
public record AuthenticatedUser(UUID id, UUID sessionId, String name, Role role) {
}
