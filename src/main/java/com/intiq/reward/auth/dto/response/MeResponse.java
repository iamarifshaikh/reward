package com.intiq.reward.auth.dto.response;

import com.intiq.reward.auth.enums.Gender;
import com.intiq.reward.auth.enums.UserStatus;

import java.time.Instant;
import java.util.UUID;

/** The signed-in login: who they are, and the one identity they act as. */
public record MeResponse(UUID id,
                         String phone,
                         String email,
                         String fullName,
                         UserStatus status,
                         ContextResponse context,
                         Gender gender,
                         String city,
                         String pincode,
                         Instant consumerEnrolledAt,
                         Instant lastLoginAt) {
}
