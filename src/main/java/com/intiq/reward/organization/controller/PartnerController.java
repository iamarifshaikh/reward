package com.intiq.reward.organization.controller;

import com.intiq.reward.common.constant.ApiPaths;
import com.intiq.reward.common.security.AuthPrincipal;
import com.intiq.reward.common.security.CurrentUser;
import com.intiq.reward.organization.dto.request.CreatePartnerRequest;
import com.intiq.reward.organization.dto.request.CreateRetailerRequest;
import com.intiq.reward.organization.dto.request.UpdatePendingPartnerRequest;
import com.intiq.reward.organization.dto.response.OrganizationResponse;
import com.intiq.reward.organization.service.OrganizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/**
 * Building the partner network. A brand may only create a distributor, which starts
 * {@code PENDING_APPROVAL} and cannot sign in until an admin approves it. A distributor may only
 * create a retailer under itself, which needs no admin approval and can sign in right away.
 */
@RestController
@RequestMapping(ApiPaths.V1 + "/partners")
@RequiredArgsConstructor
public class PartnerController {

    private final OrganizationService organizationService;

    /** Brand → distributor. Pending admin approval; see {@link OrganizationService#createPartner}. */
    @PostMapping
    @PreAuthorize("hasRole('BRAND')")
    public ResponseEntity<OrganizationResponse> createPartner(@Valid @RequestBody CreatePartnerRequest request,
                                                               @CurrentUser AuthPrincipal brand) {
        OrganizationResponse response = organizationService.createPartner(request, brand);
        return ResponseEntity.created(URI.create(ApiPaths.V1 + "/partners/" + response.id())).body(response);
    }

    /** Distributor → retailer. Auto-approved; see {@link OrganizationService#createRetailer}. */
    @PostMapping("/retailers")
    @PreAuthorize("hasRole('DISTRIBUTOR')")
    public ResponseEntity<OrganizationResponse> createRetailer(@Valid @RequestBody CreateRetailerRequest request,
                                                                @CurrentUser AuthPrincipal distributor) {
        OrganizationResponse response = organizationService.createRetailer(request, distributor);
        return ResponseEntity.created(URI.create(ApiPaths.V1 + "/partners/retailers/" + response.id())).body(response);
    }

    /**
     * Correcting a distributor that is still pending or was rejected. Refuses once it's approved —
     * see {@link OrganizationService#updatePartner}.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('BRAND')")
    public OrganizationResponse updatePartner(@PathVariable UUID id,
                                              @Valid @RequestBody UpdatePendingPartnerRequest request,
                                              @CurrentUser AuthPrincipal brand) {
        return organizationService.updatePartner(id, request, brand);
    }

    /** Puts a rejected distributor back in the admin queue after it's been corrected. */
    @PostMapping("/{id}/resubmit")
    @PreAuthorize("hasRole('BRAND')")
    public ResponseEntity<Void> resubmitPartner(@PathVariable UUID id, @CurrentUser AuthPrincipal brand) {
        organizationService.resubmitPartner(id, brand);
        return ResponseEntity.noContent().build();
    }
}
