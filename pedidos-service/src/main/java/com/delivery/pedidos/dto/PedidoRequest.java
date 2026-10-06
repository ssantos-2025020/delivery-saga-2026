package com.delivery.pedidos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PedidoRequest {
    
    @NotEmpty(message = "El pedido debe tener al menos un item")
    @Size(max = 50, message = "El pedido no puede tener más de 50 items")
    @Valid
    private List<ItemPedidoRequest> items;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ItemPedidoRequest {
        private Long productoId;
        private Integer cantidad;
    }
}
