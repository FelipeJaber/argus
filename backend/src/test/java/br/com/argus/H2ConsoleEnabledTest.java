package br.com.argus;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * Com o console habilitado (como no perfil dev), só o caminho do console é liberado.
 * O servlet do H2 não roda no MockMvc; aqui verificamos que a requisição passa pela segurança
 * (404 em vez de 401) com X-Frame-Options SAMEORIGIN, e que a API continua exigindo token.
 */
@TestPropertySource(properties = "spring.h2.console.enabled=true")
class H2ConsoleEnabledTest extends IntegrationTest {

    @Test
    void consolePathPassesSecurityWithSameOriginFrames() throws Exception {
        mvc.perform(get("/h2-console/"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"));
    }

    @Test
    void apiRemainsProtected() throws Exception {
        mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
    }
}
