package com.vanguard.entities;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "service_requests")
@Data
public class ServiceRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "customer_id")
    private User customer;

    @ManyToOne
    @JoinColumn(name = "driver_id")
    private User driver;

    @Column(name = "request_type")
    private String requestType; // FUEL_DELIVERY, ROADSIDE_ASSISTANCE

    private String status; // PENDING, ACCEPTED, EN_ROUTE, IN_PROGRESS, COMPLETED, CANCELLED

    @Column(name = "pickup_latitude")
    private String pickupLatitude;

    @Column(name = "pickup_longitude")
    private String pickupLongitude;

    @Column(name = "dropoff_latitude")
    private String dropoffLatitude;

    @Column(name = "dropoff_longitude")
    private String dropoffLongitude;

    private String address;

    @Column(name = "vehicle_type")
    private String vehicleType;

    @Column(name = "license_plate")
    private String licensePlate;

    @Column(name = "fuel_type")
    private String fuelType; // PETROL, DIESEL

    @Column(name = "fuel_quantity")
    private Double fuelQuantity;

    @Column(name = "issue_type")
    private String issueType; // FLAT_TIRE, BATTERY, TOWING, ENGINE, OTHER

    @Column(name = "estimated_price")
    private Double estimatedPrice;

    @Column(name = "final_price")
    private Double finalPrice;

    @Column(name = "payment_method")
    private String paymentMethod; // CASH, CARD

    @Column(name = "is_paid")
    private Boolean isPaid;

    @Column(name = "requested_at")
    private LocalDateTime requestedAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    private String notes;

    @Column(name = "otp_code")
    private String otpCode;

    @Column(name = "distance_km")
    private Double distanceKm;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @PrePersist
    protected void onCreate() {
        requestedAt = LocalDateTime.now();
        status = "PENDING";
        isPaid = false;
    }
}