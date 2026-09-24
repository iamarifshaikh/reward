package com.intiq.reward.common.exception;

/**
 * The one exception services throw for an expected business failure. The handler turns it into a
 * problem response, so no service needs to know about HTTP.
 */
public class DomainException extends RuntimeException {

    private final ErrorCode errorCode;

    public DomainException(ErrorCode errorCode) {
        super(errorCode.defaultMessage());
        this.errorCode = errorCode;
    }

    /** Use when the caller benefits from specifics, e.g. which field clashed. */
    public DomainException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }

    public DomainException(ErrorCode errorCode, String detail, Throwable cause) {
        super(detail, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public static DomainException notFound(String what) {
        return new DomainException(ErrorCode.COMMON_NOT_FOUND, what + " not found");
    }
}
