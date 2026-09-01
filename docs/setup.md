# Setup

## Hosting

The front end is the static files in `web/`. It cannot be served from the
Supabase `app` function: Supabase rewrites `text/html` to `text/plain` with
`nosniff` on `functions/v1` URLs (an anti-phishing measure that applies to no
other content type), so the page always arrives as source text — it cannot
render, install to a home screen, or register a service worker. The API
functions are unaffected and stay on Supabase.

To deploy, the Vercel account needs a GitHub Login Connection
(vercel.com → Settings → Login Connections → Connect GitHub). With that in
place the project links to `Kosi4/lesson-log` with **root directory `web`** and
redeploys automatically on every push.

Deploying the files directly instead produces an unclaimed deployment sitting
behind Vercel's SSO gate, which is unusable from a phone.

To preview locally without deploying:

```bash
python3 -m http.server 4173 --directory web
```

## Turning on notifications (do this once per device)

This is the step that makes the reminders real. Until a device is registered,
`push_subscriptions` is empty and nothing can be delivered.

1. On the **Android phone**, open the deployed app URL in Chrome.
2. Chrome menu → **Add to Home screen**. Launch it from the home screen icon
   from now on — an installed PWA keeps receiving push with the browser closed.
3. Tap **Enable notifications** in the yellow banner and accept the permission
   prompt.
4. Confirm it registered — this should return at least one row:

   ```bash
   curl -s 'https://eyacjldyjxojzpxslkzh.supabase.co/rest/v1/push_subscriptions?select=endpoint' -H "apikey: $SUPABASE_ANON_KEY"
   ```

Repeat on the laptop browser if desktop notifications are wanted too. Each
browser is a separate subscription; a nudge goes to all of them.

## Project details

| Thing | Value |
|---|---|
| Supabase project | `lesson-log` (`eyacjldyjxojzpxslkzh`), region `eu-west-1` |
| App URL | `https://eyacjldyjxojzpxslkzh.supabase.co/functions/v1/app` |
| Timezone | `Africa/Johannesburg` (UTC+2, no DST) |

## Secrets

The VAPID keypair lives in the `app_secrets` table, not in the repo:

| key | purpose |
|---|---|
| `vapid_public_key` | also hardcoded in `app.js` — public by design |
| `vapid_private_key` | signs pushes; **never** leaves the database |
| `vapid_subject` | `mailto:` contact required by the Web Push spec |

`app_secrets` has RLS enabled with **no policy**, so only the service role — the
edge functions — can read it. Do not add a policy to that table.

There is no `.env` file and nothing to configure locally.

## Deploying a change

Edge functions are deployed straight to Supabase. `check-reminders` and
`session-action` both import `../_shared/rules.ts`, so that file must be
included alongside the entrypoint when deploying either one.

Using the Supabase CLI:

```bash
supabase functions deploy check-reminders --project-ref eyacjldyjxojzpxslkzh
```

Always run the tests first — they are the only thing standing between a typo and
a reminder that never fires:

```bash
node --test supabase/functions/_shared/rules.test.ts
```

## Verifying a deploy without waiting for the clock

Check the function still runs at all:

```bash
curl -s -X POST https://eyacjldyjxojzpxslkzh.supabase.co/functions/v1/check-reminders
```

It answers `nothing due`, `sent`, `weekend, no nagging`, or
`due but nobody subscribed`.

Check a snooze moves the timestamp 30 minutes out, using a throwaway date such
as `2020-01-01`:

```bash
curl -s -X POST https://eyacjldyjxojzpxslkzh.supabase.co/functions/v1/session-action -H 'Content-Type: application/json' -d '{"date":"2020-01-01","session":"morning","action":"snooze"}'
```

Afterwards remove that scratch row from `lesson_log` so it never shows up in the
streak maths.
