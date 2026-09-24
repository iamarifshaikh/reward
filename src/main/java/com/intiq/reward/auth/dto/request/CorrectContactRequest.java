package com.intiq.reward.auth.dto.request;

import com.intiq.reward.common.constant.ValidationPatterns;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Fixing a mistyped contact, allowed only while the login is still UNVERIFIED. */
public record CorrectContactRequest(@Pattern(regexp = ValidationPatterns.PHONE) String phone,
                                    @Email @Size(max = 160) String email) {
}
