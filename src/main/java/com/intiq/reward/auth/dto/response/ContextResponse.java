package com.intiq.reward.auth.dto.response;

import com.intiq.reward.auth.enums.ContextType;
import com.intiq.reward.common.enums.ActorType;

import java.util.UUID;

/** One side a login may act as, so the client can show a switcher without another call. */
public record ContextResponse(ContextType contextType,
                              UUID orgId,
                              String orgCode,
                              String displayName,
                              ActorType actorType) {
}
