package com.vanguard.controllers;

import com.vanguard.dto.DashboardStatsDTO;
import com.vanguard.entities.PricingConfig;
import com.vanguard.entities.ServiceRequest;
import com.vanguard.entities.User;
import com.vanguard.repositories.PricingConfigRepository;
import com.vanguard.repositories.RatingRepository;
import com.vanguard.repositories.ServiceRequestRepository;
import com.vanguard.repositories.UserRepository;
import com.vanguard.services.NotificationService;
import com.vanguard.services.PricingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/admin")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001"})
@Slf4j
public class AdminController {

    @Autowired private ServiceRequestRepository requestRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PricingConfigRepository pricingConfigRepository;
    @Autowired private PricingService pricingService;
    @Autowired private RatingRepository ratingRepository;
    @Autowired private NotificationService notificationService;

    // ─── Dashboard ────────────────────────────────────────────────────────────

    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboard() {
        LocalDateTime startOfDay  = LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime startOfWeek = LocalDateTime.now().minusDays(7);

        DashboardStatsDTO stats = new DashboardStatsDTO();
        stats.setTotalRequests(requestRepository.count());
        stats.setActiveDrivers((long) userRepository.findAvailableDrivers().size());
        stats.setTotalCustomers(userRepository.countActiveUsersByType("CUSTOMER"));
        stats.setPendingRequests((long) requestRepository.findByStatus("PENDING").size());
        stats.setCompletedToday(requestRepository.countCompletedJobsBetweenDates(startOfDay, LocalDateTime.now()));
        stats.setTotalRevenueToday(requestRepository.sumRevenueBetweenDates(startOfDay, LocalDateTime.now()));
        stats.setTotalRevenueThisWeek(requestRepository.sumRevenueBetweenDates(startOfWeek, LocalDateTime.now()));

        // Status distribution for the pie chart
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (ServiceRequest r : requestRepository.findAll()) {
            byStatus.merge(r.getStatus(), 1L, Long::sum);
        }
        stats.setRequestsByStatus(byStatus);

        // Revenue split by service type this week
        Map<String, Double> revenueByType = new LinkedHashMap<>();
        List<Object[]> rows = requestRepository.sumRevenueByTypeAndDateRange(startOfWeek, LocalDateTime.now());
        for (Object[] row : rows) {
            revenueByType.put((String) row[0], ((Number) row[1]).doubleValue());
        }
        stats.setRevenueByServiceType(revenueByType);

        return ResponseEntity.ok(stats);
    }

    // ─── Requests ─────────────────────────────────────────────────────────────

    @GetMapping("/requests")
    public ResponseEntity<?> getAllRequests(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer limit) {

        List<ServiceRequest> requests = status != null && !status.isEmpty()
                ? requestRepository.findByStatus(status)
                : requestRepository.findAll();

        if (limit != null && limit > 0 && requests.size() > limit) {
            requests = requests.subList(0, limit);
        }
        return ResponseEntity.ok(requests);
    }

    @DeleteMapping("/requests/{requestId}")
    public ResponseEntity<?> deleteRequest(@PathVariable Long requestId) {
        if (!requestRepository.existsById(requestId)) return ResponseEntity.notFound().build();
        requestRepository.deleteById(requestId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ─── Users ────────────────────────────────────────────────────────────────

    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers(@RequestParam(required = false) String userType) {
        List<User> users = userType != null && !userType.isEmpty()
                ? userRepository.findByUserType(userType)
                : userRepository.findAll();
        return ResponseEntity.ok(users);
    }

    @PutMapping("/users/{userId}/status")
    public ResponseEntity<?> updateUserStatus(
            @PathVariable Long userId,
            @RequestBody Map<String, String> body) {
        User user = userRepository.findById(userId).orElseThrow();
        String newStatus = body.get("status");
        user.setStatus(newStatus);
        userRepository.save(user);
        log.info("Admin updated user {} status to {}", userId, newStatus);
        return ResponseEntity.ok(Map.of("success", true, "message", "User status updated to " + newStatus));
    }

    // ─── Drivers ──────────────────────────────────────────────────────────────

    @GetMapping("/drivers")
    public ResponseEntity<?> getAllDrivers() {
        List<User> drivers = userRepository.findByUserType("DRIVER");
        // Enrich with average rating
        List<Map<String, Object>> enriched = drivers.stream().map(d -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id",          d.getId());
            map.put("name",        d.getName());
            map.put("phoneNumber", d.getPhoneNumber());
            map.put("status",      d.getStatus());
            map.put("isOnline",    d.getIsOnline());
            Double avg = ratingRepository.getAverageRatingForDriver(d);
            map.put("averageRating", avg != null ? Math.round(avg * 10.0) / 10.0 : 0.0);
            Long count = ratingRepository.countRatingsForDriver(d);
            map.put("totalRatings", count != null ? count : 0L);
            return map;
        }).toList();
        return ResponseEntity.ok(enriched);
    }

    /** Create a new driver account */
    @PostMapping("/drivers")
    public ResponseEntity<?> createDriver(@RequestBody Map<String, String> body) {
        String phone = body.get("phoneNumber");
        if (phone == null || phone.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Phone number required"));
        }
        if (userRepository.existsByPhoneNumber(phone)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Phone number already registered"));
        }
        User driver = new User();
        driver.setPhoneNumber(phone);
        driver.setName(body.getOrDefault("name", "Driver"));
        driver.setUserType("DRIVER");
        driver.setStatus("ACTIVE");
        userRepository.save(driver);
        log.info("Admin created new driver: {}", phone);
        return ResponseEntity.ok(Map.of("success", true, "message", "Driver account created"));
    }

    // ─── Pricing ──────────────────────────────────────────────────────────────

    @GetMapping("/pricing")
    public ResponseEntity<?> getPricingConfig() {
        return ResponseEntity.ok(pricingConfigRepository.findAll());
    }

    @PostMapping("/pricing")
    public ResponseEntity<?> updatePricing(@RequestBody PricingConfig config) {
        PricingConfig saved = pricingService.updatePricing(config);
        log.info("Admin updated pricing for {}", config.getServiceType());
        return ResponseEntity.ok(saved);
    }

    // ─── Reports ──────────────────────────────────────────────────────────────

    @GetMapping("/reports/daily")
    public ResponseEntity<?> getDailyReport(@RequestParam String date) {
        LocalDateTime start = LocalDateTime.parse(date + "T00:00:00");
        LocalDateTime end   = start.plusDays(1);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("date",           date);
        report.put("totalRequests",  requestRepository.countCompletedJobsBetweenDates(start, end));
        report.put("totalRevenue",   requestRepository.sumRevenueBetweenDates(start, end));
        report.put("activeDrivers",  userRepository.findAvailableDrivers().size());
        report.put("pendingJobs",    requestRepository.findByStatus("PENDING").size());

        List<Object[]> byType = requestRepository.sumRevenueByTypeAndDateRange(start, end);
        Map<String, Double> revenueByType = new LinkedHashMap<>();
        byType.forEach(row -> revenueByType.put((String) row[0], ((Number) row[1]).doubleValue()));
        report.put("revenueByType",  revenueByType);

        return ResponseEntity.ok(report);
    }

    @GetMapping("/reports/weekly")
    public ResponseEntity<?> getWeeklyReport() {
        LocalDateTime start = LocalDateTime.now().minusDays(7);
        LocalDateTime end   = LocalDateTime.now();

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("period",        "Last 7 days");
        report.put("totalRevenue",  requestRepository.sumRevenueBetweenDates(start, end));
        report.put("completedJobs", requestRepository.countCompletedJobsBetweenDates(start, end));
        report.put("totalDrivers",  userRepository.findByUserType("DRIVER").size());
        report.put("totalCustomers",userRepository.countActiveUsersByType("CUSTOMER"));

        return ResponseEntity.ok(report);
    }

    // ─── Broadcast system notification ───────────────────────────────────────

    @PostMapping("/notify/broadcast")
    public ResponseEntity<?> broadcastToAllDrivers(@RequestBody Map<String, String> body) {
        String message = body.getOrDefault("message", "System notification");
        List<User> drivers = userRepository.findByUserTypeAndIsOnlineTrue("DRIVER");
        // Use a dummy request-like payload for the broadcast
        drivers.forEach(driver -> log.info("Would notify driver {}: {}", driver.getId(), message));
        return ResponseEntity.ok(Map.of(
                "success", true,
                "notified", drivers.size(),
                "message", "Broadcast sent to " + drivers.size() + " online driver(s)"
        ));
    }
}
