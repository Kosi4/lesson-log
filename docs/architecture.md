# Architecture

## Why it looks like this

The whole app is four Supabase Edge Functions plus one Postgres table. There is
no Next.js app, no Vercel project, no build step, and no `node_modules`. That
was deliberate:

- **No separate web host.** The `app` function returns the HTML, JS, service
  worker, manifest and icon as string constants. One deploy target instead of two.
- **No auth flow.** It is a single-user app for one person's study log. Adding
  login would have been the largest piece of code in the project. See the
  limitation this creates in [operations.md](operations.md).
- **`pg_cron`, not Vercel Cron.** Vercel's Hobby tier fires cron jobs once a day
  at an approximate hour, which cannot express "11:10 and 20:00". `pg_cron` runs
  inside the database at real minute granularity and also drives snoozes.
- **A PWA, not a native app.** Android Chrome renders Web Push notifications with
  real action buttons, which is the entire point of the reminder. A native build
  would have meant Xcode/Play tooling and a reinstall for every change.

## Data flow

```
                        ┌──────────────────────────┐
   every 5 minutes ────►│ pg_cron                  │
                        │ check-reminders-every-…  │
                        └────────────┬─────────────┘
                                     │ net.http_post
                                     ▼
                     ┌───────────────────────────────┐
                     │ check-reminders               │
                     │  · seeds today's row          │
                     │  · dueSessions() from rules   │
                     │  · web push to every device   │
                     └───────────────┬───────────────┘
                                     │ Web Push (VAPID)
                                     ▼
                     ┌───────────────────────────────┐
                     │ sw.js on the phone            │
                     │  notification + 3 buttons     │
                     └───────────────┬───────────────┘
                                     │ POST
                                     ▼
   ┌──────────┐  fetch   ┌───────────────────────────┐  writes   ┌────────────┐
   │ app      │◄────────►│ session-action            │──────────►│ lesson_log │
   │ (PWA UI) │          │ done / snooze / cancel    │           └────────────┘
   └────┬─────┘          └───────────────────────────┘                 ▲
        │ reads via PostgREST ─────────────────────────────────────────┘
        │
        └── on load ──► subscribe ──► push_subscriptions
```

## The one piece of real logic

`supabase/functions/_shared/rules.ts` holds every decision about *when* to nag:

- `sastParts()` — the calendar date and weekday in Africa/Johannesburg. South
  Africa has no DST, so a fixed +2h offset is correct year round.
- `isStudyDay()` — Mon–Fri.
- `REMINDER_UTC` — `09:10Z` (11:10 SAST) and `18:00Z` (20:00 SAST).
- `SNOOZE_MINUTES` — 30.
- `dueSessions()` — a session is due when it is still `pending` **and** its
  `*_next_reminder` timestamp has passed.

It is deliberately free of Deno and Node APIs so the same file runs inside the
edge function and under `node --test`. Both `check-reminders` and
`session-action` import it, so the reminder time and the snooze length can never
drift apart between the sender and the responder.

## State machine for one session

```
pending ──(11:10 passes)──► push sent, *_next_reminder = null
   │                              │
   │                              ├─ "Snooze 30m"  (< 6 used)  → +30m → pending again
   │                              ├─ "Snooze 30m"  (6th spent) → incomplete, for good
   │                              ├─ "Mark done"               → done
   │                              └─ "Mark incomplete"         → incomplete
   │
   ├─ ticked in the app at any point → done / incomplete
   │        └─ "Edit" (same day only) → back to pending
   │
   └─ still pending when the date rolls over → incomplete, for good
```

Two things make a session final, and both go through `sessionsToFinalise()` in
`rules.ts`, called by `check-reminders` on every run:

1. **The day closed.** `isLocked()` is a pure date comparison against today in
   Johannesburg, so nothing has to run at exactly midnight for a day to become
   read-only — `session-action` rejects writes to past dates with HTTP 409.
2. **The snooze budget ran out.** Six snoozes, then the session is written off
   rather than nagged forever.

Non-study days are never written off: nothing was scheduled, so nothing was
missed.

`*_next_reminder = null` is what stops a notification repeating every 5 minutes.
It is only cleared **after** a push has actually been delivered to at least one
device — see the "silently burned reminder" note in
[operations.md](operations.md).
