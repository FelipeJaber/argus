package br.com.argus.security;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/**
 * Respostas 401/403 geradas pelo filtro de segurança no mesmo formato RFC 9457 usado pela API,
 * mantendo o header {@code WWW-Authenticate} padrão de Bearer tokens.
 */
@Component
public class ProblemDetailsSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final BearerTokenAuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();
    private final BearerTokenAccessDeniedHandler bearerAccessDenied = new BearerTokenAccessDeniedHandler();
    private final JsonMapper json;

    public ProblemDetailsSecurityHandler(JsonMapper json) {
        this.json = json;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        bearerEntryPoint.commence(request, response, ex);
        boolean invalidToken = ex instanceof OAuth2AuthenticationException;
        write(request, response, HttpStatus.UNAUTHORIZED,
                invalidToken ? "INVALID_TOKEN" : "UNAUTHENTICATED",
                invalidToken ? "Token inválido, expirado ou sessão encerrada." : "Autenticação necessária.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        bearerAccessDenied.handle(request, response, ex);
        write(request, response, HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Seu perfil não tem permissão para esta ação.");
    }

    private void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String code,
            String detail) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", status.getReasonPhrase());
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("instance", request.getRequestURI());
        body.put("code", code);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getOutputStream(), body);
    }
}
