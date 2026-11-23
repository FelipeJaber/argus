package br.com.argus.shared;

import org.springframework.http.HttpStatus;

/**
 * Erro de negócio com status HTTP e código estável (para o frontend tratar sem depender do texto).
 * Convertido em RFC 9457 (Problem Details) pelo {@link GlobalExceptionHandler}.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ApiException notFound(String resource) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", resource + " não encontrado(a).");
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static ApiException unauthorized(String code, String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, code, message);
    }

    public static ApiException forbidden(String code, String message) {
        return new ApiException(HttpStatus.FORBIDDEN, code, message);
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    public static ApiException unprocessable(String code, String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, code, message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
