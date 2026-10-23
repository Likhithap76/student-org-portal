package com.studentorgs.portal.common.error;

/** 404 Not Found: a requested resource does not exist. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
