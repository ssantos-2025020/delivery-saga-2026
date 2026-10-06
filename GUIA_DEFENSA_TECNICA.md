# Guía de Defensa Técnica

## 10 Preguntas Más Probables de un Evaluador

### 1. ¿Por qué bloqueo pesimista y no solo optimista para el stock?

**Respuesta corta:** El bloqueo optimista no es suficiente para evitar que el stock quede negativo bajo alta concurrencia. Con optimista (@Version), dos hilos pueden leer stock=10 simultáneamente, ambos validar que está disponible, y al actualizar uno gana y el otro reintenta. Pero si son 200 peticiones, muchos pueden pasar la validación antes de que se actualice la versión, causando sobreventa. Con bloqueo pesimista (@Lock(PESSIMISTIC_WRITE)), la base de datos bloquea la fila inmediatamente, garantizando que solo una transacción a la vez modifique el stock. Es más lento pero absolutamente correcto para inventario.

### 2. ¿Cómo evitas deadlocks con bloqueo pesimista?

**Respuesta corta:** Adquirimos todos los bloqueos en un orden consistente: usamos `WHERE id IN (...) ORDER BY id ASC` para obtener los productos ordenados por ID. Si el pedido A compra productos 1,2,3 y el pedido B compra 3,2,1, ambos los bloquearán en orden 1,2,3, evitando el deadlock clásico donde A espera 3 y B espera 1. Además, configuramos un lock timeout de 5 segundos para que una petición no espere infinitamente.

### 3. ¿Qué pasa si dos usuarios cancelan el mismo pedido a la vez?

**Respuesta corta:** La primera cancelación obtiene el bloqueo pesimista, restaura el stock, cambia el estado a CANCELADO y libera el bloqueo. La segunda cancelación intenta obtener el bloqueo, ve que el estado ya es CANCELADO (no PENDIENTE), y lanza InvalidStatusException con mensaje claro. El stock se restaura solo una vez porque la validación del estado evita la doble restauración. La segunda petición recibe 409 Conflict.

### 4. ¿Por qué idempotencia en la creación de pedidos?

**Respuesta corta:** Si un cliente hace doble clic en "Confirmar pedido" o hay un timeout de red, el cliente puede enviar la misma petición dos veces. Sin idempotencia, se crearían dos pedidos idénticos. Con el header "Idempotency-Key", la primera petición crea el pedido y guarda la clave en BD. Las peticiones siguientes con la misma clave recuperan el pedido ya creado en lugar de duplicarlo. Es crucial para UX correcta y para evitar doble cobro.

### 5. ¿Por qué BCrypt strength 10 y no más alto?

**Respuesta corta:** BCrypt strength 10 es un equilibrio entre seguridad y rendimiento. Cada incremento de strength duplica el tiempo de hash. Strength 10 toma ~100ms en hardware moderno, aceptable para login/registro. Strength 12 tomaría ~400ms, lo que degradaría el rendimiento bajo carga alta y haría login/registro vulnerables a ataques de DoS por CPU. Para production, strength 10 es recomendado por OWASP como mínimo seguro sin impactar severamente la UX.

### 6. ¿Cómo evitas N+1 queries?

**Respuesta corta:** Usamos @EntityGraph y JOIN FETCH en los repositorios. Por ejemplo, al listar pedidos usamos `@EntityGraph(attributePaths = {"cliente", "detalles.producto", "repartidor"})` que hace un JOIN para cargar todas las relaciones en una sola consulta. Sin esto, Hibernate haría una consulta por pedido y luego N consultas adicionales para cargar cada relación. También verificamos con logs de SQL que el número de consultas es constante independientemente del tamaño de página.

### 7. ¿Por qué no validar JWT contra la BD en cada petición?

**Respuesta corta:** Validar JWT contra la BD en cada petición añadiría latencia adicional y carga innecesaria a la base de datos. En su lugar, incluimos el email y rol dentro del token codificado. El trade-off es que si un usuario es borrado o desactivado, su token seguiría siendo válido hasta que expire (24 horas en nuestra configuración). Para mitigar esto, podríamos implementar una lista negra en Redis para tokens revocados, pero para este alcance el trade-off es aceptable: seguridad razonable con mejor rendimiento.

### 8. ¿Cómo dimensionaste el pool de conexiones HikariCP?

**Respuesta corta:** Configuré maximumPoolSize=20 basándome en la fórmula: (número de cores * efectivo) + (número de discos * esperado_iops). Para un servidor típico con 4-8 cores, 20 conexiones es razonable. Más conexiones no necesariamente mejoran rendimiento porque PostgreSQL tiene overhead por conexión. connectionTimeout=5s evita que peticiones esperen infinitamente cuando el pool está agotado (responde 503 Service Unavailable). leakDetectionThreshold=60s detecta conexiones no cerradas.

### 9. ¿Qué hace el apagado graceful (graceful shutdown)?

**Respuesta corta:** Graceful shutdown permite que la aplicación termine las peticiones en curso antes de detenerse. Cuando recibe SIGTERM, Tomcat deja de aceptar nuevas peticiones pero espera hasta 30s (configurado) para que las peticiones existentes terminen. Esto evita que transacciones de pedidos queden a medias, garantizando consistencia de datos. Sin esto, peticiones en proceso podrían fallar abruptamente al detener el servidor.

### 10. ¿Cómo garantizas que el stock nunca quede negativo?

**Respuesta corta:** Tres capas de defensa: (1) Bloqueo pesimista que serializa accesos al stock, (2) Validación en código Java antes de restar (if stock < cantidad throw exception), (3) Restricción CHECK (stock >= 0) en la base de datos como último resguardo. Si algo falla en las capas 1 y 2, PostgreSQL rechazará el UPDATE. Además, los tests de concurrencia con 50 hilos confirman que el stock nunca baja de 0 incluso bajo alta carga.

---

## Resumen de Decisiones de Arquitectura

### Control de Concurrencia
- **Bloqueo pesimista**: Para operaciones críticas de inventario. Garantiza consistencia absoluta.
- **Bloqueo optimista (@Version)**: Para cambios de estado de pedidos. Detecta modificaciones concurrentes.
- **Orden de adquisición**: Products por ID ASC para evitar deadlocks.
- **Lock timeout**: 5 segundos para evitar esperas infinitas.

### Rendimiento
- **Paginación obligatoria**: Default 20, máximo 50 items por página.
- **JOIN FETCH / @EntityGraph**: Elimina N+1 queries.
- **Batch JPA**: Insert/Update en lote de 50.
- **HikariCP**: Pool de 20 conexiones con timeout y leak detection.
- **Índices BD**: En columnas frecuentemente consultadas.

### Seguridad
- **BCrypt strength 10**: Balance seguridad/CPU.
- **Rate limiting**: 5 intentos de login por minuto por IP/email.
- **JWT**: Validación sin BD, roles en token.
- **Validación estricta**: @Valid en todos los DTOs, enums inválidos → 400.
- **Protección IDOR**: Verificación de ownership en cada endpoint.

### Robustez
- **GlobalExceptionHandler**: Captura todas las excepciones, nunca filtra stacktrace.
- **Idempotencia**: Header opcional para evitar duplicados.
- **Correlation ID**: Cada petición tiene ID único en logs.
- **Graceful shutdown**: 30s para terminar peticiones en curso.
- **Health checks**: /actuator/health público para orchestration.

### Pruebas
- **Testcontainers + PostgreSQL**: Tests reales de concurrencia (H2 no soporta bloqueos pesimistas correctamente).
- **8 escenarios de concurrencia**: Validan invariantes críticas.
- **k6**: Scripts de carga listos para usar con umbrales definidos.

### Limitaciones Conocidas
- ~50-100 peticiones/segundo con una instancia local.
- Latencia adicional de ~50-100ms por bloqueos bajo carga.
- Tokens JWT válidos hasta expiración si usuario es borrado (trade-off aceptado).
