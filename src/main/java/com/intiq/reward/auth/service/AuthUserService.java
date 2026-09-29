package com.intiq.reward.auth.service;

import com.intiq.reward.auth.entity.User;
import com.intiq.reward.auth.enums.UserStatus;
import com.intiq.reward.auth.repository.UserRepository;
import com.intiq.reward.common.exception.DomainException;
import com.intiq.reward.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The only door other modules use into {@code auth}. Nothing outside this module should ever
 * inject {@link UserRepository} directly — {@code organization} needing a login created for a new
 * business is exactly the case this exists for.
 *
 * <p>Returns a small {@link Summary}, never the {@link User} entity itself, so entities stay
 * inside their own module.
 */
@Service
@RequiredArgsConstructor
public class AuthUserService {

    private final UserRepository userRepository;

    /** A contact already used by any login, business or consumer, cannot be reused for another. */
    @Transactional(readOnly = true)
    public boolean contactExists(String phone, String email) {
        return (phone != null && userRepository.existsByPhone(phone))
                || (email != null && userRepository.existsByEmail(email));
    }

    /**
     * Creates the one login a newly created business gets, as {@code UNVERIFIED}. The first
     * successful OTP against this phone or email activates it — there is no separate accept step.
     */
    @Transactional
    public Summary createBusinessLogin(UUID orgId, String phone, String email, String fullName) {
        User user = User.businessLogin(orgId, phone, email, fullName);
        user = userRepository.save(user);
        return toSummary(user);
    }

    /**
     * Fixes a mistyped name or contact on a business login that has never been verified — the case
     * for a distributor still pending approval, or a rejected one being corrected before resubmit.
     * {@link User#correctContact} and {@link User#rename} both refuse once the login is verified.
     */
    @Transactional
    public Summary correctBusinessContact(UUID orgId, String phone, String email, String fullName) {
        User user = userRepository.findByOrgId(orgId).stream().findFirst()
                .orElseThrow(() -> new DomainException(ErrorCode.USER_NOT_FOUND));
        if (phone != null && !phone.equals(user.getPhone()) && userRepository.existsByPhone(phone)) {
            throw new DomainException(ErrorCode.USER_CONTACT_TAKEN);
        }
        if (email != null && !email.equals(user.getEmail()) && userRepository.existsByEmail(email)) {
            throw new DomainException(ErrorCode.USER_CONTACT_TAKEN);
        }
        user.correctContact(phone, email);
        if (!Objects.equals(fullName, user.getFullName())) {
            user.rename(fullName);
        }
        return toSummary(user);
    }

    /** Batched contact lookup for list screens, keyed by org id — one query, not one per row. */
    @Transactional(readOnly = true)
    public Map<UUID, Summary> findByOrgIds(Collection<UUID> orgIds) {
        if (orgIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findByOrgIdIn(orgIds).stream()
                .collect(Collectors.toMap(User::getOrgId, AuthUserService::toSummary, (a, b) -> a));
    }

    private static Summary toSummary(User user) {
        return new Summary(user.getId(), user.getPhone(), user.getEmail(), user.getFullName(), user.getStatus());
    }

    public record Summary(UUID userId, String phone, String email, String fullName, UserStatus status) {
    }
}
