package com.studentorgs.portal.common.security;

/** A JWT that is missing claims, expired, malformed or wrongly signed. */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
