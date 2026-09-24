package com.intiq.reward.audit.repository;

import com.intiq.reward.audit.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

/**
 * Read-only in practice: rows are written once and never updated or deleted.
 * Specification support backs the admin search, which filters by actor, action, date range and scope.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>,
                                            JpaSpecificationExecutor<AuditLog> {

    /** Everything that happened to one tenant's data. */
    Page<AuditLog> findByScopeOrgIdOrderByOccurredAtDesc(UUID scopeOrgId, Pageable pageable);

    /** The history of one record, shown on its detail screen. */
    Page<AuditLog> findByEntityTypeAndEntityIdOrderByOccurredAtDesc(String entityType,
                                                                    UUID entityId,
                                                                    Pageable pageable);

    Page<AuditLog> findByActorUserIdOrderByOccurredAtDesc(UUID actorUserId, Pageable pageable);
}
