package com.dwellio.api.billing;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Import(BillingWorkerIT.TestJwtConfig.class)
class BillingWorkerIT {

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
    BillingFanOutService billingFanOutService;

    @Autowired
    MonthlyInvoiceCreationService monthlyInvoiceCreationService;

    @Test
    void fanOutIsIdempotentForSamePeriod() throws Exception {
        String ownerToken = mintToken("owner-" + UUID.randomUUID() + "@example.com", "Owner");
        seedActiveStay(ownerToken);

        LocalDate period = LocalDate.of(2091, 6, 1);

        var first = billingFanOutService.fanOut(period);
        var second = billingFanOutService.fanOut(period);

        org.junit.jupiter.api.Assertions.assertFalse(first.alreadyExisted());
        org.junit.jupiter.api.Assertions.assertTrue(first.newlyEnqueued() >= 1);
        org.junit.jupiter.api.Assertions.assertEquals(first.itemCount(), first.newlyEnqueued());
        org.junit.jupiter.api.Assertions.assertTrue(second.alreadyExisted());
        org.junit.jupiter.api.Assertions.assertEquals(first.itemCount(), second.itemCount());
        org.junit.jupiter.api.Assertions.assertEquals(0, second.newlyEnqueued());
    }

    @Test
    void processPendingCreatesMonthlyInvoice_andDuplicateDoesNotDoublePost() throws Exception {
        String ownerToken = mintToken("owner-" + UUID.randomUUID() + "@example.com", "Owner");
        Stay fx = seedActiveStay(ownerToken);
        LocalDate period = LocalDate.of(2092, 7, 1);

        billingFanOutService.fanOut(period);
        var first = monthlyInvoiceCreationService.processPendingItems();
        org.junit.jupiter.api.Assertions.assertTrue(first.processed() >= 1);

        mockMvc.perform(get("/api/v1/tenancies/" + fx.tenancyId + "/invoices")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.invoiceType == 'MONTHLY')].total").value(org.hamcrest.Matchers.hasItem(10000.00)))
                .andExpect(jsonPath("$[?(@.invoiceType == 'MONTHLY')].status").value(org.hamcrest.Matchers.hasItem("FINALIZED")));

        // Re-fan-out is no-op; re-process finds nothing pending for this run
        billingFanOutService.fanOut(period);
        var second = monthlyInvoiceCreationService.processPendingItems();
        org.junit.jupiter.api.Assertions.assertEquals(0, second.processed());

        mockMvc.perform(get("/api/v1/tenancies/" + fx.tenancyId + "/invoices")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.invoiceType == 'MONTHLY')]").isArray())
                .andExpect(jsonPath("$[?(@.billingPeriod == '%s')]".formatted(period.toString()),
                        org.hamcrest.Matchers.hasSize(1)));
    }

    private Stay seedActiveStay(String ownerToken) throws Exception {
        String orgId = createOrg(ownerToken, "Bill Org " + UUID.randomUUID());
        String propertyId = createProperty(ownerToken, orgId, "House");
        String roomId = createRoom(ownerToken, propertyId, "R1");
        String bedId = createBed(ownerToken, roomId, "B1");
        String tenantId = createTenant(ownerToken, propertyId, "+91" + (System.nanoTime() % 1_000_000_000L));

        mockMvc.perform(post("/api/v1/properties/" + propertyId + "/rent-config")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":10000.00,"effectiveFrom":"%s"}
                                """.formatted(LocalDate.now().minusDays(30))))
                .andExpect(status().isCreated());

        MvcResult create = mockMvc.perform(post("/api/v1/properties/" + propertyId + "/move-ins")
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
        String moveInId = com.jayway.jsonpath.JsonPath.read(create.getResponse().getContentAsString(), "$.id");
        String tenancyId = com.jayway.jsonpath.JsonPath.read(create.getResponse().getContentAsString(), "$.tenancyId");

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
                .andExpect(status().isOk());

        return new Stay(tenancyId);
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

    private record Stay(String tenancyId) {
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
