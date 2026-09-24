package com.intiq.reward.auth.dto.request;

import com.intiq.reward.auth.enums.ContextType;
import jakarta.validation.constraints.NotNull;

/**
 * A login that is both a business and a consumer picks a side; the choice is stamped on the session.
 */
public record SelectContextRequest(@NotNull ContextType contextType) {
}
