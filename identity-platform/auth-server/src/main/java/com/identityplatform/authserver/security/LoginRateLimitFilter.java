package com.identityplatform.authserver.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Servlet-level rate limiter for login/token endpoints.
 *
 * Runs before Spring Security (registered at HIGHEST_PRECEDENCE+10 via FilterRegistrationBean).
 * Tracks POST attempts per client IP in Redis with a 60-second sliding window.
 * Fails open if Redis is unavailable so auth is never blocked by infra issues.
 *
 * Limits:
 *   POST /login            — 10 attempts / 60 s
 *   POST /oauth2/token     — 10 attempts / 60 s
 *   POST /api/auth/token   — 10 attempts / 60 s
 */
@Slf4j
@RequiredArgsConstructor
public class LoginRateLimitFilter extends OncePerRequestFilter {

    static final int  MAX_ATTEMPTS   = 10;
    static final long WINDOW_SECONDS = 60;
    private static final String KEY_PREFIX = "rl:login:";

    private final StringRedisTemplate redisTemplate;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) return true;
        String path = request.getServletPath();
        return !"/login".equals(path)
                && !"/oauth2/token".equals(path)
                && !"/api/auth/token".equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String ip  = resolveClientIp(request);
        String key = KEY_PREFIX + ip;

        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (Long.valueOf(1).equals(count)) {
                redisTemplate.expire(key, WINDOW_SECONDS, TimeUnit.SECONDS);
            }
            if (count != null && count > MAX_ATTEMPTS) {
                log.warn("[RateLimit] {} blocked — {} attempts on {} in {}s window",
                        ip, count, request.getServletPath(), WINDOW_SECONDS);
                response.setStatus(429);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(
                        "{\"error\":\"too_many_requests\","
                        + "\"error_description\":\"Too many login attempts. Try again in 1 minute.\"}");
                return;
            }
        } catch (Exception e) {
            // Fail open — never block auth because Redis is down
            log.warn("[RateLimit] Redis unavailable, bypassing rate limit: {}", e.getMessage());
        }

        chain.doFilter(request, response);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].strip();
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) return realIp.strip();
        return request.getRemoteAddr();
    }
}
