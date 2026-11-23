package br.com.argus.agent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.argus.agent.AgentEnums.Category;

public interface AgentRepository extends JpaRepository<Agent, UUID> {

    @EntityGraph(attributePaths = {"owner", "submittedBy"})
    Optional<Agent> findDetailedById(UUID id);

    @EntityGraph(attributePaths = {"owner", "submittedBy"})
    @Query("select a from Agent a where (:status is null or a.status = :status)")
    Page<Agent> search(@Param("status") AgentStatus status, Pageable pageable);

    /** Catálogo: somente homologados, com filtro opcional por área e texto (nome/descrição). */
    @EntityGraph(attributePaths = "owner")
    @Query("""
            select a from Agent a
            where a.status = br.com.argus.agent.AgentStatus.APPROVED
              and (:category is null or a.category = :category)
              and (:term is null or lower(a.name) like :term or lower(a.description) like :term)
            order by a.name
            """)
    List<Agent> catalog(@Param("category") Category category, @Param("term") String term);

    @Query("select a.status as status, count(a) as total from Agent a group by a.status")
    List<StatusCount> countByStatus();

    interface StatusCount {
        AgentStatus getStatus();

        long getTotal();
    }
}
