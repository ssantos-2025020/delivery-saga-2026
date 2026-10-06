import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const errorRate = new Rate('errors');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
    scenarios: {
        concurrent_orders: {
            executor: 'constant-vus',
            vus: 50,  // 50 concurrent users
            duration: '30s',
            exec: 'createConcurrentOrders',
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<1000'],
        errors: ['rate<0.5'],  // Allow up to 50% errors (stock exhaustion)
        http_req_failed: ['rate<0.5'],
    },
};

export function setup() {
    // Register test user
    const registerRes = http.post(`${BASE_URL}/api/v1/auth/registro`, JSON.stringify({
        email: 'concurrency@example.com',
        password: 'password123',
        nombre: 'Concurrency Test'
    }), {
        headers: { 'Content-Type': 'application/json' },
    });
    
    let token = '';
    if (registerRes.status === 200) {
        token = registerRes.json('token');
    } else {
        const loginRes = http.post(`${BASE_URL}/api/v1/auth/login`, JSON.stringify({
            email: 'concurrency@example.com',
            password: 'password123'
        }), {
            headers: { 'Content-Type': 'application/json' },
        });
        token = loginRes.json('token');
    }
    
    // Get a product ID
    const headers = {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
    };
    
    const comerciosRes = http.get(`${BASE_URL}/api/v1/comercios?page=0&size=1`, { headers });
    let productoId = 1;
    
    if (comerciosRes.status === 200 && comerciosRes.json('content').length > 0) {
        const comercioId = comerciosRes.json('content')[0].id;
        const productosRes = http.get(
            `${BASE_URL}/api/v1/comercios/${comercioId}/productos?page=0&size=1`,
            { headers }
        );
        if (productosRes.status === 200 && productosRes.json('content').length > 0) {
            productoId = productosRes.json('content')[0].id;
        }
    }
    
    return { token, productoId };
}

export function createConcurrentOrders(data) {
    const headers = {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${data.token}`,
    };
    
    const orderRes = http.post(
        `${BASE_URL}/api/v1/pedidos`,
        JSON.stringify({
            items: [
                {
                    productoId: data.productoId,
                    cantidad: 1
                }
            ]
        }),
        {
            headers: {
                ...headers,
                'Idempotency-Key': `concurrency-${__VU}-${__ITER}`,
            },
        }
    );
    
    check(orderRes, {
        'order created or rejected': (r) => r.status === 200 || r.status === 409,
        'no server errors': (r) => r.status !== 500,
        'no timeout': (r) => r.status !== 503,
    }) || errorRate.add(1);
}
