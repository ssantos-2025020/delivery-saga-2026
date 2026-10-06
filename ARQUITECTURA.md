# Arquitectura de Microservicios - Delivery API

## Visión General

Sistema de delivery implementado como microservios con Spring Boot 3.x, Java 17 y patrón Saga para consistencia eventual.

## Servicios

```
┌─────────────────────────────────────────────────────────────────┐
│                        API Gateway                               │
│                    (Spring Cloud Gateway)                        │
│         • Enrutamiento • JWT validation • Rate limiting          │
│         • CORS • Request ID • Circuit Breaker                    │
│                   Puerto: 8080                                   │
└────────────────────┬────────────────────────────────────────────┘
                     │
        ┌────────────┼────────────┐
        │            │            │
        ▼            ▼            ▼
┌──────────────┐ ┌────────────┐ ┌──────────────┐
│ auth-service │ │catalogo-svc│ │pedidos-svc  │
│  Puerto:8081 │ │ Puerto:8082│ │  Puerto:8083│
└──────────────┘ └────────────┘ └──────────────┘
```

### 1. api-gateway (Puerto 8080)
**Responsabilidades:**
- Enrutamiento a servicios backend
- Validación de JWT (primer nivel de defensa)
- Rate limiting global (429)
- CORS configuración
- Request ID generation
- Circuit Breaker (Resilience4j)

**Endpoints públicos (todos /api/v1/...):**
- `/api/v1/auth/*` → auth-service
- `/api/v1/comercios/*` → catalogo-service
- `/api/v1/pedidos/*` → pedidos-service

### 2. auth-service (Puerto 8081)
**Responsabilidades:**
- Registro de usuarios
- Login y generación de JWT
- Gestión de usuarios (Cliente, Repartidor, Admin)
- BD: `auth_db`

**Endpoints:**
- `POST /api/v1/auth/registro`
- `POST /api/v1/auth/login`

### 3. catalogo-service (Puerto 8082)
**Responsabilidades:**
- Gestión de comercios
- Gestión de productos
- Control de stock con bloqueo pesimista
- Reserva/liberación de stock (endpoints internos)
- BD: `catalogo_db`

**Endpoints públicos:**
- `GET /api/v1/comercios` (paginado)
- `GET /api/v1/comercios/{id}`
- `GET /api/v1/comercios/{id}/productos` (paginado)

**Endpoints internos (NO expuestos por gateway):**
- `POST /internal/stock/reservar` - Reserva stock para un pedido
- `POST /internal/stock/liberar` - Libera stock reservado
- Protegidos por API key interna o JWT de servicio

### 4. pedidos-service (Puerto 8083)
**Responsabilidades:**
- Gestión de pedidos
- Orquestación de Saga para crear pedidos
- Estados de pedidos
- Cancelación de pedidos
- Reconciliación de reservas huérfanas
- BD: `pedidos_db`

**Endpoints públicos:**
- `POST /api/v1/pedidos` - Crear pedido (Saga)
- `GET /api/v1/pedidos/mis-pedidos` (paginado)
- `GET /api/v1/pedidos/{id}`
- `POST /api/v1/pedidos/{id}/cancelar`
- `GET /api/v1/pedidos/disponibles` (para repartidores)
- `POST /api/v1/pedidos/{id}/asignar`
- `PUT /api/v1/pedidos/{id}/estado`

## Patrón Saga - Crear Pedido

### Flujo Normal

```
Cliente → Gateway → pedidos-service
    ↓
1. Validar request y generar reservaId único
    ↓
2. Llamar catalogo-service /internal/stock/reservar
    ↓
3. catalogo-service (transacción local):
   - Bloqueo pesimista ordenado por id
   - Validar existencia, disponibilidad, stock
   - Descontar stock
   - Devolver precios y nombres reales
    ↓
4. pedidos-service calcula subtotales y total (+ Q20.00)
   Guarda Pedido + Detalles en @Transactional local
    ↓
5. Retornar éxito al cliente
```

### Flujo de Compensación

```
Si paso 4 falla (no se puede guardar pedido):
    ↓
pedidos-service llama /internal/stock/liberar
    con el mismo reservaId
    ↓
catalogo-service restaura stock (idempotente)
    ↓
pedidos-service retorna error al cliente
```

### Cancelación de Pedido

```
1. Validar estado = PENDIENTE
2. Cambiar estado a CANCELADO
3. Llamar /internal/stock/liberar con reservaId
4. Liberación es idempotente (dos cancelaciones simultáneas
   restauran UNA sola vez)
```

### Reconciliación Programada

```
@Scheduled (cada N minutos)
→ Busca reservas sin pedido asociado
→ Llama /internal/stock/liberar
→ Limpia reservas huérfanas
```

## Bases de Datos

Un contenedor PostgreSQL con 3 bases de datos independientes:

```
PostgreSQL Container (puerto 5432)
├── auth_db (usuarios)
├── catalogo_db (comercios, productos, reservas de stock)
└── pedidos_db (pedidos, detalles)
```

**Reglas:**
- Cada servicio accede SOLO a su BD
- Prohibido leer tablas de otro servicio
- Sin FK entre servicios
- Se guardan IDs de referencia (clienteId, repartidorId, productoId)
- Se copian datos necesarios (nombre, precio unitario) en pedidos

## Comunicación Entre Servicios

- **Sin Eureka ni Config Server**
- Comunicación por nombre de servicio en Docker Compose
- Configuración por variables de entorno
- HTTP REST con Resilience4j:
  - Timeout configurado
  - Retry solo en operaciones idempotentes
  - Circuit Breaker

## Seguridad

- **JWT generado por auth-service**
- Secret compartido por variable de entorno
- **Cada servicio valida JWT por su cuenta**
- Gateway valida JWT (primer nivel)
- Cada servicio aplica reglas de rol
- Endpoints internos protegidos por API key

## Resilience4j

Configurado en pedidos-service para llamadas a catalogo-service:

- **Timeout**: 5 segundos
- **Retry**: Solo en operaciones idempotentes (liberar stock)
- **Circuit Breaker**: Abre tras N fallos consecutivos
- Si catalogo-service no responde: 503 Service Unavailable
- Nunca 500 ni pedido a medias

## Características de Resistencia a Estrés Mantenidas

- **Idempotency-Key**: En creación de pedidos
- **Paginación**: Default 20, máximo 50
- **Índices**: En cada BD según necesidad
- **Bloqueo pesimista**: En catalogo-service para stock
- **@Version**: En Producto y Pedido
- **Rate limiting**: En gateway y en auth-service (login)
- **GlobalExceptionHandler**: En cada servicio
- **Correlation ID**: En gateway
- **Health checks**: En cada servicio

## Cambios vs Monolito

### Cambios Importantes

1. **Relaciones JPA**: Eliminadas entre servicios. Antes: Pedido → Usuario, Pedido → Producto con @ManyToOne. Ahora: Pedido guarda clienteId, repartidorId como Long; DetallePedido guarda productoId como Long y copia nombre, precio.

2. **Transacciones distribuidas**: Reemplazadas por Saga pattern. Antes: @Transactional cubría todo (stock + pedido). Ahora: Transacción local en catalogo-service + transacción local en pedidos-service + compensación si falla.

3. **Base de datos única**: Ahora 3 BD independientes. Antes: Una BD con todas las tablas y FKs.

4. **Stock management**: Ahora con reservas explícitas. Antes: Descontar y restaurar directamente en pedido. Ahora: Reservar → (crear pedido) → Liberar si falla.

### Qué se Mantiene Igual

- Endpoints públicos /api/v1/... (gateway los enruta)
- Validación de JWT
- Rate limiting
- Idempotencia
- Paginación
- Bloqueo pesimista en stock
- @Version para optimistic locking
- Todas las reglas de resistencia a estrés

## Diagrama de Saga en Texto

```
ESTADO INICIAL: Stock disponible

[PASO 1] pedidos-service: Generar reservaId
└─> reservaId = UUID.randomUUID()

[PASO 2] pedidos-service → catalogo-service: POST /internal/stock/reservar
└─> Body: { reservaId, items: [{productoId, cantidad}] }
    └─> catalogo-service @Transactional:
        ├─> Bloqueo pesimista de productos (ORDER BY id ASC)
        ├─> Validar stock >= cantidad
        ├─> Descontar stock
        ├─> Guardar reserva (id, productoId, cantidad, estado=RESERVADA)
        └─> Retornar: { precios, nombres, exito: true }
    └─> ÉXITO: Stock reservado

[PASO 3] pedidos-service: Calcular total y guardar pedido
└─> @Transactional local:
    ├─> Calcular subtotales
    ├─> Sumar Q20.00 de delivery
    ├─> Guardar Pedido (estado=PENDIENTE, reservaId)
    ├─> Guardar DetallePedido (con nombres y precios copiados)
    └─> COMMIT

[PASO 4] pedidos-service: Retornar éxito al cliente
└─> 200 OK con pedido creado

---

[COMPENSACIÓN] Si PASO 3 falla:

pedidos-service → catalogo-service: POST /internal/stock/liberar
└─> Body: { reservaId }
    └─> catalogo-service @Transactional:
        ├─> Buscar reserva por reservaId
        ├─> Si estado=RESERVADA:
        │   ├─> Restaurar stock (bloqueo pesimista)
        │   ├─> Marcar reserva=LIBERADA
        │   └─> COMMIT
        └─> Si ya LIBERADA: no-op (idempotente)
    └─> Retornar: { exito: true }

pedidos-service: Retornar error al cliente
└─> 503 Service Unavailable (catalogo-service no disponible)
    o 409 Conflict (error de negocio)

---

[CANCELACIÓN] Cliente cancela pedido PENDIENTE:

pedidos-service @Transactional:
├─> Validar estado = PENDIENTE
├─> Cambiar estado a CANCELADO
├─> Llamar /internal/stock/liberar con reservaId
└─> COMMIT

catalogo-service: Libera stock (idempotente)

---

[RECONCILIACIÓN] @Scheduled (cada 5 minutos):

pedidos-service:
├─> Buscar reservas en catalogo-service con estado=RESERVADA
│   y fecha < (ahora - 5 minutos)
├─> Para cada reserva huérfana:
│   └─> Llamar /internal/stock/liberar
└─> Log de reservas limpiadas
```

## Variables de Entorno por Servicio

### api-gateway
- `GATEWAY_PORT=8080`
- `AUTH_SERVICE_URL=http://auth-service:8081`
- `CATALOGO_SERVICE_URL=http://catalogo-service:8082`
- `PEDIDOS_SERVICE_URL=http://pedidos-service:8083`
- `JWT_SECRET` (compartido)

### auth-service
- `SERVER_PORT=8081`
- `DB_URL=jdbc:postgresql://postgres:5432/auth_db`
- `DB_USERNAME=delivery`
- `DB_PASSWORD=delivery`
- `JWT_SECRET` (compartido)

### catalogo-service
- `SERVER_PORT=8082`
- `DB_URL=jdbc:postgresql://postgres:5432/catalogo_db`
- `DB_USERNAME=delivery`
- `DB_PASSWORD=delivery`
- `INTERNAL_API_KEY` (para endpoints internos)

### pedidos-service
- `SERVER_PORT=8083`
- `DB_URL=jdbc:postgresql://postgres:5432/pedidos_db`
- `DB_USERNAME=delivery`
- `DB_PASSWORD=delivery`
- `JWT_SECRET` (compartido)
- `CATALOGO_SERVICE_URL=http://catalogo-service:8082`
- `INTERNAL_API_KEY` (para llamar catalogo-service)
