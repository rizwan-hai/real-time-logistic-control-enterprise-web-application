package com.example.logistics.shared.web;

import com.example.logistics.shared.config.RateLimitProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

public class RedisRateLimitFilter extends OncePerRequestFilter {
    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>(
            "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],ARGV[1]); end; return n", Long.class);
    private final StringRedisTemplate redis;
    private final RateLimitProperties properties;

    public RedisRateLimitFilter(StringRedisTemplate redis, RateLimitProperties properties) {
        this.redis = redis; this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.enabled() || !request.getRequestURI().startsWith("/api/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String remote = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        long window = Math.floorDiv(System.currentTimeMillis() / 1000, Math.max(1, properties.windowSeconds()));
        String key = "rate:v1:" + remote + ":" + window;
        try {
            Long count = redis.execute(SCRIPT, List.of(key), String.valueOf(properties.windowSeconds()));
            if (count != null && count > properties.requests()) {
                response.setStatus(429);
                response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                response.setHeader("Retry-After", String.valueOf(properties.windowSeconds()));
                response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"RATE_LIMITED\",\"status\":429,\"code\":\"RATE_LIMITED\"}");
                return;
            }
        } catch (RuntimeException ex) {
            response.setStatus(503);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"DEPENDENCY_UNAVAILABLE\",\"status\":503,\"code\":\"DEPENDENCY_UNAVAILABLE\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
