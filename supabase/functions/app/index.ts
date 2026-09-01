const HTML = `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8" />
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover" />
<title>Lesson Log</title>
<link rel="manifest" href="./manifest.json" />
<meta name="theme-color" content="#111111" />
<style>
  * { box-sizing: border-box; }
  body {
    margin: 0;
    font-family: -apple-system, system-ui, sans-serif;
    background: #f4f3ef;
    color: #1a1a1a;
    padding: 20px 16px 40px;
  }
  h1 { font-size: 20px; font-weight: 600; margin: 4px 0 20px; }
  .stats { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; margin-bottom: 20px; }
  .stat { background: #fff; border-radius: 10px; padding: 12px; }
  .stat p:first-child { font-size: 12px; color: #666; margin: 0 0 4px; }
  .stat p:last-child { font-size: 20px; font-weight: 600; margin: 0; }
  .card { background: #fff; border-radius: 12px; padding: 16px; margin-bottom: 14px; }
  .card h2 { font-size: 15px; font-weight: 600; margin: 0 0 2px; }
  .card .when { font-size: 12px; color: #888; margin: 0 0 10px; }
  .card input[type=text] { width: 100%; padding: 10px; border: 1px solid #ddd; border-radius: 8px; font-size: 14px; margin-bottom: 10px; }
  .row { display: flex; gap: 8px; }
  button { flex: 1; padding: 10px; border-radius: 8px; border: 1px solid #ccc; background: #fff; font-size: 13px; font-weight: 500; }
  button.primary { background: #1a1a1a; color: #fff; border-color: #1a1a1a; }
  .push-banner { background: #fff3d6; border-radius: 10px; padding: 12px 14px; margin-bottom: 16px; font-size: 13px; display: none; }
  .push-banner button { margin-top: 8px; }
  .log-row { display: flex; align-items: center; gap: 10px; padding: 8px 0; border-bottom: 1px solid #eee; font-size: 13px; }
  .log-row:last-child { border-bottom: none; }
  .dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
  .list-header { font-size: 12px; color: #666; margin: 20px 0 8px; }
</style>
</head>
<body>
  <h1>Lesson log</h1>
  <div id="push-banner" class="push-banner">
    Notifications aren't enabled yet - turn them on to get nudged if a session goes unlogged.
    <button id="enable-push" class="primary">Enable notifications</button>
  </div>
  <div class="stats">
    <div class="stat"><p>Streak</p><p id="streak">0</p></div>
    <div class="stat"><p>Last 14 days</p><p id="week">0/28</p></div>
    <div class="stat"><p>Total done</p><p id="total">0</p></div>
  </div>

  <div class="card">
    <h2>Morning session</h2>
    <p class="when">Math &mdash; 10:00, nudge at 11:10</p>
    <input type="text" id="morning-note" placeholder="What did you cover? (optional)" />
    <div class="row">
      <button id="morning-done" class="primary">Mark done</button>
      <button id="morning-cancel">Mark incomplete</button>
    </div>
  </div>

  <div class="card">
    <h2>Evening session</h2>
    <p class="when">CS &mdash; 17:30, nudge at 20:00</p>
    <input type="text" id="evening-note" placeholder="What did you cover? (optional)" />
    <div class="row">
      <button id="evening-done" class="primary">Mark done</button>
      <button id="evening-cancel">Mark incomplete</button>
    </div>
  </div>

  <p class="list-header">Last 14 days</p>
  <div class="card" id="log-list" style="padding: 8px 16px;"></div>

  <script src="./app.js"></script>
</body>
</html>
`;

const MANIFEST = `{
  "name": "Lesson Log",
  "short_name": "Lesson Log",
  "start_url": "./",
  "scope": "./",
  "display": "standalone",
  "background_color": "#f4f3ef",
  "theme_color": "#111111",
  "icons": [
    { "src": "./icon.png", "sizes": "512x512", "type": "image/png", "purpose": "any maskable" }
  ]
}
`;

const SW = `const SESSION_ACTION_URL = self.location.origin + "/functions/v1/session-action";

self.addEventListener("install", () => {
  self.skipWaiting();
});

self.addEventListener("activate", (e) => {
  e.waitUntil(self.clients.claim());
});

self.addEventListener("push", (event) => {
  const data = event.data ? event.data.json() : {};
  const title = data.title || "Lesson log";
  const options = {
    body: data.body || "",
    icon: "./icon.png",
    badge: "./icon.png",
    tag: "session-" + data.session + "-" + data.date,
    data: { session: data.session, date: data.date },
    actions: [
      { action: "done", title: "Mark done" },
      { action: "snooze", title: "Snooze 30m" },
      { action: "cancel", title: "Mark incomplete" },
    ],
  };
  event.waitUntil(self.registration.showNotification(title, options));
});

self.addEventListener("notificationclick", (event) => {
  const notification = event.notification;
  const info = notification.data || {};
  notification.close();

  if (event.action === "done" || event.action === "snooze" || event.action === "cancel") {
    event.waitUntil(
      fetch(SESSION_ACTION_URL, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ date: info.date, session: info.session, action: event.action }),
      }).catch(() => {})
    );
    return;
  }

  event.waitUntil(
    self.clients.matchAll({ type: "window" }).then((clients) => {
      for (const client of clients) {
        if ("focus" in client) return client.focus();
      }
      if (self.clients.openWindow) return self.clients.openWindow("./");
    })
  );
});
`;

const APPJS = `const SUPABASE_URL = "https://eyacjldyjxojzpxslkzh.supabase.co";
const SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImV5YWNqbGR5anhvanpweHNsa3poIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODc4MTQ2MzEsImV4cCI6MjEwMzM5MDYzMX0.lCRT_PwcR-BcoT8_PkOWoK_YgJjHiTP5pl5x_WIkXBY";
const VAPID_PUBLIC_KEY = "BOAxw8b0qB0y7CQkeASAhKW_ShXYsevkpe_z9kapldr5N28Kn_hkiBM_ukPboqzvkJAGQ9H29qeU3jVqVyn3nFI";
const REST = SUPABASE_URL + "/rest/v1";
const SESSION_ACTION_URL = SUPABASE_URL + "/functions/v1/session-action";
const SUBSCRIBE_URL = SUPABASE_URL + "/functions/v1/subscribe";

const headers = {
  apikey: SUPABASE_ANON_KEY,
  Authorization: "Bearer " + SUPABASE_ANON_KEY,
  "Content-Type": "application/json",
};

function todaySAST() {
  const now = new Date();
  const sast = new Date(now.getTime() + 2 * 60 * 60 * 1000);
  return sast.toISOString().slice(0, 10);
}

function dateOffsetSAST(offset) {
  const now = new Date();
  const sast = new Date(now.getTime() + 2 * 60 * 60 * 1000);
  sast.setUTCDate(sast.getUTCDate() - offset);
  return sast.toISOString().slice(0, 10);
}

async function fetchLastNDays(n) {
  const oldest = dateOffsetSAST(n - 1);
  const res = await fetch(
    REST + "/lesson_log?log_date=gte." + oldest + "&order=log_date.desc",
    { headers }
  );
  return res.json();
}

async function upsertToday(session, action, note) {
  const date = todaySAST();
  await fetch(SESSION_ACTION_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ date, session, action, note }),
  });
}

function computeStats(rows) {
  let streak = 0;
  const byDate = {};
  rows.forEach((r) => (byDate[r.log_date] = r));
  let offset = 0;
  const today = todaySAST();
  while (true) {
    const d = dateOffsetSAST(offset);
    const row = byDate[d];
    const bothDone = row && row.morning_status === "done" && row.evening_status === "done";
    const bothResolved = row && row.morning_status !== "pending" && row.evening_status !== "pending";
    if (d === today && !bothResolved) {
      offset++;
      continue;
    }
    if (bothDone) {
      streak++;
      offset++;
    } else {
      break;
    }
  }

  let weekDone = 0;
  for (let i = 0; i < 14; i++) {
    const row = byDate[dateOffsetSAST(i)];
    if (row) {
      if (row.morning_status === "done") weekDone++;
      if (row.evening_status === "done") weekDone++;
    }
  }

  let total = 0;
  rows.forEach((r) => {
    if (r.morning_status === "done") total++;
    if (r.evening_status === "done") total++;
  });

  return { streak, weekDone, total };
}

function render(rows) {
  const stats = computeStats(rows);
  document.getElementById("streak").textContent = stats.streak;
  document.getElementById("week").textContent = stats.weekDone + "/28";
  document.getElementById("total").textContent = stats.total;

  const byDate = {};
  rows.forEach((r) => (byDate[r.log_date] = r));
  const today = todaySAST();
  const todayRow = byDate[today];

  ["morning", "evening"].forEach((session) => {
    const status = todayRow ? todayRow[session + "_status"] : "pending";
    const doneBtn = document.getElementById(session + "-done");
    const cancelBtn = document.getElementById(session + "-cancel");
    const noteInput = document.getElementById(session + "-note");
    if (todayRow && todayRow[session + "_note"]) noteInput.value = todayRow[session + "_note"];
    doneBtn.textContent = status === "done" ? "Done" : "Mark done";
    cancelBtn.textContent = status === "incomplete" ? "Marked incomplete" : "Mark incomplete";
  });

  const list = document.getElementById("log-list");
  list.innerHTML = "";
  for (let i = 0; i < 14; i++) {
    const d = dateOffsetSAST(i);
    const row = byDate[d];
    const div = document.createElement("div");
    div.className = "log-row";
    const label = i === 0 ? "Today" : i === 1 ? "Yesterday" : d;
    const m = row ? row.morning_status : "pending";
    const e = row ? row.evening_status : "pending";
    const color = (s) => (s === "done" ? "#1D9E75" : s === "incomplete" ? "#D85A30" : "#ccc");
    div.innerHTML =
      '<span class="dot" style="background:' + color(m) + '"></span>' +
      '<span class="dot" style="background:' + color(e) + '"></span>' +
      "<span>" + label + "</span>";
    list.appendChild(div);
  }
}

async function refresh() {
  const rows = await fetchLastNDays(14);
  render(rows);
}

document.getElementById("morning-done").addEventListener("click", async () => {
  await upsertToday("morning", "done", document.getElementById("morning-note").value.trim());
  refresh();
});
document.getElementById("morning-cancel").addEventListener("click", async () => {
  await upsertToday("morning", "cancel");
  refresh();
});
document.getElementById("evening-done").addEventListener("click", async () => {
  await upsertToday("evening", "done", document.getElementById("evening-note").value.trim());
  refresh();
});
document.getElementById("evening-cancel").addEventListener("click", async () => {
  await upsertToday("evening", "cancel");
  refresh();
});

function urlBase64ToUint8Array(base64String) {
  const padding = "=".repeat((4 - (base64String.length % 4)) % 4);
  const base64 = (base64String + padding).replace(/-/g, "+").replace(/_/g, "/");
  const rawData = atob(base64);
  const outputArray = new Uint8Array(rawData.length);
  for (let i = 0; i < rawData.length; ++i) outputArray[i] = rawData.charCodeAt(i);
  return outputArray;
}

async function saveSubscription(sub) {
  await fetch(SUBSCRIBE_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(sub),
  });
}

async function setupPush() {
  if (!("serviceWorker" in navigator) || !("PushManager" in window)) return;
  const reg = await navigator.serviceWorker.register("./sw.js");
  await navigator.serviceWorker.ready;

  const existing = await reg.pushManager.getSubscription();
  if (existing) {
    // Re-send every load: the server prunes dead endpoints, and without this a
    // pruned row would never come back and the nudges would go quiet forever.
    await saveSubscription(existing);
    return;
  }

  document.getElementById("push-banner").style.display = "block";
  document.getElementById("enable-push").addEventListener("click", async () => {
    const permission = await Notification.requestPermission();
    if (permission !== "granted") return;
    const sub = await reg.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: urlBase64ToUint8Array(VAPID_PUBLIC_KEY),
    });
    await saveSubscription(sub);
    document.getElementById("push-banner").style.display = "none";
  });
}

refresh();
setupPush();
`;

const ICON_B64 = "iVBORw0KGgoAAAANSUhEUgAAAgAAAAIACAIAAAB7GkOtAAAKL0lEQVR4nO3dQXLTWBRA0aSLVXgB9ID9L4UBrKYH6QohThxblqWvf8+ZU6XS+7wriVR4Pp1OTwD0/LP3BQCwDwEAiBIAgCgBAIgSAIAoAQCIEgCAKAEAiBIAgCgBAIgSAIAoAQCIEgCAKAEAiBIAgCgBAIgSAIAoAQCIEgCAKAEAiBIAgCgBAIgSAIAoAQCIEgCAKAEAiBIAgCgBAIgSAIAoAQCIEgCAKAEAiBIAgCgBAIgSAIAoAQCIEgCAKAEAiBIAgCgBAIgSAIAoAQCIEgCAKAEAiBIAgCgBAIgSAIAoAQCIEgCAqG97XwA83O9fP5f9we///lj3SmAoz6fTae9rgHUsXvS3EgbmIAAc2GYb/zI94KAEgIMZZOl/Rgw4EAHgGAbf++eUgPEJAEM73N4/pwQMSwAY0QR7/5wSMBoBYCxTrv63ZIBxCABDmH7vn1MCdicA7Cy4+t+SAXYkAOwmvvrfkgF2IQDswOr/kAywMQFgU1b/l2SAzQgAG7H6ryIDbMCvg2YLtv+t3DE24A2Ax7LI7uRVgMcRAB7F6l+RDPAIPgHxELb/utxPHkEAWJ9t9QjuKqvzCYg1WVIb8DmItXgDYDW2/zbcZ9YiAKzDVtqSu80qfALiXpbRjnwO4h7eALiL7b8v9597CADL2T4jMAUWEwAWsnfGYRYsIwAsYeOMxkRYQAC4mV0zJnPhVgLAbWyZkZkONxEAbmC/jM+MuJ4AcC2b5ShMiisJAFexU47FvLiGAPA12+SITI0vCQBfsEeOy+y4TAC4xAY5OhPkAgHgU3bHHMyRzwgAH7M1ZmKafEgA+IB9MR8z5ZwAAEQJAO95VJyVyfKOAPAXO2Ju5stbAsAftkOBKfNKAACiBID/eTDsMGteCABPTzZCj4nzJAA82QVV5o4AAEQJQJ3HwDLTjxMAgCgBSPMAiDNQJgBd/ubzwknIEgCAKAGI8tDHW85DkwAARAlAkcc9zjkVQQIAECUAOR70+IyzUSMAAFEC0OIRj8uckBQBAIgSgBAPd1zDOekQAIAoAQCIEoAK7/Vcz2mJEACAKAFI8EDHrZyZAgEAiBIAgCgBmJ93eZZxcqYnAABRAgAQJQCT8xbPPZyfuQkAQJQAAEQJwMy8v3M/p2hiAgAQJQAAUQIAECUA0/LplrU4S7MSAIAoAQCIEgCAKAEAiBKAOflXO9blRE1JAACiBAAgSgAAogQAIEoAAKIEACBKAACiBGBCfmSbR3Cu5iMAAFECABAlAABRAgAQJQAAUQIAECUAAFECABAlAABRAgAQJQAAUQIAECUAAFECABAlAABRAgAQJQAAUQIAECUAAFECABAlAABRAgAQJQAAUQIAECUAAFECABAlAABRAgAQJQAAUQIAECUAAFECABAlAABRAgAQJQAAUQIAECUAAFECABAlAABRAgAQJQAAUQIAECUAAFECABAlAABRAgAQJQAAUQIAECUAE/r+74+9L4EJOVfzEQCAKAEAiBIAgCgBAIgSAIAoAQCIEgCAKAGYkx/ZZl1O1JQEACBKAACiBAAgSgAAogRgWv7VjrU4S7MSAIAoAQCIEgCAKAGYmU+33M8pmpgAAEQJAECUAEzO+zv3cH7mJgAAUQIAECUA8/MWzzJOzvQEACBKAACiBCDBuzy3cmYKBAAgSgAqPNBxPaclQgAAogQAIEoAQrzXcw3npEMAAKIEoMXDHZc5ISkCABAlADke8fiMs1EjAABRAlDkQY9zTkWQAABECUCUxz3ech6aBAAgSgC6PPTxwknIEoA0f/NxBsoEACBKAOo8AJaZfpwAAEQJAB4Do8wdAeDpyS7oMXGeBIBXNkKHWfNCAACiBIA/PBgWmDKvBIC/2A5zM1/eEgDesyNmZbK8IwAAUQLABzwqzsdMOScAfMy+mIlp8iEB4FO2xhzMkc8IAJfYHUdnglwgAHzBBjkus+MyAeBr9sgRmRpfEgCuYpsci3lxDQHgWnbKUZgUVxIAbmCzjM+MuJ4AcBv7ZWSmw00EgJvZMmMyF24lACxh14zGRFhAAFjIxhmHWbCMALCcvTMCU2AxAeAuts++3H/u8Xw6nfa+Bmbw+9fPvS+hxernft4AWId9tCV3m1UIAKuxlbbhPrMWn4BYn89BD2L1sy5vAKzPnnoEd5XVCQAPYVuty/3kEXwC4rF8DrqT1c/jCABbkIEFrH4ezScgtmCX3codYwPeANiUV4EvWf1sRgDYgQx8yOpnYwLAbmTgldXPLgSAncUzYPWzIwFgCMEMWP3sTgAYy/QlsPcZhwAwoikzYPUzGgFgaBOUwN5nWALAMRyuBPY+4xMADmbwEtj7HIgAcGCDxMDS56AEgHls1gMbnzkIAPNbHAaLnrkJAECUXwcNECUAAFECABAlAABRAgAQJQAAUQIAECUAAFECABAlAABRAgAQJQAAUQIAECUAAFECABAlAABRAgAQJQAAUQIAECUAAFECABAlAABRAgAQJQAAUQIAECUAAFECABAlAABRAgAQJQAAUQIAECUAAFECABAlAABRAgAQJQAAUQIAECUAAFECABAlAABRAgAQJQAAUQIAECUAAFECABAlAABRAgAQJQAAUQIAECUAAFECABAlAABRAgAQJQAAUQIAECUAAFECABAlAABRAgAQJQAAUQIAECUAAFHPp9Np72sAYAfeAACiBAAgSgAAogQAIEoAAKIEACBKAACiBAAgSgAAogQAIEoAAKIEACBKAACiBAAgSgAAogQAIEoAAKIEACBKAACiBAAgSgAAogQAIEoAAKIEACBKAACiBAAgSgAAogQAIEoAAKIEACBKAACiBAAgSgAAogQAIEoAAKIEACBKAACiBAAgSgAAogQAIEoAAKIEACDqP+HadzWPGkwQAAAAAElFTkSuQmCC";

function base64ToBytes(b64: string) {
  const bin = atob(b64);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  return bytes;
}

Deno.serve((req) => {
  const p = new URL(req.url).pathname;

  if (p.endsWith("/app.js")) {
    return new Response(APPJS, {
      headers: { "Content-Type": "application/javascript; charset=utf-8" },
    });
  }
  if (p.endsWith("/sw.js")) {
    return new Response(SW, {
      headers: {
        "Content-Type": "application/javascript; charset=utf-8",
        "Service-Worker-Allowed": "/functions/v1/app/",
      },
    });
  }
  if (p.endsWith("/manifest.json")) {
    return new Response(MANIFEST, { headers: { "Content-Type": "application/manifest+json" } });
  }
  if (p.endsWith("/icon.png")) {
    return new Response(base64ToBytes(ICON_B64), { headers: { "Content-Type": "image/png" } });
  }

  return new Response(HTML, { headers: { "Content-Type": "text/html; charset=utf-8" } });
});
