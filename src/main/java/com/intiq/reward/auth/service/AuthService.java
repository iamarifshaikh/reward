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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The login flow, start to finish. Holds no OTP rules of its own: it routes to
 * {@link EmailOtpService} or {@link SmsOtpService} by channel — the two own completely different
 * state (we hold the code for email, MSG91 holds it for SMS), so nothing here needs to know which.
 *
 * <p>A login has exactly one identity: a business (when {@code orgId} is set) or a consumer, never
 * both, so there is nothing to choose between at login time. {@link #resolveContext} derives that
 * one identity from the user record alone.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final EmailOtpService emailOtpService;
    private final SmsOtpService smsOtpService;
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
    public OtpIssueResult requestOtp(OtpChannel channel, String rawDestination, String ip) {
        String destination = normalize(channel, rawDestination);
        Optional<User> user = userRepository.findByPhoneOrEmail(destination);

        if (user.isEmpty() || user.get().getStatus() == UserStatus.BLOCKED) {
            log.info("OTP requested for unknown or blocked contact: {}", mask(channel, destination));
            return new OtpIssueResult(0, 0);
        }

        OtpIssueResult result = channel == OtpChannel.SMS
                ? smsOtpService.issue(destination, ip)
                : emailOtpService.issue(destination, OtpPurpose.LOGIN, ip);

        auditService.recordAs(user.get().getId(), user.get().getOrgId(), actorTypeFor(user.get()),
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
            if (channel == OtpChannel.SMS) {
                smsOtpService.verify(destination, code);
            } else {
                emailOtpService.verify(destination, OtpPurpose.LOGIN, code);
            }
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

        Context context = resolveContext(user);
        LoginResult result = openSession(user, context, userAgent, ip);

        auditService.recordAs(user.getId(), user.getOrgId(), context.actorType(),
                AuditActions.LOGIN_SUCCEEDED, AuditActions.ENTITY_USER, user.getId(),
                user.getOrgId(), Map.of("channel", channel.name()), ip);
        return result;
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

        Context context = resolveContext(user);
        if (context.contextType() != rotated.contextType()) {
            // The identity a user has is fixed at creation, so this only fires if data drifted
            // under the token's feet — treat it as the token no longer being trustworthy.
            throw new DomainException(ErrorCode.AUTH_TOKEN_INVALID);
        }

        JwtIssuer.AccessToken accessToken = jwtIssuer.issue(user.getId(), context.orgId(), context.actorType(),
                context.contextType());
        return new LoginResult(accessToken.value(), accessToken.expiresInSeconds(),
                rotated.rawToken(), rotated.expiresAt(), context);
    }

    @Transactional
    public void logout(String rawRefreshToken, UUID userId, String ip) {
        tokenService.revoke(rawRefreshToken);
        auditService.recordAnonymous(AuditActions.LOGOUT, AuditActions.ENTITY_USER, userId, Map.of(), ip);
    }

    private LoginResult openSession(User user, Context context, String userAgent, String ip) {
        TokenService.IssuedToken refreshToken =
                tokenService.startSession(user.getId(), context.contextType(), userAgent, ip);
        JwtIssuer.AccessToken accessToken =
                jwtIssuer.issue(user.getId(), context.orgId(), context.actorType(), context.contextType());

        return new LoginResult(accessToken.value(), accessToken.expiresInSeconds(),
                refreshToken.rawToken(), refreshToken.expiresAt(), context);
    }

    /**
     * A login is a business or a consumer, never both, so this is a lookup, not a choice.
     * A business whose organisation is suspended has no usable context, which is how suspending a
     * retailer locks them out without touching their user row.
     */
    @Transactional(readOnly = true)
    public Context resolveContext(User user) {
        if (user.getOrgId() != null) {
            OrgDirectoryService.OrgSummary org = orgDirectory.find(user.getOrgId())
                    .filter(o -> o.status() == OrgStatus.ACTIVE)
                    .orElseThrow(() -> new DomainException(ErrorCode.ORG_NOT_ACTIVE));
            return new Context(ContextType.ORG, org.id(), org.code(), org.displayName(), org.actorType());
        }
        return new Context(ContextType.CONSUMER, null, null, user.getFullName(), ActorType.CONSUMER);
    }

    /**
     * A safe label for audit rows: what kind of business this is, without also demanding that it
     * be active. Deliberately not {@link #resolveContext}, which throws on a suspended organisation
     * — that check belongs at login, not at "who is this for a log entry", or an OTP would stop
     * being sent the moment a business is suspended instead of failing later at verification.
     */
    private ActorType actorTypeFor(User user) {
        if (user.getOrgId() == null) {
            return ActorType.CONSUMER;
        }
        return orgDirectory.find(user.getOrgId()).map(OrgDirectoryService.OrgSummary::actorType).orElse(null);
    }

    private String normalize(OtpChannel channel, String destination) {
        return channel == OtpChannel.SMS
                ? ContactUtils.normalizePhone(destination)
                : ContactUtils.normalizeEmail(destination);
    }

    private String mask(OtpChannel channel, String destination) {
        return channel == OtpChannel.SMS ? Masking.phone(destination) : Masking.email(destination);
    }

    /** The one identity a login has. */
    public record Context(ContextType contextType, UUID orgId, String orgCode, String displayName, ActorType actorType) {
    }

    /** Everything the controller needs: the access token, the refresh token, and who the caller is. */
    public record LoginResult(String accessToken, long accessExpiresInSeconds, String refreshToken,
                              Instant refreshExpiresAt, Context context) {
    }
}
