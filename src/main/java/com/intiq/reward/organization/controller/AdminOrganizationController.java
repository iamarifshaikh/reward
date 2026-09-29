package com.intiq.reward.organization.controller;

import com.intiq.reward.common.constant.ApiPaths;
import com.intiq.reward.common.dto.PageResponse;
import com.intiq.reward.common.security.AuthPrincipal;
import com.intiq.reward.common.security.CurrentUser;
import com.intiq.reward.organization.dto.request.CreateBrandRequest;
import com.intiq.reward.organization.dto.response.BrandCardResponse;
import com.intiq.reward.organization.dto.response.OrganizationResponse;
import com.intiq.reward.organization.service.OrganizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/** Platform-admin actions on organisations. Every endpoint here is admin-only. */
@RestController
@RequestMapping(ApiPaths.V1 + "/admin/brands")
@RequiredArgsConstructor
public class AdminOrganizationController {

    private final OrganizationService organizationService;

    /** Creates a brand and its one login in a single call. The brand can log in immediately after. */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OrganizationResponse> createBrand(@Valid @RequestBody CreateBrandRequest request,
                                                            @CurrentUser AuthPrincipal admin) {
        OrganizationResponse response = organizationService.createBrand(request, admin);
        return ResponseEntity.created(URI.create(ApiPaths.V1 + "/admin/brands/" + response.id())).body(response);
    }

    /** The brand card list: name, code, status, KYC status, distributor and retailer counts. */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<BrandCardResponse> listBrands(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return organizationService.listBrands(pageable);
    }
}
