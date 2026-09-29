package com.intiq.reward.auth.controller;

import com.intiq.reward.auth.dto.response.ContextResponse;
import com.intiq.reward.auth.dto.response.MeResponse;
import com.intiq.reward.auth.entity.User;
import com.intiq.reward.auth.service.AuthService;
import com.intiq.reward.auth.service.UserService;
import com.intiq.reward.common.constant.ApiPaths;
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
    private final AuthService authService;

    /** Who am I, and which one identity I act as. Any authenticated caller may read their own record. */
    @GetMapping
    public MeResponse me(@CurrentUser AuthPrincipal principal) {
        User user = userService.findById(principal.userId());
        AuthService.Context context = authService.resolveContext(user);

        return new MeResponse(
                user.getId(),
                user.getPhone(),
                user.getEmail(),
                user.getFullName(),
                user.getStatus(),
                toResponse(context),
                user.getGender(),
                user.getCity(),
                user.getPincode(),
                user.getConsumerEnrolledAt(),
                user.getLastLoginAt());
    }

    private static ContextResponse toResponse(AuthService.Context context) {
        return new ContextResponse(context.contextType(), context.orgId(), context.orgCode(),
                context.displayName(), context.actorType());
    }
}
