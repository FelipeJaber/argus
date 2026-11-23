package br.com.argus.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** DTOs de entrada do módulo de usuários. */
public final class UserRequests {

    private UserRequests() {
    }

    public record CreateUser(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Email @Size(max = 160) String email,
            @NotNull Role role,
            @Size(max = 80) String sector,
            @NotBlank @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres.") String password) {
    }

    /** Campos nulos não são alterados. */
    public record UpdateUser(Role role, Boolean active, @Size(max = 80) String sector) {
    }

    public record ChangePassword(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres.") String newPassword) {
    }
}
