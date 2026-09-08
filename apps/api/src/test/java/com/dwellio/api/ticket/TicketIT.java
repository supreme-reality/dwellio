package com.dwellio.api.ticket;

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

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Import(TicketIT.TestJwtConfig.class)
class TicketIT {

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
    void tickets_assigneeValidation_managerScope_andDeactivateUnassigns() throws Exception {
        String ownerToken = mintToken("owner-" + UUID.randomUUID() + "@example.com", "Owner");
        String memberEmail = "member-" + UUID.randomUUID() + "@example.com";
        String memberToken = mintToken(memberEmail, "Member");
        String outsiderEmail = "out-" + UUID.randomUUID() + "@example.com";
        String outsiderToken = mintToken(outsiderEmail, "Outsider");

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk());
        MvcResult outsiderMe = mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isOk())
                .andReturn();
        String outsiderUserId = com.jayway.jsonpath.JsonPath.read(outsiderMe.getResponse().getContentAsString(), "$.id");

        String orgId = createOrg(ownerToken, "Ticket Org " + UUID.randomUUID());
        String propertyId = createProperty(ownerToken, orgId, "House");
        String otherPropertyId = createProperty(ownerToken, orgId, "Other House");

        MvcResult addMember = mockMvc.perform(post("/api/v1/organizations/" + orgId + "/members")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(memberEmail)))
                .andExpect(status().isCreated())
                .andReturn();
        String membershipId = com.jayway.jsonpath.JsonPath.read(
                addMember.getResponse().getContentAsString(), "$.membershipId");

        MvcResult assignMgr = mockMvc.perform(post("/api/v1/properties/" + propertyId + "/managers")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(memberEmail)))
                .andExpect(status().isCreated())
                .andReturn();
        String managerUserId = com.jayway.jsonpath.JsonPath.read(
                assignMgr.getResponse().getContentAsString(), "$.userId");

        mockMvc.perform(post("/api/v1/properties/" + otherPropertyId + "/tickets")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Leak"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/properties/" + propertyId + "/tickets")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Bad assignee","assignedToUserId":"%s"}
                                """.formatted(outsiderUserId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

        MvcResult create = mockMvc.perform(post("/api/v1/properties/" + propertyId + "/tickets")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"AC broken","assignedToUserId":"%s","status":"OPEN"}
                                """.formatted(managerUserId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.assignedToUserId").value(managerUserId))
                .andReturn();
        String ticketId = readId(create);

        mockMvc.perform(patch("/api/v1/tickets/" + ticketId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"IN_PROGRESS"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        mockMvc.perform(patch("/api/v1/organizations/" + orgId + "/members/" + membershipId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"INACTIVE"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/tickets/" + ticketId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedToUserId").value(nullValue()))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
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
