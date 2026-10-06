package com.delivery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComercioResponse {
    private Long id;
    private String nombre;
    private String categoria;
    private Boolean abierto;
    private List<ProductoResponse> productos;
}
