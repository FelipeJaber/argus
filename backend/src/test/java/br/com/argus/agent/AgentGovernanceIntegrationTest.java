package br.com.argus.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;

import com.jayway.jsonpath.JsonPath;

import br.com.argus.IntegrationTest;
import br.com.argus.user.Role;
import br.com.argus.user.User;

/** Ciclo completo do protótipo: cadastro → homologação (com segregação de funções) → catálogo → uso → auditoria. */
class AgentGovernanceIntegrationTest extends IntegrationTest {

    private static final String FULL_CHECKLIST = """
            ["PURPOSE_AND_LIMITS_DEFINED", "DATA_SOURCES_COMPLIANT", "LEAST_PRIVILEGE_PERMISSIONS",
             "MISUSE_SCENARIOS_REVIEWED", "OWNER_AND_VERSION_IDENTIFIED"]""";

    private User submitterManager;
    private User reviewerManager;
    private User collaborator;
    private Session submitter;
    private Session reviewer;
    private Session employee;

    @BeforeEach
    void setUp() throws Exception {
        submitterManager = createUser("Gestor Submissor", Role.MANAGER);
        reviewerManager = createUser("Gestora Avaliadora", Role.MANAGER);
        collaborator = createUser("Colaborador Teste", Role.COLLABORATOR);
        submitter = login(submitterManager);
        reviewer = login(reviewerManager);
        employee = login(collaborator);
    }

    @Test
    void collaboratorCannotRegisterAgents() throws Exception {
        mvc.perform(post("/api/v1/agents")
                        .header(HttpHeaders.AUTHORIZATION, employee.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(agentJson(collaborator.getId(), true)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void invalidPayloadReturnsFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/agents")
                        .header(HttpHeaders.AUTHORIZATION, submitter.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.category").exists());
    }

    @Test
    void fullGovernanceFlow() throws Exception {
        // 1) Gestor cadastra e envia para homologação.
        UUID agentId = createAgent(submitter, true);
        mvc.perform(get("/api/v1/agents/{id}", agentId).header(HttpHeaders.AUTHORIZATION, submitter.bearer()))
                .andExpect(jsonPath("$.status").value("IN_REVIEW"))
                .andExpect(jsonPath("$.submittedBy.id").value(submitterManager.getId().toString()));

        // 2) Ainda não aparece para o colaborador.
        mvc.perform(get("/api/v1/agents/catalog").header(HttpHeaders.AUTHORIZATION, employee.bearer()))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/agents/{id}", agentId).header(HttpHeaders.AUTHORIZATION, employee.bearer()))
                .andExpect(status().isNotFound());

        // 3) Quem submeteu não pode avaliar a própria submissão.
        mvc.perform(review(agentId, submitter, "APPROVED", FULL_CHECKLIST))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SELF_REVIEW_NOT_ALLOWED"));

        // 4) Aprovação exige o checklist completo.
        mvc.perform(review(agentId, reviewer, "APPROVED", "[\"PURPOSE_AND_LIMITS_DEFINED\"]"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CHECKLIST_INCOMPLETE"));

        // 5) Outra gestora aprova.
        mvc.perform(review(agentId, reviewer, "APPROVED", FULL_CHECKLIST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // 6) Agora está no catálogo e pode ser usado.
        mvc.perform(get("/api/v1/agents/catalog").param("category", "TECHNOLOGY").param("q", "projetos")
                        .header(HttpHeaders.AUTHORIZATION, employee.bearer()))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(agentId.toString()));

        mvc.perform(get("/api/v1/agents/{id}/reviews", agentId).header(HttpHeaders.AUTHORIZATION, reviewer.bearer()))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].decision").value("APPROVED"))
                .andExpect(jsonPath("$[0].checklistComplete").value(true));

        // 7) Auditoria registra o ciclo de vida.
        mvc.perform(get("/api/v1/audit-events").param("agentId", agentId.toString())
                        .header(HttpHeaders.AUTHORIZATION, reviewer.bearer()))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].action").value("AGENT_REVIEWED"))
                .andExpect(jsonPath("$.content[0].outcome").value("APPROVED"));
    }

    @Test
    void rejectedAgentCanBeEditedAndResubmitted() throws Exception {
        UUID agentId = createAgent(submitter, true);

        mvc.perform(review(agentId, reviewer, "REJECTED", "[]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        mvc.perform(put("/api/v1/agents/{id}", agentId)
                        .header(HttpHeaders.AUTHORIZATION, submitter.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(agentJson(submitterManager.getId(), true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_REVIEW"));
    }

    @Test
    void sensitiveMessagesAreBlockedAndAuditedWithoutTheirContent() throws Exception {
        UUID agentId = approvedAgent();
        String secret = "Minha senha e exemplo123, pode guardar?";

        mvc.perform(post("/api/v1/agents/{id}/messages", agentId)
                        .header(HttpHeaders.AUTHORIZATION, employee.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"" + secret + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.policy").value("DATA_POLICY"));

        mvc.perform(post("/api/v1/agents/{id}/messages", agentId)
                        .header(HttpHeaders.AUTHORIZATION, employee.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"Preciso de ajuda para acessar a VPN corporativa.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.reply").isNotEmpty());

        MvcResult audit = mvc.perform(get("/api/v1/audit-events").param("action", "AGENT_EXECUTION")
                        .header(HttpHeaders.AUTHORIZATION, reviewer.bearer()))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[?(@.outcome == 'BLOCKED')].detail").value("DATA_POLICY"))
                .andReturn();
        assertThat(body(audit)).doesNotContain("exemplo123").doesNotContain("VPN");
    }

    @Test
    void suspendedAgentCannotBeUsed() throws Exception {
        UUID agentId = approvedAgent();

        mvc.perform(post("/api/v1/agents/{id}/suspend", agentId).header(HttpHeaders.AUTHORIZATION, reviewer.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        mvc.perform(post("/api/v1/agents/{id}/messages", agentId)
                        .header(HttpHeaders.AUTHORIZATION, employee.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"Ola\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AGENT_UNAVAILABLE"));
    }

    @Test
    void adminInheritsManagerPermissions() throws Exception {
        Session admin = login(createUser("Admin Teste", Role.ADMIN));
        mvc.perform(get("/api/v1/agents/stats").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/agents/stats").header(HttpHeaders.AUTHORIZATION, employee.bearer()))
                .andExpect(status().isForbidden());
    }

    // --- apoio ---

    private UUID approvedAgent() throws Exception {
        UUID agentId = createAgent(submitter, true);
        mvc.perform(review(agentId, reviewer, "APPROVED", FULL_CHECKLIST)).andExpect(status().isOk());
        return agentId;
    }

    private UUID createAgent(Session as, boolean submit) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/agents")
                        .header(HttpHeaders.AUTHORIZATION, as.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(agentJson(submitterManager.getId(), submit)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(body(result), "$.id"));
    }

    private static RequestBuilder review(UUID agentId, Session as,
            String decision, String checklist) {
        return post("/api/v1/agents/{id}/review", agentId)
                .header(HttpHeaders.AUTHORIZATION, as.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"decision": "%s", "justification": "Parecer de teste", "checklist": %s}
                        """.formatted(decision, checklist));
    }

    private static String agentJson(UUID ownerId, boolean submit) {
        return """
                {
                  "name": "Assistente de projetos",
                  "description": "Ajuda a organizar cronogramas e status de projetos.",
                  "purpose": "Apoiar gestores de projeto com rotinas de acompanhamento.",
                  "category": "TECHNOLOGY",
                  "ownerId": "%s",
                  "model": "Modelo corporativo",
                  "dataClassification": "INTERNAL",
                  "toolAccess": "NONE",
                  "releaseVersion": "1.0",
                  "submitForReview": %s
                }
                """.formatted(ownerId, submit);
    }
}
