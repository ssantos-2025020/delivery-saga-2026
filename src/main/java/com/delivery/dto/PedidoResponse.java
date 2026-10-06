package com.delivery.dto;

import com.delivery.model.EstadoPedido;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PedidoResponse {
    private Long id;
    private Long clienteId;
    private String clienteNombre;
    private Long repartidorId;
    private String repartidorNombre;
    private EstadoPedido estado;
    private LocalDateTime fechaPedido;
    private BigDecimal total;
    private List<ItemPedidoResponse> detalles;
}
