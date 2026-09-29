package com.intiq.reward.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * OTP policy in one place, so tightening a limit is a config change rather than a code hunt.
 *
 * @param codeLength            digits in the code
 * @param ttl                   how long a code stays usable
 * @param maxAttempts           wrong guesses before the challenge is burned
 * @param resendCooldown        minimum gap between two codes to the same contact
 * @param maxPerDestination     codes allowed per destination inside {@code rateWindow}
 * @param maxPerIp              codes allowed per IP inside {@code ipRateWindow}
 * @param pepper                server-side secret mixed into the stored hash, so a database leak
 *                              does not let anyone brute-force six digits offline
 */
@ConfigurationProperties(prefix = "intiq.otp")
public record OtpProperties(int codeLength,Duration ttl,int maxAttempts,Duration resendCooldown,int maxPerDestination,Duration rateWindow,int maxPerIp,Duration ipRateWindow,String pepper) {}
