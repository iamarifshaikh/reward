package com.intiq.reward.auth.dto.request;

import com.intiq.reward.common.constant.ValidationPatterns;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A retailer enrolling a shopper. Counter enrolment usually has only a phone, so either contact
 * is enough; the service rejects both being blank, matching the users_contact constraint.
 */
public record CreateConsumerRequest(@Pattern(regexp = ValidationPatterns.PHONE) String phone,
                                    @Email @Size(max = 160) String email,
                                    @Size(max = 120) String fullName) {
}
