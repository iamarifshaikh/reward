package com.intiq.reward.audit.controller;

import com.intiq.reward.audit.dto.response.AuditLogResponse;
import com.intiq.reward.audit.service.AuditQueryService;
import com.intiq.reward.common.constant.ApiPaths;
import com.intiq.reward.common.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** The admin's activity feed: everything recorded in the audit trail, admin-only. */
@RestController
@RequestMapping(ApiPaths.V1 + "/admin/audit-logs")
@RequiredArgsConstructor
public class AdminAuditController {

    private final AuditQueryService auditQueryService;

    /** {@code from}/{@code to} are both optional; leaving them out returns everything, as before. */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<AuditLogResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 50, sort = "occurredAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return auditQueryService.listAll(from, to, pageable);
    }
}
