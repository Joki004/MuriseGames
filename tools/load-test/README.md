# Load and recovery test (GM-030 + GM-031)

One session, about 30 minutes, on a **test event** (never the real one).

## 1. Load: simulate 25 phones + the TV

The script only sends GET requests, at the same rhythm as the screens
(snapshot every 2 s, Alibi every 1.5 s, Social role card every 10 s, …).
It never changes the event.

**Get a token** (it lasts about 1 hour): open the app in Chrome, signed in as
any member of the test event, press F12 → Console, and run:

```js
copy(JSON.parse(localStorage.getItem(Object.keys(localStorage).find((k) => k.endsWith("-auth-token")))).access_token)
```

The token is now in your clipboard. Don't share it or commit it.

**Run** (PowerShell, from the repo root, Node 18+):

```powershell
$env:API_URL="https://murise-games-production.up.railway.app"
$env:EVENT_ID="<test event id from the URL>"
$env:TOKEN="<paste>"
$env:PLAYERS="25"; $env:MINUTES="15"
node tools/load-test/load-test.mjs
```

Every minute it prints requests per second, errors and latency per endpoint.
At the end: **PASS** if fewer than 1% errors (5xx, timeouts) and overall
p95 under 800 ms. 4xx answers (e.g. 403 on the captain-only guess board) are
expected and are not counted as errors.

**While it runs, watch:**
- Railway → service → Metrics: CPU and memory stable, no restart.
- Railway → Logs: no `ERROR`, no `Connection is not available` (Hikari).
- Supabase → Reports → Database: connections under the limit.

**If p95 is high but Railway CPU is low:**
1. Railway → service → Settings → Region: Supabase is in **eu-west-1
   (Ireland)**. If Railway runs in the US, move it to an **EU West** region;
   every query then stops crossing the Atlantic.
2. Still slow: raise `spring.datasource.hikari.maximum-pool-size` in
   `application-prod.yaml` from 5 to 10 (the Supabase pooler allows it).

## 2. Recovery: break things while the script runs

Use real phones for these, signed in as players of the test event.

- [ ] Refresh a phone during an Alibi question → same question, typed answer kept
- [ ] Refresh during guessing → guess form and clues come back
- [ ] Airplane mode 30 s, then back → the screen catches up by itself
- [ ] Double-tap "lock answer", "save result", "save star players" → saved once
- [ ] Close the TV tab and reopen → same state
- [ ] Assign a Social role to a player → their card appears within ~10 s
      without refreshing; players without a role never see the card
- [ ] Restart the API on Railway (Deployments → Restart) mid-game →
      screens show a short error, then recover within ~30 s; nothing lost

**PASS = the script says PASS and every box is ticked.** Note anything that
fails on the Trello card and fix it before Friday's rehearsal.
