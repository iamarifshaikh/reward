package com.intiq.reward.auth.dto.request;

import com.intiq.reward.auth.enums.OtpChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OtpVerifyRequest(@NotNull OtpChannel channel,
                               @NotBlank @Size(max = 150) String destination,
                               @NotBlank @Pattern(regexp = "^\\d{4}$", message = "must be a 6 digit code") String code) {
}
