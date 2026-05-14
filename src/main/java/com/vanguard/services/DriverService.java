package com.vanguard.services;

import com.vanguard.entities.DriverLog;
import com.vanguard.entities.ServiceRequest;
import com.vanguard.entities.User;
import com.vanguard.repositories.DriverLogRepository;
import com.vanguard.repositories.RatingRepository;
import com.vanguard.repositories.ServiceRequestRepository;
import com.vanguard.repositories.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
public class DriverService {

    @Autowired private DriverLogRepository driverLogRepository;
    @Autowired private ServiceRequestRepository serviceRequestRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private NotificationService notificationService;
    @Autowired private RatingRepository ratingRepository;

    // ─── Shift management ─────────────────────────────────────────────────────

    @Transactional
    @CacheEvict(value = "driverStats", key = "#driverId")
    public Map<String, Object> startShift(Long driverId) {
        User driver = getDriver(driverId);

        driverLogRepository.findByDriverAndShiftStartIsNotNullAndShiftEndIsNull(driver)
                .ifPresent(s -> { throw new RuntimeException("Driver is already on an active shift"); });

        DriverLog log = new DriverLog();
        log.setDriver(driver);
        log.setShiftStart(LocalDateTime.now());
        log.setJobsCompleted(0);
        log.setEarnings(0.0);
        log.setFuelDelivered(0.0);
        log.setDistanceTraveled(0.0);
        driverLogRepository.save(log);

        driver.setIsOnline(true);
        userRepository.save(driver);

        //log.info("Driver {} started shift", driverId);

        return Map.of(
                "success", true,
                "message", "Shift started successfully",
                "shiftId", log.getId(),
                "shiftStart", log.getShiftStart().toString()
        );
    }

    @Transactional
    @CacheEvict(value = "driverStats", key = "#driverId")
    public Map<String, Object> endShift(Long driverId) {
        User driver = getDriver(driverId);

        DriverLog shift = driverLogRepository
                .findByDriverAndShiftStartIsNotNullAndShiftEndIsNull(driver)
                .orElseThrow(() -> new RuntimeException("No active shift found"));

        shift.setShiftEnd(LocalDateTime.now());
        driverLogRepository.save(shift);

        driver.setIsOnline(false);
        userRepository.save(driver);

        log.info("Driver {} ended shift. Earned R{} over {} jobs", driverId, shift.getEarnings(), shift.getJobsCompleted());

        return Map.of(
                "success", true,
                "message", "Shift ended successfully",
                "totalEarnings", shift.getEarnings() != null ? shift.getEarnings() : 0.0,
                "totalJobs", shift.getJobsCompleted() != null ? shift.getJobsCompleted() : 0,
                "shiftDurationMinutes", java.time.Duration.between(shift.getShiftStart(), shift.getShiftEnd()).toMinutes()
        );
    }

    // ─── Stats ────────────────────────────────────────────────────────────────

    @Cacheable(value = "driverStats", key = "#driverId")
    public Map<String, Object> getDriverStats(Long driverId) {
        User driver = getDriver(driverId);

        LocalDateTime startOfDay  = LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime startOfWeek = LocalDateTime.now().minusDays(7);

        List<ServiceRequest> completedToday = serviceRequestRepository
                .findCompletedJobsByDriverBetweenDates(driver, startOfDay, LocalDateTime.now());

        Double earningsToday = driverLogRepository
                .sumEarningsByDriverBetweenDates(driver, startOfDay, LocalDateTime.now());
        Double earningsWeek = driverLogRepository
                .sumEarningsByDriverBetweenDates(driver, startOfWeek, LocalDateTime.now());

        Double avgRating = ratingRepository.getAverageRatingForDriver(driver);
        Long   ratingCount = ratingRepository.countRatingsForDriver(driver);

        Optional<DriverLog> activeShift =
                driverLogRepository.findByDriverAndShiftStartIsNotNullAndShiftEndIsNull(driver);

        Map<String, Object> stats = new HashMap<>();
        stats.put("jobsToday",        completedToday.size());
        stats.put("earningsToday",    earningsToday  != null ? earningsToday  : 0.0);
        stats.put("earningsThisWeek", earningsWeek   != null ? earningsWeek   : 0.0);
        stats.put("isOnline",         driver.getIsOnline() != null ? driver.getIsOnline() : false);
        stats.put("isOnShift",        activeShift.isPresent());
        stats.put("averageRating",    avgRating  != null ? Math.round(avgRating * 10.0) / 10.0 : 0.0);
        stats.put("totalRatings",     ratingCount != null ? ratingCount : 0L);
        stats.put("currentLatitude",  driver.getCurrentLatitude()  != null ? driver.getCurrentLatitude()  : "-26.2041");
        stats.put("currentLongitude", driver.getCurrentLongitude() != null ? driver.getCurrentLongitude() : "28.0473");

        return stats;
    }

    // ─── Job management ───────────────────────────────────────────────────────

    public List<ServiceRequest> getAvailableJobs() {
        // Only show PENDING jobs; drivers with active accepted jobs shouldn't grab more
        return serviceRequestRepository.findByStatus("PENDING");
    }

    @Transactional
    @CacheEvict(value = "driverStats", key = "#driverId")
    public ServiceRequest acceptJob(Long driverId, Long requestId) {
        User driver = getDriver(driverId);

        // Prevent a driver from holding multiple active jobs simultaneously
        long activeJobs = serviceRequestRepository.countActiveJobsForDriver(driverId);
        if (activeJobs > 0) {
            throw new RuntimeException("You already have an active job. Complete it before accepting a new one.");
        }

        ServiceRequest request = serviceRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Job not found"));

        if (!"PENDING".equals(request.getStatus())) {
            throw new RuntimeException("Job is no longer available — it may have been accepted by another driver.");
        }

        request.setDriver(driver);
        request.setStatus("ACCEPTED");
        request.setAcceptedAt(LocalDateTime.now());
        ServiceRequest saved = serviceRequestRepository.save(request);

        // Real-time notification to customer
        notificationService.notifyJobAccepted(saved);

        log.info("Driver {} accepted job #{}", driverId, requestId);
        return saved;
    }

    @Transactional
    @CacheEvict(value = "driverStats", key = "#driverId")
    public ServiceRequest updateJobStatus(Long driverId, Long requestId, String status,
                                          String notes, Double finalPrice, Double fuelDelivered) {
        ServiceRequest request = serviceRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Job not found"));

        if (request.getDriver() == null || !request.getDriver().getId().equals(driverId)) {
            throw new RuntimeException("This job is not assigned to you");
        }

        String oldStatus = request.getStatus();
        request.setStatus(status);
        if (notes != null) request.setNotes(notes);

        switch (status) {
            case "EN_ROUTE" -> {
                request.setStartedAt(LocalDateTime.now());
                notificationService.notifyDriverEnRoute(request);
            }
            case "IN_PROGRESS" -> {
                // driver has arrived — no additional action needed
            }
            case "COMPLETED" -> {
                request.setCompletedAt(LocalDateTime.now());
                double price = finalPrice != null ? finalPrice
                        : (request.getEstimatedPrice() != null ? request.getEstimatedPrice() : 0.0);
                request.setFinalPrice(price);

                // Update shift log
                User driver = getDriver(driverId);
                driverLogRepository.findByDriverAndShiftStartIsNotNullAndShiftEndIsNull(driver)
                        .ifPresent(shiftLog -> {
                            shiftLog.setJobsCompleted((shiftLog.getJobsCompleted() != null ? shiftLog.getJobsCompleted() : 0) + 1);
                            if (fuelDelivered != null) {
                                shiftLog.setFuelDelivered((shiftLog.getFuelDelivered() != null ? shiftLog.getFuelDelivered() : 0) + fuelDelivered);
                            }
                            shiftLog.setEarnings((shiftLog.getEarnings() != null ? shiftLog.getEarnings() : 0) + price);
                            driverLogRepository.save(shiftLog);
                        });

                ServiceRequest saved = serviceRequestRepository.save(request);
                notificationService.notifyJobCompleted(saved);
                return saved;
            }
        }

        ServiceRequest saved = serviceRequestRepository.save(request);
        log.info("Job #{} status: {} → {}", requestId, oldStatus, status);
        return saved;
    }

    /** Update driver GPS location and broadcast to customer tracking view */
    @Transactional
    public void updateLocation(Long driverId, String latitude, String longitude) {
        User driver = userRepository.findById(driverId)
                .orElseThrow(() -> new RuntimeException("Driver not found"));
        driver.setCurrentLatitude(latitude);
        driver.setCurrentLongitude(longitude);
        userRepository.save(driver);

        // Broadcast to any active job the driver is on
        serviceRequestRepository.findByDriverId(driverId).stream()
                .filter(r -> "EN_ROUTE".equals(r.getStatus()) || "IN_PROGRESS".equals(r.getStatus()))
                .findFirst()
                .ifPresent(r -> notificationService.broadcastDriverLocation(r.getId(), latitude, longitude, driverId));
    }

    public List<ServiceRequest> getDriverJobs(Long driverId) {
        return serviceRequestRepository.findByDriverId(driverId);
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    private User getDriver(Long driverId) {
        User driver = userRepository.findById(driverId)
                .orElseThrow(() -> new RuntimeException("Driver not found"));
        if (!"DRIVER".equals(driver.getUserType())) {
            throw new RuntimeException("User is not a driver");
        }
        return driver;
    }
}
