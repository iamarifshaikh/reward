package com.intiq.reward.auth.dto.request;

import com.intiq.reward.auth.enums.OtpChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Asks for a code. The destination is a phone in E.164 or an email; channel says which.
 * The response is always the same whether or not the contact exists, so this endpoint
 * cannot be used to discover who is registered.
 */
public record OtpRequest(@NotNull OtpChannel channel,
                         @NotBlank @Size(max = 150) String destination) {
}
