package com.portfolio.tracker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically deletes signups that were never verified and whose code has expired, so abandoned
 * registrations don't hold on to usernames and email addresses.
 */
@Component
public class UnverifiedAccountCleanupTask {

    private static final Logger logger = LoggerFactory.getLogger(UnverifiedAccountCleanupTask.class);

    private final UserService userService;

    public UnverifiedAccountCleanupTask(UserService userService) {
        this.userService = userService;
    }

    @Scheduled(cron = "${app.verification.cleanup-cron}")
    public void deleteExpiredUnverifiedAccounts() {
        try {
            int removed = userService.deleteExpiredUnverifiedAccounts();
            if (removed > 0) {
                logger.info("Removed {} expired unverified account(s)", removed);
            }
        } catch (Exception e) {
            logger.error("Cleanup of expired unverified accounts failed: {}", e.getMessage(), e);
        }
    }
}
