package com.intiq.reward.audit.service;

import com.intiq.reward.audit.dto.response.AuditLogResponse;
import com.intiq.reward.audit.entity.AuditLog;
import com.intiq.reward.audit.repository.AuditLogRepository;
import com.intiq.reward.common.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * The read side of the audit trail — {@link AuditService} only ever writes. Kept as its own class
 * rather than a new method on {@code AuditService}, so the class every other module calls to record
 * events stays untouched by this admin-only viewing feature.
 */
@Service
@RequiredArgsConstructor
public class AuditQueryService {

    /**
     * Same defensive pattern as the brand list: a blank or made-up sort value falls back to the
     * default instead of reaching the database, rather than crashing on a column that does not exist.
     */
    private static final Set<String> SORTABLE_PROPERTIES = Set.of("occurredAt", "action", "actorType", "entityType");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "occurredAt");

    private final AuditLogRepository auditLogRepository;

    /**
     * Every audit row, most recent first by default — the admin's full activity feed.
     * {@code from}/{@code to} are both optional; either or both left null means no bound on that side.
     */
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> listAll(Instant from, Instant to, Pageable pageable) {
        Page<AuditLog> page = auditLogRepository.findAll(occurredBetween(from, to), sanitizeSort(pageable));
        return PageResponse.of(page, AuditQueryService::toResponse);
    }

    private static Specification<AuditLog> occurredBetween(Instant from, Instant to) {
        return (root, query, cb) -> {
            if (from != null && to != null) {
                return cb.between(root.get("occurredAt"), from, to);
            }
            if (from != null) {
                return cb.greaterThanOrEqualTo(root.get("occurredAt"), from);
            }
            if (to != null) {
                return cb.lessThanOrEqualTo(root.get("occurredAt"), to);
            }
            return cb.conjunction();
        };
    }

    private static AuditLogResponse toResponse(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getOccurredAt(),
                log.getActorUserId(),
                null, // no name resolution yet — shown as a plain id until that is designed
                log.getActorType(),
                log.getAction(),
                log.getEntityType(),
                log.getEntityId(),
                log.getScopeOrgId(),
                log.getChanges(),
                log.getRequestId());
    }

    private Pageable sanitizeSort(Pageable pageable) {
        List<Sort.Order> validOrders = pageable.getSort().stream()
                .filter(order -> SORTABLE_PROPERTIES.contains(order.getProperty()))
                .toList();
        Sort sort = validOrders.isEmpty() ? DEFAULT_SORT : Sort.by(validOrders);
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }
}
