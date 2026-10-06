package com.delivery.gateway.filter;

import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class CorrelationIdFilter extends AbstractGatewayFilterFactory<CorrelationIdFilter.Config> implements Ordered {
    
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    
    public CorrelationIdFilter() {
        super(Config.class);
    }
    
    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String correlationId = exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER);
            
            if (correlationId == null || correlationId.isEmpty()) {
                correlationId = UUID.randomUUID().toString();
            }
            
            MDC.put("correlationId", correlationId);
            
            ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                    .header(CORRELATION_ID_HEADER, correlationId)
                    .build();
            
            try {
                return chain.filter(exchange.mutate().request(modifiedRequest).build());
            } finally {
                MDC.remove("correlationId");
            }
        };
    }
    
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
    
    public static class Config {}
}
