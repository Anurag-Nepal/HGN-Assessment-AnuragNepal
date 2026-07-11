package com.hgn.sos.repository;

import com.hgn.sos.model.Alert;
import com.hgn.sos.model.AlertStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlertRepository extends JpaRepository<Alert, UUID> {

    @Query("SELECT a FROM Alert a WHERE a.deviceId = :deviceId ORDER BY a.createdAt DESC LIMIT 1")
    Optional<Alert> findMostRecentByDeviceId(@Param("deviceId") UUID deviceId);

    @Modifying
    @Transactional
    @Query(value = """
        UPDATE alert SET status = 'CLAIMED', version = version + 1, updated_at = now()
        WHERE id = :alertId AND status = 'OPEN'
        """, nativeQuery = true)
    int claimIfOpen(@Param("alertId") UUID alertId);

    @Modifying
    @Transactional
    @Query(value = """
        UPDATE alert SET status = 'ESCALATED', urgent = true, updated_at = now()
        WHERE status = 'OPEN' AND created_at < :cutoff
        RETURNING *
        """, nativeQuery = true)
    List<Alert> escalateOlderThan(@Param("cutoff") Instant cutoff);

    List<Alert> findByStatus(AlertStatus status);

    List<Alert> findByOrderIdIsNull();
}