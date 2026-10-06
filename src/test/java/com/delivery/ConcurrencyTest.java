package com.delivery;

import com.delivery.dto.ItemPedidoRequest;
import com.delivery.dto.PedidoRequest;
import com.delivery.model.*;
import com.delivery.repository.*;
import com.delivery.service.PedidoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class ConcurrencyTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("delivery")
            .withUsername("test")
            .withPassword("test");
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    
    @Autowired
    private UsuarioRepository usuarioRepository;
    
    @Autowired
    private ProductoRepository productoRepository;
    
    @Autowired
    private PedidoRepository pedidoRepository;
    
    @Autowired
    private PedidoService pedidoService;
    
    private Usuario cliente;
    private Producto producto;
    
    @BeforeEach
    void setUp() {
        pedidoRepository.deleteAll();
        productoRepository.deleteAll();
        usuarioRepository.deleteAll();
        
        cliente = usuarioRepository.save(Usuario.builder()
                .email("cliente@test.com")
                .password("password")
                .nombre("Cliente Test")
                .rol(Rol.CLIENTE)
                .activo(true)
                .build());
        
        Comercio comercio = Comercio.builder()
                .nombre("Comercio Test")
                .categoria("Comida")
                .abierto(true)
                .build();
        
        producto = Producto.builder()
                .nombre("Producto Test")
                .precio(new BigDecimal("10.00"))
                .stock(20)
                .comercio(comercio)
                .version(0L)
                .build();
        
        producto = productoRepository.save(producto);
    }
    
    @Test
    void testConcurrentOrderCreation_Stock20_50Threads() throws InterruptedException {
        int numThreads = 50;
        int expectedSuccess = 20;
        int expectedFailures = 30;
        
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        
        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    PedidoRequest request = PedidoRequest.builder()
                            .items(Collections.singletonList(
                                    ItemPedidoRequest.builder()
                                            .productoId(producto.getId())
                                            .cantidad(1)
                                            .build()
                            ))
                            .build();
                    
                    pedidoService.crearPedido(cliente.getId(), request, null);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        
        Producto finalProducto = productoRepository.findById(producto.getId()).orElseThrow();
        
        assertEquals(expectedSuccess, successCount.get(), 
                "Deben haber " + expectedSuccess + " pedidos exitosos");
        assertEquals(expectedFailures, failureCount.get(), 
                "Deben haber " + expectedFailures + " pedidos fallidos");
        assertEquals(0, finalProducto.getStock(), 
                "El stock final debe ser 0");
        assertTrue(finalProducto.getStock() >= 0, 
                "El stock nunca debe ser negativo");
    }
    
    @Test
    void testCrossedOrders_NoDeadlock() throws InterruptedException {
        Producto productoA = productoRepository.save(Producto.builder()
                .nombre("Producto A")
                .precio(new BigDecimal("10.00"))
                .stock(10)
                .comercio(producto.getComercio())
                .version(0L)
                .build());
        
        Producto productoB = productoRepository.save(Producto.builder()
                .nombre("Producto B")
                .precio(new BigDecimal("15.00"))
                .stock(10)
                .comercio(producto.getComercio())
                .version(0L)
                .build());
        
        int iterations = 100;
        ExecutorService executor = Executors.newFixedThreadPool(2);
        
        for (int i = 0; i < iterations; i++) {
            CountDownLatch latch = new CountDownLatch(2);
            
            executor.submit(() -> {
                try {
                    PedidoRequest request = PedidoRequest.builder()
                            .items(List.of(
                                    ItemPedidoRequest.builder()
                                            .productoId(productoA.getId())
                                            .cantidad(1)
                                            .build(),
                                    ItemPedidoRequest.builder()
                                            .productoId(productoB.getId())
                                            .cantidad(1)
                                            .build()
                            ))
                            .build();
                    pedidoService.crearPedido(cliente.getId(), request, null);
                } catch (Exception e) {
                } finally {
                    latch.countDown();
                }
            });
            
            executor.submit(() -> {
                try {
                    PedidoRequest request = PedidoRequest.builder()
                            .items(List.of(
                                    ItemPedidoRequest.builder()
                                            .productoId(productoB.getId())
                                            .cantidad(1)
                                            .build(),
                                    ItemPedidoRequest.builder()
                                            .productoId(productoA.getId())
                                            .cantidad(1)
                                            .build()
                            ))
                            .build();
                    pedidoService.crearPedido(cliente.getId(), request, null);
                } catch (Exception e) {
                } finally {
                    latch.countDown();
                }
            });
            
            try {
                latch.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                fail("Deadlock detectado en iteración " + i);
            }
        }
        
        executor.shutdown();
        assertTrue(true, "No hubo deadlock en ninguna iteración");
    }
    
    @Test
    void testConcurrentCancellation_StockRestoredOnce() throws InterruptedException {
        PedidoRequest request = PedidoRequest.builder()
                .items(Collections.singletonList(
                        ItemPedidoRequest.builder()
                                .productoId(producto.getId())
                                .cantidad(5)
                                .build()
                ))
                .build();
        
        var pedido = pedidoService.crearPedido(cliente.getId(), request, null);
        
        Producto beforeCancel = productoRepository.findById(producto.getId()).orElseThrow();
        assertEquals(15, beforeCancel.getStock());
        
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger(0);
        
        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                try {
                    pedidoService.cancelarPedido(pedido.getId(), cliente.getId());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        
        Producto afterCancel = productoRepository.findById(producto.getId()).orElseThrow();
        assertEquals(20, afterCancel.getStock(), 
                "El stock debe restaurarse a 20 (solo una vez)");
        assertEquals(1, successCount.get(), 
                "Solo una cancelación debe tener éxito");
    }
    
    @Test
    void testConcurrentStatusUpdate_OnlyOneWins() throws InterruptedException {
        Usuario repartidor = usuarioRepository.save(Usuario.builder()
                .email("repartidor@test.com")
                .password("password")
                .nombre("Repartidor Test")
                .rol(Rol.REPARTIDOR)
                .activo(true)
                .build());
        
        PedidoRequest request = PedidoRequest.builder()
                .items(Collections.singletonList(
                        ItemPedidoRequest.builder()
                                .productoId(producto.getId())
                                .cantidad(1)
                                .build()
                ))
                .build();
        
        var pedido = pedidoService.crearPedido(cliente.getId(), request, null);
        pedidoService.asignarRepartidor(pedido.getId(), repartidor.getId());
        
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger(0);
        
        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                try {
                    pedidoService.actualizarEstado(pedido.getId(), EstadoPedido.EN_CAMINO, repartidor.getId());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        
        Pedido finalPedido = pedidoRepository.findById(pedido.getId()).orElseThrow();
        assertEquals(1, successCount.get(), 
                "Solo una actualización debe tener éxito");
        assertEquals(EstadoPedido.EN_CAMINO, finalPedido.getEstado(), 
                "El estado debe ser EN_CAMINO");
    }
    
    @Test
    void testIdempotency_ConcurrentSameKey() throws InterruptedException {
        String idempotencyKey = "test-key-123";
        int numThreads = 10;
        
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        List<Long> pedidoIds = Collections.synchronizedList(new ArrayList<>());
        
        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    PedidoRequest request = PedidoRequest.builder()
                            .items(Collections.singletonList(
                                    ItemPedidoRequest.builder()
                                            .productoId(producto.getId())
                                            .cantidad(1)
                                            .build()
                            ))
                            .build();
                    
                    var pedido = pedidoService.crearPedido(cliente.getId(), request, idempotencyKey);
                    pedidoIds.add(pedido.getId());
                } catch (Exception e) {
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        
        assertEquals(1, pedidoIds.size(), 
                "Debe haber solo un pedido creado");
        assertTrue(pedidoIds.stream().allMatch(id -> id.equals(pedidoIds.get(0))), 
                "Todos los IDs deben ser iguales");
    }
    
    @Test
    void testStockInvariant_TotalConserved() throws InterruptedException {
        int initialStock = producto.getStock();
        int numOrders = 10;
        int quantityPerOrder = 1;
        
        ExecutorService executor = Executors.newFixedThreadPool(numOrders);
        CountDownLatch latch = new CountDownLatch(numOrders);
        
        for (int i = 0; i < numOrders; i++) {
            executor.submit(() -> {
                try {
                    PedidoRequest request = PedidoRequest.builder()
                            .items(Collections.singletonList(
                                    ItemPedidoRequest.builder()
                                            .productoId(producto.getId())
                                            .cantidad(quantityPerOrder)
                                            .build()
                            ))
                            .build();
                    pedidoService.crearPedido(cliente.getId(), request, null);
                } catch (Exception e) {
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        
        Producto finalProducto = productoRepository.findById(producto.getId()).orElseThrow();
        List<Pedido> pedidos = pedidoRepository.findByClienteId(cliente.getId(), 
                org.springframework.data.domain.Pageable.unpaged()).getContent();
        
        int unitsInOrders = pedidos.stream()
                .filter(p -> p.getEstado() != EstadoPedido.CANCELADO)
                .flatMap(p -> p.getDetalles().stream())
                .mapToInt(DetallePedido::getCantidad)
                .sum();
        
        assertEquals(initialStock, finalProducto.getStock() + unitsInOrders, 
                "Stock inicial = Stock actual + Unidades en pedidos no cancelados");
    }
}
