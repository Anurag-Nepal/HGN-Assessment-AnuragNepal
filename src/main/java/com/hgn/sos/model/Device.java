package com.hgn.sos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

import java.util.UUID;

@Entity
public class Device {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "external_device_id", nullable = false, unique = true, length = 64)
    private String externalDeviceId;

    @Column(nullable = false, length = 16)
    private String status = "active";

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getExternalDeviceId() { return externalDeviceId; }
    public void setExternalDeviceId(String externalDeviceId) { this.externalDeviceId = externalDeviceId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}