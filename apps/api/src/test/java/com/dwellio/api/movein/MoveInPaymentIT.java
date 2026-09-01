package com.dwellio.api.movein;

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

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Import(MoveInPaymentIT.TestJwtConfig.class)
class MoveInPaymentIT {

    private static final String ISSUER = "https://dwellio-test.local/";
    private static final String AUDIENCE = "https://api.dwellio.local";
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
        registry.add("dwellio.razorpay.webhook-secret", () -> "test-razorpay-webhook-secret");
    }

    @Autowired
    MockMvc mockMvc;

    @Test
    void happyPathActivatesOccupancyAndBedBecomesOccupied() throws Exception {
        String ownerToken = mintToken("owner-" + UUID.randomUUID() + "@example.com", "Owner");
        Fixture fx = seedPropertyWithBedAndTenant(ownerToken);
        String moveInId = createDraft(ownerToken, fx);

        mockMvc.perform(post("/api/v1/move-ins/" + moveInId + "/payment")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "paymentMethod":"CASH",
                                  "amount":15000.00,
                                  "currency":"INR",
                                  "depositAmount":5000.00
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moveIn.status").value("COMPLETED"))
                .andExpect(jsonPath("$.payment.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.payment.amount").value(15000.00));

        mockMvc.perform(get("/api/v1/beds/" + fx.bedId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availability").value("OCCUPIED"));

        mockMvc.perform(get("/api/v1/move-ins/" + moveInId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void blockedOrOccupiedBedReturns409BedUnavailable_andLeavesDraft() throws Exception {
        String ownerToken = mintToken("owner-" + UUID.randomUUID() + "@example.com", "Owner");
        Fixture fx = seedPropertyWithBedAndTenant(ownerToken);
        String firstMoveInId = createDraft(ownerToken, fx);

        mockMvc.perform(post("/api/v1/move-ins/" + firstMoveInId + "/payment")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "paymentMethod":"CASH",
                                  "amount":10000.00,
                                  "currency":"INR"
                                }
                                """))
                .andExpect(status().isOk());

        String tenant2 = createTenant(ownerToken, fx.propertyId, "+91" + (System.nanoTime() % 1_000_000_000L));
        MvcResult secondDraft = mockMvc.perform(post("/api/v1/properties/" + fx.propertyId + "/move-ins")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"%s",
                                  "bedId":"%s",
                                  "moveInDate":"%s"
                                }
                                """.formatted(tenant2, fx.bedId, LocalDate.now().plusDays(2))))
                .andExpect(status().isCreated())
                .andReturn();
        String secondMoveInId = com.jayway.jsonpath.JsonPath.read(
                secondDraft.getResponse().getContentAsString(),
                "$.id"
        );

        mockMvc.perform(post("/api/v1/move-ins/" + secondMoveInId + "/payment")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "paymentMethod":"CASH",
                                  "amount":10000.00,
                                  "currency":"INR"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("BED_UNAVAILABLE"))
                .andExpect(jsonPath("$.error.details.bedId").value(fx.bedId));

        mockMvc.perform(get("/api/v1/move-ins/" + secondMoveInId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));

        // Blocked bed path
        Fixture fx2 = seedPropertyWithBedAndTenant(ownerToken);
        String blockedMoveIn = createDraft(ownerToken, fx2);
        mockMvc.perform(post("/api/v1/beds/" + fx2.bedId + "/block")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Maintenance"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/move-ins/" + blockedMoveIn + "/payment")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "paymentMethod":"BANK_TRANSFER",
                                  "amount":8000.00,
                                  "currency":"INR",
                                  "bankTransferReference":"UTR-1"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("BED_UNAVAILABLE"));

        mockMvc.perform(get("/api/v1/move-ins/" + blockedMoveIn)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void razorpayMoveInStaysDraftUntilCaptureWebhookActivates() throws Exception {
        String ownerToken = mintToken("owner-" + UUID.randomUUID() + "@example.com", "Owner");
        Fixture fx = seedPropertyWithBedAndTenant(ownerToken);
        String moveInId = createDraft(ownerToken, fx);

        MvcResult pay = mockMvc.perform(post("/api/v1/move-ins/" + moveInId + "/payment")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "paymentMethod":"RAZORPAY",
                                  "amount":12000.00,
                                  "currency":"INR",
                                  "depositAmount":4000.00
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moveIn.status").value("DRAFT"))
                .andExpect(jsonPath("$.payment.status").value("PENDING"))
                .andExpect(jsonPath("$.razorpay.orderId").isNotEmpty())
                .andExpect(jsonPath("$.razorpay.keyId").isNotEmpty())
                .andExpect(jsonPath("$.razorpay.amount").value(12000.00))
                .andReturn();

        String paymentId = com.jayway.jsonpath.JsonPath.read(pay.getResponse().getContentAsString(), "$.payment.id");
        String orderId = com.jayway.jsonpath.JsonPath.read(pay.getResponse().getContentAsString(), "$.razorpay.orderId");

        mockMvc.perform(get("/api/v1/beds/" + fx.bedId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availability").value("AVAILABLE"));

        String razorpayPaymentId = "pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        String body = """
                {
                  "event":"payment.captured",
                  "payload":{
                    "payment":{
                      "entity":{
                        "id":"%s",
                        "order_id":"%s",
                        "status":"captured",
                        "amount":1200000,
                        "currency":"INR",
                        "notes":{"dwellioPaymentId":"%s"}
                      }
                    }
                  }
                }
                """.formatted(razorpayPaymentId, orderId, paymentId);
        String signature = hmac("test-razorpay-webhook-secret", body);

        mockMvc.perform(post("/api/v1/webhooks/razorpay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", signature)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(get("/api/v1/move-ins/" + moveInId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(get("/api/v1/beds/" + fx.bedId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availability").value("OCCUPIED"));
    }

    @Test
    void cancelDraftCancelsPendingRazorpay_andWebhookDoesNotConfirm() throws Exception {
        String ownerToken = mintToken("owner-" + UUID.randomUUID() + "@example.com", "Owner");
        Fixture fx = seedPropertyWithBedAndTenant(ownerToken);
        String moveInId = createDraft(ownerToken, fx);

        MvcResult pay = mockMvc.perform(post("/api/v1/move-ins/" + moveInId + "/payment")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "paymentMethod":"RAZORPAY",
                                  "amount":9000.00,
                                  "currency":"INR"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.status").value("PENDING"))
                .andReturn();
        String paymentId = com.jayway.jsonpath.JsonPath.read(pay.getResponse().getContentAsString(), "$.payment.id");
        String orderId = com.jayway.jsonpath.JsonPath.read(pay.getResponse().getContentAsString(), "$.razorpay.orderId");

        mockMvc.perform(post("/api/v1/move-ins/" + moveInId + "/cancel")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(get("/api/v1/payments/" + paymentId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        String razorpayPaymentId = "pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        String body = """
                {
                  "event":"payment.captured",
                  "payload":{
                    "payment":{
                      "entity":{
                        "id":"%s",
                        "order_id":"%s",
                        "status":"captured",
                        "amount":900000,
                        "currency":"INR",
                        "notes":{"dwellioPaymentId":"%s"}
                      }
                    }
                  }
                }
                """.formatted(razorpayPaymentId, orderId, paymentId);
        mockMvc.perform(post("/api/v1/webhooks/razorpay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", hmac("test-razorpay-webhook-secret", body))
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ignored"));

        mockMvc.perform(get("/api/v1/payments/" + paymentId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void razorpayPayIdempotentReplay_andSecondPendingRejected() throws Exception {
        String ownerToken = mintToken("owner-" + UUID.randomUUID() + "@example.com", "Owner");
        Fixture fx = seedPropertyWithBedAndTenant(ownerToken);
        String moveInId = createDraft(ownerToken, fx);
        String idem = "movein-rzp-" + UUID.randomUUID();

        MvcResult first = mockMvc.perform(post("/api/v1/move-ins/" + moveInId + "/payment")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "paymentMethod":"RAZORPAY",
                                  "amount":11000.00,
                                  "currency":"INR",
                                  "idempotencyKey":"%s"
                                }
                                """.formatted(idem)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.status").value("PENDING"))
                .andReturn();
        String paymentId = com.jayway.jsonpath.JsonPath.read(first.getResponse().getContentAsString(), "$.payment.id");
        String orderId = com.jayway.jsonpath.JsonPath.read(first.getResponse().getContentAsString(), "$.razorpay.orderId");

        mockMvc.perform(post("/api/v1/move-ins/" + moveInId + "/payment")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "paymentMethod":"RAZORPAY",
                                  "amount":11000.00,
                                  "currency":"INR",
                                  "idempotencyKey":"%s"
                                }
                                """.formatted(idem)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.id").value(paymentId))
                .andExpect(jsonPath("$.razorpay.orderId").value(orderId))
                .andExpect(jsonPath("$.payment.status").value("PENDING"));

        mockMvc.perform(post("/api/v1/move-ins/" + moveInId + "/payment")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "paymentMethod":"RAZORPAY",
                                  "amount":11000.00,
                                  "currency":"INR"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"))
                .andExpect(jsonPath("$.error.details.paymentId").value(paymentId));
    }

    @Test
    void serviceSelectionsPersistOnDraftAndActivateOnPay() throws Exception {
        String ownerToken = mintToken("owner-" + UUID.randomUUID() + "@example.com", "Owner");
        Fixture fx = seedPropertyWithBedAndTenant(ownerToken);

        MvcResult serviceCreate = mockMvc.perform(post("/api/v1/properties/" + fx.propertyId + "/services")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Meals",
                                  "billingType":"FIXED",
                                  "billingTiming":"UPFRONT",
                                  "mandatory":false,
                                  "prorationSetting":"NONE"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String serviceId = com.jayway.jsonpath.JsonPath.read(serviceCreate.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/api/v1/services/" + serviceId + "/config")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":500.00,"effectiveFrom":"%s"}
                                """.formatted(LocalDate.now())))
                .andExpect(status().isCreated());

        MvcResult create = mockMvc.perform(post("/api/v1/properties/" + fx.propertyId + "/move-ins")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"%s",
                                  "bedId":"%s",
                                  "moveInDate":"%s",
                                  "serviceSelections":[{"serviceId":"%s","selectedAmount":500.00}]
                                }
                                """.formatted(fx.tenantId, fx.bedId, LocalDate.now().plusDays(1), serviceId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.serviceSelections.length()").value(1))
                .andExpect(jsonPath("$.serviceSelections[0].serviceId").value(serviceId))
                .andReturn();
        String moveInId = com.jayway.jsonpath.JsonPath.read(create.getResponse().getContentAsString(), "$.id");
        String tenancyId = com.jayway.jsonpath.JsonPath.read(create.getResponse().getContentAsString(), "$.tenancyId");

        mockMvc.perform(patch("/api/v1/move-ins/" + moveInId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"serviceSelections":[]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceSelections.length()").value(0));

        mockMvc.perform(patch("/api/v1/move-ins/" + moveInId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"serviceSelections":[{"serviceId":"%s","selectedAmount":500.00}]}
                                """.formatted(serviceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceSelections.length()").value(1));

        mockMvc.perform(post("/api/v1/move-ins/" + moveInId + "/payment")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "paymentMethod":"CASH",
                                  "amount":10000.00,
                                  "currency":"INR"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moveIn.status").value("COMPLETED"));

        mockMvc.perform(get("/api/v1/tenancies/" + tenancyId + "/services")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].serviceId").value(serviceId))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    private static String hmac(String secret, String body) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(
                secret.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "HmacSHA256"
        ));
        return java.util.HexFormat.of().formatHex(mac.doFinal(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private String createDraft(String ownerToken, Fixture fx) throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/properties/" + fx.propertyId + "/move-ins")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"%s",
                                  "bedId":"%s",
                                  "moveInDate":"%s"
                                }
                                """.formatted(fx.tenantId, fx.bedId, LocalDate.now().plusDays(1))))
                .andExpect(status().isCreated())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");
    }

    private Fixture seedPropertyWithBedAndTenant(String ownerToken) throws Exception {
        String orgId = createOrg(ownerToken, "MovePay Org " + UUID.randomUUID());
        String propertyId = createProperty(ownerToken, orgId, "House");
        String roomId = createRoom(ownerToken, propertyId, "R1");
        String bedId = createBed(ownerToken, roomId, "B1");
        String tenantId = createTenant(ownerToken, propertyId, "+91" + (System.nanoTime() % 1_000_000_000L));
        return new Fixture(propertyId, bedId, tenantId);
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

    private String createProperty(String token, String orgId, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/organizations/" + orgId + "/properties")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","paymentDueDays":5,"defaultCurrency":"INR"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createRoom(String token, String propertyId, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/properties/" + propertyId + "/rooms")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createBed(String token, String roomId, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/rooms/" + roomId + "/beds")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createTenant(String token, String propertyId, String phone) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/properties/" + propertyId + "/tenants")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Guest","phone":"%s"}
                                """.formatted(phone)))
                .andExpect(status().isCreated())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private record Fixture(String propertyId, String bedId, String tenantId) {
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

        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).build(),
                claims
        );
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
