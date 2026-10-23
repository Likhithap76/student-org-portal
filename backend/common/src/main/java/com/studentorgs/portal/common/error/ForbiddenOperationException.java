package com.studentorgs.portal.common.error;

/** 403 Forbidden: the caller may not perform this operation on this resource. */
public class ForbiddenOperationException extends RuntimeException {

    public ForbiddenOperationException(String message) {
        super(message);
    }
}
