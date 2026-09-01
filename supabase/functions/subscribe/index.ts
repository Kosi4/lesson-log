import { createClient } from "npm:@supabase/supabase-js@2";

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

  const sub = await req.json();
  const endpoint = sub.endpoint;
  const p256dh = sub.keys?.p256dh;
  const auth = sub.keys?.auth;

  if (!endpoint || !p256dh || !auth) {
    return new Response(JSON.stringify({ error: "bad subscription" }), {
      status: 400,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  await supabase.from("push_subscriptions").upsert(
    { endpoint, p256dh, auth },
    { onConflict: "endpoint" }
  );

  // When the browser rotates a subscription it tells us which endpoint it
  // replaced. Drop that one, or it lingers as a live orphan and every nudge
  // gets sent to it as well as to the real one.
  if (typeof sub.oldEndpoint === "string" && sub.oldEndpoint && sub.oldEndpoint !== endpoint) {
    await supabase.from("push_subscriptions").delete().eq("endpoint", sub.oldEndpoint);
  }

  return new Response(JSON.stringify({ ok: true }), {
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
});
