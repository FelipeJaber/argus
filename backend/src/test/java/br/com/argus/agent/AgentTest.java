package br.com.argus.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.argus.agent.Agent.AgentDetails;
import br.com.argus.agent.AgentEnums.Category;
import br.com.argus.agent.AgentEnums.DataClassification;
import br.com.argus.agent.AgentEnums.ToolAccess;
import br.com.argus.shared.ApiException;
import br.com.argus.user.Role;
import br.com.argus.user.User;

class AgentTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    private final User submitter = user("Submissor");
    private final User reviewer = user("Avaliador");

    @Test
    void newAgentStartsAsDraftWithInitialVersion() {
        Agent agent = newAgent();

        assertThat(agent.getStatus()).isEqualTo(AgentStatus.DRAFT);
        assertThat(agent.getReleaseVersion()).isEqualTo(Agent.INITIAL_VERSION);
        assertThat(agent.isAvailable()).isFalse();
    }

    @Test
    void happyPathDraftToApprovedToSuspended() {
        Agent agent = newAgent();

        agent.submit(submitter, NOW);
        assertThat(agent.getStatus()).isEqualTo(AgentStatus.IN_REVIEW);

        agent.approve(reviewer.getId());
        assertThat(agent.isAvailable()).isTrue();

        agent.suspend();
        assertThat(agent.getStatus()).isEqualTo(AgentStatus.SUSPENDED);
        assertThat(agent.isAvailable()).isFalse();
    }

    @Test
    void submitterCannotReviewOwnSubmission() {
        Agent agent = newAgent();
        agent.submit(submitter, NOW);

        assertThatThrownBy(() -> agent.approve(submitter.getId()))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("SELF_REVIEW_NOT_ALLOWED");
        assertThatThrownBy(() -> agent.reject(submitter.getId()))
                .isInstanceOf(ApiException.class);
        assertThat(agent.getStatus()).isEqualTo(AgentStatus.IN_REVIEW);
    }

    @Test
    void cannotApproveDraftOrSuspendUnapprovedAgent() {
        Agent agent = newAgent();

        assertThatThrownBy(() -> agent.approve(reviewer.getId())).isInstanceOf(ApiException.class);
        assertThatThrownBy(agent::suspend).isInstanceOf(ApiException.class);
    }

    @Test
    void agentUnderReviewOrApprovedCannotBeEdited() {
        Agent agent = newAgent();
        agent.submit(submitter, NOW);

        assertThatThrownBy(() -> agent.update(details("Outro nome"), submitter))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("AGENT_NOT_EDITABLE");
    }

    @Test
    void rejectedAgentCanBeEditedAndResubmitted() {
        Agent agent = newAgent();
        agent.submit(submitter, NOW);
        agent.reject(reviewer.getId());

        agent.update(details("Nome ajustado"), submitter);
        agent.submit(submitter, NOW);

        assertThat(agent.getName()).isEqualTo("Nome ajustado");
        assertThat(agent.getStatus()).isEqualTo(AgentStatus.IN_REVIEW);
    }

    private Agent newAgent() {
        return new Agent(details("Assistente"), submitter, submitter);
    }

    private static AgentDetails details(String name) {
        return new AgentDetails(name, "Descrição", "Finalidade", Category.TECHNOLOGY, null, null,
                DataClassification.INTERNAL, ToolAccess.NONE, null);
    }

    private static User user(String name) {
        User user = new User(name, name.toLowerCase() + "@teste.local", "{noop}x", Role.MANAGER, null);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }
}
