package br.com.argus.execution;

import java.util.UUID;

import org.springframework.stereotype.Service;

import br.com.argus.agent.Agent;
import br.com.argus.agent.AgentRepository;
import br.com.argus.audit.AuditAction;
import br.com.argus.audit.AuditEntry;
import br.com.argus.audit.AuditOutcome;
import br.com.argus.audit.AuditService;
import br.com.argus.execution.gateway.AgentGateway;
import br.com.argus.execution.policy.PolicyEngine;
import br.com.argus.security.AuthenticatedUser;
import br.com.argus.shared.ApiException;

/**
 * Fluxo de uso de um agente: agente homologado → políticas de uso → provedor de IA → auditoria.
 * A auditoria registra apenas o resultado e a política aplicada, nunca o conteúdo da mensagem.
 * <p>
 * Propositalmente sem {@code @Transactional}: a chamada ao provedor pode levar segundos e não deve
 * segurar uma conexão de banco aberta.
 */
@Service
public class ExecutionService {

    private final AgentRepository agents;
    private final PolicyEngine policies;
    private final AgentGateway gateway;
    private final AuditService audit;

    public ExecutionService(AgentRepository agents, PolicyEngine policies, AgentGateway gateway, AuditService audit) {
        this.agents = agents;
        this.policies = policies;
        this.gateway = gateway;
        this.audit = audit;
    }

    public ExecutionResponse execute(UUID agentId, String message, AuthenticatedUser user) {
        Agent agent = agents.findById(agentId).orElseThrow(() -> ApiException.notFound("Agente"));
        if (!agent.isAvailable()) {
            throw ApiException.conflict("AGENT_UNAVAILABLE",
                    "Agente indisponível. Escolha um agente homologado no catálogo.");
        }

        ExecutionResponse response = policies.evaluate(agent, message)
                .map(ExecutionResponse::blocked)
                .orElseGet(() -> ExecutionResponse.allowed(gateway.reply(agent, message)));

        audit.record(AuditEntry.of(AuditAction.AGENT_EXECUTION,
                        response.allowed() ? AuditOutcome.ALLOWED : AuditOutcome.BLOCKED)
                .by(user.id(), user.name())
                .on(agent.getId(), agent.getName())
                .detail(response.policy()));
        return response;
    }
}
