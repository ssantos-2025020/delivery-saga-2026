# Guía de Defensa Técnica - Microservices

## 10 Preguntas Más Probables de un Evaluador

### 1. ¿Por qué Saga y no transacción distribuida?

**Respuesta corta:** Las transacciones distribuidas (2PC) requieren coordinación compleja entre bases de datos, tienen alto overhead de latencia, y reducen drásticamente el rendimiento. Saga pattern implementa consistencia eventual con compensación: cada servicio tiene su propia transacción local ACID, y si algo falla, se ejecutan acciones de compensación para deshacer cambios. Es más rápido, más escalable, y adecuado para microservicios donde los servicios son independientes.

### 2. ¿Qué pasa si cae catalogo-service a mitad del pedido?

**Respuesta corta:** Si catalogo-service no responde cuando pedidos-service intenta reservar stock, el Circuit Breaker de Resilience4j se abre después de N fallos consecutivos. La petición recibe 503 Service Unavailable inmediatamente (no timeout infinito). Como la reserva falló, no se crea ningún pedido. El cliente puede reintentar. Jamás hay un pedido a medias o stock inconsistente.

### 3. ¿Por qué una BD por servicio y no una sola BD?

**Respuesta corta:** Una BD por servicio garantiza independencia real. Cada servicio puede escalar su BD independently según su carga. No hay FKs entre servicios, lo que elimina acoplamiento de datos. Si catalogo-service necesita hacer un schema change, no afecta a pedidos-service. Además, en producción cada servicio podría tener su propia instancia de BD en diferentes regiones para disponibilidad geográfica.

### 4. ¿Cómo evitas reservas huérfanas (stock reservado pero pedido nunca creado)?

**Respuesta corta:** Dos mecanismos: (1) Si pedidos-service falla al guardar el pedido, automáticamente llama a `/internal/stock/liberar` como compensación. (2) Un job @Scheduled se ejecuta cada 5 minutos, busca reservas en estado RESERVADA con fecha > 5 minutos, y las libera. Esto asegura que cualquier reserva huérfana se limpie eventualmente, devolviendo el stock al inventario.

### 5. ¿Por qué bloqueo pesimista y no solo optimista para el stock?

**Respuesta corta:** El bloqueo optimista (@Version) no es suficiente bajo alta concurrencia. 200 clientes pueden leer stock=10 simultáneamente, todos validar que está disponible, y al actualizar uno gana y el resto reintenta. Con bloqueo pesimista (@Lock(PESSIMISTIC_WRITE)), la BD bloquea la fila inmediatamente, garantizando que solo una transacción a la vez modifique el stock. Es más lento pero absolutamente correcto para inventario. Usamos ambos: pesimista para operaciones críticas, optimista como capa extra de defensa.

### 6. ¿Cómo evitas deadlocks con bloqueo pesimista entre servicios?

**Respuesta corta:** Adquirimos todos los bloqueos en un orden consistente: `WHERE id IN (...) ORDER BY id ASC`. Si el pedido A compra productos 1,2,3 y el pedido B compra 3,2,1, ambos los bloquearán en orden 1,2,3. Esto evita el deadlock clásico donde A espera 3 y B espera 1. Además, configuramos un lock timeout de 5 segundos para que una petición no espere infinitamente.

### 7. ¿Qué pasa si dos usuarios cancelan el mismo pedido a la vez?

**Respuesta corta:** La primera cancelación obtiene el bloqueo pesimista (usamos @Version), cambia el estado a CANCELADO, y llama a `/internal/stock/liberar`. La segunda cancelación intenta obtener el bloqueo, ve que el estado ya es CANCELADO (no PENDIENTE), y lanza InvalidStatusException. El stock se restaura solo una vez porque la validación del estado evita la doble restauración. La segunda petición recibe 409 Conflict.

### 8. ¿Por qué el gateway valida JWT y cada servicio también?

**Respuesta corta:** El gateway es la primera línea de defensa, pero no debe ser la única. Si un atacante logra bypassar el gateway (por ejemplo, accediendo directamente a pedidos-service), cada servicio debe validar JWT por su cuenta. Además, esto permite que los servicios se comuniquen directamente entre sí en caso de falla del gateway. Cada servicio aplica sus propias reglas de rol basadas en el JWT.

### 9. ¿Cómo garantizas que el stock nunca quede negativo con Saga?

**Respuesta corta:** Tres capas de defensa: (1) catalogo-service usa bloqueo pesimista con `@Lock(PESSIMISTIC_WRITE)` para serializar accesos al stock. (2) Validación en código Java antes de restar: `if stock < cantidad throw exception`. (3) Restricción CHECK (stock >= 0) en PostgreSQL como último resguardo. Si todo lo demás falla, la BD rechazará el UPDATE. Las pruebas de concurrencia confirman que el stock nunca baja de 0.

### 10. ¿Cuál es el trade-off de microservicios vs monolito en este caso?

**Respuesta corta:** Microservicios añaden complejidad operacional (5 servicios, 3 BDs, orquestación) pero permiten escalar cada componente independently. El monolito es más simple (1 servicio, 1 BD, transacciones ACID reales) y tiene menor latencia (sin overhead de HTTP entre servicios). Para el alcance actual (~50-100 tps), el monolito ofrece mejor balance de simplicidad y rendimiento. Para 10k+ tps, microservicios con Saga serían necesarios. En esta implementación, microservicios demuestra el patrón arquitectónico escalable.

---

## Preguntas Adicionales sobre la Implementación

### ¿Por qué Feign y no RestTemplate directamente?

Feign es más declarativo y simplifica la comunicación HTTP entre servicios. Genera automáticamente el código de cliente, maneja serialización/deserialización, y se integra con Resilience4j y Circuit Breaker. Con RestTemplate tendríamos que escribir ese código manualmente, siendo más propenso a errores.

### ¿Por qué Resilience4j y no Hystrix?

Hystrix está en modo mantenimiento desde 2018. Resilience4j es el reemplazo moderno, está activamente mantenido, y ofrece mejor integración con Spring Boot 3.x. Soporta Circuit Breaker, Retry, Rate Limiter, Bulkhead, y Time Limiter con configuración simple.

### ¿Cómo se maneja la consistencia eventual en los datos copiados?

En pedidos-service, copiamos el nombre y precio del producto en DetallePedido. Si el precio cambia en catalogo-service después de crear el pedido, el pedido mantiene el precio original (que es correcto para facturación). Si se necesita sincronización eventual, podríamos usar un evento de dominio (Kafka) para notificar cambios, pero para este alcance no es necesario.

### ¿Por qué no usas Eureka o Config Server?

Para mantener la implementación simple y demostrativa. Eureka y Config Server añaden complejidad operacional significante. Para producción, sí se recomendarían, pero para demostrar el patrón Saga con microservicios, la comunicación por nombre de servicio en Docker Compose es suficiente y más fácil de entender.

### ¿Cómo manejas despliegues sin downtime?

Usamos graceful shutdown configurado en Spring Boot. Al recibir SIGTERM, los servicios dejan de aceptar nuevas peticiones pero esperan hasta 30s para que las peticiones en curso terminen. Docker Compose tiene health checks que esperan a que los servicios estén listos antes de enrutar tráfico. Además, podemos hacer rolling updates actualizando un servicio a la vez.

### ¿Qué pasa si falla la reconciliación de reservas?

La reconciliación corre en pedidos-service. Si pedidos-service está down, las reservas huérfanas no se liberan automáticamente. Sin embargo, tienen una fecha de expiración de 5 minutos. Podríamos implementar un mecanismo en catalogo-service que también limpie reservas vencidas, como respaldo. En producción, esto debería ser monitoreado con alertas.

### ¿Cómo se maneja la seguridad de comunicación entre servicios?

Usamos una API key compartida (`INTERNAL_API_KEY`) configurada como variable de entorno. Los endpoints internos `/internal/*` requieren este header. En producción, esto debería rotarse regularmente y posiblemente usar mTLS entre servicios para mayor seguridad.

### ¿Por qué el costo de delivery es fijo (Q20.00)?

Es un ejemplo simple de lógica de negocio. En producción, esto podría calcularse dinámicamente basado en distancia, tiempo, o ser configurable por comercio. Lo importante es que se calcula en pedidos-service, no en catalogo-service, manteniendo la separación de responsabilidades.

### ¿Cómo se puede extender a más tipos de usuarios (admin, superadmin)?

El enum Rol ya incluye ADMIN. En SecurityConfig, podemos agregar reglas específicas como `.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")`. La lógica de autorización basada en roles está separada en cada servicio, permitiendo extensión sin modificar la arquitectura base.
