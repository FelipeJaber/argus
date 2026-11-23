package br.com.argus.auth;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.argus.auth.AuthDtos.LoginRequest;
import br.com.argus.auth.AuthDtos.SessionResponse;
import br.com.argus.auth.AuthDtos.TokenResponse;
import br.com.argus.auth.AuthService.AuthResult;
import br.com.argus.security.AuthenticatedUser;
import br.com.argus.shared.ApiException;
import br.com.argus.user.UserResponse;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService auth;
    private final RefreshTokenCookies cookies;

    public AuthController(AuthService auth, RefreshTokenCookies cookies) {
        this.auth = auth;
        this.cookies = cookies;
    }

    /** Público. Devolve o access token no corpo e o refresh token em cookie HttpOnly. */
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        return tokens(auth.login(body.email(), body.password(), ClientInfo.from(request)));
    }

    /** Público (autenticado pelo cookie). Rotaciona o refresh token e emite um novo access token. */
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(HttpServletRequest request) {
        String raw = cookies.read(request).orElseThrow(
                () -> ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Sessão expirada. Entre novamente."));
        return tokens(auth.refresh(raw, ClientInfo.from(request)));
    }

    /** Público (autenticado pelo cookie). Encerra a sessão atual e apaga o cookie. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        cookies.read(request).ifPresent(auth::logout);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                .build();
    }

    /** Encerra todas as sessões do usuário (todos os dispositivos). */
    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal AuthenticatedUser user) {
        auth.logoutAll(user.id(), user.name());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                .build();
    }

    @GetMapping("/sessions")
    public List<SessionResponse> sessions(@AuthenticationPrincipal AuthenticatedUser user) {
        return auth.activeSessions(user.id(), user.sessionId());
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> revokeSession(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID sessionId) {
        auth.revokeSession(user.id(), user.name(), sessionId);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<TokenResponse> tokens(AuthResult result) {
        TokenResponse body = new TokenResponse(result.accessToken().value(), "Bearer",
                result.accessToken().expiresIn(), UserResponse.from(result.user()));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookies.create(result.refreshToken(), result.refreshExpiresAt()).toString())
                .body(body);
    }
}
