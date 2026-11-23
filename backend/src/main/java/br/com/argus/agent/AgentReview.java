package br.com.argus.agent;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

import br.com.argus.user.User;

/** Parecer de homologação (histórico imutável de decisões sobre cada versão do agente). */
@Entity
@Immutable
@Table(name = "agent_reviews")
public class AgentReview {

    public enum Decision {
        APPROVED,
        REJECTED
    }

    /** Critérios do checklist de homologação. Todos são obrigatórios para aprovar. */
    public enum ChecklistItem {
        PURPOSE_AND_LIMITS_DEFINED,
        DATA_SOURCES_COMPLIANT,
        LEAST_PRIVILEGE_PERMISSIONS,
        MISUSE_SCENARIOS_REVIEWED,
        OWNER_AND_VERSION_IDENTIFIED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private User reviewer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Decision decision;

    @Column(nullable = false, length = 1000)
    private String justification;

    @Column(name = "reviewed_version", nullable = false, length = 20)
    private String reviewedVersion;

    @Column(name = "checklist_complete", nullable = false)
    private boolean checklistComplete;

    @Column(name = "reviewed_at", nullable = false)
    private Instant reviewedAt;

    protected AgentReview() {
        // JPA
    }

    public AgentReview(Agent agent, User reviewer, Decision decision, String justification,
            boolean checklistComplete, Instant reviewedAt) {
        this.agent = agent;
        this.reviewer = reviewer;
        this.decision = decision;
        this.justification = justification.trim();
        this.reviewedVersion = agent.getReleaseVersion();
        this.checklistComplete = checklistComplete;
        this.reviewedAt = reviewedAt;
    }

    public UUID getId() {
        return id;
    }

    public Agent getAgent() {
        return agent;
    }

    public User getReviewer() {
        return reviewer;
    }

    public Decision getDecision() {
        return decision;
    }

    public String getJustification() {
        return justification;
    }

    public String getReviewedVersion() {
        return reviewedVersion;
    }

    public boolean isChecklistComplete() {
        return checklistComplete;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }
}
