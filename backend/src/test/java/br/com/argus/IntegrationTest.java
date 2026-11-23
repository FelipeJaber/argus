package br.com.argus;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

import br.com.argus.user.Role;
import br.com.argus.user.User;
import br.com.argus.user.UserRepository;

/** Base dos testes de integração: contexto completo + H2 + MockMvc com a cadeia de segurança real. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {

    protected static final String PASSWORD = "Senha@Teste1";
    protected static final String REFRESH_COOKIE = "argus_refresh";

    private static String passwordHash;

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("delete from audit_events");
        jdbc.execute("delete from agent_reviews");
        jdbc.execute("delete from auth_sessions");
        jdbc.execute("delete from agents");
        jdbc.execute("delete from users");
    }

    protected User createUser(String name, Role role) {
        if (passwordHash == null) {
            passwordHash = passwordEncoder.encode(PASSWORD);
        }
        String email = name.toLowerCase(Locale.ROOT).replace(' ', '.') + "@teste.local";
        return users.save(new User(name, email, passwordHash, role, "Teste"));
    }

    protected Session login(User user) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(user.getEmail(), PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return session(result);
    }

    protected static Session session(MvcResult result) throws Exception {
        String token = JsonPath.read(body(result), "$.accessToken");
        Cookie cookie = result.getResponse().getCookie(REFRESH_COOKIE);
        return new Session(token, new Cookie(REFRESH_COOKIE, cookie.getValue()));
    }

    protected static String credentials(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
    }

    protected static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    protected record Session(String accessToken, Cookie refreshCookie) {

        public String bearer() {
            return "Bearer " + accessToken;
        }
    }
}
