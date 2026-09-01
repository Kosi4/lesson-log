const SESSION_ACTION_URL = "https://eyacjldyjxojzpxslkzh.supabase.co/functions/v1/session-action";
const SUBSCRIBE_URL = "https://eyacjldyjxojzpxslkzh.supabase.co/functions/v1/subscribe";
const VAPID_PUBLIC_KEY = "BOAxw8b0qB0y7CQkeASAhKW_ShXYsevkpe_z9kapldr5N28Kn_hkiBM_ukPboqzvkJAGQ9H29qeU3jVqVyn3nFI";

function urlBase64ToUint8Array(base64String) {
  const padding = "=".repeat((4 - (base64String.length % 4)) % 4);
  const base64 = (base64String + padding).replace(/-/g, "+").replace(/_/g, "/");
  const raw = atob(base64);
  const out = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; ++i) out[i] = raw.charCodeAt(i);
  return out;
}

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

// The browser rotates a push subscription on its own (service worker updates,
// key changes). Without this the replacement is stored and the superseded
// endpoint is left behind, so the table fills up with live-but-orphaned rows
// that each get sent a copy of every nudge.
self.addEventListener("pushsubscriptionchange", (event) => {
  event.waitUntil(
    (async () => {
      const oldEndpoint = event.oldSubscription && event.oldSubscription.endpoint;
      const fresh =
        event.newSubscription ||
        (await self.registration.pushManager.subscribe({
          userVisibleOnly: true,
          applicationServerKey: urlBase64ToUint8Array(VAPID_PUBLIC_KEY),
        }));

      const body = fresh.toJSON();
      body.oldEndpoint = oldEndpoint || null;

      await fetch(SUBSCRIBE_URL, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      });
    })().catch(() => {})
  );
});
