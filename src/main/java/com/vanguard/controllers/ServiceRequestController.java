package com.vanguard.controllers;

import com.vanguard.dto.ServiceRequestDTO;
import com.vanguard.dto.UpdateStatusDTO;
import com.vanguard.entities.ServiceRequest;
import com.vanguard.entities.User;
import com.vanguard.repositories.ServiceRequestRepository;
import com.vanguard.repositories.UserRepository;
import com.vanguard.services.PricingService;
import com.vanguard.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/requests")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001"})
public class ServiceRequestController {

    @Autowired
    private ServiceRequestRepository requestRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PricingService pricingService;

    @PostMapping("/create")
    public ResponseEntity<?> createRequest(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody ServiceRequestDTO requestDTO) {

        Long userId = extractUserId(authHeader);
        Optional<User> userOpt = userRepository.findById(userId);

        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
        }

        ServiceRequest request = new ServiceRequest();
        request.setCustomer(userOpt.get());
        request.setRequestType(requestDTO.getRequestType());
        request.setPickupLatitude(requestDTO.getPickupLatitude());
        request.setPickupLongitude(requestDTO.getPickupLongitude());
        request.setAddress(requestDTO.getAddress());
        request.setVehicleType(requestDTO.getVehicleType());
        request.setLicensePlate(requestDTO.getLicensePlate());
        request.setFuelType(requestDTO.getFuelType());
        request.setFuelQuantity(requestDTO.getFuelQuantity());
        request.setIssueType(requestDTO.getIssueType());
        request.setNotes(requestDTO.getNotes());

        // Calculate estimated price
        double estimatedPrice = pricingService.calculatePrice(request);
        request.setEstimatedPrice(estimatedPrice);

        // Generate OTP for completion
        String otp = String.format("%04d", (int)(Math.random() * 10000));
        request.setOtpCode(otp);

        ServiceRequest saved = requestRepository.save(request);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("requestId", saved.getId());
        response.put("estimatedPrice", estimatedPrice);
        response.put("otpCode", otp);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/customer")
    public ResponseEntity<?> getCustomerRequests(@RequestHeader("Authorization") String authHeader) {
        Long userId = extractUserId(authHeader);
        List<ServiceRequest> requests = requestRepository.findByCustomerId(userId);
        return ResponseEntity.ok(requests);
    }

    @GetMapping("/customer/{requestId}")
    public ResponseEntity<?> getCustomerRequest(@RequestHeader("Authorization") String authHeader, @PathVariable Long requestId) {
        Long userId = extractUserId(authHeader);
        Optional<ServiceRequest> requestOpt = requestRepository.findById(requestId);

        if (requestOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        ServiceRequest request = requestOpt.get();
        if (!request.getCustomer().getId().equals(userId)) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        return ResponseEntity.ok(request);
    }

    @GetMapping("/driver/pending")
    public ResponseEntity<?> getPendingRequests() {
        List<ServiceRequest> pending = requestRepository.findByStatus("PENDING");
        return ResponseEntity.ok(pending);
    }

    @GetMapping("/driver/{driverId}")
    public ResponseEntity<?> getDriverRequests(@PathVariable Long driverId) {
        List<ServiceRequest> requests = requestRepository.findByDriverId(driverId);
        return ResponseEntity.ok(requests);
    }

    @PutMapping("/{requestId}/accept")
    public ResponseEntity<?> acceptRequest(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long requestId) {

        Long driverId = extractUserId(authHeader);
        Optional<ServiceRequest> requestOpt = requestRepository.findById(requestId);

        if (requestOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Request not found"));
        }

        ServiceRequest request = requestOpt.get();
        if (!"PENDING".equals(request.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Request already accepted or completed"));
        }

        Optional<User> driverOpt = userRepository.findById(driverId);
        if (driverOpt.isEmpty() || !"DRIVER".equals(driverOpt.get().getUserType())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid driver"));
        }

        request.setDriver(driverOpt.get());
        request.setStatus("ACCEPTED");
        request.setAcceptedAt(LocalDateTime.now());

        requestRepository.save(request);

        return ResponseEntity.ok(Map.of("success", true, "message", "Request accepted"));
    }

    @PutMapping("/{requestId}/status")
    public ResponseEntity<?> updateStatus(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long requestId,
            @Valid @RequestBody UpdateStatusDTO statusUpdate) {

        Long userId = extractUserId(authHeader);
        Optional<ServiceRequest> requestOpt = requestRepository.findById(requestId);

        if (requestOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        ServiceRequest request = requestOpt.get();

        // Check if user is driver assigned or customer
        boolean isDriver = request.getDriver() != null && request.getDriver().getId().equals(userId);
        boolean isCustomer = request.getCustomer().getId().equals(userId);

        if (!isDriver && !isCustomer) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        String newStatus = statusUpdate.getStatus();
        request.setStatus(newStatus);

        if (statusUpdate.getNotes() != null) {
            request.setNotes(statusUpdate.getNotes());
        }

        if (statusUpdate.getFinalPrice() != null) {
            request.setFinalPrice(statusUpdate.getFinalPrice());
        }

        if ("EN_ROUTE".equals(newStatus)) {
            request.setStartedAt(LocalDateTime.now());
        } else if ("COMPLETED".equals(newStatus)) {
            request.setCompletedAt(LocalDateTime.now());
            if (request.getFinalPrice() == null) {
                request.setFinalPrice(request.getEstimatedPrice());
            }
        }

        requestRepository.save(request);

        return ResponseEntity.ok(Map.of("success", true, "status", newStatus));
    }

    @PostMapping("/{requestId}/complete-with-otp")
    public ResponseEntity<?> completeWithOtp(
            @PathVariable Long requestId,
            @RequestBody Map<String, String> completionData) {

        Optional<ServiceRequest> requestOpt = requestRepository.findById(requestId);
        if (requestOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        ServiceRequest request = requestOpt.get();
        String providedOtp = completionData.get("otp");

        if (!request.getOtpCode().equals(providedOtp)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid OTP"));
        }

        request.setStatus("COMPLETED");
        request.setCompletedAt(LocalDateTime.now());

        if (completionData.get("finalPrice") != null) {
            request.setFinalPrice(Double.parseDouble(completionData.get("finalPrice")));
        }

        requestRepository.save(request);

        return ResponseEntity.ok(Map.of("success", true, "message", "Service completed successfully"));
    }

    @DeleteMapping("/{requestId}/cancel")
    public ResponseEntity<?> cancelRequest(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long requestId) {

        Long userId = extractUserId(authHeader);
        Optional<ServiceRequest> requestOpt = requestRepository.findById(requestId);

        if (requestOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        ServiceRequest request = requestOpt.get();

        if (!request.getCustomer().getId().equals(userId)) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        if (!"PENDING".equals(request.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot cancel request in progress"));
        }

        request.setStatus("CANCELLED");
        requestRepository.save(request);

        return ResponseEntity.ok(Map.of("success", true, "message", "Request cancelled"));
    }

    private Long extractUserId(String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        return Long.parseLong(jwtUtil.extractUserId(token));
    }
}