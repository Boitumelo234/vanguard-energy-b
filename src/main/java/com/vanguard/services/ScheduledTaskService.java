package com.vanguard.services;

import com.vanguard.entities.ServiceRequest;
import com.vanguard.entities.User;
import com.vanguard.repositories.ServiceRequestRepository;
import com.vanguard.repositories.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Background scheduled tasks that run automatically.
 *
 * These dramatically improve response time and professionalism:
 *   - Re-broadcast pending jobs every 2 minutes so newly-online drivers see them.
 *   - Auto-cancel stale requests (pending > 30 min with no drivers online).
 *   - Mark long-running accepted jobs as potentially stale and re-notify.
 */
@Service
@Slf4j
public class ScheduledTaskService {

    @Autowired private ServiceRequestRepository requestRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private NotificationService notificationService;

    /**
     * Every 2 minutes: re-broadcast all PENDING requests to the /topic/jobs channel.
     * This ensures drivers who come online mid-session still see outstanding jobs.
     */
    @Scheduled(fixedDelay = 120_000)
    public void reBroadcastPendingJobs() {
        List<ServiceRequest> pending = requestRepository.findByStatus("PENDING");
        if (pending.isEmpty()) return;

        log.debug("Re-broadcasting {} pending job(s) to drivers", pending.size());
        pending.forEach(notificationService::notifyNewJobAvailable);
    }

    /**
     * Every 5 minutes: cancel PENDING requests older than 30 minutes if no drivers
     * are currently online. Prevents the customer from waiting indefinitely.
     */
    @Scheduled(fixedDelay = 300_000)
    @Transactional
    public void autoCancelStaleRequests() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(30);
        List<ServiceRequest> stale = requestRepository.findByStatusAndRequestedAtBefore("PENDING", cutoff);

        List<User> onlineDrivers = userRepository.findAvailableDrivers();
        if (!onlineDrivers.isEmpty()) return; // drivers are online — let them pick it up

        for (ServiceRequest r : stale) {
            r.setStatus("CANCELLED");
            r.setNotes("Auto-cancelled: no drivers available after 30 minutes.");
            requestRepository.save(r);
            notificationService.notifyJobCancelled(r, "system (no drivers available)");
            log.warn("Auto-cancelled stale request #{}", r.getId());
        }
    }

    /**
     * Every 10 minutes: alert if an ACCEPTED job hasn't moved to EN_ROUTE within 15 minutes.
     * Helps admin spot stuck jobs and keeps drivers accountable.
     */
    @Scheduled(fixedDelay = 600_000)
    public void checkStuckAcceptedJobs() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(15);
        List<ServiceRequest> stuck = requestRepository.findByStatusAndRequestedAtBefore("ACCEPTED", cutoff);
        stuck.forEach(r -> log.warn(
                "Job #{} has been ACCEPTED for >15 min with no EN_ROUTE update. Driver: {}",
                r.getId(),
                r.getDriver() != null ? r.getDriver().getId() : "none"
        ));
        // Could also push an alert to admin's WebSocket topic here
    }
}
