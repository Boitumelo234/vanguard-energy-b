package com.vanguard.services;

import com.vanguard.entities.Rating;
import com.vanguard.entities.ServiceRequest;
import com.vanguard.entities.User;
import com.vanguard.repositories.RatingRepository;
import com.vanguard.repositories.ServiceRequestRepository;
import com.vanguard.repositories.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class RatingService {

    @Autowired private RatingRepository ratingRepository;
    @Autowired private ServiceRequestRepository requestRepository;
    @Autowired private UserRepository userRepository;

    @Transactional
    @CacheEvict(value = "driverStats", key = "#customerId")
    public Rating submitRating(Long customerId, Long requestId, int stars, String comment) {
        if (stars < 1 || stars > 5) {
            throw new IllegalArgumentException("Stars must be between 1 and 5");
        }

        ServiceRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        if (!request.getCustomer().getId().equals(customerId)) {
            throw new RuntimeException("You can only rate your own service requests");
        }

        if (!"COMPLETED".equals(request.getStatus())) {
            throw new RuntimeException("Only completed jobs can be rated");
        }

        if (ratingRepository.existsByRequestId(requestId)) {
            throw new RuntimeException("You have already rated this service");
        }

        if (request.getDriver() == null) {
            throw new RuntimeException("No driver assigned to this request");
        }

        User customer = userRepository.findById(customerId).orElseThrow();

        Rating rating = new Rating();
        rating.setRequest(request);
        rating.setCustomer(customer);
        rating.setDriver(request.getDriver());
        rating.setStars(stars);
        rating.setComment(comment);

        Rating saved = ratingRepository.save(rating);
        log.info("Customer {} rated driver {} for job #{}: {} stars", customerId, request.getDriver().getId(), requestId, stars);

        return saved;
    }

    public Map<String, Object> getDriverRatingSummary(Long driverId) {
        User driver = userRepository.findById(driverId)
                .orElseThrow(() -> new RuntimeException("Driver not found"));

        Double avg   = ratingRepository.getAverageRatingForDriver(driver);
        Long   count = ratingRepository.countRatingsForDriver(driver);

        Map<String, Object> summary = new HashMap<>();
        summary.put("averageRating", avg   != null ? Math.round(avg * 10.0) / 10.0 : 0.0);
        summary.put("totalRatings",  count != null ? count : 0L);
        summary.put("recentRatings", ratingRepository.findByDriverOrderByCreatedAtDesc(driver)
                .stream().limit(10).toList());

        return summary;
    }
}
