package br.com.argus;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/** O frontend estático é público; os dados continuam protegidos pela API. */
class StaticFrontendTest extends IntegrationTest {

    @Test
    void indexIsServedWithoutAuthenticationAndWithContentSecurityPolicy() throws Exception {
        // "/" é a welcome page do Spring Boot: um forward para index.html (MockMvc não executa forwards).
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));

        mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<div id=\"app\">")))
                .andExpect(content().string(containsString("Content-Security-Policy")))
                .andExpect(content().string(containsString("script-src 'self'")));
    }

    @Test
    void assetsArePublic() throws Exception {
        mvc.perform(get("/assets/js/app.js")).andExpect(status().isOk());
        mvc.perform(get("/assets/js/api.js")).andExpect(status().isOk());
        mvc.perform(get("/assets/css/argus.css")).andExpect(status().isOk());
        mvc.perform(get("/favicon.svg")).andExpect(status().isOk());
    }

    @Test
    void apiStillRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/agents/catalog")).andExpect(status().isUnauthorized());
    }
}
