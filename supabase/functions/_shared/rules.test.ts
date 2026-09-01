import { test } from "node:test";
import assert from "node:assert/strict";
import {
  computeStreak,
  defaultReminder,
  dueSessions,
  isLocked,
  isStudyDate,
  isStudyDay,
  MAX_SNOOZES,
  sastParts,
  sessionsToFinalise,
  snoozeExhausted,
  snoozeUntil,
} from "./rules.ts";

const day = (date, over = {}) => ({
  log_date: date,
  morning_status: "pending",
  evening_status: "pending",
  morning_next_reminder: defaultReminder(date, "morning"),
  evening_next_reminder: defaultReminder(date, "evening"),
  morning_snooze_count: 0,
  evening_snooze_count: 0,
  ...over,
});

// 2026-09-01 is a Tuesday. 09:10Z == 11:10 SAST, 18:00Z == 20:00 SAST.
const TUE = "2026-09-01";

test("morning is due at 11:10 SAST, not a minute before", () => {
  assert.deepEqual(dueSessions(day(TUE), new Date(`${TUE}T09:09:00Z`)), []);
  assert.deepEqual(dueSessions(day(TUE), new Date(`${TUE}T09:10:00Z`)), ["morning"]);
});

test("evening joins once 20:00 SAST passes", () => {
  assert.deepEqual(dueSessions(day(TUE), new Date(`${TUE}T18:00:00Z`)), ["morning", "evening"]);
});

test("a resolved session is never due again", () => {
  const done = day(TUE, { morning_status: "done", morning_next_reminder: null });
  assert.deepEqual(dueSessions(done, new Date(`${TUE}T18:00:00Z`)), ["evening"]);
});

test("a delivered reminder stays quiet until a snooze re-arms it", () => {
  const sent = day(TUE, { morning_next_reminder: null, evening_next_reminder: null });
  assert.deepEqual(dueSessions(sent, new Date(`${TUE}T18:00:00Z`)), []);

  const snoozed = day(TUE, {
    evening_next_reminder: null,
    morning_next_reminder: snoozeUntil(new Date(`${TUE}T09:10:00Z`)),
    morning_snooze_count: 1,
  });
  assert.deepEqual(dueSessions(snoozed, new Date(`${TUE}T09:30:00Z`)), []);
  assert.deepEqual(dueSessions(snoozed, new Date(`${TUE}T09:40:00Z`)), ["morning"]);
});

test("weekends are never nagged", () => {
  for (const date of ["2026-09-05", "2026-09-06"]) {
    assert.deepEqual(dueSessions(day(date), new Date(`${date}T18:00:00Z`)), []);
  }
});

test("the sixth snooze running out stops the nagging and writes it off", () => {
  const spent = day(TUE, {
    evening_next_reminder: null,
    morning_snooze_count: MAX_SNOOZES,
    morning_next_reminder: `${TUE}T12:10:00Z`,
  });
  const after = new Date(`${TUE}T12:10:00Z`);

  assert.equal(snoozeExhausted(spent, "morning", after), true);
  // No longer nagged...
  assert.deepEqual(dueSessions(spent, after), []);
  // ...written off instead.
  assert.deepEqual(sessionsToFinalise(spent, after), ["morning"]);

  // One snooze short of the cap it is still a normal nudge.
  const almost = { ...spent, morning_snooze_count: MAX_SNOOZES - 1 };
  assert.deepEqual(dueSessions(almost, after), ["morning"]);
  assert.deepEqual(sessionsToFinalise(almost, after), []);
});

test("a day left pending is written off once the date rolls over", () => {
  const yesterday = day(TUE);
  const nextDay = new Date("2026-09-02T05:00:00Z");

  assert.equal(isLocked(TUE, nextDay), true);
  assert.deepEqual(sessionsToFinalise(yesterday, nextDay), ["morning", "evening"]);

  // Same row, same day: still editable, nothing written off.
  assert.equal(isLocked(TUE, new Date(`${TUE}T21:00:00Z`)), false);
  assert.deepEqual(sessionsToFinalise(yesterday, new Date(`${TUE}T09:00:00Z`)), []);
});

test("a weekend is never written off as incomplete", () => {
  // 2026-09-05 Sat, 2026-09-06 Sun: no sessions scheduled, nothing to miss.
  for (const date of ["2026-09-05", "2026-09-06"]) {
    assert.deepEqual(sessionsToFinalise(day(date), new Date("2026-09-08T05:00:00Z")), []);
  }
});

test("a finished day is left alone when it rolls over", () => {
  const finished = day(TUE, { morning_status: "done", evening_status: "incomplete" });
  assert.deepEqual(sessionsToFinalise(finished, new Date("2026-09-02T05:00:00Z")), []);
});

test("streak counts consecutive study days where both sessions were done", () => {
  const rows = ["2026-08-31", "2026-09-01", "2026-09-02"].map((d) =>
    day(d, { morning_status: "done", evening_status: "done" })
  );
  assert.equal(computeStreak(rows, new Date("2026-09-02T21:00:00Z")), 3);
});

test("an incomplete session resets the streak", () => {
  const rows = [
    day("2026-08-31", { morning_status: "done", evening_status: "done" }),
    day("2026-09-01", { morning_status: "done", evening_status: "incomplete" }),
    day("2026-09-02", { morning_status: "done", evening_status: "done" }),
  ];
  assert.equal(computeStreak(rows, new Date("2026-09-02T21:00:00Z")), 1);
});

test("a weekend carries the streak instead of breaking it", () => {
  // Fri 2026-09-04 and Mon 2026-09-07 done, nothing in between (Sat/Sun).
  const rows = [
    day("2026-09-04", { morning_status: "done", evening_status: "done" }),
    day("2026-09-07", { morning_status: "done", evening_status: "done" }),
  ];
  assert.equal(isStudyDate("2026-09-05"), false);
  assert.equal(isStudyDate("2026-09-06"), false);
  assert.equal(computeStreak(rows, new Date("2026-09-07T21:00:00Z")), 2);
});

test("an unfinished today neither extends nor breaks the streak", () => {
  const rows = [
    day("2026-09-01", { morning_status: "done", evening_status: "done" }),
    day("2026-09-02"),
  ];
  assert.equal(computeStreak(rows, new Date("2026-09-02T09:00:00Z")), 1);
});

test("SAST date rolls over two hours ahead of UTC", () => {
  assert.equal(sastParts(new Date("2026-09-01T22:30:00Z")).date, "2026-09-02");
  assert.equal(isStudyDay(sastParts(new Date("2026-09-06T09:00:00Z")).dow), false);
});
