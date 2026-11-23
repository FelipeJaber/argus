package br.com.argus.agent;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentReviewRepository extends JpaRepository<AgentReview, UUID> {

    @EntityGraph(attributePaths = "reviewer")
    List<AgentReview> findByAgentIdOrderByReviewedAtDesc(UUID agentId);
}
