package com.intiq.reward.common.security;

import com.intiq.reward.common.enums.ActorType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The scope half of authorisation, used from {@code @PreAuthorize("@orgAccess.canManageOrg(#orgId)")}.
 * The type half comes from the authority on the token.
 *
 * <p>This answers "is this caller allowed to act for that organisation". Repositories then query by
 * id <em>and</em> organisation, so a wrong id returns 404 rather than 403 and cannot be probed.
 */
@Component("orgAccess")
public class OrgAccess {

    /** Read or write an organisation's own profile, KYC and settings. */
    public boolean canManageOrg(UUID orgId) {
        AuthPrincipal principal = current();
        return principal != null && (principal.isAdmin() || principal.belongsTo(orgId));
    }

    public boolean canViewOrg(UUID orgId) {
        return canManageOrg(orgId);
    }

    /** Only a brand manages its own channel, and the platform admin acting on its behalf. */
    public boolean canManagePartners(UUID brandOrgId) {
        AuthPrincipal principal = current();
        if (principal == null) {
            return false;
        }
        return principal.isAdmin() || (principal.isBrand() && principal.belongsTo(brandOrgId));
    }

    /** Enrolling and listing consumers belongs to a retailer. */
    public boolean canManageConsumers(UUID retailerOrgId) {
        AuthPrincipal principal = current();
        if (principal == null) {
            return false;
        }
        return principal.isAdmin() || (principal.isRetailer() && principal.belongsTo(retailerOrgId));
    }

    public boolean isAdmin() {
        AuthPrincipal principal = current();
        return principal != null && principal.isAdmin();
    }

    public boolean isActingAs(ActorType actorType) {
        AuthPrincipal principal = current();
        return principal != null && principal.actorType() == actorType;
    }

    /** Null when the request is unauthenticated, which every method above treats as "no". */
    public static AuthPrincipal current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthPrincipal principal)) {
            return null;
        }
        return principal;
    }
}
