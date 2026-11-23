package br.com.argus.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Libera o console web do H2 ({@code /h2-console}) quando ele está habilitado, o que só acontece no perfil
 * {@code dev}. O próprio H2 recusa acessos que não venham do computador local ({@code web-allow-others=false})
 * e pede as credenciais JDBC do banco. Em produção a propriedade fica desligada e esta cadeia nem existe.
 */
@Configuration
@ConditionalOnProperty(name = "spring.h2.console.enabled", havingValue = "true")
class H2ConsoleSecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(H2ConsoleSecurityConfig.class);

    @Bean
    @Order(1) // antes da cadeia da API, que exige token em qualquer outra rota
    SecurityFilterChain h2ConsoleSecurity(HttpSecurity http,
            @Value("${spring.h2.console.path:/h2-console}") String path) throws Exception {
        log.warn("Console do H2 habilitado em {} (apenas para desenvolvimento/demonstração).", path);
        http
                .securityMatcher(path + "/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                // O console usa formulários próprios (sem token CSRF) e é montado em frames da mesma origem.
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        return http.build();
    }
}
