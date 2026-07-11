package com.hgn.sos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "incident")
public class Incident {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "alert_id", nullable = false, unique = true)
    private UUID alertId;

    private String claimedBy;
    private Instant claimedAt;
    private Instant resolvedAt;

    @Column(columnDefinition = "TEXT")
    private String notes;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getAlertId() { return alertId; }
    public void setAlertId(UUID alertId) { this.alertId = alertId; }

    public String getClaimedBy() { return claimedBy; }
    public void setClaimedBy(String claimedBy) { this.claimedBy = claimedBy; }

    public Instant getClaimedAt() { return claimedAt; }
    public void setClaimedAt(Instant claimedAt) { this.claimedAt = claimedAt; }

    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}