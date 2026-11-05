package com.example.logistics.identity.config;

import com.example.logistics.identity.infrastructure.DeviceSessionRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Clock;
import java.util.UUID;

/** Enforces logout and remote revocation against the persisted device session for bearer requests. */
public class DeviceSessionValidationFilter extends OncePerRequestFilter {
    private final DeviceSessionRepository sessions;
    private final Clock clock;

    public DeviceSessionValidationFilter(DeviceSessionRepository sessions, Clock clock) {
        this.sessions = sessions; this.clock = clock;
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (request.getHeader("Authorization") != null && authentication instanceof JwtAuthenticationToken jwtAuth) {
            UUID familyId;
            try { familyId = UUID.fromString(jwtAuth.getToken().getClaimAsString("familyId")); }
            catch (RuntimeException ex) { reject(response); return; }
            var session = sessions.findByFamilyId(familyId).orElse(null);
            if (session == null || session.getRevokedAt() != null || !session.getExpiresAt().isAfter(clock.instant())) {
                SecurityContextHolder.clearContext(); reject(response); return;
            }
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/problem+json");
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401,\"code\":\"SESSION_REVOKED\",\"detail\":\"Authentication session is unavailable\"}");
    }
}
