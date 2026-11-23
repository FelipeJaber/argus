package br.com.argus.agent;

import java.time.Clock;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.argus.agent.AgentDtos.AgentRequest;
import br.com.argus.agent.AgentDtos.AgentResponse;
import br.com.argus.agent.AgentDtos.AgentStats;
import br.com.argus.agent.AgentDtos.CatalogItem;
import br.com.argus.agent.AgentDtos.ReviewRequest;
import br.com.argus.agent.AgentDtos.ReviewResponse;
import br.com.argus.agent.AgentEnums.Category;
import br.com.argus.agent.AgentReview.ChecklistItem;
import br.com.argus.agent.AgentReview.Decision;
import br.com.argus.audit.AuditAction;
import br.com.argus.audit.AuditEntry;
import br.com.argus.audit.AuditOutcome;
import br.com.argus.audit.AuditService;
import br.com.argus.security.AuthenticatedUser;
import br.com.argus.shared.ApiException;
import br.com.argus.shared.PageResponse;
import br.com.argus.user.Role;
import br.com.argus.user.User;
import br.com.argus.user.UserRepository;

@Service
public class AgentService {

    private final AgentRepository agents;
    private final AgentReviewRepository reviews;
    private final UserRepository users;
    private final AuditService audit;
    private final Clock clock;

    public AgentService(AgentRepository agents, AgentReviewRepository reviews, UserRepository users,
            AuditService audit, Clock clock) {
        this.agents = agents;
        this.reviews = reviews;
        this.users = users;
        this.audit = audit;
        this.clock = clock;
    }

    // --- consultas ---

    @Transactional(readOnly = true)
    public List<CatalogItem> catalog(Category category, String search) {
        String term = search == null || search.isBlank() ? null : "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
        return agents.catalog(category, term).stream().map(CatalogItem::from).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<AgentResponse> list(AgentStatus status, Pageable pageable) {
        return PageResponse.of(agents.search(status, pageable), AgentResponse::from);
    }

    /** Colaboradores só enxergam agentes homologados; os demais retornam 404 para não vazar cadastros internos. */
    @Transactional(readOnly = true)
    public AgentResponse get(UUID id, AuthenticatedUser viewer) {
        Agent agent = findDetailed(id);
        if (viewer.role() == Role.COLLABORATOR && !agent.isAvailable()) {
            throw ApiException.notFound("Agente");
        }
        return AgentResponse.from(agent);
    }

    @Transactional(readOnly = true)
    public AgentStats stats() {
        Map<AgentStatus, Long> byStatus = new EnumMap<>(AgentStatus.class);
        for (AgentStatus status : AgentStatus.values()) {
            byStatus.put(status, 0L);
        }
        agents.countByStatus().forEach(c -> byStatus.put(c.getStatus(), c.getTotal()));
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        return new AgentStats(total, byStatus);
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> reviews(UUID agentId) {
        findDetailed(agentId);
        return reviews.findByAgentIdOrderByReviewedAtDesc(agentId).stream().map(ReviewResponse::from).toList();
    }

    // --- ciclo de vida ---

    @Transactional
    public AgentResponse create(AgentRequest request, AuthenticatedUser actor) {
        User owner = findUser(request.ownerId());
        User creator = users.getReferenceById(actor.id());
        Agent agent = agents.save(new Agent(request.toDetails(), owner, creator));
        audit.record(entry(AuditAction.AGENT_CREATED, AuditOutcome.DRAFT, actor, agent));
        if (request.shouldSubmit()) {
            submit(agent, creator, actor);
        }
        return AgentResponse.from(agent);
    }

    @Transactional
    public AgentResponse update(UUID id, AgentRequest request, AuthenticatedUser actor) {
        Agent agent = findDetailed(id);
        agent.update(request.toDetails(), findUser(request.ownerId()));
        audit.record(entry(AuditAction.AGENT_UPDATED, AuditOutcome.DRAFT, actor, agent));
        if (request.shouldSubmit()) {
            submit(agent, users.getReferenceById(actor.id()), actor);
        }
        return AgentResponse.from(agent);
    }

    @Transactional
    public AgentResponse submit(UUID id, AuthenticatedUser actor) {
        Agent agent = findDetailed(id);
        submit(agent, users.getReferenceById(actor.id()), actor);
        return AgentResponse.from(agent);
    }

    /**
     * Homologação. Regras: o agente precisa estar em análise, o avaliador não pode ser quem submeteu,
     * a justificativa é obrigatória e a aprovação exige todos os itens do checklist.
     */
    @Transactional
    public AgentResponse review(UUID id, ReviewRequest request, AuthenticatedUser reviewer) {
        Agent agent = findDetailed(id);
        Set<ChecklistItem> checked = request.checklist() == null ? Set.of() : request.checklist();
        boolean checklistComplete = checked.containsAll(EnumSet.allOf(ChecklistItem.class));

        if (request.decision() == Decision.APPROVED) {
            if (!checklistComplete) {
                throw ApiException.unprocessable("CHECKLIST_INCOMPLETE",
                        "Confirme todos os critérios do checklist para aprovar o agente.");
            }
            agent.approve(reviewer.id());
        } else {
            agent.reject(reviewer.id());
        }

        reviews.save(new AgentReview(agent, users.getReferenceById(reviewer.id()), request.decision(),
                request.justification(), checklistComplete, clock.instant()));
        AuditOutcome outcome = request.decision() == Decision.APPROVED ? AuditOutcome.APPROVED : AuditOutcome.REJECTED;
        audit.record(entry(AuditAction.AGENT_REVIEWED, outcome, reviewer, agent)
                .detail("Versão " + agent.getReleaseVersion()));
        return AgentResponse.from(agent);
    }

    @Transactional
    public AgentResponse suspend(UUID id, String reason, AuthenticatedUser actor) {
        Agent agent = findDetailed(id);
        agent.suspend();
        audit.record(entry(AuditAction.AGENT_SUSPENDED, AuditOutcome.SUSPENDED, actor, agent).detail(reason));
        return AgentResponse.from(agent);
    }

    // --- apoio ---

    private void submit(Agent agent, User submitter, AuthenticatedUser actor) {
        agent.submit(submitter, clock.instant());
        audit.record(entry(AuditAction.AGENT_SUBMITTED, AuditOutcome.IN_REVIEW, actor, agent)
                .detail("Versão " + agent.getReleaseVersion()));
    }

    Agent findDetailed(UUID id) {
        return agents.findDetailedById(id).orElseThrow(() -> ApiException.notFound("Agente"));
    }

    private User findUser(UUID id) {
        return users.findById(id)
                .filter(User::isActive)
                .orElseThrow(() -> ApiException.unprocessable("OWNER_NOT_FOUND", "Responsável não encontrado ou inativo."));
    }

    private static AuditEntry entry(AuditAction action, AuditOutcome outcome, AuthenticatedUser actor, Agent agent) {
        return AuditEntry.of(action, outcome).by(actor.id(), actor.name()).on(agent.getId(), agent.getName());
    }
}
