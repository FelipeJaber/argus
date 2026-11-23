package br.com.argus.execution.gateway;

import br.com.argus.agent.Agent;

/**
 * Porta de saída para o provedor de IA. Só é chamada depois que todas as políticas aprovaram a mensagem.
 * Para integrar um modelo real, crie outra implementação e remova (ou condicione) a {@link DemoAgentGateway}.
 */
public interface AgentGateway {

    String reply(Agent agent, String message);
}
