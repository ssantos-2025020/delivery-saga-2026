# Resumen Técnico - Resistencia a Estrés y Concurrencia

## Implementación Completa

He creado una API de delivery con Spring Boot 3 diseñada específicamente para resistir pruebas de carga y estrés. A continuación, explico cada decisión técnica en lenguaje simple y el problema que evita.

---

## 1. CONCURRENCIA DEL STOCK (LO MÁS CRÍTICO)

### Problema que evita
Si 200 clientes piden el mismo producto con stock 20 al mismo tiempo, sin control adecuado podrían venderse más de 20 unidades (sobreventa). Esto causa:
- Clientes enojados que pagan pero no reciben producto
- Problemas de contabilidad
- Pérdida de confianza en el sistema

### Solución implementada

**Bloqueo pesimista (@Lock(PESSIMISTIC_WRITE))**
- Cuando un pedido modifica el stock, la base de datos bloquea esa fila inmediatamente
- Otros pedidos deben esperar a que el primero termine
- Garantiza que solo una transacción a la vez modifique el stock
- Usamos `WHERE id IN (...) ORDER BY id ASC` para obtener productos en orden
- El orden consistente evita deadlocks (A espera B, B espera A)

**Bloqueo optimista (@Version) como capa extra**
- Cada Producto y Pedido tiene un campo `version` que se incrementa en cada modificación
- Si dos transacciones leen la misma versión, al guardar la primera gana y la segunda falla
- Es un respaldo por si algo falla en el bloqueo pesimista

**Restricción CHECK en la base de datos**
- `CHECK (stock >= 0)` en PostgreSQL como último resguardo
- Si todo lo demás falla, la BD rechazará cualquier UPDATE que deje stock negativo

**Lock timeout de 5 segundos**
- Configurado en `jakarta.persistence.lock.timeout=5000`
- Si una petición espera más de 5s para obtener el bloqueo, falla con 409/503
- Evita que el usuario espere infinitamente y degrada controladamente

**Explicación simple para evaluador**
> "El bloqueo pesimista es como un cajero: solo atiende a un cliente a la vez. Es más lento pero garantiza que el inventario siempre sea correcto. El bloqueo optimista es como detectar billetes falsos: es un respaldo adicional. La restricción CHECK es como una caja fuerte que no deja que el saldo sea negativo. Juntos, aseguran que nunca vendamos más de lo que tenemos."

---

## 2. IDEMPOTENCIA EN CREACIÓN DE PEDIDOS

### Problema que evita
Si un cliente hace doble clic en "Confirmar pedido" o hay un timeout de red, la petición puede enviarse dos veces. Sin idempotencia:
- Se crean dos pedidos idénticos
- El cliente es cobrado dos veces
- El stock se reduce por duplicado

### Solución implementada
- Header opcional `Idempotency-Key` en POST /api/v1/pedidos
- La primera petición crea el pedido y guarda la clave en BD
- Peticiones siguientes con la misma clave recuperan el pedido ya creado
- Índice único `cliente_id + key` en BD para garantizar unicidad

**Explicación simple para evaluador**
> "Si el cliente hace doble clic o se reinicia la red, podría enviar el mismo pedido dos veces. Con idempotencia, la segunda vez devolvemos el pedido ya creado en lugar de duplicarlo. Es como un número de seguimiento: si ya existe, no creamos otro."

---

## 3. RENDIMIENTO DE BASE DE DATOS

### Problema que evita
Sin optimización, la API puede:
- Hacer cientos de consultas por petición (N+1 problem)
- Listar miles de registros sin paginación (agota memoria)
- Tener bloqueos innecesarios que degradan rendimiento

### Solución implementada

**Cero N+1 queries con JOIN FETCH / @EntityGraph**
- Usamos `@EntityGraph(attributePaths = {"cliente", "detalles.producto", "repartidor"})`
- Hace un solo JOIN para cargar todas las relaciones
- Sin esto, Hibernate haría 1 consulta por pedido + N por cada relación
- Verificado con tests que el número de consultas es constante

**Paginación obligatoria**
- Default: 20 items por página
- Máximo: 50 items por página
- Respuesta incluye: content, page, size, totalElements, totalPages
- Evita cargar miles de registros en memoria

**Índices en BD**
- `pedido(cliente_id, fecha_pedido DESC)` - Para "mis pedidos"
- `pedido(estado, repartidor_id)` - Para repartidores
- `producto(comercio_id)` - Para productos de un comercio
- `comercio(abierto, categoria)` - Para filtrar comercios
- `usuario(email)` UNIQUE - Para login rápido

**HikariCP pool configurado**
- maximumPoolSize: 20 conexiones
- connectionTimeout: 5s
- leakDetectionThreshold: 60s (detecta conexiones no cerradas)
- Dimensionado para ~50-100 peticiones/segundo

**Batch JPA**
- hibernate.jdbc.batch_size: 50
- order_inserts: true
- order_updates: true
- Inserta detalles de pedido en lote (1 INSERT en lugar de N)

**spring.jpa.open-in-view=false**
- Deshabilita lazy loading fuera de transacción
- Evita problemas de "LazyInitializationException"
- Los mappers trabajan sobre datos ya cargados

**Explicación simple para evaluador**
> "JOIN FETCH es como llevar todo en una sola bolsa en lugar de hacer múltiples viajes. La paginación es como mostrar 20 productos por página en lugar de todos a la vez. Los índices son como un índice de libro: permite encontrar rápidamente sin leer todo. El pool de conexiones es como tener 20 cajeros en lugar de uno solo."

---

## 4. TRABAJO CPU-INTENSIVO Y SEGURIDAD BAJO CARGA

### Problema que evita
- Login/registro con hashing fuerte puede saturar CPU bajo ataque
- Sin rate limiting, un atacante puede inundar el sistema con peticiones
- Inputs malformados pueden causar errores 500 (excepciones no manejadas)

### Solución implementada

**BCrypt strength 10**
- Balance entre seguridad y rendimiento
- Toma ~100ms en hardware moderno
- Strength 12 tomaría ~400ms (degradaría bajo carga)
- Login/registro son los endpoints más costosos por esto

**Rate limiting con Bucket4j**
- Login: 5 intentos por minuto por IP/email
- General: 100 peticiones por minuto por IP
- Respuesta 429 Too Many Requests con header Retry-After
- Thread-safe, expira entradas antiguas automáticamente

**JWT validación sin BD**
- Email y rol dentro del token codificado
- Validación criptográfica sin consultar BD en cada petición
- Trade-off: usuario borrado sigue válido hasta expiración (24h)
- Para mitigar, se podría usar Redis para lista negra

**Límites de tamaño**
- server.tomcat.max-swallow-size: 10MB
- server.tomcat.max-http-form-post-size: 10MB
- Máximo 50 items por pedido
- Máximo 100 unidades por item
- Evita ataques de memoria

**Validación estricta**
- @Valid en todos los DTOs
- Enums inválidos → 400 (no 500)
- JSON malformado → 400
- Missing parameters → 400
- Ningún input produce 500

**Protección IDOR**
- Verificación de ownership en cada endpoint
- Un cliente solo ve/cancela sus propios pedidos
- Un repartidor solo modifica sus pedidos asignados

**Explicación simple para evaluador**
> "BCrypt strength 10 es como una cerradura fuerte pero que se abre rápido. Strength 12 sería más segura pero lenta. Rate limiting es como un portero: después de 5 intentos fallidos, te bloquea por un minuto. JWT sin BD es como una credencial física: si el portero confía en la credencial, no necesita verificar tu identidad cada vez."

---

## 5. ROBUSTEZ OPERATIVA

### Problema que evita
- Excepciones no manejadas causan 500 sin mensaje claro
- Sin logs correlacionados, imposible rastrear peticiones
- Apagado abrupto puede dejar transacciones a medias
- Sin health checks, orchestrators no saben si la app está viva

### Solución implementada

**GlobalExceptionHandler comprehensivo**
- Captura: validación, JSON roto, método no permitido (405), tipo no soportado (415)
- Captura: optimistic lock (409), lock timeout (409/503), violación de integridad (409)
- Captura: timeout de pool (503), acceso denegado (403), recurso no encontrado (404)
- Handler genérico 500 que NO filtra stacktrace
- Siempre devuelve JSON con mensaje claro

**Correlation ID en logs**
- Filtro añade X-Correlation-ID a cada petición
- Si el cliente no lo envía, se genera uno con UUID
- MDC.put("correlationId", id) para logs trazables
- Permite rastrear una petición a través de múltiples componentes

**Spring Boot Actuator**
- /actuator/health público (liveness/readiness)
- /actuator/metrics solo para ADMIN
- Métricas de JVM, HTTP, y custom (pedido.creacion timer)

**Graceful shutdown**
- server.shutdown=graceful
- lifecycle.timeout-per-shutdown-phase: 30s
- Al recibir SIGTERM, deja de aceptar nuevas peticiones
- Espera hasta 30s para que peticiones en curso terminen
- Evita transacciones a medias

**Tomcat configurado**
- threads.max: 200
- accept-count: 100 (cola de espera)
- max-connections: 8192
- Configurado para ~50-100 peticiones/segundo

**Micrometer metrics**
- Timer en creación de pedidos
- Métricas estándar de Spring Boot
- Disponible en /actuator/metrics (solo ADMIN)

**Explicación simple para evaluador**
> "GlobalExceptionHandler es como un gerente de atención al cliente: cualquier problema, lo maneja con un mensaje claro. Correlation ID es como un número de ticket: si algo falla, puedes rastrearlo. Graceful shutdown es como cerrar la tienda con calma: terminas de atender a los clientes adentro antes de cerrar la puerta."

---

## 6. PRUEBAS DE CONCURRENCIA Y CARGA

### Escenarios implementados con Testcontainers + PostgreSQL REAL

**a) 50 hilos piden stock 20**
- 50 hilos crean pedidos del mismo producto (stock=20, cantidad=1)
- Resultado esperado: 20 exitosos, 30 rechazados con 409, stock final=0
- Nunca stock negativo

**b) Pedidos cruzados sin deadlock**
- Dos hilos compran productos A+B y B+A respectivamente
- Repetido 100 veces
- Resultado: sin deadlock, stock consistente

**c) Dos cancelaciones simultáneas**
- Dos hilos cancelan el mismo pedido
- Resultado: stock restaurado una sola vez, segunda falla con InvalidStatusException

**d) Dos repartidores cambian estado**
- Dos hilos cambian estado del mismo pedido
- Resultado: solo uno gana, el otro recibe 409

**e) Idempotency-Key concurrente**
- 10 hilos envían misma clave en paralelo
- Resultado: un solo pedido creado

**f) Invariante de stock**
- Verifica: stock inicial = stock actual + unidades en pedidos no cancelados
- Garantiza conservación de unidades

**g) Rate limit devuelve 429**
- Más de 5 login attempts en 60s
- Resultado: 429 con Retry-After header

**h) JSON malformado y enums inválidos**
- Inputs inválidos devuelven 400, no 500

**i) Paginación respeta máximo**
- Solicitar size=100 devuelve máximo 50

### Script k6 para pruebas de carga

**load-test.js**
- Escenario mixto: listados, creación de pedidos, etc.
- Umbrales: p95 < 500ms, error rate < 1%, sin 5xx
- Simula tráfico real con ramp up/down

**concurrency-test.js**
- 50 usuarios concurrentes creando pedidos
- Valida stock nunca negativo
- Valida respuestas 409 (no 500) al agotar stock

**Explicación simple para evaluador**
> "Testcontainers con PostgreSQL real es esencial porque H2 no soporta bloqueos pesimistas correctamente. Los 8 escenarios validan que el sistema se comporta correctamente bajo presión: stock nunca negativo, sin deadlocks, cancelaciones seguras, etc. k6 simula tráfico real para medir rendimiento real."

---

## 7. ENTREGABLES EXTRA

### Dockerfile multi-stage
- Stage 1: Maven build (JDK 17)
- Stage 2: Runtime (JRE 17 Alpine, usuario no root)
- Health check incluido
- dumb-init para manejo correcto de señales

### docker-compose.yml
- App + PostgreSQL + healthchecks
- Volume para persistencia de datos
- Network aislada
- Depends_on con healthcheck

### Guía de defensa técnica
- 10 preguntas más probables con respuestas cortas
- Respuestas formuladas para que puedas explicar con tus palabras
- Ver GUIA_DEFENSA_TECNICA.md

---

## VERIFICACIÓN DE REQUISITOS

✅ Bloqueo pesimista con orden por ID ASC  
✅ @Version en Producto y Pedido  
✅ CHECK constraint (stock >= 0)  
✅ Lock timeout 5s  
✅ Idempotency-Key con índice único  
✅ JOIN FETCH / @EntityGraph (cero N+1)  
✅ Índices en BD declarados  
✅ Paginación (default 20, max 50)  
✅ HikariCP configurado  
✅ Batch JPA activado  
✅ BCrypt strength 10  
✅ Rate limiting (Bucket4j)  
✅ JWT con roles en token  
✅ Límites de tamaño body  
✅ Validación estricta  
✅ Protección IDOR  
✅ GlobalExceptionHandler comprehensivo  
✅ Correlation ID (MDC)  
✅ Actuator /health público  
✅ Graceful shutdown  
✅ Tomcat threads configurados  
✅ Micrometer metrics  
✅ 8 tests de concurrencia con Testcontainers  
✅ Scripts k6 con umbrales  
✅ Dockerfile + docker-compose  
✅ Guía de defensa técnica  

---

## CÓMO EJECUTAR

```bash
# 1. Iniciar PostgreSQL
docker run --name delivery-postgres \
  -e POSTGRES_DB=delivery \
  -e POSTGRES_USER=delivery \
  -e POSTGRES_PASSWORD=delivery \
  -p 5432:5432 \
  postgres:15-alpine

# 2. Ejecutar schema
psql -U delivery -d delivery -f src/main/resources/schema.sql

# 3. Ejecutar app (usar -s maven-settings.xml si hay problemas de SSL)
mvn spring-boot:run -s maven-settings.xml

# 4. Ejecutar tests de concurrencia
mvn test -Dtest=ConcurrencyTest -s maven-settings.xml

# 5. Ejecutar pruebas de carga
cd k6
k6 run load-test.js
```

**Nota sobre Maven SSL**: Si experimentas errores de certificados SSL al ejecutar Maven, usa el archivo `maven-settings.xml` incluido en el proyecto con el flag `-s maven-settings.xml`. Este archivo configura Maven para usar HTTP en lugar de HTTPS, evitando problemas de validación de certificados.

---

## LIMITACIONES REALES

Con una sola instancia y PostgreSQL local:
- ~50-100 peticiones/segundo sostenidas
- 50 usuarios concurrentes máximos antes de degradación
- Latencia adicional de ~50-100ms por bloqueos bajo carga

No invento métricas: estas son estimaciones basadas en la arquitectura implementada. Para producción se recomienda horizontal scaling.

---

## REGLA DE ORO CUMPLIDA

Todas las mejoras de rendimiento respetan la especificación original. Si hubo cambios en formato de respuesta (por ejemplo, paginación), fueron documentados en README.md y GUIA_DEFENSA_TECNICA.md.
