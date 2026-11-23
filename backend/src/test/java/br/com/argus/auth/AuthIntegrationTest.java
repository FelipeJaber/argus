package br.com.argus.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.web.servlet.MvcResult;

import br.com.argus.IntegrationTest;
import br.com.argus.user.Role;
import br.com.argus.user.User;

class AuthIntegrationTest extends IntegrationTest {

    @Test
    void loginReturnsAccessTokenInBodyAndRefreshTokenInHttpOnlyCookie() throws Exception {
        User user = createUser("Ana Colaboradora", Role.COLLABORATOR);

        MvcResult result = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(user.getEmail(), PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.email").value(user.getEmail()))
                .andExpect(jsonPath("$.user.role").value("COLLABORATOR"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        MockCookie cookie = (MockCookie) result.getResponse().getCookie(REFRESH_COOKIE);
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
        assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
        assertThat(cookie.getMaxAge()).isPositive();
    }

    @Test
    void wrongPasswordAndUnknownEmailReturnTheSameError() throws Exception {
        User user = createUser("Bruno Teste", Role.COLLABORATOR);

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(user.getEmail(), "senha-errada")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("ninguem@teste.local", PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void accountIsLockedAfterTooManyFailedAttempts() throws Exception {
        User user = createUser("Carla Teste", Role.COLLABORATOR);
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(credentials(user.getEmail(), "senha-errada")))
                    .andExpect(status().isUnauthorized());
        }

        // Mesmo com a senha correta, a conta segue bloqueada durante o período configurado.
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(user.getEmail(), PASSWORD)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    void inactiveUserCannotLogin() throws Exception {
        User user = createUser("Diego Inativo", Role.COLLABORATOR);
        user.deactivate();
        users.save(user);

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(user.getEmail(), PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointsRequireAValidAccessToken() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer nao.e.um.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        Session session = login(createUser("Eva Teste", Role.COLLABORATOR));
        String[] parts = session.accessToken().split("\\.");
        String forged = parts[0] + "." + parts[1] + "." + new StringBuilder(parts[2]).reverse();

        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRotatesTokenAndReuseOfOldTokenRevokesTheWholeSession() throws Exception {
        Session session = login(createUser("Fabio Teste", Role.COLLABORATOR));
        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(status().isOk());

        // 1) Renovação normal: novo par de tokens, refresh token diferente.
        MvcResult refreshed = mvc.perform(post("/api/v1/auth/refresh").cookie(session.refreshCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        Session rotated = session(refreshed);
        assertThat(rotated.refreshCookie().getValue()).isNotEqualTo(session.refreshCookie().getValue());

        // 2) Alguém reapresenta o token antigo (ex.: token vazado): a sessão inteira é revogada.
        mvc.perform(post("/api/v1/auth/refresh").cookie(session.refreshCookie()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        // 3) Nem o token novo nem os access tokens emitidos para a sessão continuam válidos.
        mvc.perform(post("/api/v1/auth/refresh").cookie(rotated.refreshCookie()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, rotated.bearer()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshIgnoresExpiredAuthorizationHeader() throws Exception {
        Session session = login(createUser("Gabi Teste", Role.COLLABORATOR));

        mvc.perform(post("/api/v1/auth/refresh")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token.invalido.qualquer")
                        .cookie(session.refreshCookie()))
                .andExpect(status().isOk());
    }

    @Test
    void refreshWithoutCookieIsRejected() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(REFRESH_COOKIE, "lixo")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesTheSessionImmediately() throws Exception {
        Session session = login(createUser("Hugo Teste", Role.COLLABORATOR));

        MvcResult logout = mvc.perform(post("/api/v1/auth/logout").cookie(session.refreshCookie()))
                .andExpect(status().isNoContent())
                .andReturn();
        assertThat(logout.getResponse().getCookie(REFRESH_COOKIE).getMaxAge()).isZero();

        // O access token ainda não expirou, mas a sessão dele foi encerrada.
        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").cookie(session.refreshCookie()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutAllEndsEverySessionOfTheUser() throws Exception {
        User user = createUser("Iris Teste", Role.COLLABORATOR);
        Session notebook = login(user);
        Session phone = login(user);

        mvc.perform(get("/api/v1/auth/sessions").header(HttpHeaders.AUTHORIZATION, notebook.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mvc.perform(post("/api/v1/auth/logout-all").header(HttpHeaders.AUTHORIZATION, notebook.bearer()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, notebook.bearer()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, phone.bearer()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changingPasswordKeepsCurrentSessionAndEndsTheOthers() throws Exception {
        User user = createUser("Joao Teste", Role.COLLABORATOR);
        Session current = login(user);
        Session other = login(user);

        mvc.perform(put("/api/v1/me/password")
                        .header(HttpHeaders.AUTHORIZATION, current.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "%s", "newPassword": "NovaSenha@123"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, current.bearer()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, other.bearer()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(user.getEmail(), "NovaSenha@123")))
                .andExpect(status().isOk());
    }

    @Test
    void changingUserRoleEndsTheirSessions() throws Exception {
        User admin = createUser("Admin Teste", Role.ADMIN);
        User target = createUser("Karla Teste", Role.COLLABORATOR);
        Session adminSession = login(admin);
        Session targetSession = login(target);

        mvc.perform(patch("/api/v1/users/{id}", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminSession.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\": \"MANAGER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MANAGER"));

        // O token antigo carregava o perfil COLLABORATOR: precisa entrar de novo.
        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, targetSession.bearer()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCannotDemoteOrDeactivateThemselves() throws Exception {
        User admin = createUser("Admin Solo", Role.ADMIN);
        Session session = login(admin);

        mvc.perform(patch("/api/v1/users/{id}", admin.getId())
                        .header(HttpHeaders.AUTHORIZATION, session.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\": false}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SELF_LOCKOUT"));
    }
}
