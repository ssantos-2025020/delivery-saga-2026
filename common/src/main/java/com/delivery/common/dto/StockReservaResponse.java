package com.delivery.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockReservaResponse {
    private boolean exito;
    private String mensaje;
    private List<ProductoReservado> productos;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProductoReservado {
        private Long productoId;
        private String nombre;
        private BigDecimal precio;
        private Integer cantidad;
    }
}
