package com.studentorgs.portal.ai.domain;

/** Result of one summary request. Stored lowercase. */
public enum AiOutcome {
    SUCCEEDED,
    FAILED_VALIDATION,
    SERVICE_ERROR
}
