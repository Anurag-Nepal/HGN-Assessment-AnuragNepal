package com.hgn.sos.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    @Query("""
        SELECT o FROM Order o
        WHERE o.deviceId = :deviceId
          AND o.status = 'active'
          AND :ts BETWEEN o.startDate AND o.endDate
        """)
    List<Order> findActiveOrdersCoveringTimestamp(
            @Param("deviceId") UUID deviceId, @Param("ts") Instant ts);

    @Query("""
        SELECT o FROM Order o
        WHERE o.deviceId = :deviceId
          AND o.status = 'active'
          AND :graceStart <= o.endDate
          AND :graceEnd >= o.startDate
        """)
    List<Order> findActiveOrdersWithinGraceWindow(
            @Param("deviceId") UUID deviceId,
            @Param("graceStart") Instant graceStart, @Param("graceEnd") Instant graceEnd);
}