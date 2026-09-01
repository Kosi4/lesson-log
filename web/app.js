const SUPABASE_URL = "https://eyacjldyjxojzpxslkzh.supabase.co";
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
