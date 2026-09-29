package com.intiq.reward.auth.repository;

import com.intiq.reward.auth.entity.SmsOtpAttempt;
import com.intiq.reward.auth.enums.SmsOtpStage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SmsOtpAttemptRepository extends JpaRepository<SmsOtpAttempt, UUID> {

    long countByDestinationAndStageAndCreatedAtAfter(String destination, SmsOtpStage stage, Instant since);

    long countByRequestIpAndStageAndCreatedAtAfter(String requestIp, SmsOtpStage stage, Instant since);

    /** The diagnostic view: everything that happened for one number, newest first. */
    List<SmsOtpAttempt> findByDestinationOrderByCreatedAtDesc(String destination);

    @Modifying
    @Query("delete from SmsOtpAttempt a where a.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
