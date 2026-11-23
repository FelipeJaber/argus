package br.com.argus.shared;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Converte exceções em respostas RFC 9457 (application/problem+json) com um campo {@code code} estável.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ProblemDetail handleApi(ApiException ex) {
        return problem(ex.getStatus(), ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Seu perfil não tem permissão para esta ação.");
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                "O registro foi alterado por outra operação. Recarregue e tente novamente.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("Violação de integridade: {}", ex.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "DATA_INTEGRITY", "A operação viola uma restrição de dados.");
    }

    /** Ex.: {@code ?sort=campoInexistente}. */
    @ExceptionHandler(PropertyReferenceException.class)
    ProblemDetail handleInvalidSort(PropertyReferenceException ex) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_SORT", "Campo de ordenação inválido: " + ex.getPropertyName());
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Erro inesperado", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Ocorreu um erro inesperado. Tente novamente mais tarde.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Há campos inválidos na requisição.");
        body.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(body);
    }

    /** Garante o campo {@code code} também nos erros tratados pelo próprio Spring MVC (JSON inválido, 405, 415...). */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail detail
                && (detail.getProperties() == null || !detail.getProperties().containsKey("code"))) {
            HttpStatus status = HttpStatus.resolve(statusCode.value());
            detail.setProperty("code", status != null ? status.name() : "REQUEST_ERROR");
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    public static ProblemDetail problem(HttpStatusCode status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
