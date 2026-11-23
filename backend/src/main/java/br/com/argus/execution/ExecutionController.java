package br.com.argus.execution;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import br.com.argus.security.AuthenticatedUser;

@RestController
public class ExecutionController {

    private final ExecutionService service;

    public ExecutionController(ExecutionService service) {
        this.service = service;
    }

    /** Envia uma mensagem a um agente homologado (todos os perfis). */
    @PostMapping("/api/v1/agents/{agentId}/messages")
    public ExecutionResponse send(@PathVariable UUID agentId, @Valid @RequestBody MessageRequest body,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return service.execute(agentId, body.message(), user);
    }

    public record MessageRequest(@NotBlank @Size(max = 4000) String message) {
    }
}
