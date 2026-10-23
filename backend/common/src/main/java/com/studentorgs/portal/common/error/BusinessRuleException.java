package com.studentorgs.portal.common.error;

/** 422 Unprocessable Content: the request breaks a business rule. */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
