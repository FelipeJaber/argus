package br.com.argus.audit;

import java.util.UUID;

/**
 * Descrição de um evento a registrar. Uso:
 * {@code audit.record(AuditEntry.of(AGENT_SUBMITTED, IN_REVIEW).by(id, nome).on(agentId, agentNome))}
 */
public record AuditEntry(
        AuditAction action,
        AuditOutcome outcome,
        UUID actorId,
        String actorName,
        UUID agentId,
        String agentName,
        String detail) {

    public static AuditEntry of(AuditAction action, AuditOutcome outcome) {
        return new AuditEntry(action, outcome, null, null, null, null, null);
    }

    public AuditEntry by(UUID id, String name) {
        return new AuditEntry(action, outcome, id, name, agentId, agentName, detail);
    }

    public AuditEntry on(UUID id, String name) {
        return new AuditEntry(action, outcome, actorId, actorName, id, name, detail);
    }

    public AuditEntry detail(String text) {
        return new AuditEntry(action, outcome, actorId, actorName, agentId, agentName, text);
    }
}
