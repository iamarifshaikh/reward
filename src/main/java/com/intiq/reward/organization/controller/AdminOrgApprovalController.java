package com.intiq.reward.organization.controller;

import com.intiq.reward.common.constant.ApiPaths;
import com.intiq.reward.common.dto.PageResponse;
import com.intiq.reward.common.security.AuthPrincipal;
import com.intiq.reward.common.security.CurrentUser;
import com.intiq.reward.organization.dto.request.RejectOrganizationRequest;
import com.intiq.reward.organization.dto.response.OrganizationSummaryResponse;
import com.intiq.reward.organization.service.OrganizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The platform admin's review queue: every brand-, distributor- or retailer-created organisation
 * still waiting on a decision, any type — separate from {@link AdminOrganizationController}, which
 * is brand-specific.
 */
@RestController
@RequestMapping(ApiPaths.V1 + "/admin/organizations")
@RequiredArgsConstructor
public class AdminOrgApprovalController {

    private final OrganizationService organizationService;

    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<OrganizationSummaryResponse> listPending(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return organizationService.listPending(pageable);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> approve(@PathVariable UUID id, @CurrentUser AuthPrincipal admin) {
        organizationService.approveOrganization(id, admin);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> reject(@PathVariable UUID id,
                                       @Valid @RequestBody RejectOrganizationRequest request,
                                       @CurrentUser AuthPrincipal admin) {
        organizationService.rejectOrganization(id, request.remarks(), admin);
        return ResponseEntity.noContent().build();
    }
}
