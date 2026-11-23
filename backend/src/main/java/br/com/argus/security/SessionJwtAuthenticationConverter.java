package br.com.argus.security;

import java.util.List;
import java.util.UUID;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import br.com.argus.user.Role;

/**
 * Converte o JWT validado em uma autenticação cujo principal é {@link AuthenticatedUser}.
 */
public class SessionJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Role role = Role.valueOf(jwt.getClaimAsString(AccessTokenService.CLAIM_ROLE));
        AuthenticatedUser user = new AuthenticatedUser(
                UUID.fromString(jwt.getSubject()),
                UUID.fromString(jwt.getClaimAsString(AccessTokenService.CLAIM_SESSION_ID)),
                jwt.getClaimAsString(AccessTokenService.CLAIM_NAME),
                role);
        return new Authentication(user, jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }

    static final class Authentication extends AbstractAuthenticationToken {

        private final AuthenticatedUser principal;
        private final Jwt jwt;

        Authentication(AuthenticatedUser principal, Jwt jwt, List<SimpleGrantedAuthority> authorities) {
            super(authorities);
            this.principal = principal;
            this.jwt = jwt;
            setAuthenticated(true);
        }

        @Override
        public AuthenticatedUser getPrincipal() {
            return principal;
        }

        @Override
        public Object getCredentials() {
            return jwt.getTokenValue();
        }

        @Override
        public String getName() {
            return principal.id().toString();
        }
    }
}
