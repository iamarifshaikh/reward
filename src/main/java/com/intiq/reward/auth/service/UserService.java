package com.intiq.reward.auth.service;

import com.intiq.reward.auth.entity.User;
import com.intiq.reward.auth.enums.ContextType;
import com.intiq.reward.auth.repository.UserRepository;
import com.intiq.reward.common.exception.DomainException;
import com.intiq.reward.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * The user record itself, as opposed to the act of logging in. Profile reads and edits live here.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final AuthService authService;

    @Transactional(readOnly = true)
    public Profile profile(UUID userId, ContextType activeContext) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new DomainException(ErrorCode.USER_NOT_FOUND));
        return new Profile(user, activeContext, authService.contextsFor(user));
    }

    /** The user plus the contexts they may act in, which the profile screen shows as a switcher. */
    public record Profile(User user, ContextType activeContext, List<AuthService.Context> availableContexts) {
    }
}
