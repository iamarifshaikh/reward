package com.intiq.reward.auth.controller;

import com.intiq.reward.auth.dto.response.ContextResponse;
import com.intiq.reward.auth.dto.response.MeResponse;
import com.intiq.reward.auth.entity.User;
import com.intiq.reward.auth.enums.ContextType;
import com.intiq.reward.auth.service.AuthService;
import com.intiq.reward.auth.service.UserService;
import com.intiq.reward.common.constant.ApiPaths;
import com.intiq.reward.common.enums.ActorType;
import com.intiq.reward.common.security.AuthPrincipal;
import com.intiq.reward.common.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.V1 + "/me")
@RequiredArgsConstructor
public class MeController {

    private final UserService userService;

    /** Who am I, and what else could I act as. Any authenticated caller may read their own record. */
    @GetMapping
    public MeResponse me(@CurrentUser AuthPrincipal principal) {
        ContextType activeContext = principal.actorType() == ActorType.CONSUMER
                ? ContextType.CONSUMER
                : ContextType.ORG;

        UserService.Profile profile = userService.profile(principal.userId(), activeContext);
        User user = profile.user();

        return new MeResponse(
                user.getId(),
                user.getPhone(),
                user.getEmail(),
                user.getFullName(),
                user.getStatus(),
                profile.activeContext(),
                profile.availableContexts().stream()
                        .map(context -> new ContextResponse(context.contextType(), context.orgId(),
                                context.orgCode(), context.displayName(), context.actorType()))
                        .toList(),
                user.getGender(),
                user.getCity(),
                user.getPincode(),
                user.getConsumerEnrolledAt(),
                user.getLastLoginAt());
    }
}
