package com.intiq.reward.audit.service;

import com.intiq.reward.audit.entity.AuditLog;
import com.intiq.reward.audit.repository.AuditLogRepository;
import com.intiq.reward.common.enums.ActorType;
import com.intiq.reward.common.security.AuthPrincipal;
import com.intiq.reward.common.security.OrgAccess;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

/**
 * Writes the audit trail. Called from inside the caller's transaction on purpose: if the change
 * rolls back, its audit row goes with it, so the trail never claims something that did not happen.
 *
 * <p>This is the only class other modules use from {@code audit}.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final Clock clock;

    /** Standard case: the signed-in caller changed something. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void record(String action, String entityType, UUID entityId, UUID scopeOrgId, Map<String, Object> changes) {
        AuthPrincipal principal = OrgAccess.current();
        auditLogRepository.save(AuditLog.record(
                clock.instant(),
                principal == null ? null : principal.userId(),
                principal == null ? null : principal.orgId(),
                principal == null ? null : principal.actorType(),
                action,
                entityType,
                entityId,
                scopeOrgId,
                changes,
                currentIp(),
                MDC.get("requestId")));
    }

    /**
     * For events with no signed-in caller: a failed login, or a scheduled job. Actor columns stay
     * null, which reads as "no human did this".
     */
    @Transactional
    public void recordAnonymous(String action, String entityType, UUID entityId, Map<String, Object> changes, String ip) {
        auditLogRepository.save(AuditLog.record(
                clock.instant(), null, null, null,
                action, entityType, entityId, null, changes, ip, MDC.get("requestId")));
    }

    /** Used when the actor is known but not from the security context, such as a first login. */
    @Transactional
    public void recordAs(UUID actorUserId,
                         UUID actorOrgId,
                         ActorType actorType,
                         String action,
                         String entityType,
                         UUID entityId,
                         UUID scopeOrgId,
                         Map<String, Object> changes,
                         String ip) {
        auditLogRepository.save(AuditLog.record(
                clock.instant(), actorUserId, actorOrgId, actorType,
                action, entityType, entityId, scopeOrgId, changes, ip, MDC.get("requestId")));
    }

    private String currentIp() {
        return MDC.get("clientIp");
    }
}
