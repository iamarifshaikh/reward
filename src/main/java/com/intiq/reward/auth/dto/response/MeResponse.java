package com.intiq.reward.auth.dto.response;

import com.intiq.reward.auth.enums.ContextType;
import com.intiq.reward.auth.enums.Gender;
import com.intiq.reward.auth.enums.UserStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The signed-in login: who they are, which side they are on, and what else they could switch to. */
public record MeResponse(UUID id,
                         String phone,
                         String email,
                         String fullName,
                         UserStatus status,
                         ContextType activeContext,
                         List<ContextResponse> availableContexts,
                         Gender gender,
                         String city,
                         String pincode,
                         Instant consumerEnrolledAt,
                         Instant lastLoginAt) {
}
