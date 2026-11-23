package br.com.argus.agent;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import br.com.argus.agent.Agent.AgentDetails;
import br.com.argus.agent.AgentEnums.Category;
import br.com.argus.agent.AgentEnums.DataClassification;
import br.com.argus.agent.AgentEnums.ToolAccess;
import br.com.argus.agent.AgentReview.ChecklistItem;
import br.com.argus.agent.AgentReview.Decision;
import br.com.argus.user.User;

/** DTOs do módulo de agentes. */
public final class AgentDtos {

    private AgentDtos() {
    }

    /**
     * Cadastro/edição. {@code submitForReview=true} equivale ao botão "Enviar para homologação";
     * {@code false} ou ausente, a "Salvar rascunho".
     */
    public record AgentRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 500) String description,
            @NotBlank @Size(max = 2000) String purpose,
            @NotNull Category category,
            @NotNull UUID ownerId,
            @Size(max = 120) String model,
            @NotNull DataClassification dataClassification,
            @NotNull ToolAccess toolAccess,
            @Pattern(regexp = "\\d{1,3}\\.\\d{1,3}(\\.\\d{1,3})?", message = "Use o formato 1.0 ou 1.0.0")
            String releaseVersion,
            @Size(max = 16) String icon,
            Boolean submitForReview) {

        boolean shouldSubmit() {
            return Boolean.TRUE.equals(submitForReview);
        }

        AgentDetails toDetails() {
            return new AgentDetails(name, description, purpose, category, icon, model, dataClassification,
                    toolAccess, releaseVersion);
        }
    }

    public record ReviewRequest(
            @NotNull Decision decision,
            @NotBlank @Size(max = 1000) String justification,
            Set<ChecklistItem> checklist) {
    }

    public record UserRef(UUID id, String name) {
        static UserRef of(User user) {
            return user == null ? null : new UserRef(user.getId(), user.getName());
        }
    }

    public record AgentResponse(
            UUID id,
            String name,
            String description,
            String purpose,
            Category category,
            String icon,
            AgentStatus status,
            String releaseVersion,
            String model,
            DataClassification dataClassification,
            ToolAccess toolAccess,
            UserRef owner,
            UserRef submittedBy,
            Instant submittedAt,
            Instant createdAt,
            Instant updatedAt) {

        static AgentResponse from(Agent a) {
            return new AgentResponse(a.getId(), a.getName(), a.getDescription(), a.getPurpose(), a.getCategory(),
                    a.getIcon(), a.getStatus(), a.getReleaseVersion(), a.getModel(), a.getDataClassification(),
                    a.getToolAccess(), UserRef.of(a.getOwner()), UserRef.of(a.getSubmittedBy()), a.getSubmittedAt(),
                    a.getCreatedAt(), a.getUpdatedAt());
        }
    }

    /** Card do catálogo (visão do colaborador). */
    public record CatalogItem(
            UUID id,
            String name,
            String description,
            String purpose,
            Category category,
            String icon,
            String releaseVersion,
            String ownerName) {

        static CatalogItem from(Agent a) {
            return new CatalogItem(a.getId(), a.getName(), a.getDescription(), a.getPurpose(), a.getCategory(),
                    a.getIcon(), a.getReleaseVersion(), a.getOwner().getName());
        }
    }

    public record ReviewResponse(
            UUID id,
            Decision decision,
            String justification,
            String reviewedVersion,
            boolean checklistComplete,
            UserRef reviewer,
            Instant reviewedAt) {

        static ReviewResponse from(AgentReview r) {
            return new ReviewResponse(r.getId(), r.getDecision(), r.getJustification(), r.getReviewedVersion(),
                    r.isChecklistComplete(), UserRef.of(r.getReviewer()), r.getReviewedAt());
        }
    }

    /** Indicadores da tela "Gestão de agentes". */
    public record AgentStats(long total, Map<AgentStatus, Long> byStatus) {
    }
}
