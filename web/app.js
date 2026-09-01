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

const SESSIONS = [
  { key: "morning", label: "Morning session", when: "Math — 10:00, nudge at 11:10" },
  { key: "evening", label: "Evening session", when: "CS — 17:30, nudge at 20:00" },
];

// South Africa is UTC+2 year round, so a fixed offset is safe.
const SAST_OFFSET_MS = 2 * 60 * 60 * 1000;

function todaySAST() {
  return new Date(Date.now() + SAST_OFFSET_MS).toISOString().slice(0, 10);
}

function isStudyDate(date) {
  const dow = new Date(date + "T00:00:00Z").getUTCDay();
  return dow >= 1 && dow <= 5;
}

function prettyDate(date) {
  return new Date(date + "T00:00:00Z").toLocaleDateString(undefined, {
    weekday: "short",
    day: "numeric",
    month: "short",
  });
}

let rows = [];
let byDate = new Map();
let firstTracked = null; // earliest logged day; nothing before it is a "miss"
let calCursor = null; // {year, month} of the month on screen

async function fetchAll() {
  const res = await fetch(REST + "/lesson_log?order=log_date.desc", { headers });
  rows = await res.json();
  byDate = new Map(rows.map((r) => [r.log_date, r]));
  firstTracked = rows.length ? rows[rows.length - 1].log_date : todaySAST();
}

async function act(date, session, action, note) {
  const res = await fetch(SESSION_ACTION_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ date, session, action, note }),
  });
  const body = await res.json().catch(() => ({}));
  if (!res.ok) {
    alert(body.message || "That change could not be saved.");
  }
  await refresh();
}

/* ---------- stats ---------- */

function statusOf(row, key) {
  return row ? row[key + "_status"] : "pending";
}

function computeStreak() {
  const today = todaySAST();
  const cursor = new Date(today + "T00:00:00Z");
  let streak = 0;

  for (let i = 0; i < 400; i++) {
    const date = cursor.toISOString().slice(0, 10);
    cursor.setUTCDate(cursor.getUTCDate() - 1);
    if (!isStudyDate(date)) continue; // weekends carry the streak

    const row = byDate.get(date);
    const m = statusOf(row, "morning");
    const e = statusOf(row, "evening");

    // An unfinished today neither extends nor breaks the run.
    if (date === today && (m === "pending" || e === "pending")) continue;

    if (m === "done" && e === "done") streak++;
    else break;
  }
  return streak;
}

function renderStats() {
  const month = todaySAST().slice(0, 7);
  let monthDone = 0;
  let total = 0;
  for (const r of rows) {
    for (const s of SESSIONS) {
      if (r[s.key + "_status"] === "done") {
        total++;
        if (r.log_date.startsWith(month)) monthDone++;
      }
    }
  }
  document.getElementById("streak").textContent = computeStreak();
  document.getElementById("month-done").textContent = monthDone;
  document.getElementById("total").textContent = total;
}

/* ---------- today ---------- */

function renderToday() {
  const today = todaySAST();
  const row = byDate.get(today);
  const host = document.getElementById("today");
  host.innerHTML = "";

  if (!isStudyDate(today)) {
    host.innerHTML = '<div class="card"><p class="empty">No sessions scheduled today. Enjoy it.</p></div>';
    return;
  }

  const pending = SESSIONS.filter((s) => statusOf(row, s.key) === "pending");
  const settled = SESSIONS.filter((s) => statusOf(row, s.key) !== "pending");

  // Anything still open gets a full card with a note field.
  for (const s of pending) {
    const card = document.createElement("div");
    card.className = "card";
    card.innerHTML =
      "<h2>" + s.label + "</h2>" +
      '<p class="when">' + s.when + "</p>" +
      '<input type="text" placeholder="What did you cover? (optional)" />' +
      '<div class="row">' +
      '<button class="primary" data-act="done">Mark done</button>' +
      '<button data-act="cancel">Mark incomplete</button>' +
      "</div>";
    const input = card.querySelector("input");
    if (row && row[s.key + "_note"]) input.value = row[s.key + "_note"];
    card.querySelector('[data-act="done"]').onclick = () =>
      act(today, s.key, "done", input.value.trim());
    card.querySelector('[data-act="cancel"]').onclick = () => act(today, s.key, "cancel");
    host.appendChild(card);
  }

  // Resolved sessions drop off the dashboard into a compact strip that stays
  // editable until the day closes at 23:59.
  if (settled.length) {
    const card = document.createElement("div");
    card.className = "card";
    for (const s of settled) {
      const status = statusOf(row, s.key);
      const note = row[s.key + "_note"];
      const line = document.createElement("div");
      line.className = "settled";
      line.innerHTML =
        '<div class="body"><div class="head">' + s.label + "</div>" +
        (note ? '<div class="sub">' + escapeHtml(note) + "</div>" : "") +
        "</div>" +
        '<span class="pill ' + status + '">' + (status === "done" ? "Done" : "Incomplete") + "</span>";
      const edit = document.createElement("button");
      edit.className = "link";
      edit.textContent = "Edit";
      edit.onclick = () => act(today, s.key, "edit");
      line.appendChild(edit);
      card.appendChild(line);
    }
    const note = document.createElement("p");
    note.className = "locked-note";
    note.textContent = "Editable until 23:59 today, then locked for good.";
    card.appendChild(note);
    host.appendChild(card);
  }

  if (!pending.length && !settled.length) {
    host.innerHTML = '<div class="card"><p class="empty">Nothing logged yet today.</p></div>';
  }
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) =>
    ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c])
  );
}

/* ---------- month grid ---------- */

/**
 * One day is one cell split in two: morning above, evening below. Colouring the
 * halves separately keeps which session was missed, which a single blended
 * colour threw away.
 */
function dayCell(date) {
  const today = todaySAST();
  const el = document.createElement("div");
  el.title = date;

  const untracked =
    date > today || (firstTracked && date < firstTracked);
  const offday = !isStudyDate(date);

  const number = document.createElement("span");
  number.className = "n";
  number.textContent = Number(date.slice(8, 10));

  if (untracked || offday) {
    // Weekends read as off-days even before they arrive, so the shape of the
    // term is visible ahead of time rather than only in hindsight.
    el.className = "day" + (offday ? " offday" : "");
    el.appendChild(number);
    return el;
  }

  const row = byDate.get(date);
  const am = statusOf(row, "morning");
  const pm = statusOf(row, "evening");

  el.className = "day";
  for (const [session, status] of [["am", am], ["pm", pm]]) {
    const half = document.createElement("div");
    half.className = "half " + session + " " + status;
    el.appendChild(half);
  }

  // The numeral sits over the morning half, so its contrast is decided by that
  // half alone rather than by whatever the two happen to blend into.
  if (am === "done" || am === "incomplete") number.classList.add("on-status");
  el.appendChild(number);
  return el;
}

function renderCalendar() {
  const { year, month } = calCursor;
  const grid = document.getElementById("cal-grid");
  const first = new Date(Date.UTC(year, month, 1));
  const daysInMonth = new Date(Date.UTC(year, month + 1, 0)).getUTCDate();
  // Monday-first: JS getUTCDay() is 0=Sun.
  const lead = (first.getUTCDay() + 6) % 7;

  document.getElementById("cal-title").textContent = first.toLocaleDateString(undefined, {
    month: "long",
    year: "numeric",
  });

  grid.innerHTML = "";
  for (const d of ["M", "T", "W", "T", "F", "S", "S"]) {
    const el = document.createElement("div");
    el.className = "dow";
    el.textContent = d;
    grid.appendChild(el);
  }
  for (let i = 0; i < lead; i++) {
    const el = document.createElement("div");
    el.className = "day blank";
    grid.appendChild(el);
  }

  const today = todaySAST();
  for (let d = 1; d <= daysInMonth; d++) {
    const date =
      year + "-" + String(month + 1).padStart(2, "0") + "-" + String(d).padStart(2, "0");
    const el = dayCell(date);
    if (date === today) el.classList.add("today");
    grid.appendChild(el);
  }

  // Never scroll past the current month.
  const now = new Date(todaySAST() + "T00:00:00Z");
  const atCurrent = year === now.getUTCFullYear() && month === now.getUTCMonth();
  document.getElementById("cal-next").disabled = atCurrent;
}

function shiftMonth(delta) {
  const d = new Date(Date.UTC(calCursor.year, calCursor.month + delta, 1));
  const now = new Date(todaySAST() + "T00:00:00Z");
  if (d > new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), 1))) return;
  calCursor = { year: d.getUTCFullYear(), month: d.getUTCMonth() };
  renderCalendar();
}

function wireCalendar() {
  document.getElementById("cal-prev").onclick = () => shiftMonth(-1);
  document.getElementById("cal-next").onclick = () => shiftMonth(1);

  // Swipe left/right through months on touch devices.
  const grid = document.getElementById("cal-grid");
  let startX = null;
  let startY = null;
  grid.addEventListener("touchstart", (e) => {
    startX = e.changedTouches[0].clientX;
    startY = e.changedTouches[0].clientY;
  }, { passive: true });
  grid.addEventListener("touchend", (e) => {
    if (startX === null) return;
    const dx = e.changedTouches[0].clientX - startX;
    const dy = e.changedTouches[0].clientY - startY;
    startX = startY = null;
    // Ignore mostly-vertical drags so page scrolling still works.
    if (Math.abs(dx) < 45 || Math.abs(dx) < Math.abs(dy)) return;
    shiftMonth(dx < 0 ? 1 : -1);
  }, { passive: true });
}

/* ---------- completed archive ---------- */

function renderCompleted() {
  const host = document.getElementById("completed");
  const entries = [];
  for (const r of rows) {
    for (const s of SESSIONS) {
      if (r[s.key + "_status"] === "done") {
        entries.push({ date: r.log_date, label: s.label, note: r[s.key + "_note"] });
      }
    }
  }
  entries.sort((a, b) => (a.date < b.date ? 1 : a.date > b.date ? -1 : 0));

  if (!entries.length) {
    host.innerHTML = '<p class="empty">Nothing completed yet. Tick a session off and it lands here.</p>';
    return;
  }

  host.innerHTML = entries
    .map(
      (e) =>
        '<div class="settled"><div class="body">' +
        '<div class="head">' + prettyDate(e.date) + " · " + e.label + "</div>" +
        (e.note ? '<div class="sub">' + escapeHtml(e.note) + "</div>" : "") +
        '</div><span class="pill done">Done</span></div>'
    )
    .join("");
}

/* ---------- push ---------- */

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

/* ---------- boot ---------- */

async function refresh() {
  await fetchAll();
  renderStats();
  renderToday();
  renderCompleted();
  renderCalendar();
}

(async function init() {
  const now = new Date(todaySAST() + "T00:00:00Z");
  calCursor = { year: now.getUTCFullYear(), month: now.getUTCMonth() };
  wireCalendar();
  await refresh();
  setupPush();
  // Acting on a notification updates the database, not this tab.
  document.addEventListener("visibilitychange", () => {
    if (!document.hidden) refresh();
  });
})();
