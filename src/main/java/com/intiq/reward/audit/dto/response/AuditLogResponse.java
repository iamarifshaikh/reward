package com.intiq.reward.audit.dto.response;

import com.intiq.reward.common.enums.ActorType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditLogResponse(Long id,Instant occurredAt,UUID actorUserId,String actorName,ActorType actorType,String action,String entityType,UUID entityId,UUID scopeOrgId,Map<String, Object> changes,String requestId) {}
