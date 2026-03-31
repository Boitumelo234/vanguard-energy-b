package com.vanguard.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String phoneNumber;

    private String name;
    private String email;

    @Column(name = "user_type")
    private String userType; // CUSTOMER, DRIVER, ADMIN

    private String status; // ACTIVE, INACTIVE, SUSPENDED

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "current_latitude")
    private String currentLatitude;

    @Column(name = "current_longitude")
    private String currentLongitude;

    @Column(name = "is_online")
    private Boolean isOnline;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = "ACTIVE";
        if (isOnline == null) isOnline = false;
        if (userType == null) userType = "CUSTOMER";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}