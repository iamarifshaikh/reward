package com.intiq.reward.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Map;

/**
 * Turns every exception into RFC 9457 problem JSON with a stable {@code code} and the request id,
 * so a support ticket can be traced straight to the log line that produced it.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final String REQUEST_ID = "requestId";
    private static final String CODE = "code";

    @ExceptionHandler(DomainException.class)
    public ProblemDetail handleDomain(DomainException e, HttpServletRequest request) {
        ErrorCode code = e.errorCode();
        if (code.status().is5xxServerError()) {
            log.error("Domain failure {}", code, e);
        } else {
            log.debug("Domain failure {}: {}", code, e.getMessage());
        }
        return problem(code.status(), code.name(), e.getMessage(), request);
    }

    /** Bean validation on a request body: one entry per offending field. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
        List<Map<String, String>> errors = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> Map.of(
                        "field", fieldError.getField(),
                        "message", String.valueOf(fieldError.getDefaultMessage())))
                .toList();

        ProblemDetail problem = problem(ErrorCode.COMMON_VALIDATION.status(),
                ErrorCode.COMMON_VALIDATION.name(),
                ErrorCode.COMMON_VALIDATION.defaultMessage(),
                request);
        problem.setProperty("errors", errors);
        return problem;
    }

    /** A unique index or check constraint fired: the database is the final guard, not the first. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleIntegrity(DataIntegrityViolationException e, HttpServletRequest request) {
        log.warn("Constraint violation on {}", request.getRequestURI(), e);
        return problem(ErrorCode.COMMON_CONFLICT.status(),
                ErrorCode.COMMON_CONFLICT.name(),
                ErrorCode.COMMON_CONFLICT.defaultMessage(),
                request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(OptimisticLockingFailureException e, HttpServletRequest request) {
        return problem(ErrorCode.COMMON_VERSION_CONFLICT.status(),
                ErrorCode.COMMON_VERSION_CONFLICT.name(),
                ErrorCode.COMMON_VERSION_CONFLICT.defaultMessage(),
                request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleUnauthenticated(AuthenticationException e, HttpServletRequest request) {
        return problem(ErrorCode.AUTH_UNAUTHENTICATED.status(),
                ErrorCode.AUTH_UNAUTHENTICATED.name(),
                ErrorCode.AUTH_UNAUTHENTICATED.defaultMessage(),
                request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException e, HttpServletRequest request) {
        return problem(ErrorCode.COMMON_FORBIDDEN.status(),
                ErrorCode.COMMON_FORBIDDEN.name(),
                ErrorCode.COMMON_FORBIDDEN.defaultMessage(),
                request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResource(NoResourceFoundException e, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, ErrorCode.COMMON_NOT_FOUND.name(), "No such endpoint", request);
    }

    /** Last resort: log the detail, tell the caller nothing about our internals. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), e);
        return problem(ErrorCode.COMMON_INTERNAL.status(),
                ErrorCode.COMMON_INTERNAL.name(),
                ErrorCode.COMMON_INTERNAL.defaultMessage(),
                request);
    }

    private ProblemDetail problem(HttpStatus status, String code, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty(CODE, code);
        problem.setProperty(REQUEST_ID, request.getAttribute(REQUEST_ID));
        return problem;
    }
}
