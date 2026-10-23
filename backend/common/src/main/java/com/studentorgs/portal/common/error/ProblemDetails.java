package com.studentorgs.portal.common.error;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/** Builds RFC 7807 problem details with the portal's extra fields: {@code timestamp} and {@code errors[]}. */
public final class ProblemDetails {

    public static final String TIMESTAMP = "timestamp";
    public static final String ERRORS = "errors";

    private ProblemDetails() {
    }

    public static ProblemDetail of(HttpStatusCode status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty(TIMESTAMP, Instant.now());
        return problem;
    }

    public static ProblemDetail of(HttpStatusCode status, String detail, List<FieldViolation> errors) {
        ProblemDetail problem = of(status, detail);
        problem.setProperty(ERRORS, errors);
        return problem;
    }
}
