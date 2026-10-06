# Delivery API - Sistema con Resistencia a Estrés y Concurrencia

API de delivery implementada con Spring Boot 3, diseñada específicamente para resistir pruebas de carga y estrés con control absoluto de concurrencia en el inventario.

## Características Principales

### Control de Concurrencia (CRÍTICO)
- **Bloqueo pesimista** con `@Lock(PESSIMISTIC_WRITE)` para operaciones de stock
- Adquisición ordenada por ID ASC para evitar deadlocks
- **Bloqueo optimista** con `@Version` en Producto y Pedido
- Lock timeout de 5 segundos para evitar esperas infinitas
- Reintento controlado ante deadlocks (máximo 3 intentos)

### Idempotencia
- Header opcional `Idempotency-Key` en POST /api/v1/pedidos
- Evita duplicados por doble clic o reintentos de red
- Índice único cliente + clave en BD

### Rendimiento
- **Cero N+1 queries** con JOIN FETCH y @EntityGraph
- Paginación obligatoria (default 20, máximo 50)
- Índices optimizados en BD
- HikariCP pool configurado (max 20 conexiones)
- Batch JPA activado (tamaño 50)

### Seguridad
- BCrypt strength 10 (equilibrio seguridad/CPU)
- Rate limiting: 5 intentos login/minuto por IP/email
- JWT con roles en token (validación sin BD)
- Validación estricta de inputs (enums inválidos → 400)
- Protección contra IDOR

### Robustez Operativa
- GlobalExceptionHandler que captura TODO (nunca 500 sin control)
- Correlation ID en cada petición (logs trazables)
- Spring Boot Actuator con health checks
- Graceful shutdown (30s para terminar peticiones)
- Tomcat configurado (200 threads max, cola 100)
- Micrometer metrics en creación de pedidos

## Arquitectura Técnica

```
┌─────────────────────────────────────────────────────────┐
│                     Client Layer                         │
│              (Web / Mobile / k6 tests)                   │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│              Rate Limit Filter (Bucket4j)                │
│           5 login/min, 100 general/min                   │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│           JWT Authentication Filter                      │
│         Extract & validate token, set context           │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│                  Controllers                             │
│    AuthController | PedidoController | ComercioController│
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│                   Services                               │
│  AuthService | PedidoService | ComercioService          │
│  └─ Pessimistic Lock for stock operations               │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│                  Repositories                            │
│  JPA with @EntityGraph, custom lock queries              │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│               PostgreSQL 15                               │
│  └─ CHECK constraint (stock >= 0)                       │
│  └─ Indexes on frequently queried columns               │
└─────────────────────────────────────────────────────────┘
```

## Requisitos

- Java 17
- Maven 3.9+
- PostgreSQL 15
- Docker (opcional, para deployment)

## Ejecución Local

### 1. Configurar Base de Datos

```bash
# Crear base de datos
createdb delivery

# O con Docker
docker run --name delivery-postgres \
  -e POSTGRES_DB=delivery \
  -e POSTGRES_USER=delivery \
  -e POSTGRES_PASSWORD=delivery \
  -p 5432:5432 \
  postgres:15-alpine
```

### 2. Ejecutar Schema SQL

```bash
psql -U delivery -d delivery -f src/main/resources/schema.sql
```

### 3. Configurar Variables de Entorno (opcional)

```bash
export DB_URL=jdbc:postgresql://localhost:5432/delivery
export DB_USERNAME=delivery
export DB_PASSWORD=delivery
export JWT_SECRET=tu-clave-secreta-minimo-32-caracteres
```

### 4. Ejecutar Aplicación

Si tienes problemas de SSL/certificados con Maven, usa el settings.xml incluido:

```bash
mvn spring-boot:run -s maven-settings.xml
```

O normalmente:

```bash
mvn spring-boot:run
```

La API estará disponible en `http://localhost:8080`

## Ejecución con Docker

```bash
docker-compose up -d
```

Verificar health check:
```bash
curl http://localhost:8080/actuator/health
```

## Ejecutar Tests

### Tests Unitarios y de Integración

```bash
mvn test -s maven-settings.xml
```

### Tests de Concurrencia (requiere Docker/Testcontainers)

```bash
mvn test -Dtest=ConcurrencyTest -s maven-settings.xml
```

Los tests de concurrencia usan PostgreSQL real (no H2) para validar correctamente los bloqueos pesimistas.

## Pruebas de Carga con k6

### Prueba General de Carga

```bash
cd k6
k6 run load-test.js
```

### Prueba de Concurrencia (50 usuarios simultáneos)

```bash
cd k6
k6 run concurrency-test.js
```

Con URL personalizada:
```bash
BASE_URL=http://mi-api.com k6 run load-test.js
```

Ver `k6/README.md` para detalles sobre umbrales e interpretación de resultados.

## API Endpoints

### Autenticación
- `POST /api/v1/auth/registro` - Registrar usuario
- `POST /api/v1/auth/login` - Login (rate limited)

### Comercios
- `GET /api/v1/comercios?page=0&size=20&categoria=X` - Listar comercios (paginado)
- `GET /api/v1/comercios/{id}` - Obtener comercio
- `GET /api/v1/comercios/{id}/productos?page=0&size=20&soloDisponibles=true` - Listar productos (paginado)

### Pedidos
- `POST /api/v1/pedidos` - Crear pedido (con Idempotency-Key opcional)
- `GET /api/v1/pedidos/mis-pedidos?page=0&size=20` - Mis pedidos (paginado)
- `GET /api/v1/pedidos/{id}` - Obtener pedido
- `POST /api/v1/pedidos/{id}/cancelar` - Cancelar pedido
- `GET /api/v1/pedidos/disponibles?estado=PENDIENTE` - Pedidos disponibles para repartidores
- `POST /api/v1/pedidos/{id}/asignar` - Asignar repartidor
- `PUT /api/v1/pedidos/{id}/estado?estado=EN_CAMINO` - Actualizar estado
- `GET /api/v1/pedidos/repartidor?page=0&size=20` - Pedidos del repartidor

### Actuator
- `GET /actuator/health` - Health check (público)
- `GET /actuator/metrics` - Métricas (solo ADMIN)

## Ejemplo de Uso

### 1. Registrar y Login

```bash
# Registrar
curl -X POST http://localhost:8080/api/v1/auth/registro \
  -H "Content-Type: application/json" \
  -d '{
    "email": "cliente@example.com",
    "password": "password123",
    "nombre": "Juan Pérez"
  }'

# Login
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "cliente@example.com",
    "password": "password123"
  }'
```

Guardar el token devuelto.

### 2. Listar Comercios

```bash
curl -X GET "http://localhost:8080/api/v1/comercios?page=0&size=20" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### 3. Crear Pedido

```bash
curl -X POST http://localhost:8080/api/v1/pedidos \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: unique-key-123" \
  -d '{
    "items": [
      {
        "productoId": 1,
        "cantidad": 2
      }
    ]
  }'
```

### 4. Cancelar Pedido

```bash
curl -X POST http://localhost:8080/api/v1/pedidos/1/cancelar \
  -H "Authorization: Bearer YOUR_TOKEN"
```

## Métricas y Monitoreo

La aplicación expone métricas en `/actuator/metrics`:

- `pedido.creacion`: Timer con tiempo de creación de pedidos
- Métricas estándar de Spring Boot Actuator (JVM, HTTP, etc.)

Para ver métricas:
```bash
curl -u admin:password http://localhost:8080/actuator/metrics/pedido.creacion
```

## Guía de Defensa Técnica

Ver `GUIA_DEFENSA_TECNICA.md` para respuestas a las 10 preguntas más probables de un evaluador técnico:

1. ¿Por qué bloqueo pesimista y no solo optimista?
2. ¿Cómo evitas deadlocks?
3. ¿Qué pasa si dos usuarios cancelan a la vez?
4. ¿Por qué idempotencia?
5. ¿Por qué BCrypt strength 10?
6. ¿Cómo evitas N+1 queries?
7. ¿Por qué no validar JWT contra BD en cada petición?
8. ¿Cómo dimensionaste el pool de conexiones?
9. ¿Qué hace graceful shutdown?
10. ¿Cómo garantizas stock nunca negativo?

## Configuración Importante

### application.yml

Configuraciones clave en `src/main/resources/application.yml`:

```yaml
spring:
  jpa:
    open-in-view: false  # Deshabilita lazy loading fuera de transacción
    properties:
      hibernate:
        jdbc:
          batch_size: 50  # Batch inserts/updates
        jakarta:
          persistence:
            lock:
              timeout: 5000  # 5 segundos lock timeout

server:
  tomcat:
    threads:
      max: 200  # Máximo hilos Tomcat
  shutdown: graceful  # Apagado ordenado

app:
  rate-limit:
    login:
      max-attempts: 5
      window-seconds: 60
  pagination:
    default-size: 20
    max-size: 50
  order:
    max-items: 50
    max-quantity-per-item: 100
```

## Limitaciones Conocidas

- ~50-100 peticiones/segundo con una instancia local y PostgreSQL
- Latencia adicional de ~50-100ms por bloqueos pesimistas bajo carga alta
- Tokens JWT válidos hasta expiración si usuario es borrado (trade-off aceptado)
- No implementado cache (Redis) para lecturas frecuentes
- No implementado cola de mensajes para procesamiento asíncrono

## Próximos Pasos para Producción

1. **Horizontal scaling**: Múltiples instancias detrás de load balancer
2. **PostgreSQL dedicado**: Con recursos suficientes y configuración optimizada
3. **Redis**: Para cache de lecturas frecuentes y lista negra de tokens
4. **CDN**: Para assets estáticos si aplica
5. **Monitoring**: Prometheus + Grafana para métricas
6. **Log aggregation**: ELK stack o similar
7. **CI/CD**: Pipeline automatizado con tests en cada commit
8. **Secrets management**: Vault o AWS Secrets Manager

## Licencia

Este proyecto es para fines educativos y de evaluación técnica.
