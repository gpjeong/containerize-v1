package com.containerize.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * IP-based Token Bucket rate limiter.
 * Applies different limits for general API calls vs heavy operations (upload/generate).
 */
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitInterceptor.class);

    private final int generalRequestsPerMinute;
    private final int heavyRequestsPerMinute;
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String[] HEAVY_PATH_PREFIXES = {"/api/upload", "/api/generate"};

    public RateLimitInterceptor(int generalRequestsPerMinute, int heavyRequestsPerMinute) {
        this.generalRequestsPerMinute = generalRequestsPerMinute;
        this.heavyRequestsPerMinute = heavyRequestsPerMinute;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String clientIp = getClientIp(request);
        String path = request.getRequestURI();
        boolean isHeavy = isHeavyPath(path);

        int limit = isHeavy ? heavyRequestsPerMinute : generalRequestsPerMinute;
        String bucketKey = clientIp + (isHeavy ? ":heavy" : ":general");

        TokenBucket bucket = buckets.computeIfAbsent(bucketKey, k -> new TokenBucket(limit));

        if (!bucket.tryConsume()) {
            logger.warn("Rate limit exceeded for IP: {} on path: {}", clientIp, path);

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", "60");

            Map<String, String> body = Map.of("detail", "Rate limit exceeded. Please try again later.");
            response.getWriter().write(objectMapper.writeValueAsString(body));
            return false;
        }

        return true;
    }

    private boolean isHeavyPath(String path) {
        for (String prefix : HEAVY_PATH_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Simple Token Bucket implementation with per-minute refill.
     */
    static class TokenBucket {
        private final int maxTokens;
        private final AtomicLong tokens;
        private volatile long lastRefillTime;

        TokenBucket(int maxTokens) {
            this.maxTokens = maxTokens;
            this.tokens = new AtomicLong(maxTokens);
            this.lastRefillTime = System.currentTimeMillis();
        }

        synchronized boolean tryConsume() {
            refill();
            long current = tokens.get();
            if (current > 0) {
                tokens.decrementAndGet();
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRefillTime;
            if (elapsed >= 60_000) {
                tokens.set(maxTokens);
                lastRefillTime = now;
            }
        }
    }
}
