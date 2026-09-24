package com.intiq.reward.common.config;

import com.intiq.reward.common.security.AuthPrincipal;
import com.intiq.reward.common.security.OrgAccess;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;
import java.util.UUID;

/**
 * Fills created_by and updated_by from the authenticated caller, so no service has to remember to
 * set them. Returns empty for unauthenticated work such as migrations and scheduled jobs, which
 * leaves the columns null, meaning "the system did it".
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<UUID> auditorAware() {
        return () -> Optional.ofNullable(OrgAccess.current()).map(AuthPrincipal::userId);
    }
}
