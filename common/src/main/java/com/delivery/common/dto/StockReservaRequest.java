package com.delivery.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockReservaRequest {
    private String reservaId;
    private List<StockItemRequest> items;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StockItemRequest {
        private Long productoId;
        private Integer cantidad;
    }
}
