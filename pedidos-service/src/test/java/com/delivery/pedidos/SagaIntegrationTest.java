package com.delivery.pedidos;

import com.delivery.pedidos.service.PedidosService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SagaIntegrationTest {
    
    @Autowired
    private PedidosService pedidosService;
    
    @Test
    void contextLoads() {
        // Test básico de que el contexto carga correctamente
        // Los tests completos de Saga requieren todos los servicios levantados
    }
}
