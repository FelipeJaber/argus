package br.com.argus.agent;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.argus.agent.AgentDtos.AgentRequest;
import br.com.argus.agent.AgentDtos.AgentResponse;
import br.com.argus.agent.AgentDtos.AgentStats;
import br.com.argus.agent.AgentDtos.CatalogItem;
import br.com.argus.agent.AgentDtos.ReviewRequest;
import br.com.argus.agent.AgentDtos.ReviewResponse;
import br.com.argus.agent.AgentEnums.Category;
import br.com.argus.security.AuthenticatedUser;
import br.com.argus.shared.PageResponse;

@RestController
@RequestMapping("/api/v1/agents")
public class AgentController {

    private final AgentService service;

    public AgentController(AgentService service) {
        this.service = service;
    }

    /** Catálogo de agentes homologados (todos os perfis). */
    @GetMapping("/catalog")
    public List<CatalogItem> catalog(@RequestParam(required = false) Category category,
            @RequestParam(name = "q", required = false) String search) {
        return service.catalog(category, search);
    }

    @GetMapping("/{id}")
    public AgentResponse get(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser viewer) {
        return service.get(id, viewer);
    }

    // --- governança ---

    @GetMapping
    @PreAuthorize("hasRole('MANAGER')")
    public PageResponse<AgentResponse> list(@RequestParam(required = false) AgentStatus status,
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(status, pageable);
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('MANAGER')")
    public AgentStats stats() {
        return service.stats();
    }

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public AgentResponse create(@Valid @RequestBody AgentRequest body, @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.create(body, actor);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public AgentResponse update(@PathVariable UUID id, @Valid @RequestBody AgentRequest body,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.update(id, body, actor);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('MANAGER')")
    public AgentResponse submit(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.submit(id, actor);
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasRole('MANAGER')")
    public AgentResponse review(@PathVariable UUID id, @Valid @RequestBody ReviewRequest body,
            @AuthenticationPrincipal AuthenticatedUser reviewer) {
        return service.review(id, body, reviewer);
    }

    @GetMapping("/{id}/reviews")
    @PreAuthorize("hasRole('MANAGER')")
    public List<ReviewResponse> reviews(@PathVariable UUID id) {
        return service.reviews(id);
    }

    @PostMapping("/{id}/suspend")
    @PreAuthorize("hasRole('MANAGER')")
    public AgentResponse suspend(@PathVariable UUID id, @RequestBody(required = false) @Valid SuspendRequest body,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return service.suspend(id, body == null ? null : body.reason(), actor);
    }

    public record SuspendRequest(@Size(max = 255) String reason) {
    }
}
