package br.com.argus.audit;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

/**
 * Evento de auditoria imutável. Guarda apenas metadados (quem, o quê, quando, resultado):
 * o conteúdo das mensagens enviadas aos agentes nunca é persistido.
 * Nomes são copiados no momento do evento para que o histórico não mude se o cadastro mudar.
 */
@Entity
@Immutable
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuditOutcome outcome;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_name", length = 120)
    private String actorName;

    @Column(name = "agent_id")
    private UUID agentId;

    @Column(name = "agent_name", length = 120)
    private String agentName;

    @Column(length = 255)
    private String detail;

    protected AuditEvent() {
        // JPA
    }

    AuditEvent(Instant occurredAt, AuditEntry entry) {
        this.occurredAt = occurredAt;
        this.action = entry.action();
        this.outcome = entry.outcome();
        this.actorId = entry.actorId();
        this.actorName = entry.actorName();
        this.agentId = entry.agentId();
        this.agentName = entry.agentName();
        this.detail = entry.detail() == null || entry.detail().length() <= 255
                ? entry.detail()
                : entry.detail().substring(0, 255);
    }

    public UUID getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public AuditAction getAction() {
        return action;
    }

    public AuditOutcome getOutcome() {
        return outcome;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getActorName() {
        return actorName;
    }

    public UUID getAgentId() {
        return agentId;
    }

    public String getAgentName() {
        return agentName;
    }

    public String getDetail() {
        return detail;
    }
}
