package com.hgn.sos.repository;

import com.hgn.sos.model.Alert;
import com.hgn.sos.model.AlertStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlertRepository extends JpaRepository<Alert, UUID> {

    @Query("SELECT a FROM Alert a WHERE a.deviceId = :deviceId ORDER BY a.createdAt DESC LIMIT 1")
    Optional<Alert> findMostRecentByDeviceId(@Param("deviceId") UUID deviceId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE alert SET status = 'CLAIMED', claimed_by = :claimedBy, claimed_at = now(),
                          updated_at = now()
        WHERE id = :alertId AND status = 'OPEN'
        """, nativeQuery = true)
    int claimIfOpen(@Param("alertId") UUID alertId, @Param("claimedBy") String claimedBy);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE alert SET status = 'ESCALATED', urgent = true, updated_at = now()
        WHERE status = 'OPEN' AND created_at < :cutoff
        RETURNING *
        """, nativeQuery = true)
    List<Alert> escalateOlderThan(@Param("cutoff") Instant cutoff);

    Page<Alert> findByStatus(AlertStatus status, Pageable pageable);

    List<Alert> findByStatus(AlertStatus status);

    @Query("SELECT a FROM Alert a WHERE a.orderId IS NULL ORDER BY a.receivedAt DESC")
    List<Alert> findUnresolvedOwnership();

    List<Alert> findByOrderIdIsNull();
}