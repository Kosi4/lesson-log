# Lesson Log

A personal daily check-in for the two study sessions on my calendar. Tick each
session off, jot what I covered, and get nudged if a session goes unlogged —
so consistency is measurable instead of a feeling.

**Live app:** https://lesson-log-indol.vercel.app

The UI cannot be served from Supabase: Supabase rewrites `text/html` to
`text/plain` on `functions/v1` URLs, so the page arrives as source text and can
neither install nor register a service worker. The front end therefore lives in
`web/` and is hosted on Vercel, which serves it as real HTML. The four API
functions stay on Supabase and are unaffected.

Open the URL on the phone and on the laptop. Both talk to the same
database, so a tick on one shows up on the other. On Android, add it to the home
screen (Chrome menu → *Add to Home screen*) so it launches like an app and can
receive notifications with the browser closed.

## The daily loop

| Session | Study block | Nudge if unlogged |
|---|---|---|
| Morning | Math — Precalc → Calc 1, 10:00–10:40 | **11:10** |
| Evening | CS — CS50 → Odin → Build, 17:30–19:30 | **20:00** |

Mon–Fri only. Saturday is the catch-up day and Sunday is off, so neither nags.

The notification carries three buttons:

- **Mark done** — logs it, no need to open the app
- **Snooze 30m** — asks again in 30 minutes
- **Mark incomplete** — records that the session did not happen

Marking incomplete is deliberate: a blank day is just missing data, an
*incomplete* is an honest miss. Only explicit misses count against the streak.

## How it fits together

Everything runs inside one Supabase project — no separate web host.

```
pg_cron (every 5 min)
    └─> check-reminders  ── decides what's due, sends Web Push
                                    └─> service worker on the phone
                                            └─> session-action ── writes the log
app  ── serves the PWA (HTML + app.js + sw.js + manifest + icon)
subscribe ── stores the device's push subscription
```

- `web/` — the front end as plain static files. This is what gets deployed.
- `supabase/functions/_shared/rules.ts` — the only place reminder times, snooze
  length and the weekday rule live. Change them there.
- `supabase/functions/*/index.ts` — the four edge functions.
- `supabase/migrations/0001_current_schema.sql` — reference copy of the live schema.

`web/` is generated from the string constants inside
`supabase/functions/app/index.ts`, which is the original single-file version of
the UI. That function is now superseded and only kept as the source of truth for
the markup.

## Docs

- [docs/architecture.md](docs/architecture.md) — data flow and why it's built this way
- [docs/setup.md](docs/setup.md) — deploying, secrets, first-time device setup
- [docs/operations.md](docs/operations.md) — changing times, debugging a missed nudge, known limitations

## Tests

```bash
node --test supabase/functions/_shared/rules.test.ts
```

Covers the reminder rules: due at 11:10 but not 11:09, never re-firing once
logged, snooze pushing it out 30 minutes, weekends staying silent, and the
SAST date rolling over two hours ahead of UTC.
