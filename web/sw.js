const SESSION_ACTION_URL = "https://eyacjldyjxojzpxslkzh.supabase.co/functions/v1/session-action";

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
