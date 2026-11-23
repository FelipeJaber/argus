package br.com.argus.config;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.com.argus.agent.Agent;
import br.com.argus.agent.Agent.AgentDetails;
import br.com.argus.agent.AgentEnums.Category;
import br.com.argus.agent.AgentEnums.DataClassification;
import br.com.argus.agent.AgentEnums.ToolAccess;
import br.com.argus.agent.AgentRepository;
import br.com.argus.user.Role;
import br.com.argus.user.User;
import br.com.argus.user.UserRepository;

/**
 * Dados fictícios do protótipo, carregados apenas no perfil {@code dev} e só se o banco estiver vazio.
 * Senha de todos os usuários: {@code argus.seed.password} (padrão no application-dev.yml).
 */
@Component
@Profile("dev")
class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);
    private static final String MODEL = "Modelo corporativo • demonstração";

    private final UserRepository users;
    private final AgentRepository agents;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final String password;

    DevDataSeeder(UserRepository users, AgentRepository agents, PasswordEncoder passwordEncoder, Clock clock,
            @Value("${argus.seed.password}") String password) {
        this.users = users;
        this.agents = agents;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.password = password;
    }

    /** Um hash por usuário: o BCrypt gera um salt novo a cada chamada, como num cadastro real. */
    private String hash() {
        return passwordEncoder.encode(password);
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        User breno = users.save(new User("Breno Cardoso Vieira", "breno@empresa.exemplo", hash(), Role.COLLABORATOR, "Comunicação"));
        User karen = users.save(new User("Karen Quézia Oliveira", "karen@empresa.exemplo", hash(), Role.MANAGER, "Governança"));
        User felipe = users.save(new User("Felipe Jaber Andrade", "felipe@empresa.exemplo", hash(), Role.ADMIN, "Tecnologia"));
        users.save(new User("Marya Laura Menezes", "marya@empresa.exemplo", hash(), Role.COLLABORATOR, "Comunicação"));

        Agent ti = new Agent(new AgentDetails("Suporte de TI",
                "Encontre orientações e procedimentos para resolver demandas técnicas do dia a dia.",
                "Orientar colaboradores com base nos procedimentos internos de TI.",
                Category.TECHNOLOGY, "⌘", MODEL, DataClassification.INTERNAL,
                ToolAccess.KNOWLEDGE_BASE_READ_ONLY, "1.2"), felipe, felipe);
        ti.submit(felipe, clock.instant());
        ti.approve(karen.getId());
        agents.save(ti);

        Agent comunicacao = new Agent(new AgentDetails("Comunicação corporativa",
                "Transforme suas ideias em comunicados, e-mails e mensagens profissionais.",
                "Apoiar a redação de comunicações profissionais.",
                Category.COMMUNICATION, "✎", MODEL, DataClassification.PUBLIC, ToolAccess.NONE, "1.0"),
                breno, felipe);
        comunicacao.submit(felipe, clock.instant());
        comunicacao.approve(karen.getId());
        agents.save(comunicacao);

        Agent rh = new Agent(new AgentDetails("Assistente de RH",
                "Consulte políticas de benefícios, férias e processos de gestão de pessoas.",
                "Consultar políticas internas de RH, sem dados pessoais de colaboradores.",
                Category.PEOPLE, "♧", MODEL, DataClassification.INTERNAL,
                ToolAccess.KNOWLEDGE_BASE_READ_ONLY, "1.0"), karen, felipe);
        rh.submit(felipe, clock.instant());
        agents.save(rh);

        log.info("Dados de demonstração criados: 4 usuários (@empresa.exemplo) e 3 agentes.");
    }
}
