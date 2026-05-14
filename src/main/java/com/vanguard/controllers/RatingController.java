package com.vanguard.controllers;

import com.vanguard.entities.Rating;
import com.vanguard.services.RatingService;
import com.vanguard.utils.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * POST /ratings/{requestId}           → customer submits a rating
 * GET  /ratings/driver/{driverId}     → public driver rating summary
 */
@RestController
@RequestMapping("/ratings")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001"})
@Slf4j
public class RatingController {

    @Autowired private RatingService ratingService;
    @Autowired private JwtUtil jwtUtil;

    @PostMapping("/{requestId}")
    public ResponseEntity<?> submitRating(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long requestId,
            @RequestBody Map<String, Object> body) {

        Long customerId = extractUserId(authHeader);
        int stars = Integer.parseInt(body.get("stars").toString());
        String comment = body.containsKey("comment") ? body.get("comment").toString() : null;

        try {
            Rating saved = ratingService.submitRating(customerId, requestId, stars, comment);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Thank you for your feedback!",
                    "ratingId", saved.getId()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/driver/{driverId}")
    public ResponseEntity<?> getDriverRating(@PathVariable Long driverId) {
        return ResponseEntity.ok(ratingService.getDriverRatingSummary(driverId));
    }

    private Long extractUserId(String authHeader) {
        return Long.parseLong(jwtUtil.extractUserId(authHeader.replace("Bearer ", "")));
    }
}
