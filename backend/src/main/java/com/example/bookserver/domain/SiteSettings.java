package com.example.bookserver.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "site_settings")
public class SiteSettings {

    public static final int SINGLETON_ID = 1;

    @Id
    private Integer id = SINGLETON_ID;

    @Column(name = "registration_enabled", nullable = false)
    private boolean registrationEnabled = true;

    @Column(name = "first_public_registration_completed", nullable = false)
    private boolean firstPublicRegistrationCompleted;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public boolean isRegistrationEnabled() { return registrationEnabled; }
    public void setRegistrationEnabled(boolean registrationEnabled) { this.registrationEnabled = registrationEnabled; }
    public boolean isFirstPublicRegistrationCompleted() { return firstPublicRegistrationCompleted; }
    public void setFirstPublicRegistrationCompleted(boolean firstPublicRegistrationCompleted) {
        this.firstPublicRegistrationCompleted = firstPublicRegistrationCompleted;
    }
}
