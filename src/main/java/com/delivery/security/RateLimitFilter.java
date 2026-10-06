package com.delivery.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@Order(1)
public class RateLimitFilter implements Filter {
    
    private static final Logger logger = LoggerFactory.getLogger(RateLimitFilter.class);
    
    @Value("${app.rate-limit.login.max-attempts}")
    private int loginMaxAttempts;
    
    @Value("${app.rate-limit.login.window-seconds}")
    private int loginWindowSeconds;
    
    @Value("${app.rate-limit.general.max-requests}")
    private int generalMaxRequests;
    
    @Value("${app.rate-limit.general.window-seconds}")
    private int generalWindowSeconds;
    
    private final Map<String, Bucket> loginBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> generalBuckets = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    public RateLimitFilter() {
        scheduler.scheduleAtFixedRate(this::cleanupExpiredBuckets, 1, 1, TimeUnit.MINUTES);
    }
    
    private void cleanupExpiredBuckets() {
        long now = System.currentTimeMillis();
        loginBuckets.entrySet().removeIf(entry -> entry.getValue().getAvailableTokens() >= loginMaxAttempts);
        generalBuckets.entrySet().removeIf(entry -> entry.getValue().getAvailableTokens() >= generalMaxRequests);
    }
    
    private Bucket createLoginBucket() {
        Bandwidth limit = Bandwidth.classic(loginMaxAttempts, 
            Refill.greedy(loginMaxAttempts, Duration.ofSeconds(loginWindowSeconds)));
        return Bucket4j.builder().addLimit(limit).build();
    }
    
    private Bucket createGeneralBucket() {
        Bandwidth limit = Bandwidth.classic(generalMaxRequests, 
            Refill.greedy(generalMaxRequests, Duration.ofSeconds(generalWindowSeconds)));
        return Bucket4j.builder().addLimit(limit).build();
    }
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        String path = httpRequest.getRequestURI();
        String identifier = getClientIdentifier(httpRequest);
        
        Bucket bucket;
        if (path.contains("/login")) {
            bucket = loginBuckets.computeIfAbsent(identifier, k -> createLoginBucket());
        } else {
            bucket = generalBuckets.computeIfAbsent(identifier, k -> createGeneralBucket());
        }
        
        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            logger.warn("Rate limit excedido para {} en {}", identifier, path);
            httpResponse.setStatus(429);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write("{\"error\":\"Demasiadas solicitudes. Intente más tarde.\"}");
            httpResponse.setHeader("Retry-After", "60");
        }
    }
    
    private String getClientIdentifier(HttpServletRequest request) {
        String email = request.getParameter("email");
        if (email != null && !email.isEmpty()) {
            return "email:" + email;
        }
        return request.getRemoteAddr();
    }
}
