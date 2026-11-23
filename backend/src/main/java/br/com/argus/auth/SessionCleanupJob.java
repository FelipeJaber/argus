package br.com.argus.auth;

import java.time.Clock;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Remove diariamente sessões expiradas ou revogadas há mais de 30 dias. */
@Component
class SessionCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(SessionCleanupJob.class);
    private static final Duration RETENTION = Duration.ofDays(30);

    private final AuthSessionRepository sessions;
    private final Clock clock;

    SessionCleanupJob(AuthSessionRepository sessions, Clock clock) {
        this.sessions = sessions;
        this.clock = clock;
    }

    @Scheduled(cron = "${argus.security.session-cleanup-cron:0 30 3 * * *}")
    @Transactional
    void purgeStaleSessions() {
        int removed = sessions.deleteStaleBefore(clock.instant().minus(RETENTION));
        if (removed > 0) {
            log.info("Removidas {} sessões antigas", removed);
        }
    }
}
