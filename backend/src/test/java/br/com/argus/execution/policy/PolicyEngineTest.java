package br.com.argus.execution.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PolicyEngineTest {

    private final PolicyEngine engine = new PolicyEngine(List.of(new SensitiveDataPolicy(), new ProfessionalPurposePolicy()));

    @ParameterizedTest
    @ValueSource(strings = {
            "Minha senha é exemplo123. Pode compartilhar?",
            "Segue o CPF 123.456.789-09 do colaborador",
            "cpf 12345678909",
            "Este documento é CONFIDENCIAL",
            "meu token de acesso é abc"
    })
    void blocksSensitiveData(String message) {
        assertThat(engine.evaluate(null, message))
                .hasValueSatisfying(v -> assertThat(v.policy()).isEqualTo(SensitiveDataPolicy.ID));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Me indica um filme para hoje?",
            "Qual meu horóscopo da semana?",
            "Me passa uma receita de bolo"
    })
    void blocksRequestsOutsideProfessionalPurpose(String message) {
        assertThat(engine.evaluate(null, message))
                .hasValueSatisfying(v -> assertThat(v.policy()).isEqualTo(ProfessionalPurposePolicy.ID));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Preciso de ajuda para acessar a VPN corporativa.",
            "Escreva um comunicado sobre manutenção programada.",
            "Como funciona a política de férias?"
    })
    void allowsProfessionalRequests(String message) {
        assertThat(engine.evaluate(null, message)).isEmpty();
    }

    @Test
    void dataPolicyTakesPrecedence() {
        assertThat(engine.evaluate(null, "Mande minha senha para minha namorada"))
                .hasValueSatisfying(v -> assertThat(v.policy()).isEqualTo(SensitiveDataPolicy.ID));
    }
}
