package com.vanguard.services;

import com.vanguard.entities.Notification;
import com.vanguard.entities.ServiceRequest;
import com.vanguard.entities.User;
import com.vanguard.repositories.NotificationRepository;
import com.vanguard.repositories.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Central notification hub.
 *
 * Every important event calls one of the convenience methods here.
 * Each method:
 *   1. Persists a Notification row (so users can see history).
 *   2. Pushes a real-time WebSocket message to the relevant topic/queue.
 */
@Service
@Slf4j
public class NotificationService {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    // ─── Public convenience methods ───────────────────────────────────────────

    /** Called when a new PENDING request is created — broadcasts to ALL online drivers */
    @Transactional
    public void notifyNewJobAvailable(ServiceRequest request) {
        String title   = "New Job Available";
        String message = buildJobMessage(request);

        // Broadcast to all drivers subscribed to /topic/jobs
        Map<String, Object> payload = buildPayload(title, message, request, "JOB_AVAILABLE");
        messagingTemplate.convertAndSend("/topic/jobs", payload);

        log.info("Broadcasted new job #{} to all drivers", request.getId());
    }

    /** Called when a driver accepts a job — notifies the customer */
    @Transactional
    public void notifyJobAccepted(ServiceRequest request) {
        String title   = "Driver Assigned!";
        String message = String.format(
                "%s is on the way to you. Track them in real time.",
                driverName(request)
        );
        persistAndSend(request.getCustomer(), request, title, message, "JOB_ACCEPTED");
    }

    /** Called when driver sets status → EN_ROUTE */
    @Transactional
    public void notifyDriverEnRoute(ServiceRequest request) {
        String title   = "Driver En Route";
        String message = String.format(
                "%s is heading to your location. Estimated arrival soon.",
                driverName(request)
        );
        persistAndSend(request.getCustomer(), request, title, message, "DRIVER_EN_ROUTE");

        // Also push to per-job topic so the tracking widget updates
        messagingTemplate.convertAndSend(
                "/topic/job/" + request.getId(),
                buildPayload(title, message, request, "DRIVER_EN_ROUTE")
        );
    }

    /** Called when service is completed */
    @Transactional
    public void notifyJobCompleted(ServiceRequest request) {
        // Notify customer
        String custTitle = "Service Completed ✓";
        String custMsg   = String.format(
                "Your service is complete. Total: R%.2f. Please rate your experience.",
                request.getFinalPrice() != null ? request.getFinalPrice() : 0.0
        );
        persistAndSend(request.getCustomer(), request, custTitle, custMsg, "JOB_COMPLETED");

        // Notify driver
        if (request.getDriver() != null) {
            String drvTitle = "Job Completed — Earnings Updated";
            String drvMsg   = String.format(
                    "Job #%d completed. You earned R%.2f.",
                    request.getId(),
                    request.getFinalPrice() != null ? request.getFinalPrice() : 0.0
            );
            persistAndSend(request.getDriver(), request, drvTitle, drvMsg, "JOB_COMPLETED");
        }
    }

    /** Called when a job is cancelled */
    @Transactional
    public void notifyJobCancelled(ServiceRequest request, String cancelledBy) {
        String title   = "Job Cancelled";
        String message = "Job #" + request.getId() + " has been cancelled.";

        // Notify customer
        persistAndSend(request.getCustomer(), request, title, message, "JOB_CANCELLED");

        // Notify driver if one was assigned
        if (request.getDriver() != null) {
            persistAndSend(request.getDriver(), request, title,
                    "Job #" + request.getId() + " was cancelled by the " + cancelledBy + ".",
                    "JOB_CANCELLED");
        }

        // Remove it from the jobs broadcast topic
        messagingTemplate.convertAndSend("/topic/job/" + request.getId(),
                Map.of("type", "JOB_CANCELLED", "requestId", request.getId()));
    }

    /** Send a driver's location update to the customer's tracking topic */
    public void broadcastDriverLocation(Long requestId, String latitude, String longitude, Long driverId) {
        Map<String, Object> location = new HashMap<>();
        location.put("type", "LOCATION_UPDATE");
        location.put("requestId", requestId);
        location.put("latitude", latitude);
        location.put("longitude", longitude);
        location.put("driverId", driverId);

        messagingTemplate.convertAndSend("/topic/driver/" + driverId + "/location", location);
        messagingTemplate.convertAndSend("/topic/job/" + requestId, location);
    }

    /** Notify a single user about a payment event */
    @Transactional
    public void notifyPaymentReceived(User customer, ServiceRequest request, Double amount) {
        String title   = "Payment Confirmed";
        String message = String.format("Payment of R%.2f received for job #%d. Thank you!", amount, request.getId());
        persistAndSend(customer, request, title, message, "PAYMENT_RECEIVED");
    }

    // ─── Query methods ────────────────────────────────────────────────────────

    public List<Notification> getNotificationsForUser(User user) {
        return notificationRepository.findByUserOrderByCreatedAtDesc(user);
    }

    public long getUnreadCount(User user) {
        return notificationRepository.countByUserAndIsReadFalse(user);
    }

    @Transactional
    public void markAllRead(User user) {
        notificationRepository.markAllReadForUser(user);
    }

    @Transactional
    public void markOneRead(Long notificationId, User user) {
        notificationRepository.markOneRead(notificationId, user);
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    private void persistAndSend(User recipient, ServiceRequest request,
                                String title, String message, String type) {
        // 1. Persist
        Notification n = new Notification();
        n.setUser(recipient);
        n.setRequest(request);
        n.setTitle(title);
        n.setMessage(message);
        n.setNotificationType(type);
        notificationRepository.save(n);

        // 2. Real-time push to the user's personal queue
        Map<String, Object> payload = buildPayload(title, message, request, type);
        messagingTemplate.convertAndSendToUser(
                recipient.getId().toString(),
                "/queue/notifications",
                payload
        );

        log.debug("Notification sent to user {} [{}]: {}", recipient.getId(), type, title);
    }

    private Map<String, Object> buildPayload(String title, String message,
                                             ServiceRequest request, String type) {
        Map<String, Object> p = new HashMap<>();
        p.put("type", type);
        p.put("title", title);
        p.put("message", message);
        p.put("requestId", request.getId());
        p.put("requestType", request.getRequestType());
        p.put("status", request.getStatus());
        p.put("estimatedPrice", request.getEstimatedPrice());
        if (request.getDriver() != null) {
            p.put("driverId", request.getDriver().getId());
            p.put("driverName", request.getDriver().getName());
        }
        return p;
    }

    private String buildJobMessage(ServiceRequest request) {
        if ("FUEL_DELIVERY".equals(request.getRequestType())) {
            return String.format("Fuel delivery: %.0f L %s near %s. Earn R%.2f",
                    request.getFuelQuantity() != null ? request.getFuelQuantity() : 0,
                    request.getFuelType() != null ? request.getFuelType() : "",
                    shorten(request.getAddress()),
                    request.getEstimatedPrice() != null ? request.getEstimatedPrice() : 0.0);
        } else {
            return String.format("Roadside: %s near %s. Earn R%.2f",
                    request.getIssueType() != null ? request.getIssueType() : "Assistance",
                    shorten(request.getAddress()),
                    request.getEstimatedPrice() != null ? request.getEstimatedPrice() : 0.0);
        }
    }

    private String driverName(ServiceRequest r) {
        if (r.getDriver() != null && r.getDriver().getName() != null) {
            return r.getDriver().getName();
        }
        return "Your driver";
    }

    private String shorten(String address) {
        if (address == null) return "your location";
        return address.length() > 40 ? address.substring(0, 40) + "…" : address;
    }
}
