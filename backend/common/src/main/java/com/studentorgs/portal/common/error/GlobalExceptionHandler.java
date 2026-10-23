package com.studentorgs.portal.common.error;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

/**
 * Turns every error into an RFC 7807 {@link ProblemDetail}
 * ({@code type, title, status, detail, instance, timestamp, errors[]}).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    // SQLSTATE codes shared by PostgreSQL and H2.
    private static final String UNIQUE_VIOLATION = "23505";
    private static final String FOREIGN_KEY_VIOLATION = "23503";

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail handleNotFound(NotFoundException e) {
        return ProblemDetails.of(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail handleConflict(ConflictException e) {
        return ProblemDetails.of(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    ProblemDetail handleForbiddenOperation(ForbiddenOperationException e) {
        return ProblemDetails.of(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(BusinessRuleException.class)
    ProblemDetail handleBusinessRule(BusinessRuleException e) {
        return ProblemDetails.of(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    ProblemDetail handleServiceUnavailable(ServiceUnavailableException e) {
        log.warn("Dependency unavailable: {}", e.getMessage());
        return ProblemDetails.of(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    /** The database could not be reached (e.g. connection timeout): 503, not a generic 500. */
    @ExceptionHandler({CannotCreateTransactionException.class, DataAccessResourceFailureException.class})
    ProblemDetail handleDatabaseUnavailable(Exception e) {
        log.warn("Database unavailable: {}", e.getClass().getSimpleName());
        return ProblemDetails.of(HttpStatus.SERVICE_UNAVAILABLE, "Database unavailable. Please try again");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrity(DataIntegrityViolationException e) {
        String sqlState = sqlState(e);
        log.warn("Constraint violation (SQLSTATE {})", sqlState);
        if (UNIQUE_VIOLATION.equals(sqlState)) {
            return ProblemDetails.of(HttpStatus.CONFLICT, "A record with the same unique value already exists");
        }
        if (FOREIGN_KEY_VIOLATION.equals(sqlState)) {
            return ProblemDetails.of(HttpStatus.CONFLICT, "The record references, or is referenced by, other data");
        }
        return ProblemDetails.of(HttpStatus.UNPROCESSABLE_ENTITY, "The data violates a database constraint");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraintViolation(ConstraintViolationException e) {
        List<FieldViolation> errors = e.getConstraintViolations().stream()
                .map(v -> new FieldViolation(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return ProblemDetails.of(HttpStatus.BAD_REQUEST, "Validation failed", errors);
    }

    /** Login with a wrong email or password. One generic message, so callers can't probe which emails exist. */
    @ExceptionHandler(BadCredentialsException.class)
    ProblemDetail handleBadCredentials(BadCredentialsException e) {
        return ProblemDetails.of(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail handleAuthentication(AuthenticationException e) {
        return ProblemDetails.of(HttpStatus.UNAUTHORIZED, "Authentication is required");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException e) {
        return ProblemDetails.of(HttpStatus.FORBIDDEN, "You do not have permission to do this");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return ProblemDetails.of(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> errors = Stream.concat(
                e.getBindingResult().getFieldErrors().stream()
                        .map(f -> new FieldViolation(f.getField(), f.getDefaultMessage())),
                e.getBindingResult().getGlobalErrors().stream()
                        .map(g -> new FieldViolation(g.getObjectName(), g.getDefaultMessage())))
                .toList();
        return ResponseEntity.status(status).headers(headers)
                .body(ProblemDetails.of(status, "Validation failed", errors));
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> errors = e.getAllValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream()
                        .map(err -> new FieldViolation(r.getMethodParameter().getParameterName(), err.getDefaultMessage())))
                .toList();
        return ResponseEntity.status(status).headers(headers)
                .body(ProblemDetails.of(status, "Validation failed", errors));
    }

    /** Adds {@code timestamp} to problems built by Spring's own handlers (400, 404, 405, ...). */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception e, @Nullable Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(e, body, headers, status, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem
                && (problem.getProperties() == null || !problem.getProperties().containsKey(ProblemDetails.TIMESTAMP))) {
            problem.setProperty(ProblemDetails.TIMESTAMP, Instant.now());
        }
        return response;
    }

    private static String sqlState(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
        }
        return null;
    }
}
