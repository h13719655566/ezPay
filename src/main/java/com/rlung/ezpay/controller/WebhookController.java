package com.rlung.ezpay.controller;

import com.rlung.ezpay.dto.webhook.RegisterWebhookRequest;
import com.rlung.ezpay.dto.webhook.RegisterWebhookResponse;
import com.rlung.ezpay.entity.WebhookDelivery;
import com.rlung.ezpay.entity.WebhookEndpoint;
import com.rlung.ezpay.service.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/webhooks")
@RequiredArgsConstructor
@CrossOrigin
public class WebhookController {

    private final WebhookService webhookService;

    @Operation(
            summary = "Register a webhook endpoint",
            description = "Client provides a URL. Server generates a secret and stores the endpoint.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Webhook registered"),
                    @ApiResponse(responseCode = "400", description = "Invalid input")
            }
    )
    @PostMapping("/register")
    public ResponseEntity<RegisterWebhookResponse> register(
            @Valid @RequestBody RegisterWebhookRequest req) {

        log.info("Registering webhook endpoint: {}", req.getUrl());

        WebhookEndpoint ep = webhookService.register(req.getUrl());

        return ResponseEntity.status(201).body(
                new RegisterWebhookResponse(ep.getId(), ep.getUrl(), ep.getSecret())
        );
    }

    @Operation(
            summary = "Get a history of all webhook delivery attempts",
            description = "Retrieves all recorded delivery tasks to monitor the system's notification performance."
    )
    @GetMapping("/deliveries")
    public ResponseEntity<List<WebhookDelivery>> getAllDeliveries() {
        log.info("Received request to get all webhook deliveries for monitoring.");

        List<WebhookDelivery> deliveries = webhookService.findAllDeliveries();

        return ResponseEntity.ok(deliveries);
    }
}
