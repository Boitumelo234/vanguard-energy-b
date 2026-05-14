package com.vanguard.controllers;

import com.vanguard.entities.Notification;
import com.vanguard.entities.User;
import com.vanguard.repositories.UserRepository;
import com.vanguard.services.NotificationService;
import com.vanguard.utils.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST endpoints for the notification bell / drawer in the frontend.
 *
 * GET  /notifications          → all notifications for the logged-in user
 * GET  /notifications/unread   → unread count badge
 * PUT  /notifications/read-all → mark all as read
 * PUT  /notifications/{id}/read → mark one as read
 */
@RestController
@RequestMapping("/notifications")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001"})
@Slf4j
public class NotificationController {

    @Autowired private NotificationService notificationService;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtUtil jwtUtil;

    @GetMapping
    public ResponseEntity<?> getNotifications(@RequestHeader("Authorization") String authHeader) {
        User user = getUser(authHeader);
        List<Notification> notifications = notificationService.getNotificationsForUser(user);
        return ResponseEntity.ok(notifications);
    }

    @GetMapping("/unread-count")
    public ResponseEntity<?> getUnreadCount(@RequestHeader("Authorization") String authHeader) {
        User user = getUser(authHeader);
        long count = notificationService.getUnreadCount(user);
        return ResponseEntity.ok(Map.of("unreadCount", count));
    }

    @PutMapping("/read-all")
    public ResponseEntity<?> markAllRead(@RequestHeader("Authorization") String authHeader) {
        User user = getUser(authHeader);
        notificationService.markAllRead(user);
        return ResponseEntity.ok(Map.of("success", true, "message", "All notifications marked as read"));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<?> markOneRead(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id) {
        User user = getUser(authHeader);
        notificationService.markOneRead(id, user);
        return ResponseEntity.ok(Map.of("success", true));
    }

    private User getUser(String authHeader) {
        Long userId = Long.parseLong(jwtUtil.extractUserId(authHeader.replace("Bearer ", "")));
        return userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
    }
}
