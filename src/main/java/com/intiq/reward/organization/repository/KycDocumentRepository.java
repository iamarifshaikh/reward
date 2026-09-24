package com.intiq.reward.organization.repository;

import com.intiq.reward.organization.entity.KycDocument;
import com.intiq.reward.organization.enums.KycDocStatus;
import com.intiq.reward.organization.enums.KycDocType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KycDocumentRepository extends JpaRepository<KycDocument, UUID> {

    List<KycDocument> findByOrgIdOrderByCreatedAtDesc(UUID orgId);

    /**
     * Tenant-scoped read: a document id from another organisation returns empty, so the caller
     * answers 404 rather than 403 and ids cannot be probed.
     */
    Optional<KycDocument> findByIdAndOrgId(UUID id, UUID orgId);

    /** Previous files of the same type, marked SUPERSEDED when a new one is uploaded. */
    List<KycDocument> findByOrgIdAndDocTypeAndStatus(UUID orgId, KycDocType docType, KycDocStatus status);

    Page<KycDocument> findByStatus(KycDocStatus status, Pageable pageable);

    long countByOrgIdAndStatus(UUID orgId, KycDocStatus status);
}
