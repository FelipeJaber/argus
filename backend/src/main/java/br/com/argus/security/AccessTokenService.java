package br.com.argus.security;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import br.com.argus.user.User;

/**
 * Emite o access token (JWT HS256, curta duração). O claim {@code sid} amarra o token à sessão
 * no servidor, o que permite revogação imediata (logout, desativação de usuário, roubo de token).
 */
@Service
public class AccessTokenService {

    public static final String CLAIM_SESSION_ID = "sid";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_NAME = "name";

    private final JwtEncoder encoder;
    private final SecurityProperties properties;
    private final Clock clock;

    public AccessTokenService(JwtEncoder encoder, SecurityProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedAccessToken issue(User user, UUID sessionId) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.jwt().accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.jwt().issuer())
                .subject(user.getId().toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(CLAIM_SESSION_ID, sessionId.toString())
                .claim(CLAIM_ROLE, user.getRole().name())
                .claim(CLAIM_NAME, user.getName())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedAccessToken(token, expiresAt, properties.jwt().accessTokenTtl().toSeconds());
    }

    /** @param expiresIn segundos de validade a partir da emissão */
    public record IssuedAccessToken(String value, Instant expiresAt, long expiresIn) {
    }
}
