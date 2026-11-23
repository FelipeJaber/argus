package br.com.argus.agent;

/**
 * Ciclo de vida de um agente:
 *
 * <pre>
 * DRAFT ──submit──▶ IN_REVIEW ──approve──▶ APPROVED ──suspend──▶ SUSPENDED
 *   ▲                   │                                          │
 *   │                reject                                        │
 *   │                   ▼                                          │
 *   └──(editar)──── REJECTED ◀──────────(editar e submeter)────────┘
 * </pre>
 *
 * Somente agentes {@link #APPROVED} (homologados) aparecem no catálogo e podem ser executados.
 */
public enum AgentStatus {
    /** Rascunho. */
    DRAFT,
    /** Em análise pela governança. */
    IN_REVIEW,
    /** Homologado: disponível no catálogo. */
    APPROVED,
    /** Rejeitado: precisa de ajustes antes de nova submissão. */
    REJECTED,
    /** Suspenso: novas execuções bloqueadas. */
    SUSPENDED;

    public boolean isEditable() {
        return this == DRAFT || this == REJECTED || this == SUSPENDED;
    }

    public boolean canBeSubmitted() {
        return isEditable();
    }
}
