package com.dwellio.api.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dwellio.razorpay")
public class RazorpayProperties {

    /**
     * Razorpay webhook signing secret. Empty disables acceptance (all webhooks rejected).
     */
    private String webhookSecret = "";

    /** Publishable key id for Checkout (rzp_test_… / rzp_live_…). */
    private String keyId = "";

    /** API secret for Orders API. Empty → stub orders (local/test). */
    private String keySecret = "";

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret == null ? "" : webhookSecret;
    }

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId == null ? "" : keyId;
    }

    public String getKeySecret() {
        return keySecret;
    }

    public void setKeySecret(String keySecret) {
        this.keySecret = keySecret == null ? "" : keySecret;
    }
}
