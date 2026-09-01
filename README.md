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
- **Snooze 30m** — asks again in 30 minutes, up to **six times** (three hours of
  grace). When the sixth runs out the session goes down as incomplete for good.
- **Mark incomplete** — records that the session did not happen

## Everything closes at 23:59

A day is only editable while it is still that day, in Johannesburg time:

- Anything left unlogged when the date rolls over is **incomplete for good**.
- A session marked done drops off the dashboard immediately and moves to
  **Completed lessons** with its date and note. An **Edit** button can undo a
  mistaken tick, but only until 23:59 — after that it is locked.
- An incomplete session breaks the streak. Weekends do not: no sessions are
  scheduled, so there is nothing to miss and the run carries across them.

## Seeing the run

**Streaks** shows the current month as a grid — green when both sessions were
done, amber for one, red for a miss, muted for days with nothing scheduled.
Swipe left and right (or use the arrows) to page back through previous months.
Days before you started tracking are left blank rather than counted as misses.

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

`supabase/functions/app/` is the original single-file version of the UI, kept
only for reference. **It is superseded by `web/` and has diverged from it — do
not edit it.** Both it and its deployed copy can be cleared out whenever
convenient; see the follow-up note in [docs/operations.md](docs/operations.md).

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
