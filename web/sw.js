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
    // A reminder that sits silently in the status bar is useless. Vibration and
    // renotify are what push Android to treat this as a high-importance alert
    // and float it over whatever is on screen, rather than filing it quietly.
    // renotify only applies because a tag is set: without it, a notification
    // replacing one with the same tag arrives with no alert at all.
    vibrate: [300, 120, 300],
    renotify: true,
    silent: false,
    requireInteraction: true,
    actions: [
      { action: "done", title: "Mark done" },
      { action: "snooze", title: "Snooze 30m" },
      { action: "cancel", title: "Mark incomplete" },
    ],
  };
  event.waitUntil(self.registration.showNotification(title, options));
});

/**
 * Post a notification action, retrying a few times. The phone is often on a
 * flaky connection at exactly the moment a nudge is tapped, and a swallowed
 * failure here is invisible: the notification disappears and nothing is saved,
 * so the tap looks like it worked. If it really cannot be saved, say so rather
 * than pretend.
 */
async function sendAction(info, action) {
  const body = JSON.stringify({ date: info.date, session: info.session, action });

  for (let attempt = 0; attempt < 3; attempt++) {
    try {
      const res = await fetch(SESSION_ACTION_URL, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body,
      });
      if (res.ok) return;
    } catch (err) {
      // fall through to retry
    }
    await new Promise((r) => setTimeout(r, 800 * (attempt + 1)));
  }

  await self.registration.showNotification("Couldn't save that", {
    body: "Tap to open Lesson Log and log the session yourself.",
    icon: "./icon.png",
    badge: "./icon.png",
    tag: "action-failed",
    requireInteraction: true,
    vibrate: [300, 120, 300],
  });
}

self.addEventListener("notificationclick", (event) => {
  const notification = event.notification;
  const info = notification.data || {};
  notification.close();

  if (event.action === "done" || event.action === "snooze" || event.action === "cancel") {
    event.waitUntil(sendAction(info, event.action));
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
