package com.dwellio.api.checkout;

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
import java.math.RoundingMode;
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
@Import(CheckoutIT.TestJwtConfig.class)
class CheckoutIT {

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

    @Test
    void previewMatchesSettlementFormula_whenDepositCoversOwed() throws Exception {
        String ownerToken = mintToken("owner-" + UUID.randomUUID() + "@example.com", "Owner");
        Stay fx = seedActiveStay(ownerToken, "10000.00");

        // SD=5000; damages=2000 → owed=2000+rentProration; use zero rent property path via override damages only on 0 rent
        // Re-seed with zero property rent for deterministic formula
        Stay zeroRent = seedActiveStay(ownerToken, "0.00");

        mockMvc.perform(post("/api/v1/checkout/preview")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenancyId":"%s",
                                  "damagesAmount":2000.00,
                                  "manualChargesAmount":500.00
                                }
                                """.formatted(zeroRent.tenancyId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenancyId").value(zeroRent.tenancyId))
                .andExpect(jsonPath("$.outstandingReceivables").value(0.00))
                .andExpect(jsonPath("$.newCheckoutCharges").value(2500.00))
                .andExpect(jsonPath("$.depositBalanceBefore").value(5000.00))
                .andExpect(jsonPath("$.depositDeduction").value(2500.00))
                .andExpect(jsonPath("$.depositRefundDue").value(2500.00))
                .andExpect(jsonPath("$.totalReceivable").value(2500.00))
                .andExpect(jsonPath("$.netReceivable").value(0.00))
                .andExpect(jsonPath("$.refundDue").value(2500.00))
                .andExpect(jsonPath("$.currency").value("INR"));
    }

    @Test
    void confirmWithLeaveReceivable_closesStay_writesDeduction_andSecondCheckoutConflicts() throws Exception {
        String ownerToken = mintToken("owner-" + UUID.randomUUID() + "@example.com", "Owner");
        Stay fx = seedActiveStay(ownerToken, "0.00");

        // owed 8000 > SD 5000 → net 3000
        MvcResult confirm = mockMvc.perform(post("/api/v1/checkout")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenancyId":"%s",
                                  "damagesAmount":8000.00,
                                  "leaveReceivable":true
                                }
                                """.formatted(fx.tenancyId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.netReceivable").value(3000.00))
                .andExpect(jsonPath("$.refundDue").value(0.00))
                .andExpect(jsonPath("$.depositDeduction").value(5000.00))
                .andExpect(jsonPath("$.checkoutInvoiceId").isNotEmpty())
                .andExpect(jsonPath("$.settlementId").isNotEmpty())
                .andReturn();

        String settlementId = com.jayway.jsonpath.JsonPath.read(
                confirm.getResponse().getContentAsString(),
                "$.settlementId"
        );

        mockMvc.perform(get("/api/v1/settlements/" + settlementId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.netReceivable").value(3000.00))
                .andExpect(jsonPath("$.tenancyId").value(fx.tenancyId));

        mockMvc.perform(get("/api/v1/tenancies/" + fx.tenancyId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHECKED_OUT"))
                .andExpect(jsonPath("$.currentOccupancy").doesNotExist());

        mockMvc.perform(get("/api/v1/beds/" + fx.bedId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availability").value("AVAILABLE"));

        mockMvc.perform(get("/api/v1/tenancies/" + fx.tenancyId + "/deposit")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(0.00));

        mockMvc.perform(post("/api/v1/checkout")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenancyId":"%s",
                                  "leaveReceivable":true
                                }
                                """.formatted(fx.tenancyId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    @Test
    void confirmWithoutLeaveReceivableWhenNetDue_rejected() throws Exception {
        String ownerToken = mintToken("owner-" + UUID.randomUUID() + "@example.com", "Owner");
        Stay fx = seedActiveStay(ownerToken, "0.00");

        mockMvc.perform(post("/api/v1/checkout")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenancyId":"%s",
                                  "damagesAmount":8000.00
                                }
                                """.formatted(fx.tenancyId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void confirmWhenDepositCoversAll_setsRefundDue_andEndsEnrollments() throws Exception {
        String ownerToken = mintToken("owner-" + UUID.randomUUID() + "@example.com", "Owner");
        Stay fx = seedActiveStayWithService(ownerToken, "0.00");

        mockMvc.perform(post("/api/v1/checkout")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenancyId":"%s",
                                  "damagesAmount":1000.00
                                }
                                """.formatted(fx.tenancyId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.netReceivable").value(0.00))
                .andExpect(jsonPath("$.refundDue").value(4000.00))
                .andExpect(jsonPath("$.depositDeduction").value(1000.00));

        mockMvc.perform(get("/api/v1/tenancies/" + fx.tenancyId + "/services")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ENDED"));
    }

    @Test
    void refundPaysRemainingDeposit_andCannotOverRefund() throws Exception {
        String ownerToken = mintToken("owner-" + UUID.randomUUID() + "@example.com", "Owner");
        Stay fx = seedActiveStay(ownerToken, "0.00");

        MvcResult confirm = mockMvc.perform(post("/api/v1/checkout")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenancyId":"%s",
                                  "damagesAmount":1000.00
                                }
                                """.formatted(fx.tenancyId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundDue").value(4000.00))
                .andReturn();
        String settlementId = com.jayway.jsonpath.JsonPath.read(
                confirm.getResponse().getContentAsString(),
                "$.settlementId"
        );

        mockMvc.perform(post("/api/v1/settlements/" + settlementId + "/refund")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentMethod":"CASH"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(4000.00))
                .andExpect(jsonPath("$.depositBalanceAfter").value(0.00));

        mockMvc.perform(get("/api/v1/tenancies/" + fx.tenancyId + "/deposit")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(0.00));

        mockMvc.perform(post("/api/v1/settlements/" + settlementId + "/refund")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentMethod":"CASH"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void refundBlockedWhenRefundDueIsZero() throws Exception {
        String ownerToken = mintToken("owner-" + UUID.randomUUID() + "@example.com", "Owner");
        Stay fx = seedActiveStay(ownerToken, "0.00");

        MvcResult confirm = mockMvc.perform(post("/api/v1/checkout")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenancyId":"%s",
                                  "damagesAmount":8000.00,
                                  "leaveReceivable":true
                                }
                                """.formatted(fx.tenancyId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundDue").value(0.00))
                .andReturn();
        String settlementId = com.jayway.jsonpath.JsonPath.read(
                confirm.getResponse().getContentAsString(),
                "$.settlementId"
        );

        mockMvc.perform(post("/api/v1/settlements/" + settlementId + "/refund")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentMethod":"CASH"}
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    private Stay seedActiveStay(String ownerToken, String rentAmount) throws Exception {
        return seedActiveStayInternal(ownerToken, rentAmount, false);
    }

    private Stay seedActiveStayWithService(String ownerToken, String rentAmount) throws Exception {
        return seedActiveStayInternal(ownerToken, rentAmount, true);
    }

    private Stay seedActiveStayInternal(String ownerToken, String rentAmount, boolean withService) throws Exception {
        String orgId = createOrg(ownerToken, "Checkout Org " + UUID.randomUUID());
        String propertyId = createProperty(ownerToken, orgId, "House");
        String roomId = createRoom(ownerToken, propertyId, "R1");
        String bedId = createBed(ownerToken, roomId, "B1");
        String tenantId = createTenant(ownerToken, propertyId, "+91" + (System.nanoTime() % 1_000_000_000L));

        mockMvc.perform(post("/api/v1/properties/" + propertyId + "/rent-config")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":%s,"effectiveFrom":"%s"}
                                """.formatted(rentAmount, LocalDate.now().minusDays(30))))
                .andExpect(status().isCreated());

        String serviceSelection = "";
        if (withService) {
            MvcResult serviceCreate = mockMvc.perform(post("/api/v1/properties/" + propertyId + "/services")
                            .header("Authorization", "Bearer " + ownerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name":"Wifi",
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
                                    {"amount":100.00,"effectiveFrom":"%s"}
                                    """.formatted(LocalDate.now())))
                    .andExpect(status().isCreated());
            serviceSelection = ",\"serviceSelections\":[{\"serviceId\":\"%s\",\"selectedAmount\":100.00}]"
                    .formatted(serviceId);
        }

        MvcResult create = mockMvc.perform(post("/api/v1/properties/" + propertyId + "/move-ins")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"%s",
                                  "bedId":"%s",
                                  "moveInDate":"%s"%s
                                }
                                """.formatted(tenantId, bedId, LocalDate.now().plusDays(1), serviceSelection)))
                .andExpect(status().isCreated())
                .andReturn();
        String moveInId = com.jayway.jsonpath.JsonPath.read(create.getResponse().getContentAsString(), "$.id");
        String tenancyId = com.jayway.jsonpath.JsonPath.read(create.getResponse().getContentAsString(), "$.tenancyId");

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
                .andExpect(status().isOk());

        return new Stay(propertyId, bedId, tenancyId);
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

    private record Stay(String propertyId, String bedId, String tenancyId) {
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
