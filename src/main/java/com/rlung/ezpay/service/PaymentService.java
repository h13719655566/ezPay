package com.rlung.ezpay.service;

import com.rlung.ezpay.dto.PaymentRequest;
import com.rlung.ezpay.dto.PaymentResponse;
import com.rlung.ezpay.entity.Payment;
import com.rlung.ezpay.event.PaymentCreatedEvent;
import com.rlung.ezpay.repo.PaymentRepository;
import com.rlung.ezpay.repo.WebhookDeliveryRepository;
import com.rlung.ezpay.util.CardUtil;
import com.rlung.ezpay.util.CryptoUtil;
import com.rlung.ezpay.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentPersistenceService paymentPersistenceService;
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentRepository paymentRepo;
    private final WebhookDeliveryRepository deliveryRepo;

    @Value("${app.encryption.key}")
    private String encryptionKey;

    /**
     * Create a new payment
     * Encrypt card, save to DB (retry if needed), and fire the event
     */
    @Transactional
    public PaymentResponse createPayment(PaymentRequest req) {

        // if CryptoUtil has error will throw RuntimeException (EncryptionException)，doesn't need try-catch
        String encryptedCard = CryptoUtil.encrypt(req.getCardNumber(), encryptionKey);
        String last4 = CardUtil.extractLast4(req.getCardNumber());

        Payment savedPayment = savePaymentWithRetry(req, encryptedCard, last4);

        // Publish event
        eventPublisher.publishEvent(new PaymentCreatedEvent(savedPayment));

        log.info("Payment created successfully. ID: {}", savedPayment.getPaymentId());

        return PaymentResponse.builder()
                .paymentId(savedPayment.getPaymentId())
                .status("created")
                .last4(last4)
                .build();
    }

    /**
     * Optimistic Strategy:
     * assume we are lucky and the ID is unique (99.9% true)
     * If the database complains about a duplicate ID, we simply try again with a new one
     */
    private Payment savePaymentWithRetry(PaymentRequest req, String encryptedCard, String last4) {
        int maxRetries = 3;
        int attempt = 0;
        String paymentId = null;

        while (attempt < maxRetries) {
            try {
                // new ID
                paymentId = IdGenerator.generatePaymentId();

                Payment payment = Payment.builder()
                        .paymentId(paymentId)
                        .firstName(req.getFirstName())
                        .lastName(req.getLastName())
                        .zipCode(req.getZipCode())
                        .encryptedCardNumber(encryptedCard)
                        .last4(last4)
                        .amount(req.getAmount())
                        .currency(req.getCurrency())
                        .createdAt(LocalDateTime.now())
                        .build();

                //  flush to make sure Constraint check works
                return paymentPersistenceService.saveInNewTransaction(payment);

            } catch (DataIntegrityViolationException e) {
                // catch ID Duplicate
                attempt++;
                log.warn("Payment ID collision for {} (Attempt {}/{})",
                        paymentId, attempt, maxRetries);

                if (attempt >= maxRetries) {
                    log.error("Failed to generate unique Payment ID after {} retries.", maxRetries);
                    throw new RuntimeException("System busy, please try again later.", e);
                }
            }
        }
        throw new RuntimeException("Unexpected error during payment creation");
    }
}