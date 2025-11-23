package com.rlung.ezpay.controller;

import com.rlung.ezpay.dto.PaymentRequest;
import com.rlung.ezpay.dto.PaymentResponse;
import com.rlung.ezpay.service.PaymentService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;

@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@CrossOrigin
@Tag(name = "Payment Management", description = "APIs for processing payments")
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(
            summary = "Create a new payment",
            description = "Encrypts card data, stores payment, and triggers webhook event."
    )
    @ApiResponse(
            responseCode = "201",
            description = "Payment created successfully",
            content = @Content(schema = @Schema(implementation = PaymentResponse.class))
    )
    @ApiResponse(
            responseCode = "400",
            description = "Invalid request payload"
    )
    @ApiResponse(
            responseCode = "500",
            description = "Internal server error"
    )
    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @Valid @RequestBody PaymentRequest request) {

        log.info("Received create payment request");

        PaymentResponse response = paymentService.createPayment(request);

        return ResponseEntity.status(201).body(response);
    }
}
