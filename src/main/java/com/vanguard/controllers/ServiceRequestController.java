package com.vanguard.controllers;

import com.vanguard.dto.ServiceRequestDTO;
import com.vanguard.dto.UpdateStatusDTO;
import com.vanguard.entities.ServiceRequest;
import com.vanguard.entities.User;
import com.vanguard.repositories.ServiceRequestRepository;
import com.vanguard.repositories.UserRepository;
import com.vanguard.services.NotificationService;
import com.vanguard.services.PricingService;
import com.vanguard.utils.JwtUtil;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/requests")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001"})
@Slf4j
public class ServiceRequestController {

    @Autowired private ServiceRequestRepository requestRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private PricingService pricingService;
    @Autowired private NotificationService notificationService;

    // ─── Create ───────────────────────────────────────────────────────────────

    @PostMapping("/create")
    public ResponseEntity<?> createRequest(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody ServiceRequestDTO dto) {

        Long userId = extractUserId(authHeader);
        User customer = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        ServiceRequest request = new ServiceRequest();
        request.setCustomer(customer);
        request.setRequestType(dto.getRequestType());
        request.setPickupLatitude(dto.getPickupLatitude());
        request.setPickupLongitude(dto.getPickupLongitude());
        request.setAddress(dto.getAddress());
        request.setVehicleType(dto.getVehicleType());
        request.setLicensePlate(dto.getLicensePlate());
        request.setFuelType(dto.getFuelType());
        request.setFuelQuantity(dto.getFuelQuantity());
        request.setIssueType(dto.getIssueType());
        request.setNotes(dto.getNotes());
        //request.setPaymentMethod(dto.getPaymentMethod() != null ? dto.getPaymentMethod() : "CASH");

        double price = pricingService.calculatePrice(request);
        request.setEstimatedPrice(price);

        // 4-digit OTP for service completion verification
        String otp = String.format("%04d", (int)(Math.random() * 10000));
        request.setOtpCode(otp);

        ServiceRequest saved = requestRepository.save(request);

        // Broadcast to all online drivers immediately
        notificationService.notifyNewJobAvailable(saved);

        log.info("New {} request #{} created by customer {}", saved.getRequestType(), saved.getId(), userId);

        return ResponseEntity.ok(Map.of(
                "success",        true,
                "requestId",      saved.getId(),
                "estimatedPrice", price,
                "otpCode",        otp,     // customer uses this to confirm completion
                "message",        "Request submitted! We are finding a driver for you."
        ));
    }

    // ─── Customer queries ─────────────────────────────────────────────────────

    @GetMapping("/customer")
    public ResponseEntity<?> getCustomerRequests(@RequestHeader("Authorization") String authHeader) {
        Long userId = extractUserId(authHeader);
        return ResponseEntity.ok(requestRepository.findByCustomerId(userId));
    }

    @GetMapping("/customer/active")
    public ResponseEntity<?> getActiveCustomerRequests(@RequestHeader("Authorization") String authHeader) {
        Long userId = extractUserId(authHeader);
        return ResponseEntity.ok(requestRepository.findActiveRequestsByCustomer(userId));
    }

    @GetMapping("/customer/{requestId}")
    public ResponseEntity<?> getCustomerRequest(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long requestId) {

        Long userId = extractUserId(authHeader);
        ServiceRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        if (!request.getCustomer().getId().equals(userId)) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        // Include driver location if en route
        Map<String, Object> result = new HashMap<>();
        result.put("request", request);
        if (request.getDriver() != null &&
                ("EN_ROUTE".equals(request.getStatus()) || "IN_PROGRESS".equals(request.getStatus()))) {
            result.put("driverLatitude",  request.getDriver().getCurrentLatitude());
            result.put("driverLongitude", request.getDriver().getCurrentLongitude());
        }

        return ResponseEntity.ok(result);
    }

    // ─── Driver queries ───────────────────────────────────────────────────────

    @GetMapping("/driver/pending")
    public ResponseEntity<?> getPendingRequests() {
        return ResponseEntity.ok(requestRepository.findByStatus("PENDING"));
    }

    @GetMapping("/driver/{driverId}")
    public ResponseEntity<?> getDriverRequests(@PathVariable Long driverId) {
        return ResponseEntity.ok(requestRepository.findByDriverId(driverId));
    }

    // ─── Accept ───────────────────────────────────────────────────────────────

    @PutMapping("/{requestId}/accept")
    public ResponseEntity<?> acceptRequest(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long requestId) {

        Long driverId = extractUserId(authHeader);
        ServiceRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        if (!"PENDING".equals(request.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Request already accepted or completed"));
        }

        User driver = userRepository.findById(driverId).orElseThrow();
        if (!"DRIVER".equals(driver.getUserType())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Only drivers can accept jobs"));
        }

        request.setDriver(driver);
        request.setStatus("ACCEPTED");
        request.setAcceptedAt(LocalDateTime.now());
        requestRepository.save(request);

        notificationService.notifyJobAccepted(request);

        return ResponseEntity.ok(Map.of("success", true, "message", "Request accepted. Head to the customer!"));
    }

    // ─── Status update ────────────────────────────────────────────────────────

    @PutMapping("/{requestId}/status")
    public ResponseEntity<?> updateStatus(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long requestId,
            @Valid @RequestBody UpdateStatusDTO statusUpdate) {

        Long userId = extractUserId(authHeader);
        ServiceRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        boolean isDriver   = request.getDriver()   != null && request.getDriver().getId().equals(userId);
        boolean isCustomer = request.getCustomer().getId().equals(userId);

        if (!isDriver && !isCustomer) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        String newStatus = statusUpdate.getStatus();
        request.setStatus(newStatus);
        if (statusUpdate.getNotes()      != null) request.setNotes(statusUpdate.getNotes());
        if (statusUpdate.getFinalPrice() != null) request.setFinalPrice(statusUpdate.getFinalPrice());

        switch (newStatus) {
            case "EN_ROUTE"   -> { request.setStartedAt(LocalDateTime.now()); notificationService.notifyDriverEnRoute(request); }
            case "COMPLETED"  -> {
                request.setCompletedAt(LocalDateTime.now());
                if (request.getFinalPrice() == null) request.setFinalPrice(request.getEstimatedPrice());
                requestRepository.save(request);
                notificationService.notifyJobCompleted(request);
                return ResponseEntity.ok(Map.of("success", true, "status", newStatus));
            }
            case "CANCELLED"  -> notificationService.notifyJobCancelled(request, isCustomer ? "customer" : "driver");
        }

        requestRepository.save(request);
        return ResponseEntity.ok(Map.of("success", true, "status", newStatus));
    }

    // ─── OTP completion ───────────────────────────────────────────────────────

    @PostMapping("/{requestId}/complete-with-otp")
    public ResponseEntity<?> completeWithOtp(
            @PathVariable Long requestId,
            @RequestBody Map<String, String> body) {

        ServiceRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        if (!request.getOtpCode().equals(body.get("otp"))) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid OTP — please check with the customer"));
        }

        request.setStatus("COMPLETED");
        request.setCompletedAt(LocalDateTime.now());
        if (body.get("finalPrice") != null) {
            request.setFinalPrice(Double.parseDouble(body.get("finalPrice")));
        } else if (request.getFinalPrice() == null) {
            request.setFinalPrice(request.getEstimatedPrice());
        }

        requestRepository.save(request);
        notificationService.notifyJobCompleted(request);

        return ResponseEntity.ok(Map.of("success", true, "message", "Service completed successfully! Great job."));
    }

    // ─── Cancel ───────────────────────────────────────────────────────────────

    @DeleteMapping("/{requestId}/cancel")
    public ResponseEntity<?> cancelRequest(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long requestId) {

        Long userId = extractUserId(authHeader);
        ServiceRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        if (!request.getCustomer().getId().equals(userId)) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        if (!List.of("PENDING", "ACCEPTED").contains(request.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot cancel a job that is already in progress"));
        }

        request.setStatus("CANCELLED");
        requestRepository.save(request);
        notificationService.notifyJobCancelled(request, "customer");

        return ResponseEntity.ok(Map.of("success", true, "message", "Request cancelled"));
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private Long extractUserId(String authHeader) {
        return Long.parseLong(jwtUtil.extractUserId(authHeader.replace("Bearer ", "")));
    }
}
