package com.intiq.reward.audit.entity;

import com.intiq.reward.common.enums.ActorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Append-only record of who did what. Deliberately has no foreign keys: it points at rows in
 * several different tables and must outlive whatever happens to them.
 * Written in the same transaction as the change it describes, so a rollback drops both.
 */
@Entity
@Table(name = "audit_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog {

    /** A plain sequence: this is the largest table on the platform and is never exposed in a URL. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "actor_user_id", updatable = false)
    private UUID actorUserId;

    @Column(name = "actor_org_id", updatable = false)
    private UUID actorOrgId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", length = 16, updatable = false)
    private ActorType actorType;

    /** Stable code such as ORG_CREATED, KYC_APPROVED, LOGIN_FAILED. */
    @Column(name = "action", nullable = false, length = 60, updatable = false)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 40, updatable = false)
    private String entityType;

    @Column(name = "entity_id", updatable = false)
    private UUID entityId;

    /** Whose data was touched, which is not always the actor's own organisation. */
    @Column(name = "scope_org_id", updatable = false)
    private UUID scopeOrgId;

    /** Field-level diff, e.g. {"status": ["UNVERIFIED", "ACTIVE"]}. PII is masked before it is written. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "changes", updatable = false)
    private Map<String, Object> changes;

    @Column(name = "ip", length = 45, updatable = false)
    private String ip;

    @Column(name = "request_id", length = 40, updatable = false)
    private String requestId;

    public static AuditLog record(Instant occurredAt,
                                  UUID actorUserId,
                                  UUID actorOrgId,
                                  ActorType actorType,
                                  String action,
                                  String entityType,
                                  UUID entityId,
                                  UUID scopeOrgId,
                                  Map<String, Object> changes,
                                  String ip,
                                  String requestId) {
        AuditLog log = new AuditLog();
        log.occurredAt = occurredAt;
        log.actorUserId = actorUserId;
        log.actorOrgId = actorOrgId;
        log.actorType = actorType;
        log.action = action;
        log.entityType = entityType;
        log.entityId = entityId;
        log.scopeOrgId = scopeOrgId;
        log.changes = changes;
        log.ip = ip;
        log.requestId = requestId;
        return log;
    }
}
