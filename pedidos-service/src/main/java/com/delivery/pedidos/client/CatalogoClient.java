package com.delivery.pedidos.client;

import com.delivery.common.dto.StockReservaRequest;
import com.delivery.common.dto.StockReservaResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "catalogo-service", url = "${catalogo-service.url}")
public interface CatalogoClient {
    
    @PostMapping("/internal/stock/reservar")
    StockReservaResponse reservarStock(@RequestHeader("X-Internal-API-Key") String apiKey,
                                       @RequestBody StockReservaRequest request);
    
    @PostMapping("/internal/stock/liberar")
    void liberarStock(@RequestHeader("X-Internal-API-Key") String apiKey,
                      @RequestParam String reservaId);
    
    @PostMapping("/internal/stock/confirmar")
    void confirmarReserva(@RequestHeader("X-Internal-API-Key") String apiKey,
                          @RequestParam String reservaId);
}
