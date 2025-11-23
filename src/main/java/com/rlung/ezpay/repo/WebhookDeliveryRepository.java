package com.rlung.ezpay.repo;

import com.rlung.ezpay.entity.WebhookDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, Long> {
    List<WebhookDelivery> findBySuccessFalseAndNextRetryAtBefore(LocalDateTime now);
}
