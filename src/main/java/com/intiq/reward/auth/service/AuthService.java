package com.intiq.reward.auth.service;

import com.intiq.reward.audit.constant.AuditActions;
import com.intiq.reward.audit.service.AuditService;
import com.intiq.reward.auth.entity.User;
import com.intiq.reward.auth.enums.ContextType;
import com.intiq.reward.auth.enums.OtpChannel;
import com.intiq.reward.auth.enums.OtpPurpose;
import com.intiq.reward.auth.enums.UserStatus;
import com.intiq.reward.auth.repository.UserRepository;
import com.intiq.reward.auth.security.JwtIssuer;
import com.intiq.reward.common.enums.ActorType;
import com.intiq.reward.common.exception.DomainException;
import com.intiq.reward.common.exception.ErrorCode;
import com.intiq.reward.common.util.ContactUtils;
import com.intiq.reward.common.util.Masking;
import com.intiq.reward.organization.enums.OrgStatus;
import com.intiq.reward.organization.service.OrgDirectoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The login flow, start to finish. Holds no rules of its own: it sequences {@link OtpService},
 * {@link TokenService} and {@link JwtIssuer}, and decides which contexts a login may act in.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final OtpService otpService;
    private final TokenService tokenService;
    private final JwtIssuer jwtIssuer;
    private final OrgDirectoryService orgDirectory;
    private final AuditService auditService;
    private final Clock clock;

    /**
     * Sends a code, but only to a contact that actually exists.
     *
     * <p>The result is identical either way. Telling the caller "no such account" would turn this
     * endpoint into a way of discovering which phone numbers are registered on the platform.
     */
    @Transactional
    public OtpService.OtpIssueResult requestOtp(OtpChannel channel, String rawDestination, String ip) {
        String destination = normalize(channel, rawDestination);
        Optional<User> user = userRepository.findByPhoneOrEmail(destination);

        if (user.isEmpty() || user.get().getStatus() == UserStatus.BLOCKED) {
            log.info("OTP requested for unknown or blocked contact: {}", mask(channel, destination));
            return new OtpService.OtpIssueResult(0, 0);
        }

        OtpService.OtpIssueResult result = otpService.issue(channel, destination, OtpPurpose.LOGIN, ip);
        auditService.recordAs(user.get().getId(), user.get().getOrgId(), null,
                AuditActions.OTP_REQUESTED, AuditActions.ENTITY_USER, user.get().getId(),
                user.get().getOrgId(), Map.of("channel", channel.name()), ip);
        return result;
    }

    /**
     * Verifies the code and opens a session. A first successful verification also activates the
     * login, which is how a created account becomes usable without any separate accept step.
     */
    @Transactional
    public LoginResult verifyOtp(OtpChannel channel, String rawDestination, String code, String userAgent, String ip) {
        String destination = normalize(channel, rawDestination);

        try {
            otpService.verify(channel, destination, OtpPurpose.LOGIN, code);
        } catch (DomainException e) {
            auditService.recordAnonymous(AuditActions.LOGIN_FAILED, AuditActions.ENTITY_USER, null,
                    Map.of("destination", mask(channel, destination), "reason", e.errorCode().name()), ip);
            throw e;
        }

        User user = userRepository.findByPhoneOrEmail(destination)
                .orElseThrow(() -> new DomainException(ErrorCode.USER_NOT_FOUND));
        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new DomainException(ErrorCode.AUTH_USER_BLOCKED);
        }

        Instant now = clock.instant();
        if (channel == OtpChannel.SMS) {
            user.verifyPhone(now);
        } else {
            user.verifyEmail(now);
        }
        user.recordLogin(now);

        List<Context> contexts = availableContexts(user);
        if (contexts.isEmpty()) {
            throw new DomainException(ErrorCode.ORG_NOT_ACTIVE);
        }

        Context active = contexts.getFirst();
        LoginResult result = openSession(user, active, contexts, userAgent, ip);

        auditService.recordAs(user.getId(), user.getOrgId(), active.actorType(),
                AuditActions.LOGIN_SUCCEEDED, AuditActions.ENTITY_USER, user.getId(),
                user.getOrgId(), Map.of("channel", channel.name()), ip);
        return result;
    }

    /**
     * Switches a login that is both a business and a consumer to its other side.
     * The current session ends and a new one starts, so a token always names exactly one context.
     */
    @Transactional
    public LoginResult selectContext(UUID userId, ContextType contextType, String currentRefreshToken,
                                     String userAgent, String ip) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new DomainException(ErrorCode.USER_NOT_FOUND));

        List<Context> contexts = availableContexts(user);
        Context target = contexts.stream()
                .filter(context -> context.contextType() == contextType)
                .findFirst()
                .orElseThrow(() -> new DomainException(ErrorCode.AUTH_CONTEXT_INVALID));

        if (currentRefreshToken != null) {
            tokenService.revoke(currentRefreshToken);
        }
        return openSession(user, target, contexts, userAgent, ip);
    }

    /**
     * Trades a refresh token for a fresh access token. The user and organisation are re-read every
     * time, so a block or suspension takes effect at the next refresh rather than in 30 days.
     */
    @Transactional
    public LoginResult refresh(String rawRefreshToken, String userAgent, String ip) {
        TokenService.IssuedToken rotated = tokenService.rotate(rawRefreshToken, userAgent, ip);

        User user = userRepository.findById(rotated.userId())
                .orElseThrow(() -> new DomainException(ErrorCode.AUTH_TOKEN_INVALID));
        if (user.getStatus() == UserStatus.BLOCKED) {
            tokenService.revokeAllForUser(user.getId());
            throw new DomainException(ErrorCode.AUTH_USER_BLOCKED);
        }

        List<Context> contexts = availableContexts(user);
        Context active = contexts.stream()
                .filter(context -> context.contextType() == rotated.contextType())
                .findFirst()
                .orElseThrow(() -> new DomainException(ErrorCode.AUTH_CONTEXT_INVALID));

        JwtIssuer.AccessToken accessToken = jwtIssuer.issue(user.getId(), active.orgId(), active.actorType(),
                active.contextType());
        return new LoginResult(accessToken.value(), accessToken.expiresInSeconds(),
                rotated.rawToken(), rotated.expiresAt(), active, contexts);
    }

    @Transactional
    public void logout(String rawRefreshToken, UUID userId, String ip) {
        tokenService.revoke(rawRefreshToken);
        auditService.recordAnonymous(AuditActions.LOGOUT, AuditActions.ENTITY_USER, userId, Map.of(), ip);
    }

    private LoginResult openSession(User user, Context active, List<Context> contexts, String userAgent, String ip) {
        TokenService.IssuedToken refreshToken =
                tokenService.startSession(user.getId(), active.contextType(), userAgent, ip);
        JwtIssuer.AccessToken accessToken =
                jwtIssuer.issue(user.getId(), active.orgId(), active.actorType(), active.contextType());

        return new LoginResult(accessToken.value(), accessToken.expiresInSeconds(),
                refreshToken.rawToken(), refreshToken.expiresAt(), active, contexts);
    }

    /**
     * A login can act for its business, as a consumer, or both. A business whose organisation is
     * suspended contributes nothing, which is how suspending a retailer locks them out without
     * touching their user row.
     */
    /** Same view of contexts the login flow uses, exposed for the profile screen. */
    @Transactional(readOnly = true)
    public List<Context> contextsFor(User user) {
        return availableContexts(user);
    }

    private List<Context> availableContexts(User user) {
        List<Context> contexts = new ArrayList<>();

        if (user.getOrgId() != null) {
            orgDirectory.find(user.getOrgId())
                    .filter(org -> org.status() == OrgStatus.ACTIVE)
                    .ifPresent(org -> contexts.add(new Context(ContextType.ORG, org.id(), org.code(),
                            org.displayName(), org.actorType())));
        }
        if (user.isConsumer()) {
            contexts.add(new Context(ContextType.CONSUMER, null, null, user.getFullName(), ActorType.CONSUMER));
        }
        return contexts;
    }

    private String normalize(OtpChannel channel, String destination) {
        return channel == OtpChannel.SMS
                ? ContactUtils.normalizePhone(destination)
                : ContactUtils.normalizeEmail(destination);
    }

    private String mask(OtpChannel channel, String destination) {
        return channel == OtpChannel.SMS ? Masking.phone(destination) : Masking.email(destination);
    }

    /** One side a login may act as. */
    public record Context(ContextType contextType, UUID orgId, String orgCode, String displayName, ActorType actorType) {
    }

    /** Everything the controller needs: the access token, the refresh token, and the context picture. */
    public record LoginResult(String accessToken,
                              long accessExpiresInSeconds,
                              String refreshToken,
                              Instant refreshExpiresAt,
                              Context activeContext,
                              List<Context> availableContexts) {
    }
}
