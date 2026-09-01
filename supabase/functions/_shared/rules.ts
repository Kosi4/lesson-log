// Pure reminder logic. No Deno/Node APIs in here so it runs under both the
// edge function and `node --test`.

export type Session = "morning" | "evening";
export type Status = "pending" | "done" | "incomplete";

export type LogRow = {
  log_date: string;
  morning_status: string;
  evening_status: string;
  morning_note?: string | null;
  evening_note?: string | null;
  morning_next_reminder: string | null;
  evening_next_reminder: string | null;
  morning_snooze_count?: number;
  evening_snooze_count?: number;
};

// South Africa is UTC+2 year round — no DST — so a fixed offset is safe here.
const SAST_OFFSET_MS = 2 * 60 * 60 * 1000;

export const SESSIONS: Session[] = ["morning", "evening"];

/** Calendar date and weekday in Africa/Johannesburg for a given instant. */
export function sastParts(now: Date): { date: string; dow: number } {
  const sast = new Date(now.getTime() + SAST_OFFSET_MS);
  return { date: sast.toISOString().slice(0, 10), dow: sast.getUTCDay() };
}

export function todaySAST(now: Date): string {
  return sastParts(now).date;
}

/** Mon–Fri are study days. Sat is catch-up, Sun is off — neither gets nagged. */
export function isStudyDay(dow: number): boolean {
  return dow >= 1 && dow <= 5;
}

/** Whether a YYYY-MM-DD date is a study day, independent of any clock. */
export function isStudyDate(date: string): boolean {
  return isStudyDay(new Date(`${date}T00:00:00Z`).getUTCDay());
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

/**
 * Six snoozes, i.e. three hours of grace. When the sixth one runs out the
 * session is marked incomplete for good rather than nagging forever.
 */
export const MAX_SNOOZES = 6;

export function snoozeUntil(now: Date): string {
  return new Date(now.getTime() + SNOOZE_MINUTES * 60 * 1000).toISOString();
}

export function statusOf(row: LogRow, session: Session): string {
  return session === "morning" ? row.morning_status : row.evening_status;
}

export function reminderOf(row: LogRow, session: Session): string | null {
  return session === "morning" ? row.morning_next_reminder : row.evening_next_reminder;
}

export function snoozeCountOf(row: LogRow, session: Session): number {
  const n = session === "morning" ? row.morning_snooze_count : row.evening_snooze_count;
  return typeof n === "number" ? n : 0;
}

/**
 * A day is editable only until 23:59 in Johannesburg. Once the date has rolled
 * over, whatever it says is final: a missed session stays incomplete and a
 * completed one can no longer be taken back.
 */
export function isLocked(logDate: string, now: Date): boolean {
  return logDate < todaySAST(now);
}

/** True once the snooze budget is spent and the last one has run out. */
export function snoozeExhausted(row: LogRow, session: Session, now: Date): boolean {
  if (statusOf(row, session) !== "pending") return false;
  if (snoozeCountOf(row, session) < MAX_SNOOZES) return false;
  const at = reminderOf(row, session);
  return !!at && new Date(at) <= now;
}

/**
 * Which sessions should be nudged right now. A session is due when it is still
 * pending and its reminder timestamp has passed. A null timestamp means the
 * reminder was already delivered (or explicitly resolved) and must not re-fire.
 * Sessions whose snooze budget has run out are excluded — those get marked
 * incomplete instead of nagged again.
 */
export function dueSessions(row: LogRow, now: Date): Session[] {
  const { dow } = sastParts(now);
  if (!isStudyDay(dow)) return [];

  return SESSIONS.filter((session) => {
    if (statusOf(row, session) !== "pending") return false;
    if (snoozeExhausted(row, session, now)) return false;
    const at = reminderOf(row, session);
    return !!at && new Date(at) <= now;
  });
}

/**
 * Sessions on this row that must be written off as incomplete: the day has
 * closed, or the snooze budget is spent. Non-study days are never written off —
 * nothing was scheduled, so there was nothing to miss.
 */
export function sessionsToFinalise(row: LogRow, now: Date): Session[] {
  if (!isStudyDate(row.log_date)) return [];
  return SESSIONS.filter((session) => {
    if (statusOf(row, session) !== "pending") return false;
    return isLocked(row.log_date, now) || snoozeExhausted(row, session, now);
  });
}

/**
 * Streak of consecutive study days, most recent first, where both sessions were
 * done. Weekends carry the streak rather than breaking it — there are no
 * sessions scheduled then, so there is nothing to miss. Today only counts once
 * both of its sessions have been resolved, so an unfinished today neither
 * extends nor breaks the run.
 */
export function computeStreak(rows: LogRow[], now: Date): number {
  const byDate = new Map(rows.map((r) => [r.log_date, r]));
  const today = todaySAST(now);
  const cursor = new Date(`${today}T00:00:00Z`);
  let streak = 0;

  for (let i = 0; i < 400; i++) {
    const date = cursor.toISOString().slice(0, 10);
    cursor.setUTCDate(cursor.getUTCDate() - 1);

    if (!isStudyDate(date)) continue;

    const row = byDate.get(date);
    const morning = row ? row.morning_status : "pending";
    const evening = row ? row.evening_status : "pending";

    if (date === today && (morning === "pending" || evening === "pending")) continue;

    if (morning === "done" && evening === "done") streak++;
    else break;
  }

  return streak;
}
