// Pure reminder logic. No Deno/Node APIs in here so it runs under both the
// edge function and `node --test`.

export type Session = "morning" | "evening";

export type LogRow = {
  log_date: string;
  morning_status: string;
  evening_status: string;
  morning_next_reminder: string | null;
  evening_next_reminder: string | null;
};

// South Africa is UTC+2 year round — no DST — so a fixed offset is safe here.
const SAST_OFFSET_MS = 2 * 60 * 60 * 1000;

/** Calendar date and weekday in Africa/Johannesburg for a given instant. */
export function sastParts(now: Date): { date: string; dow: number } {
  const sast = new Date(now.getTime() + SAST_OFFSET_MS);
  return { date: sast.toISOString().slice(0, 10), dow: sast.getUTCDay() };
}

/** Mon–Fri are study days. Sat is catch-up, Sun is off — neither gets nagged. */
export function isStudyDay(dow: number): boolean {
  return dow >= 1 && dow <= 5;
}

// 30 minutes after each session ends, per the Google Calendar schedule:
// Math 10:00–10:40 -> 11:10 SAST (09:10Z); CS 17:30–19:30 -> 20:00 SAST (18:00Z).
export const REMINDER_UTC: Record<Session, string> = {
  morning: "09:10:00Z",
  evening: "18:00:00Z",
};

export function defaultReminder(date: string, session: Session): string {
  return `${date}T${REMINDER_UTC[session]}`;
}

export const SNOOZE_MINUTES = 30;

export function snoozeUntil(now: Date): string {
  return new Date(now.getTime() + SNOOZE_MINUTES * 60 * 1000).toISOString();
}

/**
 * Which sessions should be nudged right now. A session is due when it is still
 * pending and its reminder timestamp has passed. A null timestamp means the
 * reminder was already delivered (or explicitly resolved) and must not re-fire.
 */
export function dueSessions(row: LogRow, now: Date): Session[] {
  const { dow } = sastParts(now);
  if (!isStudyDay(dow)) return [];

  const due: Session[] = [];
  for (const session of ["morning", "evening"] as Session[]) {
    const status = session === "morning" ? row.morning_status : row.evening_status;
    const at = session === "morning" ? row.morning_next_reminder : row.evening_next_reminder;
    if (status !== "pending") continue;
    if (!at) continue;
    if (new Date(at) <= now) due.push(session);
  }
  return due;
}
