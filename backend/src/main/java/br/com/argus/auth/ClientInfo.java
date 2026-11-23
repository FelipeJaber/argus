package br.com.argus.auth;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpHeaders;

/** Metadados do cliente gravados na sessão (exibidos em "meus dispositivos"). */
public record ClientInfo(String userAgent, String ipAddress) {

    public static ClientInfo from(HttpServletRequest request) {
        // getRemoteAddr já respeita X-Forwarded-For quando server.forward-headers-strategy está configurado.
        return new ClientInfo(request.getHeader(HttpHeaders.USER_AGENT), request.getRemoteAddr());
    }
}
