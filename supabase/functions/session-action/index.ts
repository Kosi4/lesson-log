import { createClient } from "npm:@supabase/supabase-js@2";
import {
  isLocked,
  MAX_SNOOZES,
  snoozeCountOf,
  snoozeUntil,
  type LogRow,
  type Session,
} from "../_shared/rules.ts";

const supabase = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
);

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

const ACTIONS = ["done", "snooze", "cancel", "edit"];

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });

  const { date, session, action, note } = await req.json();

  if (!date || !["morning", "evening"].includes(session) || !ACTIONS.includes(action)) {
    return json({ error: "bad request" }, 400);
  }

  const now = new Date();

  // Past days are final. Nothing can be ticked, untricked, or snoozed on them.
  if (isLocked(date, now)) {
    return json({ error: "locked", message: "That day is closed and can no longer be changed." }, 409);
  }

  const { data: existing } = await supabase
    .from("lesson_log")
    .select("*")
    .eq("log_date", date)
    .maybeSingle();

  const statusField = `${session}_status`;
  const reminderField = `${session}_next_reminder`;
  const noteField = `${session}_note`;
  const snoozeField = `${session}_snooze_count`;

  const update: Record<string, unknown> = {};

  if (action === "done") {
    update[statusField] = "done";
    update[reminderField] = null;
    if (typeof note === "string") update[noteField] = note;
  } else if (action === "cancel") {
    // "Cancel" the nag by admitting the session did not happen.
    update[statusField] = "incomplete";
    update[reminderField] = null;
  } else if (action === "edit") {
    // Undo a mistaken tick. Only possible on the current day; the reminder is
    // deliberately not re-armed, so it must be re-marked before the day closes.
    update[statusField] = "pending";
    update[reminderField] = null;
  } else if (action === "snooze") {
    const used = snoozeCountOf((existing ?? {}) as LogRow, session as Session);
    if (used >= MAX_SNOOZES) {
      // The grace period is spent — write it off rather than nag again.
      update[statusField] = "incomplete";
      update[reminderField] = null;
    } else {
      update[snoozeField] = used + 1;
      update[reminderField] = snoozeUntil(now);
    }
  }

  await supabase.from("lesson_log").upsert({ log_date: date, ...update }, { onConflict: "log_date" });

  const { data: row } = await supabase
    .from("lesson_log")
    .select("*")
    .eq("log_date", date)
    .maybeSingle();

  return json({
    ok: true,
    status: row?.[statusField] ?? null,
    snoozesLeft: Math.max(0, MAX_SNOOZES - (row?.[snoozeField] ?? 0)),
  });
});
