package br.com.argus.execution.policy;

import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import br.com.argus.agent.Agent;

/** Bloqueia credenciais, documentos pessoais e conteúdo marcado como confidencial. */
@Component
@Order(10)
public class SensitiveDataPolicy implements UsagePolicy {

    public static final String ID = "DATA_POLICY";

    private static final Pattern SENSITIVE_TERMS = Pattern.compile(
            "\\b(senha|password|cpf|segredo|token|confidencial|api[ _-]?key)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS);

    /** CPF com ou sem pontuação (000.000.000-00 ou 00000000000). */
    private static final Pattern CPF = Pattern.compile("\\b\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}\\b");

    @Override
    public Optional<PolicyViolation> evaluate(Agent agent, String message) {
        if (SENSITIVE_TERMS.matcher(message).find() || CPF.matcher(message).find()) {
            return Optional.of(new PolicyViolation(ID,
                    "Solicitação bloqueada pela política de dados. Remova credenciais ou informações sensíveis "
                            + "e reformule sua mensagem. Nenhum conteúdo foi enviado a um provedor."));
        }
        return Optional.empty();
    }
}
