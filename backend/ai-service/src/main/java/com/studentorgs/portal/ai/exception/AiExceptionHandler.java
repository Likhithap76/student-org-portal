package com.studentorgs.portal.ai.exception;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.studentorgs.portal.common.error.ProblemDetails;

/** LLM failures as problem details. Runs before common's handler (which would turn them into 500). */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AiExceptionHandler {

    @ExceptionHandler(LlmUnavailableException.class)
    ProblemDetail handleUnavailable(LlmUnavailableException e) {
        return ProblemDetails.of(HttpStatus.SERVICE_UNAVAILABLE, "The AI service is unavailable. Please try again later");
    }

    @ExceptionHandler(LlmInvalidOutputException.class)
    ProblemDetail handleInvalidOutput(LlmInvalidOutputException e) {
        return ProblemDetails.of(HttpStatus.BAD_GATEWAY, "The AI returned an unusable answer. Please try again");
    }
}
