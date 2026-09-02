package com.dwellio.api.transfer;

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
@Import(TransferIT.TestJwtConfig.class)
class TransferIT {

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
    void previewShowsDestinationRentProrationAndInformationalEnrollments() throws Exception {
        String ownerToken = mintToken("owner-" + UUID.randomUUID() + "@example.com", "Owner");
        StayFixture fx = seedActiveStayWithSecondBedAndService(ownerToken);

        mockMvc.perform(post("/api/v1/transfers/preview")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenancyId":"%s",
                                  "destinationBedId":"%s"
                                }
                                """.formatted(fx.tenancyId, fx.destinationBedId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenancyId").value(fx.tenancyId))
                .andExpect(jsonPath("$.sourceBedId").value(fx.sourceBedId))
                .andExpect(jsonPath("$.destinationBedId").value(fx.destinationBedId))
                .andExpect(jsonPath("$.destinationAvailable").value(true))
                .andExpect(jsonPath("$.sourceRent").value(10000.00))
                .andExpect(jsonPath("$.destinationRent").value(15000.00))
                .andExpect(jsonPath("$.prorationDiff").isNumber())
                .andExpect(jsonPath("$.chargeAmount").isNumber())
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.currentEnrollments.length()").value(1))
                .andExpect(jsonPath("$.currentEnrollments[0].serviceId").value(fx.serviceId))
                .andExpect(jsonPath("$.currentEnrollments[0].status").value("ACTIVE"));
    }

    @Test
    void executeSwapsOccupancyAtomically_keepsTenancyAndEnrollments_noDepositRefund() throws Exception {
        String ownerToken = mintToken("owner-" + UUID.randomUUID() + "@example.com", "Owner");
        StayFixture fx = seedActiveStayWithSecondBedAndService(ownerToken);

        mockMvc.perform(get("/api/v1/tenancies/" + fx.tenancyId + "/deposit")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(5000.00));

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenancyId":"%s",
                                  "destinationBedId":"%s"
                                }
                                """.formatted(fx.tenancyId, fx.destinationBedId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenancyId").value(fx.tenancyId))
                .andExpect(jsonPath("$.sourceOccupancyStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.destinationBedId").value(fx.destinationBedId))
                .andExpect(jsonPath("$.destinationOccupancyStatus").value("ACTIVE"));

        mockMvc.perform(get("/api/v1/beds/" + fx.sourceBedId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availability").value("AVAILABLE"));

        mockMvc.perform(get("/api/v1/beds/" + fx.destinationBedId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availability").value("OCCUPIED"));

        mockMvc.perform(get("/api/v1/tenancies/" + fx.tenancyId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.currentOccupancy.bedId").value(fx.destinationBedId));

        mockMvc.perform(get("/api/v1/tenancies/" + fx.tenancyId + "/services")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].serviceId").value(fx.serviceId))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));

        mockMvc.perform(get("/api/v1/tenancies/" + fx.tenancyId + "/deposit")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(5000.00));
    }

    @Test
    void executeUsesOverrideAmountForProrationCharge() throws Exception {
        String ownerToken = mintToken("owner-" + UUID.randomUUID() + "@example.com", "Owner");
        StayFixture fx = seedActiveStayWithSecondBedAndService(ownerToken);

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenancyId":"%s",
                                  "destinationBedId":"%s",
                                  "prorationOverrideAmount":250.00
                                }
                                """.formatted(fx.tenancyId, fx.destinationBedId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chargeAmount").value(250.00))
                .andExpect(jsonPath("$.prorationInvoiceId").isNotEmpty());

        mockMvc.perform(get("/api/v1/tenancies/" + fx.tenancyId + "/invoices")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].invoiceType").value("UPFRONT"))
                .andExpect(jsonPath("$[0].total").value(250.00))
                .andExpect(jsonPath("$[0].status").value("FINALIZED"));
    }

    @Test
    void unavailableDestinationReturns409_andLeavesSourceActive() throws Exception {
        String ownerToken = mintToken("owner-" + UUID.randomUUID() + "@example.com", "Owner");
        StayFixture fx = seedActiveStayWithSecondBedAndService(ownerToken);

        // Occupy destination with another tenant
        String otherTenant = createTenant(ownerToken, fx.propertyId, "+91" + (System.nanoTime() % 1_000_000_000L));
        MvcResult otherMoveIn = mockMvc.perform(post("/api/v1/properties/" + fx.propertyId + "/move-ins")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"%s",
                                  "bedId":"%s",
                                  "moveInDate":"%s"
                                }
                                """.formatted(otherTenant, fx.destinationBedId, LocalDate.now().plusDays(1))))
                .andExpect(status().isCreated())
                .andReturn();
        String otherMoveInId = com.jayway.jsonpath.JsonPath.read(
                otherMoveIn.getResponse().getContentAsString(),
                "$.id"
        );
        mockMvc.perform(post("/api/v1/move-ins/" + otherMoveInId + "/payment")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "paymentMethod":"CASH",
                                  "amount":8000.00,
                                  "currency":"INR"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenancyId":"%s",
                                  "destinationBedId":"%s"
                                }
                                """.formatted(fx.tenancyId, fx.destinationBedId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("BED_UNAVAILABLE"))
                .andExpect(jsonPath("$.error.details.bedId").value(fx.destinationBedId));

        mockMvc.perform(get("/api/v1/beds/" + fx.sourceBedId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availability").value("OCCUPIED"));

        mockMvc.perform(get("/api/v1/tenancies/" + fx.tenancyId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentOccupancy.bedId").value(fx.sourceBedId))
                .andExpect(jsonPath("$.currentOccupancy.status").value("ACTIVE"));
    }

    private StayFixture seedActiveStayWithSecondBedAndService(String ownerToken) throws Exception {
        String orgId = createOrg(ownerToken, "Transfer Org " + UUID.randomUUID());
        String propertyId = createProperty(ownerToken, orgId, "House");
        String roomId = createRoom(ownerToken, propertyId, "R1");
        String sourceBedId = createBed(ownerToken, roomId, "B1");
        String destinationBedId = createBed(ownerToken, roomId, "B2");
        String tenantId = createTenant(ownerToken, propertyId, "+91" + (System.nanoTime() % 1_000_000_000L));

        mockMvc.perform(post("/api/v1/properties/" + propertyId + "/rent-config")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":10000.00,"effectiveFrom":"%s"}
                                """.formatted(LocalDate.now().minusDays(30))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/beds/" + destinationBedId + "/rent-config")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":15000.00,"effectiveFrom":"%s"}
                                """.formatted(LocalDate.now().minusDays(30))))
                .andExpect(status().isCreated());

        MvcResult serviceCreate = mockMvc.perform(post("/api/v1/properties/" + propertyId + "/services")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Laundry",
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
                                {"amount":300.00,"effectiveFrom":"%s"}
                                """.formatted(LocalDate.now())))
                .andExpect(status().isCreated());

        MvcResult create = mockMvc.perform(post("/api/v1/properties/" + propertyId + "/move-ins")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"%s",
                                  "bedId":"%s",
                                  "moveInDate":"%s",
                                  "serviceSelections":[{"serviceId":"%s","selectedAmount":300.00}]
                                }
                                """.formatted(tenantId, sourceBedId, LocalDate.now().plusDays(1), serviceId)))
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

        return new StayFixture(propertyId, sourceBedId, destinationBedId, tenancyId, serviceId);
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

    private record StayFixture(
            String propertyId,
            String sourceBedId,
            String destinationBedId,
            String tenancyId,
            String serviceId
    ) {
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
