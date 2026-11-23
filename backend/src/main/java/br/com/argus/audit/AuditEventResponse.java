package br.com.argus.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditEventResponse(
        UUID id,
        Instant occurredAt,
        AuditAction action,
        AuditOutcome outcome,
        UUID actorId,
        String actorName,
        UUID agentId,
        String agentName,
        String detail) {

    static AuditEventResponse from(AuditEvent e) {
        return new AuditEventResponse(e.getId(), e.getOccurredAt(), e.getAction(), e.getOutcome(),
                e.getActorId(), e.getActorName(), e.getAgentId(), e.getAgentName(), e.getDetail());
    }
}
