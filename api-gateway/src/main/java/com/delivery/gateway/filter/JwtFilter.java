package com.delivery.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.Key;

@Component
public class JwtFilter extends AbstractGatewayFilterFactory<JwtFilter.Config> {
    
    @Value("${jwt.secret:your-secret-key-minimum-32-characters}")
    private String jwtSecret;
    
    public JwtFilter() {
        super(Config.class);
    }
    
    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String path = exchange.getRequest().getPath().getValue();
            
            // Skip auth for /api/v1/auth/**
            if (path.contains("/api/v1/auth/")) {
                return chain.filter(exchange);
            }
            
            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return unauthorized(exchange);
            }
            
            String token = authHeader.substring(7);
            
            try {
                Key key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
                Claims claims = Jwts.parserBuilder()
                        .setSigningKey(key)
                        .build()
                        .parseClaimsJws(token)
                        .getBody();
                
                String userId = claims.getSubject();
                String email = claims.get("email", String.class);
                String rol = claims.get("rol", String.class);
                
                // Add user info to headers
                ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                        .header("X-User-Id", userId)
                        .header("X-User-Email", email)
                        .header("X-User-Rol", rol)
                        .build();
                
                return chain.filter(exchange.mutate().request(modifiedRequest).build());
                
            } catch (Exception e) {
                return unauthorized(exchange);
            }
        };
    }
    
    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        
        String body = "{\"error\":\"Unauthorized\"}";
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
    
    public static class Config {}
}
