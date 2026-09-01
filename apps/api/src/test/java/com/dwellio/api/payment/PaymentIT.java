package com.dwellio.api.payment;

import com.dwellio.api.invoice.InvoiceEntity;
import com.dwellio.api.invoice.InvoiceRepository;
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

import java.math.BigDecimal;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Import(PaymentIT.TestJwtConfig.class)
class PaymentIT {

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
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    InvoiceRepository invoiceRepository;

    @Test
    void confirmIsImmutable_duplicateIdempotencyConflicts_andPartialPayRejected() throws Exception {
        String ownerToken = mintToken("owner-" + UUID.randomUUID() + "@example.com", "Owner");
        Fixture fx = seedOrgPropertyTenancy(ownerToken);
        String idempotencyKey = "pay-key-" + UUID.randomUUID();

        MvcResult createResult = mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationId":"%s",
                                  "tenancyId":"%s",
                                  "paymentMethod":"CASH",
                                  "amount":5000.00,
                                  "currency":"INR",
                                  "idempotencyKey":"%s"
                                }
                                """.formatted(fx.orgId, fx.tenancyId, idempotencyKey)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.paymentMethod").value("CASH"))
                .andExpect(jsonPath("$.amount").value(5000.00))
                .andReturn();

        String paymentId = com.jayway.jsonpath.JsonPath.read(
                createResult.getResponse().getContentAsString(),
                "$.id"
        );

        mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationId":"%s",
                                  "tenancyId":"%s",
                                  "paymentMethod":"CASH",
                                  "amount":5000.00,
                                  "currency":"INR",
                                  "idempotencyKey":"%s"
                                }
                                """.formatted(fx.orgId, fx.tenancyId, idempotencyKey)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));

        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/confirm")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.confirmedAt").isNotEmpty());

        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/confirm")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"))
                .andExpect(jsonPath("$.error.message").value("Payment is already confirmed and immutable"));

        mockMvc.perform(get("/api/v1/payments/" + paymentId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        seedFinalizedInvoice(fx.tenancyId, new BigDecimal("10000.00"));

        MvcResult partialCreate = mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationId":"%s",
                                  "tenancyId":"%s",
                                  "paymentMethod":"BANK_TRANSFER",
                                  "amount":1000.00,
                                  "currency":"INR",
                                  "bankTransferReference":"NEFT-123"
                                }
                                """.formatted(fx.orgId, fx.tenancyId)))
                .andExpect(status().isCreated())
                .andReturn();
        String partialPaymentId = com.jayway.jsonpath.JsonPath.read(
                partialCreate.getResponse().getContentAsString(),
                "$.id"
        );

        // Server owns settlement: unpaid invoices exist but amount != sum → reject (no client invoiceIds).
        mockMvc.perform(post("/api/v1/payments/" + partialPaymentId + "/confirm")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.message")
                        .value("Partial payments are not allowed; payment amount must equal invoice total"));

        MvcResult fullCreate = mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationId":"%s",
                                  "tenancyId":"%s",
                                  "paymentMethod":"CASH",
                                  "amount":10000.00,
                                  "currency":"INR"
                                }
                                """.formatted(fx.orgId, fx.tenancyId)))
                .andExpect(status().isCreated())
                .andReturn();
        String fullPaymentId = com.jayway.jsonpath.JsonPath.read(
                fullCreate.getResponse().getContentAsString(),
                "$.id"
        );

        mockMvc.perform(post("/api/v1/payments/" + fullPaymentId + "/confirm")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.settlements.length()").value(1))
                .andExpect(jsonPath("$.settlements[0].amount").value(10000.00));

        mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationId":"%s",
                                  "paymentMethod":"BANK_TRANSFER",
                                  "amount":100.00,
                                  "currency":"INR"
                                }
                                """.formatted(fx.orgId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    private UUID seedFinalizedInvoice(String tenancyId, BigDecimal total) {
        Instant now = Instant.now();
        LocalDate today = LocalDate.now();
        InvoiceEntity invoice = invoiceRepository.save(new InvoiceEntity(
                UUID.randomUUID(),
                UUID.fromString(tenancyId),
                "UPFRONT",
                null,
                today,
                today.plusDays(5),
                "FINALIZED",
                "INR",
                total,
                total,
                now,
                now,
                now
        ));
        return invoice.getId();
    }

    private Fixture seedOrgPropertyTenancy(String ownerToken) throws Exception {
        String orgId = createOrg(ownerToken, "Pay Org " + UUID.randomUUID());
        String propertyId = createProperty(ownerToken, orgId, "House");
        String roomId = createRoom(ownerToken, propertyId, "R1");
        String bedId = createBed(ownerToken, roomId, "B1");
        String tenantId = createTenant(ownerToken, propertyId, "+91" + (System.nanoTime() % 1_000_000_000L));

        MvcResult moveIn = mockMvc.perform(post("/api/v1/properties/" + propertyId + "/move-ins")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"%s",
                                  "bedId":"%s",
                                  "moveInDate":"%s"
                                }
                                """.formatted(tenantId, bedId, LocalDate.now().plusDays(1))))
                .andExpect(status().isCreated())
                .andReturn();
        String tenancyId = com.jayway.jsonpath.JsonPath.read(moveIn.getResponse().getContentAsString(), "$.tenancyId");
        return new Fixture(orgId, propertyId, tenancyId);
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

    private record Fixture(String orgId, String propertyId, String tenancyId) {
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
        JwtDecoder jwtDecoder() {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEY_PAIR.getPublic()).build();
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefaultWithIssuer(ISSUER),
                    Auth0JwtConfig.audienceValidator(AUDIENCE)
            ));
            return decoder;
        }
    }
}
