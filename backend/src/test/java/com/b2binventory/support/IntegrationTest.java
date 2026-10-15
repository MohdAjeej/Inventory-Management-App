package com.b2binventory.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Boots the full application against a throwaway PostgreSQL container (Docker must be running).
 * The container is shared by all test classes; each test registers its own businesses, so tests stay independent.
 */
@SpringBootTest(properties = {
        "app.jwt.secret=dGVzdC1zZWNyZXQtdGhhdC1pcy1kZWZpbml0ZWx5LWxvbmctZW5vdWdoLWZvci1oczM4NA==",
        "DB_PASSWORD=unused",
        "app.uploads.dir=build/test-uploads"
})
@AutoConfigureMockMvc
public abstract class IntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    /** A logged-in member of a business. */
    public record Session(String token, long businessId, long membershipId, String email, String password) {
    }

    public record Response(int status, JsonNode body) {
        public JsonNode get(String field) {
            return body.get(field);
        }
    }

    protected Session registerBusiness(String name) {
        String email = "admin-" + UUID.randomUUID() + "@test.local";
        Response r = call(null, HttpMethod.POST, "/api/auth/register", Map.of(
                "businessName", name, "businessType", "Testing", "fullName", "Admin " + name,
                "email", email, "password", "password123"));
        if (r.status() != 201) throw new AssertionError("register failed: " + r);
        return session(r, email, "password123");
    }

    /** Invites a new employee with the given role and returns their session after accepting. */
    protected Session addEmployee(Session admin, String role) {
        String email = "emp-" + UUID.randomUUID() + "@test.local";
        Response invite = call(admin, HttpMethod.POST, "/api/invitations", Map.of("email", email, "role", role));
        if (invite.status() != 201) throw new AssertionError("invite failed: " + invite);
        Response accepted = call(null, HttpMethod.POST, "/api/auth/accept-invitation", Map.of(
                "code", invite.get("code").asText(), "fullName", role + " user", "password", "password123"));
        if (accepted.status() != 200) throw new AssertionError("accept failed: " + accepted);
        return session(accepted, email, "password123");
    }

    protected Session session(Response auth, String email, String password) {
        JsonNode user = auth.get("user");
        return new Session(auth.get("accessToken").asText(), user.get("businessId").asLong(),
                user.get("membershipId").asLong(), email, password);
    }

    protected Response get(Session s, String path) {
        return call(s, HttpMethod.GET, path, null);
    }

    protected Response post(Session s, String path, Object body) {
        return call(s, HttpMethod.POST, path, body);
    }

    protected Response put(Session s, String path, Object body) {
        return call(s, HttpMethod.PUT, path, body);
    }

    protected Response delete(Session s, String path) {
        return call(s, HttpMethod.DELETE, path, null);
    }

    protected Response call(Session s, HttpMethod method, String path, Object body) {
        try {
            MockHttpServletRequestBuilder req = request(method, path).accept(MediaType.APPLICATION_JSON);
            if (s != null) req.header("Authorization", "Bearer " + s.token());
            if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
            MvcResult result = mvc.perform(req).andReturn();
            String content = result.getResponse().getContentAsString();
            JsonNode node = content.isBlank() ? json.nullNode() : parse(content);
            return new Response(result.getResponse().getStatus(), node);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private JsonNode parse(String content) {
        try {
            return json.readTree(content);
        } catch (Exception e) {
            return json.getNodeFactory().textNode(content);
        }
    }
}
