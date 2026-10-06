import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const errorRate = new Rate('errors');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
    stages: [
        { duration: '30s', target: 10 },   // Ramp up to 10 users
        { duration: '1m', target: 10 },    // Stay at 10 users
        { duration: '30s', target: 50 },   // Ramp up to 50 users
        { duration: '1m', target: 50 },    // Stay at 50 users
        { duration: '30s', target: 0 },    // Ramp down to 0
    ],
    thresholds: {
        http_req_duration: ['p(95)<500'],  // 95% of requests must complete below 500ms
        errors: ['rate<0.01'],              // Error rate must be less than 1%
        http_req_failed: ['rate<0.01'],    // Failed requests must be less than 1%
    },
};

export function setup() {
    // Register a test user for login tests
    const registerRes = http.post(`${BASE_URL}/api/v1/auth/registro`, JSON.stringify({
        email: 'loadtest@example.com',
        password: 'password123',
        nombre: 'Load Test User'
    }), {
        headers: { 'Content-Type': 'application/json' },
    });
    
    let token = '';
    if (registerRes.status === 200) {
        token = registerRes.json('token');
    } else {
        // If already exists, login
        const loginRes = http.post(`${BASE_URL}/api/v1/auth/login`, JSON.stringify({
            email: 'loadtest@example.com',
            password: 'password123'
        }), {
            headers: { 'Content-Type': 'application/json' },
        });
        token = loginRes.json('token');
    }
    
    return { token };
}

export default function (data) {
    const headers = {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${data.token}`,
    };
    
    // Scenario 1: List comercios (read-heavy operation)
    const comerciosRes = http.get(`${BASE_URL}/api/v1/comercios?page=0&size=20`, { headers });
    check(comerciosRes, {
        'list comercios status 200': (r) => r.status === 200,
        'list comercios has content': (r) => r.json('content') !== undefined,
    }) || errorRate.add(1);
    
    sleep(Math.random() * 2);
    
    // Scenario 2: List products from first comercio
    if (comerciosRes.status === 200 && comerciosRes.json('content').length > 0) {
        const comercioId = comerciosRes.json('content')[0].id;
        const productosRes = http.get(
            `${BASE_URL}/api/v1/comercios/${comercioId}/productos?page=0&size=20&soloDisponibles=true`,
            { headers }
        );
        check(productosRes, {
            'list productos status 200': (r) => r.status === 200,
        }) || errorRate.add(1);
    }
    
    sleep(Math.random() * 2);
    
    // Scenario 3: Create order (write operation with locking)
    if (comerciosRes.status === 200 && comerciosRes.json('content').length > 0) {
        const comercioId = comerciosRes.json('content')[0].id;
        const productosRes = http.get(
            `${BASE_URL}/api/v1/comercios/${comercioId}/productos?page=0&size=20&soloDisponibles=true`,
            { headers }
        );
        
        if (productosRes.status === 200 && productosRes.json('content').length > 0) {
            const productoId = productosRes.json('content')[0].id;
            const orderRes = http.post(
                `${BASE_URL}/api/v1/pedidos`,
                JSON.stringify({
                    items: [
                        {
                            productoId: productoId,
                            cantidad: 1
                        }
                    ]
                }),
                {
                    headers: {
                        ...headers,
                        'Idempotency-Key': `k6-test-${__VU}-${__ITER}`,
                    },
                }
            );
            
            check(orderRes, {
                'create order status 200 or 409': (r) => r.status === 200 || r.status === 409,
                'create order not 500': (r) => r.status !== 500,
            }) || errorRate.add(1);
        }
    }
    
    sleep(Math.random() * 3);
    
    // Scenario 4: List my orders
    const myOrdersRes = http.get(`${BASE_URL}/api/v1/pedidos/mis-pedidos?page=0&size=20`, { headers });
    check(myOrdersRes, {
        'list my orders status 200': (r) => r.status === 200,
    }) || errorRate.add(1);
    
    sleep(Math.random() * 2);
}

export function teardown(data) {
    // Cleanup could be added here if needed
}
