package br.com.argus.config;

import java.time.Clock;
import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Relógio único da aplicação. Todo código que precisa de "agora" recebe o {@link Clock}
 * por injeção, o que torna regras de expiração testáveis.
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class TimeConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(clock.instant());
    }
}
