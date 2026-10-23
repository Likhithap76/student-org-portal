package com.studentorgs.portal.common.error;

/** 409 Conflict: the request clashes with existing data (e.g. duplicate email). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
