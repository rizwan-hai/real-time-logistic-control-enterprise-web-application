package com.example.logistics.administration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {
    private final AdministrationService administration;
    private final Clock clock;

    public ApiKeyAuthenticationFilter(AdministrationService administration, Clock clock) {
        this.administration = administration;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String apiKey = request.getHeader("X-API-Key");
        if (apiKey == null || apiKey.isBlank()) {
            chain.doFilter(request, response);
            return;
        }
        if (request.getHeader("Authorization") != null) {
            reject(response);
            return;
        }
        var identity = administration.authenticateApiKey(apiKey, null, request.getRemoteAddr());
        if (identity == null) {
            reject(response);
            return;
        }
        var authorities = new ArrayList<SimpleGrantedAuthority>();
        authorities.add(new SimpleGrantedAuthority("ROLE_API_CLIENT"));
        identity.scopes().forEach(scope -> authorities.add(new SimpleGrantedAuthority(scope)));
        var claims = Jwt.withTokenValue("api-key")
                .header("alg", "none")
                .subject(identity.id().toString())
                .issuedAt(clock.instant())
                .expiresAt(identity.expiresAt() == null ? clock.instant().plusSeconds(300) : identity.expiresAt())
                .claim("purpose", "access")
                .claim("organizationId", identity.organizationId().toString())
                .claim("authorities", identity.scopes())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(claims, authorities, identity.id().toString()));
        chain.doFilter(request, response);
    }

    private static void reject(HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/problem+json");
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401,\"code\":\"API_KEY_INVALID\",\"detail\":\"API key is invalid or expired\"}");
    }
}
