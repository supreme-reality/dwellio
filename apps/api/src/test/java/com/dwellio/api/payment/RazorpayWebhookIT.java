package com.dwellio.api.payment;

import com.dwellio.api.security.Auth0JwtConfig;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Import(RazorpayWebhookIT.TestJwtConfig.class)
class RazorpayWebhookIT {

    private static final String ISSUER = "https://dwellio-test.local/";
    private static final String AUDIENCE = "https://api.dwellio.local";
    private static final String WEBHOOK_SECRET = "test-razorpay-webhook-secret";
    private static final KeyPair KEY_PAIR = generateRsaKeyPair();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("dwellio")
            .withUsername("dwellio")
            .withPassword("dwellio");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> ISSUER);
        registry.add("dwellio.auth0.audience", () -> AUDIENCE);
        registry.add("dwellio.razorpay.webhook-secret", () -> WEBHOOK_SECRET);
    }

    @Autowired
    MockMvc mockMvc;

    @Test
    void validWebhookConfirmsIdempotently_andBadSignatureRejected() throws Exception {
        String ownerToken = mintToken("owner-" + UUID.randomUUID() + "@example.com", "Owner");
        String orgId = createOrg(ownerToken, "Rz Org " + UUID.randomUUID());
        String razorpayPaymentId = "pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);

        MvcResult create = mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationId":"%s",
                                  "paymentMethod":"RAZORPAY",
                                  "amount":2500.00,
                                  "currency":"INR",
                                  "externalReference":"%s"
                                }
                                """.formatted(orgId, razorpayPaymentId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        String paymentId = com.jayway.jsonpath.JsonPath.read(create.getResponse().getContentAsString(), "$.id");

        String body = """
                {
                  "event":"payment.captured",
                  "payload":{
                    "payment":{
                      "entity":{
                        "id":"%s",
                        "order_id":"order_abc",
                        "status":"captured",
                        "amount":250000,
                        "currency":"INR",
                        "notes":{"dwellioPaymentId":"%s"}
                      }
                    }
                  }
                }
                """.formatted(razorpayPaymentId, paymentId);

        String signature = hmac(WEBHOOK_SECRET, body);

        mockMvc.perform(post("/api/v1/webhooks/razorpay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(RazorpayWebhookController.SIGNATURE_HEADER, signature)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        // Replay same event — no double-confirm error; still CONFIRMED.
        mockMvc.perform(post("/api/v1/webhooks/razorpay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(RazorpayWebhookController.SIGNATURE_HEADER, signature)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(get("/api/v1/payments/" + paymentId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        String authorizedBody = """
                {
                  "event":"payment.authorized",
                  "payload":{
                    "payment":{
                      "entity":{
                        "id":"%s",
                        "order_id":"order_abc",
                        "status":"authorized",
                        "amount":250000,
                        "currency":"INR",
                        "notes":{"dwellioPaymentId":"%s"}
                      }
                    }
                  }
                }
                """.formatted(razorpayPaymentId, paymentId);
        mockMvc.perform(post("/api/v1/webhooks/razorpay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(RazorpayWebhookController.SIGNATURE_HEADER, hmac(WEBHOOK_SECRET, authorizedBody))
                        .content(authorizedBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ignored"));

        MvcResult wrongAmountCreate = mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationId":"%s",
                                  "paymentMethod":"RAZORPAY",
                                  "amount":1000.00,
                                  "currency":"INR"
                                }
                                """.formatted(orgId)))
                .andExpect(status().isCreated())
                .andReturn();
        String wrongPaymentId = com.jayway.jsonpath.JsonPath.read(
                wrongAmountCreate.getResponse().getContentAsString(),
                "$.id"
        );
        String wrongBody = """
                {
                  "event":"payment.captured",
                  "payload":{
                    "payment":{
                      "entity":{
                        "id":"pay_wrong_amount",
                        "order_id":"order_wrong",
                        "status":"captured",
                        "amount":999,
                        "currency":"INR",
                        "notes":{"dwellioPaymentId":"%s"}
                      }
                    }
                  }
                }
                """.formatted(wrongPaymentId);
        mockMvc.perform(post("/api/v1/webhooks/razorpay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(RazorpayWebhookController.SIGNATURE_HEADER, hmac(WEBHOOK_SECRET, wrongBody))
                        .content(wrongBody))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/v1/webhooks/razorpay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(RazorpayWebhookController.SIGNATURE_HEADER, "deadbeef")
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        mockMvc.perform(post("/api/v1/webhooks/razorpay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void webhookCaptureSettlesUnpaidInvoicesForTenancy() throws Exception {
        String ownerToken = mintToken("owner-" + UUID.randomUUID() + "@example.com", "Owner");
        String orgId = createOrg(ownerToken, "Settle Org " + UUID.randomUUID());
        String propertyId = createProperty(ownerToken, orgId);
        String roomId = createRoom(ownerToken, propertyId);
        String bedId = createBed(ownerToken, roomId);
        String tenantId = createTenant(ownerToken, propertyId);

        MvcResult moveIn = mockMvc.perform(post("/api/v1/properties/" + propertyId + "/move-ins")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"%s",
                                  "bedId":"%s",
                                  "moveInDate":"%s"
                                }
                                """.formatted(tenantId, bedId, java.time.LocalDate.now().plusDays(1))))
                .andExpect(status().isCreated())
                .andReturn();
        String tenancyId = com.jayway.jsonpath.JsonPath.read(moveIn.getResponse().getContentAsString(), "$.tenancyId");

        Instant now = Instant.now();
        invoiceRepository.save(new com.dwellio.api.invoice.InvoiceEntity(
                UUID.randomUUID(),
                UUID.fromString(tenancyId),
                "MONTHLY",
                null,
                java.time.LocalDate.now(),
                java.time.LocalDate.now().plusDays(5),
                "FINALIZED",
                "INR",
                new java.math.BigDecimal("3000.00"),
                new java.math.BigDecimal("3000.00"),
                now,
                now,
                now
        ));

        MvcResult create = mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationId":"%s",
                                  "tenancyId":"%s",
                                  "paymentMethod":"RAZORPAY",
                                  "amount":3000.00,
                                  "currency":"INR"
                                }
                                """.formatted(orgId, tenancyId)))
                .andExpect(status().isCreated())
                .andReturn();
        String paymentId = com.jayway.jsonpath.JsonPath.read(create.getResponse().getContentAsString(), "$.id");

        String body = """
                {
                  "event":"payment.captured",
                  "payload":{
                    "payment":{
                      "entity":{
                        "id":"pay_settle_%s",
                        "order_id":"order_settle",
                        "status":"captured",
                        "amount":300000,
                        "currency":"INR",
                        "notes":{"dwellioPaymentId":"%s"}
                      }
                    }
                  }
                }
                """.formatted(UUID.randomUUID().toString().substring(0, 8), paymentId);

        mockMvc.perform(post("/api/v1/webhooks/razorpay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(RazorpayWebhookController.SIGNATURE_HEADER, hmac(WEBHOOK_SECRET, body))
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.settlements.length()").value(1))
                .andExpect(jsonPath("$.settlements[0].amount").value(3000.00));
    }

    @Autowired
    com.dwellio.api.invoice.InvoiceRepository invoiceRepository;

    private String createProperty(String token, String orgId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/organizations/" + orgId + "/properties")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"House","paymentDueDays":5,"defaultCurrency":"INR"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createRoom(String token, String propertyId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/properties/" + propertyId + "/rooms")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"R1"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createBed(String token, String roomId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/rooms/" + roomId + "/beds")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"B1"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createTenant(String token, String propertyId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/properties/" + propertyId + "/tenants")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Guest","phone":"+91%s"}
                                """.formatted(System.nanoTime() % 1_000_000_000L)))
                .andExpect(status().isCreated())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createOrg(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/organizations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private static String hmac(String secret, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    private static String mintToken(String email, String name) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject("auth0|" + UUID.randomUUID())
                .audience(List.of(AUDIENCE))
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(3600)))
                .claim("email", email)
                .claim("name", name)
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).build(), claims);
        jwt.sign(new RSASSASigner((RSAPrivateKey) KEY_PAIR.getPrivate()));
        return jwt.serialize();
    }

    private static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @TestConfiguration
    static class TestJwtConfig {
        @Bean
        @Primary
        JwtDecoder testJwtDecoder() {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEY_PAIR.getPublic()).build();
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefaultWithIssuer(ISSUER),
                    Auth0JwtConfig.audienceValidator(AUDIENCE)
            ));
            return decoder;
        }
    }
}
