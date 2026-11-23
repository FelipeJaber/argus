package br.com.argus.user;

/**
 * Perfis de acesso (público-alvo da identidade da marca).
 * Hierarquia: ADMIN > MANAGER > COLLABORATOR (configurada em {@code SecurityConfig#roleHierarchy}).
 */
public enum Role {
    /** Colaborador: encontra agentes homologados e os utiliza. */
    COLLABORATOR,
    /** Gestor de governança: cadastra, avalia e homologa agentes; consulta auditoria. */
    MANAGER,
    /** Administrador de TI: gerencia usuários, perfis e configurações. */
    ADMIN
}
