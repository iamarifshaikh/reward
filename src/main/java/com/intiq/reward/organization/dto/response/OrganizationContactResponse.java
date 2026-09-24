package com.intiq.reward.organization.dto.response;

import com.intiq.reward.auth.enums.UserStatus;

import java.util.UUID;

/** The single login of an organisation, which is also its contact. */
public record OrganizationContactResponse(UUID userId,
                                          String fullName,
                                          String phone,
                                          String email,
                                          UserStatus loginStatus) {
}
