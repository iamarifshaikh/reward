package com.intiq.reward.auth.dto.request;

import com.intiq.reward.auth.enums.Gender;
import com.intiq.reward.common.constant.ValidationPatterns;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateConsumerProfileRequest(@Size(max = 120) String fullName,
                                           Gender gender,
                                           @Size(max = 80) String city,
                                           @Pattern(regexp = ValidationPatterns.PINCODE) String pincode) {
}
