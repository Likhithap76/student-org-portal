package com.studentorgs.portal.common.error;

/** 503 Service Unavailable: a service this request depends on could not be reached. */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
