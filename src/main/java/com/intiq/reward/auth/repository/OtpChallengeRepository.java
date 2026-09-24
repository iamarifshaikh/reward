package com.intiq.reward.auth.repository;

import com.intiq.reward.auth.entity.OtpChallenge;
import com.intiq.reward.auth.enums.OtpPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, UUID> {

    /** The challenge a verify attempt is matched against: newest code for that destination and purpose. */
    Optional<OtpChallenge> findFirstByDestinationAndPurposeOrderByCreatedAtDesc(String destination, OtpPurpose purpose);

    /** Rate limiting: how many codes went to this destination recently. */
    long countByDestinationAndCreatedAtAfter(String destination, Instant since);

    long countByRequestIpAndCreatedAtAfter(String requestIp, Instant since);

    @Modifying
    @Query("delete from OtpChallenge o where o.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
