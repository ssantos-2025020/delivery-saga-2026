# Delivery API - Microservices Architecture

Sistema de delivery implementado como microservios con Spring Boot 3, patrón Saga para consistencia eventual, y resistencia a estrés.

## Arquitectura

```
┌─────────────────────────────────────────┐
│         API Gateway (Puerto 8080)        │
│   • Enrutamiento • JWT • Rate Limit    │
└────────────┬────────────────────────────┘
             │
    ┌────────┼────────┐
    │        │        │
    ▼        ▼        ▼
┌─────────┐ ┌─────────┐ ┌─────────┐
│ Auth   │ │Catalogo │ │Pedidos  │
│ 8081   │ │  8082   │ │  8083   │
└─────────┘ └─────────┘ └─────────┘
             │
             ▼
    ┌────────────────┐
    │ PostgreSQL     │
    │ 3 BDs:        │
    │ auth_db       │
    │ catalogo_db   │
    │ pedidos_db    │
    └────────────────┘
```

## Servicios

### 1. auth-service (Puerto 8081)
- Registro y login de usuarios
- Generación de JWT
- BD: auth_db

### 2. catalogo-service (Puerto 8082)
- Gestión de comercios y productos
- **Endpoints internos** para reserva/liberación de stock
- Bloqueo pesimista para control de stock
- BD: catalogo_db

### 3. pedidos-service (Puerto 8083)
- Gestión de pedidos
- **Orquestador Saga** para crear pedidos
- Reconciliación de reservas huérfanas
- BD: pedidos_db

### 4. api-gateway (Puerto 8080)
- Enrutamiento a servicios backend
- Validación de JWT
- CORS

## Patrón Saga - Crear Pedido

### Flujo Normal
1. Cliente → Gateway → pedidos-service
2. pedidos-service genera reservaId único
3. Llama catalogo-service `/internal/stock/reservar`
4. catalogo-service desconta stock (bloqueo pesimista)
5. pedidos-service calcula total (+ Q20.00) y guarda pedido
6. catalogo-service confirma reserva

### Compensación
Si falla el paso 5 (guardar pedido):
- pedidos-service llama `/internal/stock/liberar`
- catalogo-service restaura stock (idempotente)
- Retorna error al cliente

### Reconciliación
@Scheduled cada 5 minutos:
- Busca reservas RESERVADAS sin pedido asociado
- Libera stock automáticamente

## Ejecución con Docker

### 1. Iniciar PostgreSQL y servicios

```bash
docker-compose up -d
```

Esto iniciará:
- PostgreSQL con 3 BDs (auth_db, catalogo_db, pedidos_db)
- auth-service (puerto 8081)
- catalogo-service (puerto 8082)
- pedidos-service (puerto 8083)
- api-gateway (puerto 8080)

### 2. Verificar health checks

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
```

## API Endpoints (vía Gateway)

### Autenticación
- `POST http://localhost:8080/api/v1/auth/registro`
- `POST http://localhost:8080/api/v1/auth/login`

### Comercios
- `GET http://localhost:8080/api/v1/comercios?page=0&size=20`
- `GET http://localhost:8080/api/v1/comercios/{id}`
- `GET http://localhost:8080/api/v1/comercios/{id}/productos`

### Pedidos
- `POST http://localhost:8080/api/v1/pedidos` (con JWT)
- `GET http://localhost:8080/api/v1/pedidos/mis-pedidos` (con JWT)
- `GET http://localhost:8080/api/v1/pedidos/{id}` (con JWT)
- `POST http://localhost:8080/api/v1/pedidos/{id}/cancelar` (con JWT)

## Ejemplo de Uso

### 1. Registrar usuario

```bash
curl -X POST http://localhost:8080/api/v1/auth/registro \
  -H "Content-Type: application/json" \
  -d '{
    "email": "cliente@example.com",
    "password": "password123",
    "nombre": "Juan Pérez"
  }'
```

Respuesta:
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tipo": "Bearer",
  "userId": 1,
  "email": "cliente@example.com",
  "rol": "CLIENTE"
}
```

### 2. Listar comercios

```bash
curl http://localhost:8080/api/v1/comercios
```

### 3. Crear pedido

```bash
curl -X POST http://localhost:8080/api/v1/pedidos \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "items": [
      {
        "productoId": 1,
        "cantidad": 2
      }
    ]
  }'
```

## Características de Resistencia a Estrés

### Concurrencia del Stock
- Bloqueo pesimista con orden por ID ASC (evita deadlocks)
- Lock timeout de 5 segundos
- @Version para bloqueo optimista (capa extra)
- CHECK constraint (stock >= 0) en BD

### Idempotencia
- Reservas de stock son idempotentes (liberar dos veces = una sola restauración)
- Cancelación idempotente (segunda cancelación falla limpiamente)

### Resilience4j
- Circuit Breaker en llamadas a catalogo-service
- Retry en operaciones idempotentes (liberar stock)
- Fallback cuando circuit breaker está abierto

### Reconciliación
- @Scheduled cada 5 minutos
- Limpia reservas huérfanas automáticamente

## Configuración

### Variables de Entorno

```bash
JWT_SECRET=your-secret-key-minimum-32-characters
INTERNAL_API_KEY=internal-api-key-secret
```

### Credenciales PostgreSQL
- Usuario: `postgres`
- Password: `admin`
- Host: `postgres` (docker-compose)

## Limitaciones Conocidas

- ~50-100 peticiones/segundo con una instancia
- Latencia adicional de ~50-100ms por comunicación HTTP entre servicios
- Tokens JWT válidos hasta expiración si usuario es borrado

## Próximos Pasos para Producción

1. Horizontal scaling (múltiples instancias por servicio)
2. PostgreSQL dedicado con recursos suficientes
3. Service discovery (Eureka o Consul)
4. Config server centralizado
5. Redis para cache y lista negra de tokens
6. Monitoring con Prometheus + Grafana
7. Log aggregation (ELK stack)

## Documentación Adicional

- `ARQUITECTURA.md` - Arquitectura detallada y diagramas de Saga
- `RESUMEN_MICROSERVICIOS_ACTUAL.md` - Estado de implementación
- `IMPLEMENTACION_MINIMA.md` - Guía de completación
