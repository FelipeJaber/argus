package br.com.argus.audit;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    @Query("""
            select e from AuditEvent e
            where (:outcome is null or e.outcome = :outcome)
              and (:action is null or e.action = :action)
              and (:agentId is null or e.agentId = :agentId)
            """)
    Page<AuditEvent> search(@Param("outcome") AuditOutcome outcome,
            @Param("action") AuditAction action,
            @Param("agentId") UUID agentId,
            Pageable pageable);
}
