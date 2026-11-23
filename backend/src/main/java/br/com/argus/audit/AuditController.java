package br.com.argus.audit;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.argus.shared.PageResponse;

@RestController
@RequestMapping("/api/v1/audit-events")
@PreAuthorize("hasRole('MANAGER')")
public class AuditController {

    private final AuditService audit;

    public AuditController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping
    public PageResponse<AuditEventResponse> search(
            @RequestParam(required = false) AuditOutcome outcome,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) UUID agentId,
            @PageableDefault(size = 20, sort = "occurredAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return audit.search(outcome, action, agentId, pageable);
    }
}
