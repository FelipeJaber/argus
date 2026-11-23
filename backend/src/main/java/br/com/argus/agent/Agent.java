package br.com.argus.agent;

import java.time.Instant;
import java.util.Objects;
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

import br.com.argus.agent.AgentEnums.Category;
import br.com.argus.agent.AgentEnums.DataClassification;
import br.com.argus.agent.AgentEnums.ToolAccess;
import br.com.argus.shared.ApiException;
import br.com.argus.shared.AuditableEntity;
import br.com.argus.user.User;

/**
 * Agente de IA cadastrado na organização. As transições de estado ficam aqui para que nenhuma
 * camada consiga, por exemplo, homologar um rascunho ou aprovar a própria submissão.
 */
@Entity
@Table(name = "agents")
public class Agent extends AuditableEntity {

    public static final String INITIAL_VERSION = "1.0";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false, length = 2000)
    private String purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Category category;

    @Column(length = 16)
    private String icon;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AgentStatus status;

    @Column(name = "release_version", nullable = false, length = 20)
    private String releaseVersion;

    @Column(length = 120)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_classification", nullable = false, length = 20)
    private DataClassification dataClassification;

    @Enumerated(EnumType.STRING)
    @Column(name = "tool_access", nullable = false, length = 40)
    private ToolAccess toolAccess;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false, updatable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_by_id")
    private User submittedBy;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    protected Agent() {
        // JPA
    }

    public Agent(AgentDetails details, User owner, User createdBy) {
        apply(details, owner);
        this.createdBy = Objects.requireNonNull(createdBy);
        this.status = AgentStatus.DRAFT;
    }

    public void update(AgentDetails details, User newOwner) {
        if (!status.isEditable()) {
            throw ApiException.conflict("AGENT_NOT_EDITABLE",
                    "Agentes em análise ou homologados não podem ser editados. Suspenda o agente para alterá-lo.");
        }
        apply(details, newOwner);
    }

    public void submit(User submitter, Instant now) {
        if (!status.canBeSubmitted()) {
            throw ApiException.conflict("AGENT_INVALID_TRANSITION",
                    "Somente rascunhos, agentes rejeitados ou suspensos podem ser enviados para homologação.");
        }
        this.status = AgentStatus.IN_REVIEW;
        this.submittedBy = Objects.requireNonNull(submitter);
        this.submittedAt = now;
    }

    public void approve(UUID reviewerId) {
        requireReviewable(reviewerId);
        this.status = AgentStatus.APPROVED;
    }

    public void reject(UUID reviewerId) {
        requireReviewable(reviewerId);
        this.status = AgentStatus.REJECTED;
    }

    public void suspend() {
        if (status != AgentStatus.APPROVED) {
            throw ApiException.conflict("AGENT_INVALID_TRANSITION", "Somente agentes homologados podem ser suspensos.");
        }
        this.status = AgentStatus.SUSPENDED;
    }

    public boolean isAvailable() {
        return status == AgentStatus.APPROVED;
    }

    /** Segregação de funções: quem submete não avalia a própria solicitação. */
    private void requireReviewable(UUID reviewerId) {
        if (status != AgentStatus.IN_REVIEW) {
            throw ApiException.conflict("AGENT_INVALID_TRANSITION", "O agente não está aguardando homologação.");
        }
        if (submittedBy != null && Objects.equals(submittedBy.getId(), reviewerId)) {
            throw ApiException.forbidden("SELF_REVIEW_NOT_ALLOWED",
                    "A pessoa que submeteu não pode aprovar ou rejeitar a própria solicitação.");
        }
    }

    private void apply(AgentDetails details, User newOwner) {
        this.name = details.name().trim();
        this.description = details.description().trim();
        this.purpose = details.purpose().trim();
        this.category = details.category();
        this.icon = details.icon();
        this.model = details.model();
        this.dataClassification = details.dataClassification();
        this.toolAccess = details.toolAccess();
        this.releaseVersion = details.releaseVersion() != null ? details.releaseVersion() : INITIAL_VERSION;
        this.owner = Objects.requireNonNull(newOwner);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getPurpose() {
        return purpose;
    }

    public Category getCategory() {
        return category;
    }

    public String getIcon() {
        return icon;
    }

    public AgentStatus getStatus() {
        return status;
    }

    public String getReleaseVersion() {
        return releaseVersion;
    }

    public String getModel() {
        return model;
    }

    public DataClassification getDataClassification() {
        return dataClassification;
    }

    public ToolAccess getToolAccess() {
        return toolAccess;
    }

    public User getOwner() {
        return owner;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public User getSubmittedBy() {
        return submittedBy;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    /** Dados editáveis do agente (cadastro e edição). */
    public record AgentDetails(
            String name,
            String description,
            String purpose,
            Category category,
            String icon,
            String model,
            DataClassification dataClassification,
            ToolAccess toolAccess,
            String releaseVersion) {
    }
}
