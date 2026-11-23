package br.com.argus.agent;

/** Enumerações de cadastro do agente. */
public final class AgentEnums {

    private AgentEnums() {
    }

    /** Área de atuação (filtros do catálogo). */
    public enum Category {
        TECHNOLOGY,
        COMMUNICATION,
        PEOPLE
    }

    /** Classificação máxima de dados que o agente pode receber. */
    public enum DataClassification {
        PUBLIC,
        INTERNAL,
        CONFIDENTIAL
    }

    /** Ferramentas que o agente pode acionar (princípio do menor privilégio). */
    public enum ToolAccess {
        NONE,
        KNOWLEDGE_BASE_READ_ONLY
    }
}
