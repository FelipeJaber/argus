package br.com.argus.audit;

import java.time.Clock;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.argus.shared.PageResponse;

@Service
public class AuditService {

    private final AuditEventRepository events;
    private final Clock clock;

    public AuditService(AuditEventRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    /** Participa da transação de quem chama: se a operação de negócio falhar, o evento também não é gravado. */
    @Transactional
    public void record(AuditEntry entry) {
        events.save(new AuditEvent(clock.instant(), entry));
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEventResponse> search(AuditOutcome outcome, AuditAction action, UUID agentId,
            Pageable pageable) {
        return PageResponse.of(events.search(outcome, action, agentId, pageable), AuditEventResponse::from);
    }
}
