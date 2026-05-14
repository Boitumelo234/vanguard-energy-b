package com.vanguard.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Persisted notification record.
 * Every important event (job accepted, driver en route, job completed, etc.)
 * creates a Notification row so users can view their history even after
 * the WebSocket message is gone.
 */
@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notif_user", columnList = "user_id"),
        @Index(name = "idx_notif_read", columnList = "is_read"),
        @Index(name = "idx_notif_created", columnList = "created_at")
})
@Data
@NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Recipient
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Optional link back to the request this notification is about
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id")
    private ServiceRequest request;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 500)
    private String message;

    /**
     * JOB_ASSIGNED, JOB_ACCEPTED, DRIVER_EN_ROUTE, JOB_COMPLETED,
     * JOB_CANCELLED, PAYMENT_RECEIVED, SHIFT_REMINDER, SYSTEM
     */
    @Column(name = "notification_type", nullable = false)
    private String notificationType;

    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (isRead == null) isRead = false;
    }
}
