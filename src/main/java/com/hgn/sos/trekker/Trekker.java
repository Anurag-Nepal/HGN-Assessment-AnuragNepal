package com.hgn.sos.trekker;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

import java.util.UUID;

@Entity
public class Trekker {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "emergency_contact", length = 128)
    private String emergencyContact;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmergencyContact() { return emergencyContact; }
    public void setEmergencyContact(String emergencyContact) { this.emergencyContact = emergencyContact; }
}