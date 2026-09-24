package com.intiq.reward.organization.dto.request;

import jakarta.validation.constraints.Size;

/** Admin approving or rejecting one document. The service requires remarks on a rejection. */
public record KycDecisionRequest(@Size(max = 500) String remarks) {
}
