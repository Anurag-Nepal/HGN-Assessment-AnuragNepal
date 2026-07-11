package com.hgn.sos.repository;

import com.hgn.sos.model.Incident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface IncidentRepository extends JpaRepository<Incident, UUID> {
    
    Optional<Incident> findByAlertId(UUID alertId);

    @Modifying
    @Query("UPDATE Incident i SET i.claimedBy = :coordinatorId, i.claimedAt = :claimedAt WHERE i.alertId = :alertId")
    void updateClaim(@Param("alertId") UUID alertId, @Param("coordinatorId") String coordinatorId, @Param("claimedAt") Instant claimedAt);
}