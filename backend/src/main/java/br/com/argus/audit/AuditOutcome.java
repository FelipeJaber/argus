package br.com.argus.audit;

/** Resultado exibido na tela de auditoria (Permitido, Bloqueado, Em análise, Homologado...). */
public enum AuditOutcome {
    ALLOWED,
    BLOCKED,
    DRAFT,
    IN_REVIEW,
    APPROVED,
    REJECTED,
    SUSPENDED
}
