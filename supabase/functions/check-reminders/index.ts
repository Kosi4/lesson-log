import webpush from "npm:web-push@3.6.7";
import { createClient } from "npm:@supabase/supabase-js@2";
import {
  defaultReminder,
  dueSessions,
  isStudyDay,
  MAX_SNOOZES,
  sastParts,
  sessionsToFinalise,
  snoozeCountOf,
  SNOOZE_MINUTES,
  type LogRow,
  type Session,
} from "../_shared/rules.ts";

const supabase = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
);

const LABEL: Record<Session, string> = {
  morning: "Morning session (Math)",
  evening: "Evening session (CS)",
};

/**
 * Write off anything still pending that has run out of road: days that have
 * rolled past midnight, and sessions whose six snoozes are spent. Looks back a
 * fortnight so a few days of downtime still get closed out properly.
 */
async function finaliseStale(now: Date) {
  const since = new Date(now.getTime() - 14 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
  const { data: rows } = await supabase
    .from("lesson_log")
    .select("*")
    .gte("log_date", since)
    .or("morning_status.eq.pending,evening_status.eq.pending");

  let closed = 0;
  for (const row of (rows ?? []) as LogRow[]) {
    const stale = sessionsToFinalise(row, now);
    if (stale.length === 0) continue;

    const update: Record<string, unknown> = {};
    for (const session of stale) {
      update[`${session}_status`] = "incomplete";
      update[`${session}_next_reminder`] = null;
    }
    await supabase.from("lesson_log").update(update).eq("log_date", row.log_date);
    closed += stale.length;
  }
  return closed;
}

Deno.serve(async () => {
  const now = new Date();
  const { date, dow } = sastParts(now);

  // Runs every day, weekend included: yesterday still needs closing out.
  const closed = await finaliseStale(now);

  if (!isStudyDay(dow)) {
    return new Response(`weekend, no nagging (closed ${closed})`, { status: 200 });
  }

  // Seed today's row with both reminder times. ignoreDuplicates keeps an
  // existing row (and any snooze or completed status on it) untouched.
  await supabase.from("lesson_log").upsert(
    {
      log_date: date,
      morning_next_reminder: defaultReminder(date, "morning"),
      evening_next_reminder: defaultReminder(date, "evening"),
    },
    { onConflict: "log_date", ignoreDuplicates: true }
  );

  const { data: row } = await supabase
    .from("lesson_log")
    .select("*")
    .eq("log_date", date)
    .single();
  if (!row) return new Response("no row", { status: 200 });

  const due = dueSessions(row as LogRow, now);
  if (due.length === 0) return new Response(`nothing due (closed ${closed})`, { status: 200 });

  const { data: subs } = await supabase.from("push_subscriptions").select("*");
  if (!subs || subs.length === 0) {
    // No device registered. Leave the reminder timestamps alone so the nudge
    // still lands once a phone subscribes, instead of being silently burned.
    console.warn("reminder due but no push subscriptions registered", { date, due });
    return new Response("due but nobody subscribed", { status: 200 });
  }

  const { data: secrets } = await supabase.from("app_secrets").select("key,value");
  const map: Record<string, string> = {};
  for (const s of (secrets ?? []) as { key: string; value: string }[]) map[s.key] = s.value;
  webpush.setVapidDetails(map.vapid_subject, map.vapid_public_key, map.vapid_private_key);

  for (const session of due) {
    const left = MAX_SNOOZES - snoozeCountOf(row as LogRow, session);
    const payload = JSON.stringify({
      title: `${LABEL[session]} not logged`,
      body:
        left > 0
          ? `Tick it off, snooze ${SNOOZE_MINUTES} min (${left} left), or mark incomplete.`
          : `Last call — snoozes are used up. Tick it off or it goes down as incomplete.`,
      session,
      date,
      snoozesLeft: left,
    });

    let delivered = 0;
    for (const sub of subs) {
      try {
        await webpush.sendNotification(
          { endpoint: sub.endpoint, keys: { p256dh: sub.p256dh, auth: sub.auth } },
          payload,
          {
            // Expire the push rather than letting a stale nag arrive tomorrow.
            TTL: 4 * 60 * 60,
            // Android defers normal-urgency pushes while the device is dozing,
            // so the nudge only surfaces when the app is next opened — useless
            // for a reminder. High urgency asks to wake the device instead.
            urgency: "high",
          }
        );
        delivered++;
      } catch (err) {
        console.error("push failed", err);
        if ((err as { statusCode?: number }).statusCode === 410) {
          await supabase.from("push_subscriptions").delete().eq("id", sub.id);
        }
      }
    }

    // Only consume the reminder once it actually reached a device.
    if (delivered > 0) {
      await supabase
        .from("lesson_log")
        .update({ [`${session}_next_reminder`]: null })
        .eq("log_date", date);
    }
  }

  return new Response(`sent (closed ${closed})`, { status: 200 });
});
