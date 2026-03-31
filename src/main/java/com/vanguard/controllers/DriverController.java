package com.vanguard.controllers;

import com.vanguard.dto.LocationUpdateDTO;
import com.vanguard.dto.UpdateStatusDTO;
import com.vanguard.entities.ServiceRequest;
import com.vanguard.entities.User;
import com.vanguard.repositories.UserRepository;
import com.vanguard.services.DriverService;
import com.vanguard.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/driver")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001"})
public class DriverController {

    @Autowired
    private DriverService driverService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/shift/start")
    public ResponseEntity<?> startShift(@RequestHeader("Authorization") String authHeader) {
        try {
            Long driverId = extractUserId(authHeader);
            System.out.println("Starting shift for driver: " + driverId);
            Map<String, Object> result = driverService.startShift(driverId);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            System.err.println("Error starting shift: " + e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error: " + e.getMessage()));
        }
    }

    @PostMapping("/shift/end")
    public ResponseEntity<?> endShift(@RequestHeader("Authorization") String authHeader) {
        try {
            Long driverId = extractUserId(authHeader);
            System.out.println("Ending shift for driver: " + driverId);
            Map<String, Object> result = driverService.endShift(driverId);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            System.err.println("Error ending shift: " + e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error: " + e.getMessage()));
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getStats(@RequestHeader("Authorization") String authHeader) {
        try {
            Long driverId = extractUserId(authHeader);
            System.out.println("Getting stats for driver: " + driverId);
            Map<String, Object> stats = driverService.getDriverStats(driverId);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            System.err.println("Error getting stats: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", "Failed to fetch stats: " + e.getMessage()));
        }
    }

    @GetMapping("/jobs/available")
    public ResponseEntity<?> getAvailableJobs() {
        try {
            List<ServiceRequest> jobs = driverService.getAvailableJobs();
            return ResponseEntity.ok(jobs);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to fetch jobs: " + e.getMessage()));
        }
    }

    @PostMapping("/jobs/{jobId}/accept")
    public ResponseEntity<?> acceptJob(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long jobId) {
        try {
            Long driverId = extractUserId(authHeader);
            ServiceRequest accepted = driverService.acceptJob(driverId, jobId);
            return ResponseEntity.ok(accepted);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/jobs/{jobId}/status")
    public ResponseEntity<?> updateJobStatus(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long jobId,
            @Valid @RequestBody UpdateStatusDTO statusUpdate) {
        try {
            Long driverId = extractUserId(authHeader);
            ServiceRequest updated = driverService.updateJobStatus(
                    driverId,
                    jobId,
                    statusUpdate.getStatus(),
                    statusUpdate.getNotes(),
                    statusUpdate.getFinalPrice(),
                    statusUpdate.getFuelDelivered()
            );
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/jobs")
    public ResponseEntity<?> getMyJobs(@RequestHeader("Authorization") String authHeader) {
        try {
            Long driverId = extractUserId(authHeader);
            List<ServiceRequest> jobs = driverService.getDriverJobs(driverId);
            return ResponseEntity.ok(jobs);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to fetch jobs: " + e.getMessage()));
        }
    }

    @PostMapping("/location")
    public ResponseEntity<?> updateLocation(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody LocationUpdateDTO locationUpdate) {
        try {
            Long driverId = extractUserId(authHeader);
            User driver = userRepository.findById(driverId).orElseThrow();
            driver.setCurrentLatitude(locationUpdate.getLatitude());
            driver.setCurrentLongitude(locationUpdate.getLongitude());
            userRepository.save(driver);
            return ResponseEntity.ok(Map.of("success", true, "message", "Location updated"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to update location: " + e.getMessage()));
        }
    }

    private Long extractUserId(String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        return Long.parseLong(jwtUtil.extractUserId(token));
    }
}