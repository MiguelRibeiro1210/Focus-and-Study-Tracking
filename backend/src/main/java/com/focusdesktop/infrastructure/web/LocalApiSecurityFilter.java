package com.focusdesktop.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.focusdesktop.infrastructure.config.ApiTokenConfiguration.ApiToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class LocalApiSecurityFilter extends OncePerRequestFilter {
    private final byte[] expectedToken;
    private final ObjectMapper objectMapper;

    public LocalApiSecurityFilter(ApiToken token, ObjectMapper objectMapper) {
        this.expectedToken = token.value().getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        if (origin != null && !isLocalOrigin(origin)) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN_ORIGIN",
                    "Requests from this origin are not allowed");
            return;
        }

        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || isHealth(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authorization = request.getHeader("Authorization");
        String suppliedToken = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring("Bearer ".length()) : "";
        if (!MessageDigest.isEqual(expectedToken, suppliedToken.getBytes(StandardCharsets.UTF_8))) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED",
                    "A valid bearer token is required");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static boolean isHealth(HttpServletRequest request) {
        String requestPath = request.getRequestURI().substring(request.getContextPath().length());
        return "/health".equals(requestPath);
    }

    private static boolean isLocalOrigin(String value) {
        if ("null".equalsIgnoreCase(value)) {
            return false;
        }
        try {
            URI origin = URI.create(value);
            String scheme = origin.getScheme();
            String host = origin.getHost();
            if (scheme == null || host == null || origin.getUserInfo() != null) {
                return false;
            }
            String normalizedHost = host.toLowerCase(java.util.Locale.ROOT);
            boolean localHost = normalizedHost.equals("localhost")
                    || normalizedHost.endsWith(".localhost")
                    || normalizedHost.equals("127.0.0.1")
                    || normalizedHost.equals("::1")
                    || normalizedHost.equals("[::1]");
            return localHost && (scheme.equals("http") || scheme.equals("https") || scheme.equals("tauri"));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private void writeError(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(),
                new ApiErrorResponse(code, message, java.util.List.of(), Instant.now()));
    }
}
