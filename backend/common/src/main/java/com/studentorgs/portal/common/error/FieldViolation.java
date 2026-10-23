package com.studentorgs.portal.common.error;

/** One entry of a problem's {@code errors[]}: which field failed and why. */
public record FieldViolation(String field, String message) {
}
