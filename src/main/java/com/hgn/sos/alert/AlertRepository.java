package com.hgn.sos.alert;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AlertRepository extends JpaRepository<Alert, UUID> {

    @Query("SELECT a FROM Alert a WHERE a.deviceId = :deviceId ORDER BY a.createdAt DESC LIMIT 1")
    Optional<Alert> findMostRecentByDeviceId(@Param("deviceId") UUID deviceId);
}