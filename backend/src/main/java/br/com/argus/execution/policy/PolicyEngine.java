package br.com.argus.execution.policy;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import br.com.argus.agent.Agent;

/** Avalia as políticas na ordem de {@code @Order} e para na primeira violação. */
@Component
public class PolicyEngine {

    private final List<UsagePolicy> policies;

    public PolicyEngine(List<UsagePolicy> policies) {
        this.policies = List.copyOf(policies);
    }

    public Optional<PolicyViolation> evaluate(Agent agent, String message) {
        for (UsagePolicy policy : policies) {
            Optional<PolicyViolation> violation = policy.evaluate(agent, message);
            if (violation.isPresent()) {
                return violation;
            }
        }
        return Optional.empty();
    }
}
