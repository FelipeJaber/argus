package br.com.argus.audit;

public enum AuditAction {
    // autenticação
    LOGIN,
    LOGOUT,
    SESSION_REVOKED,
    REFRESH_TOKEN_REUSE,
    PASSWORD_CHANGED,
    // usuários
    USER_CREATED,
    USER_UPDATED,
    // ciclo de vida dos agentes
    AGENT_CREATED,
    AGENT_UPDATED,
    AGENT_SUBMITTED,
    AGENT_REVIEWED,
    AGENT_SUSPENDED,
    // uso
    AGENT_EXECUTION
}
