package com.intiq.reward.auth.repository;

import com.intiq.reward.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByPhone(String phone);

    Optional<User> findByEmail(String email);

    boolean existsByPhone(String phone);

    boolean existsByEmail(String email);

    /** Login lookup: the OTP destination is a phone or an email, and we do not know which. */
    @Query("select u from User u where u.phone = :destination or u.email = :destination")
    Optional<User> findByPhoneOrEmail(@Param("destination") String destination);

    List<User> findByOrgId(UUID orgId);

    /** Enforces one login per business (§1.1); the platform organisation is allowed more. */
    long countByOrgId(UUID orgId);

    /** Consumers enrolled by one retailer, used by the store's consumer list. */
    List<User> findByEnrolledByOrgId(UUID retailerOrgId);
}
