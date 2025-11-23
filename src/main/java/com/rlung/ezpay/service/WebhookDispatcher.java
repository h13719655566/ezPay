package com.rlung.ezpay.service;

import com.rlung.ezpay.entity.WebhookDelivery;
import com.rlung.ezpay.entity.WebhookEndpoint;
import com.rlung.ezpay.repo.WebhookDeliveryRepository;
import com.rlung.ezpay.repo.WebhookEndpointRepository;
import com.rlung.ezpay.util.HmacUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookDispatcher {

    private static final int MAX_ATTEMPTS = 20;

    private final WebhookDeliveryRepository deliveryRepo;
    private final WebhookEndpointRepository endpointRepo;

    private final RestTemplate restTemplate = createCustomRestTemplate();

    private static RestTemplate createCustomRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        return new RestTemplate(factory);
    }

    public void dispatchPending() {
        LocalDateTime now = LocalDateTime.now();
        List<WebhookDelivery> tasks =
                deliveryRepo.findBySuccessFalseAndNextRetryAtBefore(now);

        if (tasks.isEmpty()) {
            return;
        }

        log.info("Found {} pending webhook deliveries", tasks.size());

        for (WebhookDelivery task : tasks) {
            processOne(task);
        }
    }

    private void processOne(WebhookDelivery task) {
        WebhookEndpoint endpoint = endpointRepo.findById(task.getEndpointId())
                .orElse(null);

        if (endpoint == null) {
            log.warn("Webhook endpoint {} not found, marking delivery {} as permanently failed",
                    task.getEndpointId(), task.getId());
            task.setSuccess(false);
            task.setNextRetryAt(null);
            task.setStatusCode(410);
            task.setResponseBody("Endpoint not found");
            deliveryRepo.save(task);
            return;
        }

        String url = endpoint.getUrl();
        String payload = task.getPayload();

        long timestamp = System.currentTimeMillis() / 1000L;
        String message  = timestamp + "." + payload;

        // Generate HMAC-SHA256 signature
        String signature = HmacUtil.signHex(endpoint.getSecret(), message);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String signatureHeader = "t=" + timestamp + ", v1=" + signature;
        headers.add("Ezpay-Signature", signatureHeader);

        HttpEntity<String> entity = new HttpEntity<>(payload, headers);

        int newAttempt = task.getAttempt() + 1;
        task.setAttempt(newAttempt);

        try {
            ResponseEntity<String> response =
                    restTemplate.postForEntity(url, entity, String.class);

            int status = response.getStatusCodeValue();
            String body = truncate(response.getBody(), 1024);

            task.setStatusCode(status);
            task.setResponseBody(body);

            if (response.getStatusCode().is2xxSuccessful()) {
                // success
                task.setSuccess(true);
                task.setNextRetryAt(null);
                endpoint.setFailureCount(0);
                log.info("Webhook delivery {} succeeded with status {}", task.getId(), status);
            } else {
                handleFailure(task, endpoint, "Non-2xx status " + status);
            }

        } catch (Exception ex) {
            String msg = truncate(ex.getMessage(), 1024);
            task.setResponseBody(msg);
            task.setStatusCode(0);
            handleFailure(task, endpoint, "Exception when sending webhook");
        }

        endpointRepo.save(endpoint);
        deliveryRepo.save(task);
    }

    private void handleFailure(WebhookDelivery task, WebhookEndpoint endpoint, String reason) {
        log.warn("Webhook delivery {} failed (attempt {}): {}",
                task.getId(), task.getAttempt(), reason);

        endpoint.setFailureCount(endpoint.getFailureCount() + 1);

        if (task.getAttempt() >= MAX_ATTEMPTS) {
            log.error("Giving up on webhook delivery {} after {} attempts", task.getId(), task.getAttempt());
            task.setNextRetryAt(null);
            return;
        }

        long delaySeconds = computeBackoffSeconds(task.getAttempt());
        task.setNextRetryAt(LocalDateTime.now().plusSeconds(delaySeconds));
    }

    /**
     * Simple exponential backoff for webhook retry.
     *
     * attempt=1 → 5s
     * attempt=2 → 10s
     * attempt=3 → 20s
     * attempt=4 → 40s
     * attempt=5 → 80s
     * ...
     * capped at 10 minutes
     */
    private long computeBackoffSeconds(int attempt) {

        // attempt 1 → shift 0
        int shift = Math.max(0, attempt - 1);

        long backoff = 5L * (1L << shift);

        // Maximum 10 minutes
        return Math.min(backoff, 600L);
    }

    private String truncate(String value, int maxLen) {
        if (value == null) return null;
        if (value.length() <= maxLen) return value;
        return value.substring(0, maxLen);
    }
}
