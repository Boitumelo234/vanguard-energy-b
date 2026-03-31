package com.vanguard.services;

import com.vanguard.entities.DriverLog;
import com.vanguard.entities.ServiceRequest;
import com.vanguard.entities.User;
import com.vanguard.repositories.DriverLogRepository;
import com.vanguard.repositories.ServiceRequestRepository;
import com.vanguard.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class DriverService {

    @Autowired
    private DriverLogRepository driverLogRepository;

    @Autowired
    private ServiceRequestRepository serviceRequestRepository;

    @Autowired
    private UserRepository userRepository;

    @Transactional
    public Map<String, Object> startShift(Long driverId) {
        User driver = userRepository.findById(driverId)
                .orElseThrow(() -> new RuntimeException("Driver not found"));

        // Check if driver is already a DRIVER type
        if (!"DRIVER".equals(driver.getUserType())) {
            throw new RuntimeException("User is not a driver");
        }

        // Check if already on shift
        Optional<DriverLog> existingShift = driverLogRepository
                .findByDriverAndShiftStartIsNotNullAndShiftEndIsNull(driver);

        if (existingShift.isPresent()) {
            throw new RuntimeException("Driver already on shift");
        }

        // Create new shift log
        DriverLog log = new DriverLog();
        log.setDriver(driver);
        log.setShiftStart(LocalDateTime.now());
        log.setJobsCompleted(0);
        log.setEarnings(0.0);
        log.setFuelDelivered(0.0);
        log.setDistanceTraveled(0.0);

        driverLogRepository.save(log);

        // Update driver online status
        driver.setIsOnline(true);
        userRepository.save(driver);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Shift started successfully");
        result.put("shiftId", log.getId());

        return result;
    }

    @Transactional
    public Map<String, Object> endShift(Long driverId) {
        User driver = userRepository.findById(driverId)
                .orElseThrow(() -> new RuntimeException("Driver not found"));

        DriverLog currentShift = driverLogRepository
                .findByDriverAndShiftStartIsNotNullAndShiftEndIsNull(driver)
                .orElseThrow(() -> new RuntimeException("No active shift found"));

        currentShift.setShiftEnd(LocalDateTime.now());
        driverLogRepository.save(currentShift);

        // Update driver online status
        driver.setIsOnline(false);
        userRepository.save(driver);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Shift ended successfully");
        result.put("totalEarnings", currentShift.getEarnings());
        result.put("totalJobs", currentShift.getJobsCompleted());

        return result;
    }

    public Map<String, Object> getDriverStats(Long driverId) {
        User driver = userRepository.findById(driverId)
                .orElseThrow(() -> new RuntimeException("Driver not found"));

        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LocalDateTime startOfWeek = LocalDateTime.now().minusDays(7);

        // Get today's completed jobs
        List<ServiceRequest> completedJobsToday = serviceRequestRepository
                .findCompletedJobsByDriverBetweenDates(driver, startOfDay, LocalDateTime.now());

        // Get current shift
        Optional<DriverLog> currentShift = driverLogRepository
                .findByDriverAndShiftStartIsNotNullAndShiftEndIsNull(driver);

        // Calculate earnings
        Double earningsToday = driverLogRepository
                .sumEarningsByDriverBetweenDates(driver, startOfDay, LocalDateTime.now());
        Double earningsThisWeek = driverLogRepository
                .sumEarningsByDriverBetweenDates(driver, startOfWeek, LocalDateTime.now());

        Map<String, Object> stats = new HashMap<>();
        stats.put("jobsToday", completedJobsToday.size());
        stats.put("earningsToday", earningsToday != null ? earningsToday : 0.0);
        stats.put("earningsThisWeek", earningsThisWeek != null ? earningsThisWeek : 0.0);
        stats.put("isOnline", driver.getIsOnline() != null ? driver.getIsOnline() : false);
        stats.put("isOnShift", currentShift.isPresent());
        stats.put("currentLatitude", driver.getCurrentLatitude() != null ? driver.getCurrentLatitude() : "-26.2041");
        stats.put("currentLongitude", driver.getCurrentLongitude() != null ? driver.getCurrentLongitude() : "28.0473");

        return stats;
    }

    public List<ServiceRequest> getAvailableJobs() {
        return serviceRequestRepository.findByStatus("PENDING");
    }

    @Transactional
    public ServiceRequest acceptJob(Long driverId, Long requestId) {
        User driver = userRepository.findById(driverId)
                .orElseThrow(() -> new RuntimeException("Driver not found"));

        ServiceRequest request = serviceRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Job not found"));

        if (!"PENDING".equals(request.getStatus())) {
            throw new RuntimeException("Job is no longer available");
        }

        request.setDriver(driver);
        request.setStatus("ACCEPTED");
        request.setAcceptedAt(LocalDateTime.now());

        return serviceRequestRepository.save(request);
    }

    @Transactional
    public ServiceRequest updateJobStatus(Long driverId, Long requestId, String status,
                                          String notes, Double finalPrice, Double fuelDelivered) {
        ServiceRequest request = serviceRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Job not found"));

        if (request.getDriver() == null || !request.getDriver().getId().equals(driverId)) {
            throw new RuntimeException("Job not assigned to you");
        }

        request.setStatus(status);

        if (notes != null) {
            request.setNotes(notes);
        }

        if ("COMPLETED".equals(status)) {
            request.setCompletedAt(LocalDateTime.now());
            if (finalPrice != null) {
                request.setFinalPrice(finalPrice);
            } else if (request.getEstimatedPrice() != null) {
                request.setFinalPrice(request.getEstimatedPrice());
            }

            // Update driver log
            User driver = userRepository.findById(driverId).orElseThrow();
            Optional<DriverLog> currentShift = driverLogRepository
                    .findByDriverAndShiftStartIsNotNullAndShiftEndIsNull(driver);

            if (currentShift.isPresent()) {
                DriverLog log = currentShift.get();
                log.setJobsCompleted((log.getJobsCompleted() != null ? log.getJobsCompleted() : 0) + 1);
                if (fuelDelivered != null) {
                    log.setFuelDelivered((log.getFuelDelivered() != null ? log.getFuelDelivered() : 0) + fuelDelivered);
                }
                if (request.getFinalPrice() != null) {
                    log.setEarnings((log.getEarnings() != null ? log.getEarnings() : 0) + request.getFinalPrice());
                }
                driverLogRepository.save(log);
            }
        }

        return serviceRequestRepository.save(request);
    }

    public List<ServiceRequest> getDriverJobs(Long driverId) {
        return serviceRequestRepository.findByDriverId(driverId);
    }
}