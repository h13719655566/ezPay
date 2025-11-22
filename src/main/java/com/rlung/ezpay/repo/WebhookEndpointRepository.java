package com.rlung.ezpay.repo;

import com.rlung.ezpay.entity.WebhookEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, Long> {
}