package com.intiq.reward.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Every business failure the API can return. The code travels to the client in the problem body,
 * so the frontend switches on a stable string instead of parsing English messages.
 */
public enum ErrorCode {

    // common
    COMMON_VALIDATION(HttpStatus.BAD_REQUEST, "Request validation failed"),
    COMMON_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    COMMON_CONFLICT(HttpStatus.CONFLICT, "Conflicting state"),
    COMMON_FORBIDDEN(HttpStatus.FORBIDDEN, "Not allowed"),
    COMMON_VERSION_CONFLICT(HttpStatus.CONFLICT, "The record changed since you loaded it"),
    COMMON_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests, try again later"),
    COMMON_INTERNAL(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong"),

    // authentication
    AUTH_OTP_NOT_FOUND(HttpStatus.BAD_REQUEST, "No active code for this contact"),
    AUTH_OTP_EXPIRED(HttpStatus.BAD_REQUEST, "This code has expired"),
    AUTH_OTP_INVALID(HttpStatus.BAD_REQUEST, "Incorrect code"),
    AUTH_OTP_ATTEMPTS_EXCEEDED(HttpStatus.BAD_REQUEST, "Too many incorrect attempts, request a new code"),
    AUTH_OTP_COOLDOWN(HttpStatus.TOO_MANY_REQUESTS, "A code was just sent, wait before requesting another"),
    AUTH_UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Sign in to continue"),
    AUTH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Session is no longer valid"),
    AUTH_TOKEN_REUSED(HttpStatus.UNAUTHORIZED, "Session ended for security reasons"),
    AUTH_USER_BLOCKED(HttpStatus.FORBIDDEN, "This account is blocked"),
    AUTH_CONTEXT_INVALID(HttpStatus.BAD_REQUEST, "You cannot act in that context"),

    // users
    USER_CONTACT_REQUIRED(HttpStatus.BAD_REQUEST, "A phone number or an email is required"),
    USER_CONTACT_TAKEN(HttpStatus.CONFLICT, "That phone or email already belongs to another login"),
    USER_CONTACT_LOCKED(HttpStatus.CONFLICT, "The contact is verified and can only be changed with an OTP"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User not found"),

    // organizations
    ORG_NOT_FOUND(HttpStatus.NOT_FOUND, "Organization not found"),
    ORG_NOT_ACTIVE(HttpStatus.FORBIDDEN, "This organization is not active"),
    ORG_GSTIN_DUPLICATE(HttpStatus.CONFLICT, "Another organization already uses this GSTIN"),
    ORG_PAN_DUPLICATE(HttpStatus.CONFLICT, "Another organization already uses this PAN"),
    ORG_LOGIN_EXISTS(HttpStatus.CONFLICT, "This organization already has a login"),
    ORG_INVALID_STATE(HttpStatus.CONFLICT, "That change is not allowed in the current state"),

    // channel relationships
    PARTNER_ALREADY_LINKED(HttpStatus.CONFLICT, "This partner is already linked to the brand"),
    PARTNER_NOT_LINKED(HttpStatus.NOT_FOUND, "This partner is not linked to the brand"),
    PARTNER_PARENT_INVALID(HttpStatus.BAD_REQUEST, "The parent must be a distributor of the same brand"),

    // kyc
    KYC_DOC_NOT_FOUND(HttpStatus.NOT_FOUND, "Document not found"),
    KYC_INVALID_STATE(HttpStatus.CONFLICT, "That document has already been reviewed"),
    KYC_REMARKS_REQUIRED(HttpStatus.BAD_REQUEST, "Remarks are required when rejecting"),
    KYC_NOTHING_TO_SUBMIT(HttpStatus.BAD_REQUEST, "Upload at least one document before submitting"),

    // storage
    STORAGE_UPLOAD_FAILED(HttpStatus.BAD_REQUEST, "The uploaded file could not be verified"),
    STORAGE_TYPE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "That file type is not allowed");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
