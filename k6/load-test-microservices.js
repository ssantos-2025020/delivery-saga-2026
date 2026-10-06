import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const errorRate = new Rate('errors');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
    stages: [
        { duration: '30s', target: 10 },
        { duration: '1m', target: 10 },
        { duration: '30s', target: 50 },
        { duration: '1m', target: 50 },
        { duration: '30s', target: 0 },
    ],
    thresholds: {
        http_req_duration: ['p(95)<500'],
        errors: ['rate<0.01'],
        http_req_failed: ['rate<0.01'],
    },
};

export function setup() {
    // Registrar usuario
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
    
    // Listar comercios
    const comerciosRes = http.get(`${BASE_URL}/api/v1/comercios?page=0&size=20`, { headers });
    check(comerciosRes, {
        'list comercios status 200': (r) => r.status === 200,
    }) || errorRate.add(1);
    
    sleep(Math.random() * 2);
    
    // Listar productos
    if (comerciosRes.status === 200 && comerciosRes.json('content').length > 0) {
        const comercioId = comerciosRes.json('content')[0].id;
        const productosRes = http.get(
            `${BASE_URL}/api/v1/comercios/${comercioId}/productos?page=0&size=20`,
            { headers }
        );
        check(productosRes, {
            'list productos status 200': (r) => r.status === 200,
        }) || errorRate.add(1);
    }
    
    sleep(Math.random() * 2);
    
    // Crear pedido
    if (comerciosRes.status === 200 && comerciosRes.json('content').length > 0) {
        const comercioId = comerciosRes.json('content')[0].id;
        const productosRes = http.get(
            `${BASE_URL}/api/v1/comercios/${comercioId}/productos?page=0&size=20`,
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
                    headers: headers,
                }
            );
            
            check(orderRes, {
                'create order status 200 or 409': (r) => r.status === 200 || r.status === 409,
                'create order not 500': (r) => r.status !== 500,
            }) || errorRate.add(1);
        }
    }
    
    sleep(Math.random() * 3);
    
    // Listar mis pedidos
    const myOrdersRes = http.get(`${BASE_URL}/api/v1/pedidos/mis-pedidos`, { headers });
    check(myOrdersRes, {
        'list my orders status 200': (r) => r.status === 200,
    }) || errorRate.add(1);
    
    sleep(Math.random() * 2);
}
