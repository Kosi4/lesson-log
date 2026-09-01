import { createClient } from "npm:@supabase/supabase-js@2";
import { snoozeUntil } from "../_shared/rules.ts";

const supabase = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
);

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });

  const { date, session, action, note } = await req.json();

  if (
    !date ||
    !["morning", "evening"].includes(session) ||
    !["done", "snooze", "cancel"].includes(action)
  ) {
    return new Response(JSON.stringify({ error: "bad request" }), {
      status: 400,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  const statusField = `${session}_status`;
  const reminderField = `${session}_next_reminder`;
  const noteField = `${session}_note`;

  const update: Record<string, unknown> = {};
  if (action === "done") {
    update[statusField] = "done";
    update[reminderField] = null;
    if (typeof note === "string") update[noteField] = note;
  } else if (action === "cancel") {
    // "Cancel" the nag by admitting the session did not happen.
    update[statusField] = "incomplete";
    update[reminderField] = null;
  } else if (action === "snooze") {
    update[reminderField] = snoozeUntil(new Date());
  }

  await supabase.from("lesson_log").upsert({ log_date: date, ...update }, { onConflict: "log_date" });

  return new Response(JSON.stringify({ ok: true }), {
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
});
