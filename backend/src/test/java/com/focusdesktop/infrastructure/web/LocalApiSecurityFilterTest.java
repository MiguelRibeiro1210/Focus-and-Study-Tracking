package com.focusdesktop.infrastructure.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.focusdesktop.infrastructure.config.ApiTokenConfiguration.ApiToken;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;

class LocalApiSecurityFilterTest {
    private final LocalApiSecurityFilter filter = new LocalApiSecurityFilter(new ApiToken("test-token"),
            objectMapper());

    @Test
    void websocketHandshakeRequiresBearerToken() throws Exception {
        MockHttpServletRequest request = websocketRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertEquals("UNAUTHORIZED", objectMapper().readTree(response.getContentAsString())
                .path("code").asText());
    }

    @Test
    void websocketHandshakeRejectsNonLocalOriginAndAcceptsAuthorizedLocalOrigin() throws Exception {
        MockHttpServletRequest rejected = websocketRequest();
        rejected.addHeader("Authorization", "Bearer test-token");
        rejected.addHeader("Origin", "https://example.com");
        MockHttpServletResponse rejectedResponse = new MockHttpServletResponse();
        filter.doFilter(rejected, rejectedResponse, new MockFilterChain());
        assertEquals(403, rejectedResponse.getStatus());

        MockHttpServletRequest accepted = websocketRequest();
        accepted.addHeader("Authorization", "Bearer test-token");
        accepted.addHeader("Origin", "http://localhost:5173");
        MockHttpServletResponse acceptedResponse = new MockHttpServletResponse();
        MockFilterChain acceptedChain = new MockFilterChain();
        filter.doFilter(accepted, acceptedResponse, acceptedChain);
        assertNotNull(acceptedChain.getRequest());
    }

    private static MockHttpServletRequest websocketRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws");
        request.setRequestURI("/ws");
        return request;
    }

    private static ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    }
}
