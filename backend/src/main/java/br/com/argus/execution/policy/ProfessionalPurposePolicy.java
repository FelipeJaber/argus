package br.com.argus.execution.policy;

import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import br.com.argus.agent.Agent;

/**
 * Heurística simples para pedidos fora da finalidade profissional. Na evolução do projeto pode ser
 * substituída por um classificador, sem mudar o restante do fluxo.
 */
@Component
@Order(20)
public class ProfessionalPurposePolicy implements UsagePolicy {

    public static final String ID = "PURPOSE_POLICY";

    private static final Pattern OFF_TOPIC = Pattern.compile(
            "\\b(namorad[oa]|filme|s[ée]rie|receita de bolo|hor[óo]scopo|signo|aposta)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS);

    @Override
    public Optional<PolicyViolation> evaluate(Agent agent, String message) {
        if (OFF_TOPIC.matcher(message).find()) {
            return Optional.of(new PolicyViolation(ID,
                    "Esta solicitação parece estar fora da finalidade profissional deste agente. "
                            + "Reformule indicando a atividade de trabalho que deseja realizar."));
        }
        return Optional.empty();
    }
}
