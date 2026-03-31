package com.vanguard.controllers;

import com.vanguard.dto.DashboardStatsDTO;
import com.vanguard.entities.PricingConfig;
import com.vanguard.entities.ServiceRequest;
import com.vanguard.entities.User;
import com.vanguard.repositories.PricingConfigRepository;
import com.vanguard.repositories.ServiceRequestRepository;
import com.vanguard.repositories.UserRepository;
import com.vanguard.services.PricingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001"})
public class AdminController {

    @Autowired
    private ServiceRequestRepository requestRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PricingConfigRepository pricingConfigRepository;

    @Autowired
    private PricingService pricingService;

    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboard() {
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LocalDateTime startOfWeek = LocalDateTime.now().minusDays(7);

        DashboardStatsDTO stats = new DashboardStatsDTO();
        stats.setTotalRequests(requestRepository.count());
        stats.setActiveDrivers((long) userRepository.findAvailableDrivers().size());
        stats.setTotalCustomers(userRepository.countActiveUsersByType("CUSTOMER"));
        stats.setPendingRequests((long) requestRepository.findByStatus("PENDING").size());
        stats.setCompletedToday(requestRepository.countCompletedJobsBetweenDates(startOfDay, LocalDateTime.now()));
        stats.setTotalRevenueToday(requestRepository.sumRevenueBetweenDates(startOfDay, LocalDateTime.now()));
        stats.setTotalRevenueThisWeek(requestRepository.sumRevenueBetweenDates(startOfWeek, LocalDateTime.now()));

        // Status distribution
        Map<String, Long> requestsByStatus = new HashMap<>();
        for (ServiceRequest request : requestRepository.findAll()) {
            requestsByStatus.merge(request.getStatus(), 1L, Long::sum);
        }
        stats.setRequestsByStatus(requestsByStatus);

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/requests")
    public ResponseEntity<?> getAllRequests(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer limit) {

        List<ServiceRequest> requests;
        if (status != null && !status.isEmpty()) {
            requests = requestRepository.findByStatus(status);
        } else {
            requests = requestRepository.findAll();
        }

        if (limit != null && limit > 0 && requests.size() > limit) {
            requests = requests.subList(0, limit);
        }

        return ResponseEntity.ok(requests);
    }

    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers(@RequestParam(required = false) String userType) {
        List<User> users;
        if (userType != null && !userType.isEmpty()) {
            users = userRepository.findByUserType(userType);
        } else {
            users = userRepository.findAll();
        }
        return ResponseEntity.ok(users);
    }

    @PutMapping("/users/{userId}/status")
    public ResponseEntity<?> updateUserStatus(@PathVariable Long userId, @RequestBody Map<String, String> statusUpdate) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        user.setStatus(statusUpdate.get("status"));
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("success", true, "message", "User status updated"));
    }

    @GetMapping("/drivers")
    public ResponseEntity<?> getAllDrivers() {
        List<User> drivers = userRepository.findByUserType("DRIVER");
        return ResponseEntity.ok(drivers);
    }

    @GetMapping("/pricing")
    public ResponseEntity<?> getPricingConfig() {
        List<PricingConfig> configs = pricingConfigRepository.findAll();
        return ResponseEntity.ok(configs);
    }

    @PostMapping("/pricing")
    public ResponseEntity<?> updatePricing(@RequestBody PricingConfig pricingConfig) {
        PricingConfig saved = pricingService.updatePricing(pricingConfig);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/reports/daily")
    public ResponseEntity<?> getDailyReport(@RequestParam String date) {
        LocalDateTime startDate = LocalDateTime.parse(date + "T00:00:00");
        LocalDateTime endDate = startDate.plusDays(1);

        Long totalRequests = requestRepository.countCompletedJobsBetweenDates(startDate, endDate);
        Double totalRevenue = requestRepository.sumRevenueBetweenDates(startDate, endDate);

        Map<String, Object> report = new HashMap<>();
        report.put("date", date);
        report.put("totalRequests", totalRequests);
        report.put("totalRevenue", totalRevenue != null ? totalRevenue : 0);
        report.put("activeDrivers", userRepository.findAvailableDrivers().size());

        return ResponseEntity.ok(report);
    }

    @DeleteMapping("/requests/{requestId}")
    public ResponseEntity<?> deleteRequest(@PathVariable Long requestId) {
        if (!requestRepository.existsById(requestId)) {
            return ResponseEntity.notFound().build();
        }
        requestRepository.deleteById(requestId);
        return ResponseEntity.ok(Map.of("success", true, "message", "Request deleted"));
    }
}