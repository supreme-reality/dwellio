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
@Import(MoveInIT.TestJwtConfig.class)
class MoveInIT {

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
    void draftDoesNotChangeBedAvailability_andGetResumesFields() throws Exception {
        String ownerToken = mintToken("owner-" + UUID.randomUUID() + "@example.com", "Owner");
        Fixture fx = seedPropertyWithBedAndTenant(ownerToken);
        LocalDate moveInDate = LocalDate.now().plusDays(3);

        MvcResult createResult = mockMvc.perform(post("/api/v1/properties/" + fx.propertyId + "/move-ins")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId":"%s",
                                  "bedId":"%s",
                                  "moveInDate":"%s"
                                }
                                """.formatted(fx.tenantId, fx.bedId, moveInDate)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.tenantId").value(fx.tenantId))
                .andExpect(jsonPath("$.bedId").value(fx.bedId))
                .andExpect(jsonPath("$.moveInDate").value(moveInDate.toString()))
                .andExpect(jsonPath("$.tenancyId").isNotEmpty())
                .andExpect(jsonPath("$.tenancyStatus").value("ACTIVE"))
                .andReturn();

        String moveInId = com.jayway.jsonpath.JsonPath.read(
                createResult.getResponse().getContentAsString(),
                "$.id"
        );
        String tenancyId = com.jayway.jsonpath.JsonPath.read(
                createResult.getResponse().getContentAsString(),
                "$.tenancyId"
        );

        mockMvc.perform(get("/api/v1/beds/" + fx.bedId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availability").value("AVAILABLE"));

        mockMvc.perform(get("/api/v1/move-ins/" + moveInId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(moveInId))
                .andExpect(jsonPath("$.tenancyId").value(tenancyId))
                .andExpect(jsonPath("$.tenantId").value(fx.tenantId))
                .andExpect(jsonPath("$.bedId").value(fx.bedId))
                .andExpect(jsonPath("$.moveInDate").value(moveInDate.toString()))
                .andExpect(jsonPath("$.status").value("DRAFT"));

        LocalDate updatedDate = moveInDate.plusDays(2);
        mockMvc.perform(patch("/api/v1/move-ins/" + moveInId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"moveInDate":"%s"}
                                """.formatted(updatedDate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moveInDate").value(updatedDate.toString()))
                .andExpect(jsonPath("$.status").value("DRAFT"));

        mockMvc.perform(get("/api/v1/beds/" + fx.bedId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availability").value("AVAILABLE"));
    }

    @Test
    void cancelDraftCancelsMoveInAndTenancy() throws Exception {
        String ownerToken = mintToken("owner-" + UUID.randomUUID() + "@example.com", "Owner");
        Fixture fx = seedPropertyWithBedAndTenant(ownerToken);

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

        String moveInId = com.jayway.jsonpath.JsonPath.read(
                createResult.getResponse().getContentAsString(),
                "$.id"
        );
        String tenancyId = com.jayway.jsonpath.JsonPath.read(
                createResult.getResponse().getContentAsString(),
                "$.tenancyId"
        );

        mockMvc.perform(post("/api/v1/move-ins/" + moveInId + "/cancel")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.tenancyStatus").value("CANCELLED"));

        mockMvc.perform(get("/api/v1/tenants/" + fx.tenantId + "/stay-history")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tenancyId").value(tenancyId))
                .andExpect(jsonPath("$[0].status").value("CANCELLED"));

        mockMvc.perform(get("/api/v1/beds/" + fx.bedId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availability").value("AVAILABLE"));
    }

    @Test
    void outOfScopeGetReturns404MoveInNotFound() throws Exception {
        String ownerA = mintToken("owner-a-" + UUID.randomUUID() + "@example.com", "Owner A");
        String ownerB = mintToken("owner-b-" + UUID.randomUUID() + "@example.com", "Owner B");

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + ownerB))
                .andExpect(status().isOk());

        Fixture fx = seedPropertyWithBedAndTenant(ownerA);
        String moveInId = createDraftMoveIn(ownerA, fx);

        mockMvc.perform(get("/api/v1/move-ins/" + moveInId)
                        .header("Authorization", "Bearer " + ownerB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("Move-in not found"));
    }

    @Test
    void outOfScopeMutateOnNonDraftReturns404Not409() throws Exception {
        String ownerA = mintToken("owner-a-" + UUID.randomUUID() + "@example.com", "Owner A");
        String ownerB = mintToken("owner-b-" + UUID.randomUUID() + "@example.com", "Owner B");

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + ownerB))
                .andExpect(status().isOk());

        Fixture fx = seedPropertyWithBedAndTenant(ownerA);
        String moveInId = createDraftMoveIn(ownerA, fx);

        mockMvc.perform(post("/api/v1/move-ins/" + moveInId + "/cancel")
                        .header("Authorization", "Bearer " + ownerA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(patch("/api/v1/move-ins/" + moveInId)
                        .header("Authorization", "Bearer " + ownerB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"moveInDate":"%s"}
                                """.formatted(LocalDate.now().plusDays(5))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("Move-in not found"));

        mockMvc.perform(post("/api/v1/move-ins/" + moveInId + "/cancel")
                        .header("Authorization", "Bearer " + ownerB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("Move-in not found"));
    }

    private String createDraftMoveIn(String ownerToken, Fixture fx) throws Exception {
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
        return com.jayway.jsonpath.JsonPath.read(
                createResult.getResponse().getContentAsString(),
                "$.id"
        );
    }

    private Fixture seedPropertyWithBedAndTenant(String ownerToken) throws Exception {
        String orgId = createOrg(ownerToken, "MoveIn Org " + UUID.randomUUID());
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
            throw new IllegalStateException("Failed to generate RSA key pair", ex);
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
