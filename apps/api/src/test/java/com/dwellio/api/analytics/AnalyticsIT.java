package com.dwellio.api.analytics;

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
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Import(AnalyticsIT.TestJwtConfig.class)
class AnalyticsIT {

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
        registry.add("dwellio.storage.type", () -> "memory");
    }

    @Autowired
    MockMvc mockMvc;

    @Test
    void analyticsShapes_andCsvExports_doNotMutate() throws Exception {
        String ownerToken = mintToken("owner-" + UUID.randomUUID() + "@example.com", "Owner");
        String orgId = createOrg(ownerToken, "Analytics Org " + UUID.randomUUID());
        String propertyId = createProperty(ownerToken, orgId, "House");
        createRoomAndBed(ownerToken, propertyId);

        mockMvc.perform(get("/api/v1/properties/" + propertyId + "/analytics/occupancy")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBeds").value(1))
                .andExpect(jsonPath("$.occupiedBeds").value(0))
                .andExpect(jsonPath("$.blockedBeds").value(0))
                .andExpect(jsonPath("$.occupancyRate").value(0.0));

        mockMvc.perform(get("/api/v1/properties/" + propertyId + "/analytics/revenue")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.paidAmount").value(0.0))
                .andExpect(jsonPath("$.finalizedInvoiceAmount").value(0.0));

        mockMvc.perform(get("/api/v1/properties/" + propertyId + "/analytics/expenses")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.totalAmount").value(0.0))
                .andExpect(jsonPath("$.byType").isArray());

        mockMvc.perform(get("/api/v1/properties/" + propertyId + "/exports/tenants")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(content().string(containsString("id,firstName,lastName,phone,email,status")));

        mockMvc.perform(get("/api/v1/properties/" + propertyId + "/exports/expenses")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")));

        mockMvc.perform(get("/api/v1/properties/" + propertyId + "/exports/invoices")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")));

        mockMvc.perform(get("/api/v1/properties/" + propertyId + "/exports/unknown")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/properties/" + propertyId + "/analytics/occupancy")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBeds").value(1));
    }

    private void createRoomAndBed(String token, String propertyId) throws Exception {
        MvcResult room = mockMvc.perform(post("/api/v1/properties/" + propertyId + "/rooms")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"R1"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String roomId = readId(room);
        mockMvc.perform(post("/api/v1/rooms/" + roomId + "/beds")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"B1"}
                                """))
                .andExpect(status().isCreated());
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
        return readId(result);
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
        return readId(result);
    }

    private static String readId(MvcResult result) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
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
