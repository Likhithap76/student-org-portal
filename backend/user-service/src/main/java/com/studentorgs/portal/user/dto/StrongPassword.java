package com.studentorgs.portal.user.dto;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Password rule: 8 to 72 characters (BCrypt limit) with at least one letter and one digit. */
@Documented
@Constraint(validatedBy = {})
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@NotNull
@Size(min = 8, max = 72)
@Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).*$")
@ReportAsSingleViolation
public @interface StrongPassword {

    String message() default "must be 8 to 72 characters and contain at least one letter and one digit";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
