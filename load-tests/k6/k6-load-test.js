import http from 'k6/http';
import { sleep, check } from 'k6';
import { randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

// ── Configuration ───────────────────────────────────────────────────────────────
// Can be overridden via environment variables: K6_VUS=50 K6_DURATION=10m k6 run ...
export const options = {
    vus: __ENV.VUS ? parseInt(__ENV.VUS) : 10,
    duration: __ENV.DURATION || '1m',
    thresholds: {
        http_req_failed: ['rate<0.01'],   // < 1% errors
        http_req_duration: ['p(95)<500'], // 95% of requests < 500ms
    },
};

// Default points to k3d/k8s Ingress host. Override via BASE_URL env var.
// Example: BASE_URL=http://localhost:8080 make load-test-docker
const BASE_URL = __ENV.BASE_URL || 'http://my-service.localhost';

export default function () {
    // 1. Choose a random scenario (weights: 40% / 40% / 20%)
    const rand = Math.random();

    if (rand < 0.4) {
        // Scenario A: View product list
        const res = http.get(`${BASE_URL}/products?page=0&size=20`);
        check(res, { 'status is 200 (list)': (r) => r.status === 200 });
    } 
    else if (rand < 0.8) {
        // Scenario B: View specific product
        const id = randomIntBetween(1, 1000);
        const res = http.get(`${BASE_URL}/products/${id}`);
        check(res, { 'status is 200 (get)': (r) => r.status === 200 });
    } 
    else {
        // Scenario C: Recommendations (heavy endpoint)
        const id = randomIntBetween(1, 1000);
        const res = http.get(`${BASE_URL}/products/${id}/recommendations`);
        check(res, { 'status is 200 (recs)': (r) => r.status === 200 });
    }

    // Small pause between user actions (0.5 - 2 sec)
    sleep(Math.random() * 1.5 + 0.5);
}
