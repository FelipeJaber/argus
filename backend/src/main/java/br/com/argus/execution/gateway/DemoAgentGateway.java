package br.com.argus.execution.gateway;

import org.springframework.stereotype.Component;

import br.com.argus.agent.Agent;

/** Respostas demonstrativas predefinidas (sem conexão com um modelo de IA), como no protótipo. */
@Component
public class DemoAgentGateway implements AgentGateway {

    @Override
    public String reply(Agent agent, String message) {
        return switch (agent.getCategory()) {
            case TECHNOLOGY -> """
                    Exemplo de orientação de TI:
                    1. Confirme sua conexão com a internet.
                    2. Abra o cliente VPN aprovado pela empresa.
                    3. Utilize seu acesso corporativo e autenticação adicional.
                    4. Se o erro persistir, abra um chamado com a mensagem de erro, sem incluir senhas.

                    Fonte demonstrativa: Procedimentos internos • TI-004.""";
            case PEOPLE -> """
                    Exemplo de orientação de RH:
                    Consulte a política interna de férias e alinhe o período pretendido com sua liderança. \
                    Nesta demonstração, nenhuma informação individual de colaboradores é acessada.""";
            case COMMUNICATION -> """
                    Exemplo de rascunho:

                    Olá, equipe!
                    Informamos que haverá uma manutenção programada nos sistemas corporativos. Confirmaremos a data \
                    e o período de indisponibilidade antes da execução. Em caso de dúvidas, entre em contato com o \
                    suporte de TI.

                    Agradecemos a compreensão.

                    Revise e adapte as informações antes de utilizar.""";
        };
    }
}
