package br.com.argus.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Set;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import br.com.argus.auth.AuthSessionRepository;

/**
 * API stateless: cada requisição se autentica com {@code Authorization: Bearer <access token>}.
 * Login/refresh/logout são públicos e usam o cookie HttpOnly do refresh token.
 * Autorização por perfil é feita com {@code @PreAuthorize} nos controllers.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /** Endpoints de autenticação públicos (autenticados por credenciais ou pelo cookie de refresh). */
    static final Set<String> PUBLIC_AUTH_PATHS = Set.of(
            "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout");

    private static final String[] PUBLIC_GET_PATHS = {
            "/actuator/health", "/actuator/health/**", "/actuator/info",
            "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**"
    };

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, JwtDecoder jwtDecoder, ProblemDetailsSecurityHandler problems)
            throws Exception {
        http
                // Sem sessão HTTP nem cookie de autenticação nas rotas protegidas => CSRF não se aplica.
                // O cookie de refresh é SameSite=Strict e restrito a /api/v1/auth.
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, PUBLIC_AUTH_PATHS.toArray(String[]::new)).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_PATHS).permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .bearerTokenResolver(bearerTokenResolver())
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(new SessionJwtAuthenticationConverter()))
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems));
        return http.build();
    }

    /**
     * Ignora o header Authorization nos endpoints públicos de autenticação: um access token expirado
     * enviado pelo frontend não pode impedir o próprio refresh.
     */
    private static BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
        return request -> {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            return PUBLIC_AUTH_PATHS.contains(path) ? null : delegate.resolve(request);
        };
    }

    @Bean
    SecretKey jwtSigningKey(SecurityProperties properties) {
        return new SecretKeySpec(properties.jwt().secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return NimbusJwtEncoder.withSecretKey(jwtSigningKey).algorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSigningKey, SecurityProperties properties, AuthSessionRepository sessions,
            Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.jwt().issuer()),
                new ActiveSessionValidator(sessions, clock)));
        return decoder;
    }

    /** BCrypt por padrão, com prefixo {@code {bcrypt}} para permitir migrar de algoritmo no futuro. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role("ADMIN").implies("MANAGER")
                .role("MANAGER").implies("COLLABORATOR")
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.cors().allowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cors.setAllowCredentials(true); // necessário para o cookie de refresh
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }
}
