package com.vanguard.entities;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "driver_logs")
@Data
public class DriverLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "driver_id")
    private User driver;

    @ManyToOne
    @JoinColumn(name = "request_id")
    private ServiceRequest request;

    @Column(name = "fuel_delivered")
    private Double fuelDelivered;

    @Column(name = "distance_traveled")
    private Double distanceTraveled;

    private Double earnings;

    @Column(name = "shift_start")
    private LocalDateTime shiftStart;

    @Column(name = "shift_end")
    private LocalDateTime shiftEnd;

    @Column(name = "jobs_completed")
    private Integer jobsCompleted;

    @Column(name = "rating")
    private Double rating;

    @Column(name = "customer_feedback")
    private String customerFeedback;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (jobsCompleted == null) jobsCompleted = 0;
    }
}
