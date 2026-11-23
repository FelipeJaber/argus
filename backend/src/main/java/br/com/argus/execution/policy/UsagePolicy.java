package br.com.argus.execution.policy;

import java.util.Optional;

import br.com.argus.agent.Agent;

/**
 * Política avaliada antes de qualquer mensagem chegar ao provedor de IA.
 * Para adicionar uma política nova basta criar um {@code @Component} que implemente esta interface
 * ({@code @Order} define a precedência).
 */
public interface UsagePolicy {

    Optional<PolicyViolation> evaluate(Agent agent, String message);
}
