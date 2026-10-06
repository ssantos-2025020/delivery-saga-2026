# Pruebas de Carga con k6

Este directorio contiene scripts de prueba de carga usando k6 para evaluar la resistencia a estrés de la API de delivery.

## Requisitos Previos

- [k6](https://k6.io/docs/getting-started/installation/) instalado
- La aplicación debe estar ejecutándose en `http://localhost:8080`
- Base de datos PostgreSQL ejecutándose

## Scripts Disponibles

### 1. `load-test.js` - Prueba de Carga General

Escenario mixto que simula tráfico real:
- Listado de comercios (lectura)
- Listado de productos (lectura)
- Creación de pedidos (escritura con bloqueo)
- Listado de mis pedidos (lectura)

**Ejecutar:**
```bash
k6 run load-test.js
```

**Con URL personalizada:**
```bash
BASE_URL=http://mi-api.com k6 run load-test.js
```

### 2. `concurrency-test.js` - Prueba de Concurrencia

Prueba específica de concurrencia que simula 50 usuarios creando pedidos simultáneamente sobre el mismo producto. Esto valida:
- Que el stock nunca queda negativo
- Que los bloqueos pesimistas funcionan correctamente
- Que la API responde con 409 cuando se agota el stock (no 500)

**Ejecutar:**
```bash
k6 run concurrency-test.js
```

## Umbrales y Métricas

### load-test.js
- **p95 < 500ms**: El 95% de las peticiones deben completarse en menos de 500ms
- **Error rate < 1%**: Menos del 1% de peticiones deben fallar
- **No errores 5xx**: La API no debe devolver errores 500

### concurrency-test.js
- **p95 < 1000ms**: El 95% de las peticiones deben completarse en menos de 1s
- **Error rate < 50%**: Se aceptan hasta 50% de errores (409 por stock agotado)
- **No errores 5xx**: La API no debe devolver errores 500 ni 503

## Interpretación de Resultados

### Métricas Clave

1. **http_req_duration**: Tiempo de respuesta
   - `p(95)`: Percentil 95 (el 95% de peticiones son más rápidas que este valor)
   - `p(99)`: Percentil 99 (el 99% de peticiones son más rápidas que este valor)

2. **http_reqs/s**: Peticiones por segundo (throughput)

3. **vus**: Usuarios virtuales simultáneos

4. **errors**: Tasa de errores personalizada

### Ejemplo de Salida Exitosa

```
✓ list comercios status 200
✓ list comercios has content
✓ list productos status 200
✓ create order status 200 or 409
✓ create order not 500
✓ list my orders status 200

checks.........................: 100.00% ✓ 1234     ✗ 0
data_received..................: 2.1 MB  45 kB/s
data_sent......................: 567 kB  12 kB/s
http_req_duration..............: avg=245ms  min=45ms  med=210ms  max=890ms  p(95)=480ms  p(99)=720ms
http_reqs......................: 1234    26.5/s
```

### Qué Buscar

**✅ Saludable:**
- p95 < 500ms en load-test.js
- Tasa de errores < 1%
- Sin errores 500
- Stock final = 0 en pruebas de concurrencia (nunca negativo)

**⚠️ Problemas:**
- p95 > 1000ms: Puede indicar contención de bloqueos
- Errores 500: Excepciones no manejadas
- Errores 503: Pool de conexiones agotado
- Stock negativo: Falla crítica en concurrencia

## Limitaciones Conocidas

Con una sola instancia y PostgreSQL local:
- ~50-100 peticiones/segundo sostenidas
- 50 usuarios concurrentes máximos antes de degradación
- Latencia adicional de ~50-100ms por bloqueos pesimistas bajo carga

Para producción se recomienda:
- Horizontal scaling (múltiples instancias)
- PostgreSQL dedicado con recursos suficientes
- Considerar cache (Redis) para lecturas frecuentes
- Balanceador de carga con health checks
