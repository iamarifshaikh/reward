package com.intiq.reward.auth.service;

import com.intiq.reward.auth.entity.User;
import com.intiq.reward.auth.repository.UserRepository;
import com.intiq.reward.common.exception.DomainException;
import com.intiq.reward.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The user record itself, as opposed to the act of logging in. Profile reads and edits live here.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public User findById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new DomainException(ErrorCode.USER_NOT_FOUND));
    }
}
