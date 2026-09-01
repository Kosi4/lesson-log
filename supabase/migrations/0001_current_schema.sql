-- Reference snapshot of the live schema in Supabase project `eyacjldyjxojzpxslkzh`.
-- Already applied (migrations 20260827083557, 20260827083824, 20260829081601).
-- Kept in the repo so the schema is readable and reproducible, not to be re-run
-- against the existing project.

-- One row per day. Wide rather than normalised: exactly two sessions a day,
-- so a row-per-session table would buy nothing.
create table if not exists public.lesson_log (
  log_date              date primary key,
  morning_status        text not null default 'pending',  -- pending | done | incomplete
  evening_status        text not null default 'pending',
  morning_note          text,
  evening_note          text,
  morning_next_reminder timestamptz,  -- null = already delivered or resolved
  evening_next_reminder timestamptz,
  created_at            timestamptz not null default now()
);

create table if not exists public.push_subscriptions (
  id         uuid primary key default gen_random_uuid(),
  endpoint   text not null unique,
  p256dh     text not null,
  auth       text not null,
  created_at timestamptz not null default now()
);

-- VAPID keypair. RLS is on with NO policy, so only the service role (i.e. the
-- edge functions) can read it. Never expose this table to the anon key.
create table if not exists public.app_secrets (
  key   text primary key,
  value text not null
);

alter table public.lesson_log         enable row level security;
alter table public.push_subscriptions enable row level security;
alter table public.app_secrets        enable row level security;

-- NOTE: these are wide open to the anon key. See docs/operations.md
-- ("Known limitation: the app is unauthenticated") before treating this as private.
create policy "allow all lesson_log"         on public.lesson_log         for all using (true) with check (true);
create policy "allow all push_subscriptions" on public.push_subscriptions for all using (true) with check (true);

-- Scheduler: pokes check-reminders every 5 minutes. That function decides
-- whether anything is actually due.
select cron.schedule(
  'check-reminders-every-5-min',
  '*/5 * * * *',
  $$
  select net.http_post(
    url := 'https://eyacjldyjxojzpxslkzh.supabase.co/functions/v1/check-reminders',
    headers := '{"Content-Type": "application/json"}'::jsonb
  );
  $$
);
