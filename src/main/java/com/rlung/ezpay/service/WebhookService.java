package com.rlung.ezpay.service;

import com.rlung.ezpay.entity.WebhookDelivery;
import com.rlung.ezpay.entity.WebhookEndpoint;
import com.rlung.ezpay.repo.WebhookDeliveryRepository;
import com.rlung.ezpay.repo.WebhookEndpointRepository;
import com.rlung.ezpay.util.SecureRandomUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WebhookService {

    private final WebhookEndpointRepository endpointRepo;
    private final WebhookDeliveryRepository deliveryRepo;

    public WebhookEndpoint register(String url) {

        // Generate HMAC secret
        byte[] key = SecureRandomUtil.randomBytes(32);
        String secret = Base64.getEncoder().encodeToString(key);

        WebhookEndpoint ep = WebhookEndpoint.builder()
                .url(url)
                .secret(secret)
                .build();

        return endpointRepo.save(ep);
    }

    public List<WebhookDelivery> findAllDeliveries() {
        return deliveryRepo.findAll();
    }
}
