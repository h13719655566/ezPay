package com.rlung.ezpay.scheduler;

import com.rlung.ezpay.service.WebhookDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookScheduler {

    private final WebhookDispatcher dispatcher;

    // Run every 5 seconds to dispatch pending webhook deliveries
    @Scheduled(fixedDelay = 5000)
    public void runDispatcher() {
        try {
            log.debug("WebhookScheduler triggered.");
            dispatcher.dispatchPending();
        } catch (Exception e) {
            log.error("WebhookScheduler encountered an unexpected error", e);
        }
    }
}
