package br.com.argus.user;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.argus.audit.AuditAction;
import br.com.argus.audit.AuditEntry;
import br.com.argus.audit.AuditOutcome;
import br.com.argus.audit.AuditService;
import br.com.argus.auth.AuthService;
import br.com.argus.auth.RevocationReason;
import br.com.argus.security.AuthenticatedUser;
import br.com.argus.shared.ApiException;
import br.com.argus.shared.PageResponse;
import br.com.argus.user.UserRequests.ChangePassword;
import br.com.argus.user.UserRequests.CreateUser;
import br.com.argus.user.UserRequests.UpdateUser;

@Service
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthService auth;
    private final AuditService audit;

    public UserService(UserRepository users, PasswordEncoder passwordEncoder, AuthService auth, AuditService audit) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.auth = auth;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID id) {
        return UserResponse.from(find(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(Pageable pageable) {
        return PageResponse.of(users.findAll(pageable), UserResponse::from);
    }

    @Transactional
    public UserResponse create(CreateUser request, AuthenticatedUser admin) {
        String email = User.normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("EMAIL_ALREADY_USED", "Já existe um usuário com este e-mail.");
        }
        User user = users.save(new User(request.name(), email, passwordEncoder.encode(request.password()),
                request.role(), request.sector()));
        audit.record(AuditEntry.of(AuditAction.USER_CREATED, AuditOutcome.ALLOWED)
                .by(admin.id(), admin.name()).detail("Usuário " + user.getEmail() + " (" + user.getRole() + ")"));
        return UserResponse.from(user);
    }

    /** Mudança de perfil ou desativação derruba as sessões do usuário afetado. */
    @Transactional
    public UserResponse update(UUID id, UpdateUser request, AuthenticatedUser admin) {
        User user = find(id);
        boolean self = user.getId().equals(admin.id());
        if (self && (request.role() != null && request.role() != user.getRole()
                || Boolean.FALSE.equals(request.active()))) {
            throw ApiException.unprocessable("SELF_LOCKOUT",
                    "Você não pode alterar o próprio perfil nem desativar a própria conta.");
        }
        RevocationReason revoke = null;
        if (request.sector() != null) {
            user.changeSector(request.sector().isBlank() ? null : request.sector().trim());
        }
        if (request.role() != null && request.role() != user.getRole()) {
            user.changeRole(request.role());
            revoke = RevocationReason.ROLE_CHANGED;
        }
        if (request.active() != null && request.active() != user.isActive()) {
            if (request.active()) {
                user.activate();
            } else {
                user.deactivate();
                revoke = RevocationReason.USER_DEACTIVATED;
            }
        }
        UserResponse response = UserResponse.from(user);
        audit.record(AuditEntry.of(AuditAction.USER_UPDATED, AuditOutcome.ALLOWED)
                .by(admin.id(), admin.name())
                .detail("Usuário " + user.getEmail() + ": perfil " + user.getRole() + ", ativo=" + user.isActive()));
        if (revoke != null) {
            // Por último: a revogação em massa faz flush das alterações acima e limpa o contexto de persistência.
            auth.revokeAllSessions(user.getId(), revoke);
        }
        return response;
    }

    /** Troca a própria senha e encerra as demais sessões (mantém o dispositivo atual conectado). */
    @Transactional
    public void changePassword(AuthenticatedUser me, ChangePassword request) {
        User user = find(me.id());
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.unprocessable("INVALID_CURRENT_PASSWORD", "A senha atual não confere.");
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        auth.revokeOtherSessions(user.getId(), me.sessionId(), RevocationReason.PASSWORD_CHANGED);
        audit.record(AuditEntry.of(AuditAction.PASSWORD_CHANGED, AuditOutcome.ALLOWED).by(me.id(), me.name()));
    }

    private User find(UUID id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("Usuário"));
    }
}
