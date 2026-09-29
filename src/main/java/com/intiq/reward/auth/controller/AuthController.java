package com.intiq.reward.auth.controller;

import com.intiq.reward.auth.config.AuthCookieProperties;
import com.intiq.reward.auth.dto.request.OtpRequest;
import com.intiq.reward.auth.dto.request.OtpVerifyRequest;
import com.intiq.reward.auth.dto.response.ContextResponse;
import com.intiq.reward.auth.dto.response.LoginResponse;
import com.intiq.reward.auth.dto.response.OtpChallengeResponse;
import com.intiq.reward.auth.service.AuthService;
import com.intiq.reward.auth.service.OtpIssueResult;
import com.intiq.reward.common.constant.ApiPaths;
import com.intiq.reward.common.exception.DomainException;
import com.intiq.reward.common.exception.ErrorCode;
import com.intiq.reward.common.security.AuthPrincipal;
import com.intiq.reward.common.security.CurrentUser;
import com.intiq.reward.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;

/**
 * The public half of the API. Everything here is reachable without a token, which is why each
 * endpoint is rate limited and none of them reveal whether an account exists.
 *
 * <p>There is no endpoint to switch identity: a login is a business or a consumer, fixed at
 * creation by which contact it was given, never both. See {@link AuthService#resolveContext}.
 */
@RestController
@RequestMapping(ApiPaths.V1 + "/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthCookieProperties cookieProperties;

    /** Step one: send a code. Returns the same body whether or not the contact is registered. */
    @PostMapping("/otp")
    public OtpChallengeResponse requestOtp(@Valid @RequestBody OtpRequest request, HttpServletRequest httpRequest) {
        OtpIssueResult result =
                authService.requestOtp(request.channel(), request.destination(), RequestIdFilter.clientIp(httpRequest));
        return new OtpChallengeResponse("If that contact is registered, a code has been sent.",
                result.expiresInSeconds(), result.retryAfterSeconds());
    }

    /** Step two: exchange the code for a session. */
    @PostMapping("/otp/verify")
    public ResponseEntity<LoginResponse> verifyOtp(@Valid @RequestBody OtpVerifyRequest request,
                                                   HttpServletRequest httpRequest) {
        AuthService.LoginResult result = authService.verifyOtp(
                request.channel(),
                request.destination(),
                request.code(),
                httpRequest.getHeader(HttpHeaders.USER_AGENT),
                RequestIdFilter.clientIp(httpRequest));
        return respond(result);
    }

    /** Trades the refresh cookie for a new access token, and a new cookie. */
    @PostMapping("/token/refresh")
    public ResponseEntity<LoginResponse> refresh(
            @CookieValue(name = "${intiq.auth.cookie.name}", required = false) String refreshToken,
            HttpServletRequest httpRequest) {
        if (refreshToken == null) {
            throw new DomainException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        AuthService.LoginResult result = authService.refresh(
                refreshToken,
                httpRequest.getHeader(HttpHeaders.USER_AGENT),
                RequestIdFilter.clientIp(httpRequest));
        return respond(result);
    }

    /** Ends this session only. Other devices stay signed in. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "${intiq.auth.cookie.name}", required = false) String refreshToken,
            @CurrentUser AuthPrincipal principal,
            HttpServletRequest httpRequest) {
        if (refreshToken != null) {
            authService.logout(refreshToken, principal == null ? null : principal.userId(),
                    RequestIdFilter.clientIp(httpRequest));
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expiredCookie().toString())
                .build();
    }

    private ResponseEntity<LoginResponse> respond(AuthService.LoginResult result) {
        LoginResponse body = new LoginResponse(
                result.accessToken(),
                result.accessExpiresInSeconds(),
                toResponse(result.context()));

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken(), result.refreshExpiresAt()).toString())
                .body(body);
    }

    /**
     * httpOnly so page scripts cannot read it, SameSite=Strict so another site cannot trigger a
     * refresh, and scoped to the auth path so it is not attached to ordinary API calls.
     */
    private ResponseCookie refreshCookie(String value, Instant expiresAt) {
        return ResponseCookie.from(cookieProperties.name(), value)
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite(cookieProperties.sameSite())
                .path(cookieProperties.path())
                .maxAge(Duration.between(Instant.now(), expiresAt))
                .build();
    }

    private ResponseCookie expiredCookie() {
        return ResponseCookie.from(cookieProperties.name(), "")
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite(cookieProperties.sameSite())
                .path(cookieProperties.path())
                .maxAge(0)
                .build();
    }

    private static ContextResponse toResponse(AuthService.Context context) {
        return new ContextResponse(context.contextType(), context.orgId(), context.orgCode(),
                context.displayName(), context.actorType());
    }
}
