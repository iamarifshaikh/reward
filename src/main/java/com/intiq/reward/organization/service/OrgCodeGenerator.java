package com.intiq.reward.organization.service;

import com.intiq.reward.organization.enums.OrgType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The human-readable {@code BRD-00042} / {@code DST-000123} / {@code RTL-000456} codes, drawn from
 * the per-type sequences already created in {@code V2026_09_23_1000}. A database sequence, not an
 * application counter, so two concurrent creates can never race onto the same number.
 */
@Component
public class OrgCodeGenerator {

    private final JdbcTemplate jdbcTemplate;

    public OrgCodeGenerator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String next(OrgType orgType) {
        return switch (orgType) {
            case BRAND -> format("BRD", "seq_brand_code", 5);
            case DISTRIBUTOR -> format("DST", "seq_distributor_code", 6);
            case RETAILER -> format("RTL", "seq_retailer_code", 6);
            case PLATFORM -> "PLATFORM";
        };
    }

    private String format(String prefix, String sequenceName, int digits) {
        Long next = jdbcTemplate.queryForObject("select nextval('" + sequenceName + "')", Long.class);
        return prefix + "-" + String.format("%0" + digits + "d", next);
    }
}
