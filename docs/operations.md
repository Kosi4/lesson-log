# Operations

## Changing the reminder times

Both times live in one place — `supabase/functions/_shared/rules.ts`:

```ts
export const REMINDER_UTC: Record<Session, string> = {
  morning: "09:10:00Z",  // 11:10 SAST
  evening: "18:00:00Z",  // 20:00 SAST
};
```

They are written in UTC because the edge runtime works in UTC; SAST is always
UTC+2. Update the constant, update the test that asserts on it, redeploy
`check-reminders` **and** `session-action` (both import this file).

A row for today may already exist with the old timestamps — the seeding upsert
deliberately does not overwrite an existing row, so bump today's row by hand if
the change should take effect the same day.

## Changing the snooze length

`SNOOZE_MINUTES` in the same file. Also update the button label in the `SW`
constant inside `supabase/functions/app/index.ts` — it is a hardcoded string
(`"Snooze 30m"`) because the service worker is a plain string, not compiled code.
The label and the constant will not warn you if they drift apart.

## Changing which days get nagged

`isStudyDay()` in `rules.ts`. Currently Mon–Fri (`dow >= 1 && dow <= 5`).
Saturday is the calendar's catch-up day and Sunday is off.

## The nudge did not arrive

Work down this list:

1. **Is a device registered?** `select count(*) from push_subscriptions;`
   If it is zero, nothing can be delivered — redo
   [setup.md](setup.md) on the phone. `check-reminders` will answer
   `due but nobody subscribed`.
2. **Is the reminder still armed?** `*_next_reminder` is set to `null` once a
   push is delivered. If it is null and the status is still `pending`, the
   notification was sent and simply not acted on.
3. **Is the cron alive?**
   `select * from cron.job_run_details order by start_time desc limit 10;`
4. **Did the function error?** Check the `check-reminders` logs in the Supabase
   dashboard, or `query_logs`. A push that fails with HTTP 410 means the browser
   revoked the subscription; the function prunes that row automatically and the
   app re-registers on next load.
5. **Is the phone's battery optimisation killing Chrome?** Android can suppress
   background push for aggressively optimised apps. Exclude Chrome if nudges are
   intermittent.

## Timing precision

`pg_cron` pokes `check-reminders` every 5 minutes, so a nudge lands somewhere in
the 5 minutes after 11:10 or 20:00, and a snooze expires within 5 minutes of its
30 minutes being up. Tighten the schedule to `* * * * *` if that ever matters —
the function returns early when nothing is due, so the extra runs are cheap.

## Duplicate push subscriptions

Every nudge is sent to every row in `push_subscriptions`, so orphaned rows mean
duplicate sends. The notification `tag` collapses them into one visible alert
per device, which is why this can pile up unnoticed — seven rows accumulated for
a single phone before anyone spotted it.

The browser mints a new subscription whenever it rotates one (service worker
updates, key changes). `sw.js` handles `pushsubscriptionchange`, sends the
replacement along with an `oldEndpoint`, and `subscribe` deletes that old row —
so rotations no longer leave orphans behind.

To check the current state, and drop any endpoint the push service has stopped
recognising:

```bash
curl -s -X POST "https://eyacjldyjxojzpxslkzh.supabase.co/functions/v1/prune-subscriptions?dry=1" -H "Authorization: Bearer $SUPABASE_ANON_KEY"
```

`dry=1` only reports. Without it, endpoints returning 404/410 are removed;
anything still live is kept. Note this sends a real notification to every live
device — that is the only way to tell a working endpoint from a dead one.

Clearing the table entirely is safe: the app re-registers the current device on
its next load. Nothing is delivered in the meantime, so reopen the app afterwards.

## Follow-up work

### Add authentication (agreed, not yet done)

`lesson_log` and `push_subscriptions` have RLS enabled but with a
`using (true) with check (true)` policy, and the anon key is embedded in
`app.js`. There is no login. In practice that means:

- Anyone who has the app URL can read the log, tick sessions, and register their
  own device to receive the nudges.
- The VAPID private key is **not** exposed — `app_secrets` has no policy at all,
  so only the edge functions can read it.

This is accepted for now but **is scheduled to be fixed**. The intended change:
Supabase magic-link auth, a `user_id` column on `lesson_log` and
`push_subscriptions`, and `user_id = auth.uid()` policies replacing the
`using (true)` ones. Contained, but it touches every function and the client, so
it is roughly the size of everything built so far. Until then, treat the app URL
as semi-private and do not post it publicly.

### Remove the one-off `prune-subscriptions` function

It was deployed to identify dead push endpoints and is not part of the running
system. It requires the anon key and only ever deletes endpoints the push
service has already rejected, but it is still an endpoint that can fire
notifications — delete it in the Supabase dashboard once it is no longer needed.

### Retire the superseded `app` edge function

`supabase/functions/app/` and its deployed counterpart served the UI before it
moved to `web/`. The two have since diverged, so the stale copy is a trap: an
edit there would look correct and change nothing users see. Drop the directory
from the repo and remove the `app` function in the Supabase dashboard.

### Remove the leftover `ctype-probe` storage bucket

An empty public storage bucket named `ctype-probe` was created while testing
whether Supabase Storage could host the front end, and holds one orphan metadata
row (`probe.html`) with no file behind it. Requests to it return HTTP 400.
Postgres blocks deleting storage rows directly, so it has to go via the Storage
API or the dashboard: Storage → `ctype-probe` → delete bucket. Harmless, just
untidy.

## Fixed defects worth remembering

- **Reminders were silently burned.** The original `check-reminders` cleared
  `*_next_reminder` after its send loop whether or not any push went out. With
  zero subscriptions that consumed the day's reminder every time, so the first
  nudge after registering a phone would never have arrived. It now only clears
  the timestamp once at least one device has actually received the push.
- **A pruned subscription never came back.** The client only posted its
  subscription on first grant. Since the server prunes dead endpoints on HTTP
  410, a pruned row left the app permanently silent while still looking enabled.
  It now re-posts the existing subscription on every load.
- **Times were 30 minutes after each session *started*** (10:30 and 18:00),
  not after it ended.
- **The snooze button said 15m** while the server snoozed for a different span.
