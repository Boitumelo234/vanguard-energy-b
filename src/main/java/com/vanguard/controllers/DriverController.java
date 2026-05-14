package com.vanguard.controllers;

import com.vanguard.dto.LocationUpdateDTO;
import com.vanguard.dto.UpdateStatusDTO;
import com.vanguard.entities.ServiceRequest;
import com.vanguard.services.DriverService;
import com.vanguard.utils.JwtUtil;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/driver")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001"})
@Slf4j
public class DriverController {

    @Autowired private DriverService driverService;
    @Autowired private JwtUtil jwtUtil;

    // ─── Shift ────────────────────────────────────────────────────────────────

    @PostMapping("/shift/start")
    public ResponseEntity<?> startShift(@RequestHeader("Authorization") String authHeader) {
        try {
            return ResponseEntity.ok(driverService.startShift(extractUserId(authHeader)));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/shift/end")
    public ResponseEntity<?> endShift(@RequestHeader("Authorization") String authHeader) {
        try {
            return ResponseEntity.ok(driverService.endShift(extractUserId(authHeader)));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─── Stats ────────────────────────────────────────────────────────────────

    @GetMapping("/stats")
    public ResponseEntity<?> getStats(@RequestHeader("Authorization") String authHeader) {
        try {
            return ResponseEntity.ok(driverService.getDriverStats(extractUserId(authHeader)));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to fetch stats: " + e.getMessage()));
        }
    }

    // ─── Jobs ─────────────────────────────────────────────────────────────────

    @GetMapping("/jobs/available")
    public ResponseEntity<?> getAvailableJobs() {
        try {
            List<ServiceRequest> jobs = driverService.getAvailableJobs();
            return ResponseEntity.ok(jobs);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to fetch jobs"));
        }
    }

    @PostMapping("/jobs/{jobId}/accept")
    public ResponseEntity<?> acceptJob(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long jobId) {
        try {
            return ResponseEntity.ok(driverService.acceptJob(extractUserId(authHeader), jobId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/jobs/{jobId}/status")
    public ResponseEntity<?> updateJobStatus(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long jobId,
            @Valid @RequestBody UpdateStatusDTO dto) {
        try {
            Long driverId = extractUserId(authHeader);
            ServiceRequest updated = driverService.updateJobStatus(
                    driverId, jobId, dto.getStatus(), dto.getNotes(), dto.getFinalPrice(), dto.getFuelDelivered()
            );
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/jobs")
    public ResponseEntity<?> getMyJobs(@RequestHeader("Authorization") String authHeader) {
        try {
            return ResponseEntity.ok(driverService.getDriverJobs(extractUserId(authHeader)));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to fetch jobs"));
        }
    }

    // ─── Location ─────────────────────────────────────────────────────────────

    /**
     * The driver app calls this every ~5–10 seconds while on a job.
     * The service persists the lat/lng to the User entity AND broadcasts
     * it via WebSocket to /topic/driver/{id}/location and /topic/job/{jobId}.
     */
    @PostMapping("/location")
    public ResponseEntity<?> updateLocation(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody LocationUpdateDTO locationUpdate) {
        try {
            Long driverId = extractUserId(authHeader);
            driverService.updateLocation(driverId, locationUpdate.getLatitude(), locationUpdate.getLongitude());
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to update location"));
        }
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private Long extractUserId(String authHeader) {
        return Long.parseLong(jwtUtil.extractUserId(authHeader.replace("Bearer ", "")));
    }
}
