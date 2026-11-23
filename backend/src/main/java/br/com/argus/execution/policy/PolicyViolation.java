package br.com.argus.execution.policy;

/**
 * @param policy  identificador estável da política (vai para a auditoria)
 * @param message orientação ao usuário, no tom de voz da marca: diz o que aconteceu e o próximo passo
 */
public record PolicyViolation(String policy, String message) {
}
