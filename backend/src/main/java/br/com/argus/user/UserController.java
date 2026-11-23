package br.com.argus.user;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.argus.security.AuthenticatedUser;
import br.com.argus.shared.PageResponse;
import br.com.argus.user.UserRequests.ChangePassword;
import br.com.argus.user.UserRequests.CreateUser;
import br.com.argus.user.UserRequests.UpdateUser;

@RestController
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    // --- usuário autenticado ---

    @GetMapping("/api/v1/me")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser me) {
        return service.get(me.id());
    }

    @PutMapping("/api/v1/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal AuthenticatedUser me, @Valid @RequestBody ChangePassword body) {
        service.changePassword(me, body);
    }

    // --- pessoas e acessos ---

    @GetMapping("/api/v1/users")
    @PreAuthorize("hasRole('MANAGER')")
    public PageResponse<UserResponse> list(@PageableDefault(size = 50, sort = "name") Pageable pageable) {
        return service.list(pageable);
    }

    @PostMapping("/api/v1/users")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUser body, @AuthenticationPrincipal AuthenticatedUser admin) {
        return service.create(body, admin);
    }

    @PatchMapping("/api/v1/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUser body,
            @AuthenticationPrincipal AuthenticatedUser admin) {
        return service.update(id, body, admin);
    }
}
