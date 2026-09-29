package com.intiq.reward.organization.dto.request;

import jakarta.validation.constraints.Size;

/** Admin rejecting a pending organisation. Remarks are optional but go straight to the audit trail. */
public record RejectOrganizationRequest(@Size(max = 500) String remarks) {
}
