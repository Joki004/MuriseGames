// Usage:
//   TOKEN="<supabase-jwt>" EVENT_ID="<event-uuid>" node scripts/rehearse-polling.mjs
const API_BASE = process.env.API_BASE || "http://localhost:8080";
const TOKEN = process.env.TOKEN;
const EVENT_ID = process.env.EVENT_ID;
const CLIENTS = 50;
const ROUNDS = 5; // 5 polls per client at 2s intervals = 250 requests

if (!TOKEN || !EVENT_ID) {
  console.error("Set TOKEN and EVENT_ID env vars first.");
  process.exit(1);
}

const latencies = [];
let errors = 0;

async function pollOnce() {
  const start = performance.now();
  try {
    const res = await fetch(`${API_BASE}/api/v1/events/${EVENT_ID}/snapshot`, {
      headers: { Authorization: `Bearer ${TOKEN}` },
    });
    if (!res.ok) errors++;
    await res.json();
  } catch {
    errors++;
  } finally {
    latencies.push(performance.now() - start);
  }
}

for (let r = 0; r < ROUNDS; r++) {
  await Promise.all(Array.from({ length: CLIENTS }, () => pollOnce()));
  if (r < ROUNDS - 1) await new Promise((res) => setTimeout(res, 2000));
}

latencies.sort((a, b) => a - b);
const p50 = latencies[Math.floor(latencies.length * 0.5)].toFixed(1);
const p95 = latencies[Math.floor(latencies.length * 0.95)].toFixed(1);
const max = latencies[latencies.length - 1].toFixed(1);

console.log(
  `50-client polling rehearsal (${latencies.length} reqs): p50=${p50}ms, p95=${p95}ms, max=${max}ms, errors=${errors}`
);