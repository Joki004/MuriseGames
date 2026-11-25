#!/usr/bin/env node
// GM-030 + GM-031: light load test. Simulates the phones and the TV of one event
// polling the API exactly like the screens do, then prints latency and errors.
//
// Read-only: it only sends GET requests, so it never changes the event.
//
//   API_URL=https://murise-games-production.up.railway.app \
//   EVENT_ID=<test event id> TOKEN=<access token> \
//   PLAYERS=25 MINUTES=15 node tools/load-test/load-test.mjs
//
// TOKEN: see README.md (one signed-in account is enough; tokens last ~1 hour).

const API_URL = (process.env.API_URL ?? "http://localhost:8080").replace(/\/$/, "");
const EVENT_ID = process.env.EVENT_ID;
const TOKEN = process.env.TOKEN;
const PLAYERS = Number(process.env.PLAYERS ?? 25);
const TVS = Number(process.env.TVS ?? 1);
const MINUTES = Number(process.env.MINUTES ?? 15);

if (!EVENT_ID || !TOKEN) {
  console.error("Set EVENT_ID and TOKEN (see tools/load-test/README.md).");
  process.exit(1);
}

const e = encodeURIComponent(EVENT_ID);
const PATHS = {
  snapshot: `/api/v1/events/${e}/snapshot`,
  alibiMe: `/api/events/${e}/alibi/me`,
  alibiLive: `/api/events/${e}/alibi/live`,
  socialRound1: `/api/events/${e}/social-cases/role-card?roundNumber=1`,
  socialRound2: `/api/events/${e}/social-cases/role-card?roundNumber=2`,
  teamResults: `/api/events/${e}/team-results`,
  reveals: `/api/events/${e}/finale/reveals`,
  clues: `/api/events/${e}/clues/released`,
  guesses: `/api/events/${e}/guesses`,
  scoreboard: `/api/events/${e}/scoreboard`,
  starPlayers: `/api/events/${e}/player-points/ranking`,
  roster: `/api/events/${e}/roster`,
};

// Same rhythm as the screens (see the POLL_MS / REFRESH_MS constants in the web app).
const PHONE = [
  ["snapshot", 2_000],
  ["alibiMe", 1_500],
  ["socialRound1", 10_000],
  ["socialRound2", 10_000],
  ["teamResults", 30_000],
  ["reveals", 20_000],
  ["guesses", 15_000],
  ["roster", 300_000],
];
const TV = [
  ["snapshot", 2_000],
  ["alibiLive", 2_000],
  ["reveals", 20_000],
  ["clues", 10_000],
  ["scoreboard", 10_000],
  ["starPlayers", 10_000],
  ["roster", 300_000],
];

const stats = new Map();
const statusCounts = new Map();
let inFlight = 0;
let maxInFlight = 0;

function record(name, ms, status) {
  const s = stats.get(name) ?? { times: [], errors: 0 };
  s.times.push(ms);
  if (status === 0 || status >= 500 || status === 429) s.errors++;
  stats.set(name, s);
  statusCounts.set(status, (statusCounts.get(status) ?? 0) + 1);
}

async function hit(name) {
  const started = performance.now();
  inFlight++;
  maxInFlight = Math.max(maxInFlight, inFlight);
  let status = 0;
  try {
    const res = await fetch(API_URL + PATHS[name], {
      headers: { Authorization: `Bearer ${TOKEN}`, Accept: "application/json" },
      signal: AbortSignal.timeout(15_000),
    });
    status = res.status;
    await res.arrayBuffer();
  } catch {
    status = 0; // network error or timeout
  } finally {
    inFlight--;
    record(name, performance.now() - started, status);
  }
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const endAt = Date.now() + MINUTES * 60_000;

async function loop(name, everyMs) {
  // Spread the clients over the first interval, like people opening the page.
  await sleep(Math.min(Math.random() * Math.min(everyMs, 10_000), Math.max(0, endAt - Date.now())));
  while (Date.now() < endAt) {
    const started = Date.now();
    await hit(name);
    const jitter = everyMs * (0.9 + Math.random() * 0.2);
    const wait = Math.max(0, jitter - (Date.now() - started));
    // Never sleep past the end of the test (the roster only refreshes every 5 min).
    await sleep(Math.min(wait, Math.max(0, endAt - Date.now())));
  }
}

function percentile(sorted, p) {
  if (sorted.length === 0) return 0;
  return sorted[Math.min(sorted.length - 1, Math.floor((p / 100) * sorted.length))];
}

function report(final) {
  const rows = [];
  let total = 0;
  let errors = 0;
  for (const [name, s] of [...stats.entries()].sort()) {
    const sorted = [...s.times].sort((a, b) => a - b);
    total += sorted.length;
    errors += s.errors;
    rows.push({
      endpoint: name,
      requests: sorted.length,
      errors: s.errors,
      p50_ms: Math.round(percentile(sorted, 50)),
      p95_ms: Math.round(percentile(sorted, 95)),
      max_ms: Math.round(sorted.at(-1) ?? 0),
    });
  }
  const elapsedS = (Date.now() - startedAt) / 1000;
  console.log(
    `\n${final ? "FINAL" : "Progress"} after ${Math.round(elapsedS)} s — ` +
      `${total} requests (${(total / elapsedS).toFixed(1)}/s), ${errors} errors, ` +
      `max ${maxInFlight} at once. Statuses: ${JSON.stringify(Object.fromEntries(statusCounts))}`,
  );
  console.table(rows);

  if (final) {
    const all = [...stats.values()].flatMap((s) => s.times).sort((a, b) => a - b);
    const p95 = percentile(all, 95);
    const errorRate = total === 0 ? 1 : errors / total;
    const pass = errorRate < 0.01 && p95 < 800;
    console.log(
      `\n${pass ? "PASS" : "FAIL"}: error rate ${(errorRate * 100).toFixed(2)}% ` +
        `(must be < 1%), overall p95 ${Math.round(p95)} ms (must be < 800 ms).`,
    );
    if (statusCounts.get(401)) {
      console.log("401s: the token expired or is wrong. Get a fresh one (README).");
    }
    process.exitCode = pass ? 0 : 1;
  }
}

const startedAt = Date.now();
console.log(
  `Simulating ${PLAYERS} phones + ${TVS} TV on ${API_URL} for ${MINUTES} min (GET only)…`,
);

const loops = [];
for (let i = 0; i < PLAYERS; i++) for (const [n, ms] of PHONE) loops.push(loop(n, ms));
for (let i = 0; i < TVS; i++) for (const [n, ms] of TV) loops.push(loop(n, ms));

const progress = setInterval(() => report(false), 60_000);
await Promise.all(loops);
clearInterval(progress);
report(true);
