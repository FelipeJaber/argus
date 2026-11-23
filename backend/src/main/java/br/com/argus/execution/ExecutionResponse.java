package br.com.argus.execution;

import br.com.argus.execution.policy.PolicyViolation;

/**
 * @param allowed {@code false} quando uma política bloqueou a mensagem
 * @param policy  política que bloqueou (nulo quando permitido)
 * @param reply   resposta do agente ou orientação da política
 */
public record ExecutionResponse(boolean allowed, String policy, String reply) {

    static ExecutionResponse allowed(String reply) {
        return new ExecutionResponse(true, null, reply);
    }

    static ExecutionResponse blocked(PolicyViolation violation) {
        return new ExecutionResponse(false, violation.policy(), violation.message());
    }
}
