package com.rlung.ezpay.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class HmacUtil {

    private static final String ALGO = "HmacSHA256";

    /**
     * secret = Base64 encoded key
     * return hex signature
     */
    public static String signHex(String base64Secret, String message) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(base64Secret);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, ALGO);

            Mac mac = Mac.getInstance(ALGO);
            mac.init(keySpec);
            byte[] raw = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));

            // convert to hex
            StringBuilder hex = new StringBuilder(raw.length * 2);
            for (byte b : raw) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();

        } catch (Exception e) {
            throw new RuntimeException("Failed to compute HMAC signature", e);
        }
    }
}
