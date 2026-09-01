import { test } from "node:test";
import assert from "node:assert/strict";
import {
  dueSessions,
  defaultReminder,
  isStudyDay,
  sastParts,
  snoozeUntil,
} from "./rules.ts";

const pending = {
  log_date: "2026-09-01",
  morning_status: "pending",
  evening_status: "pending",
  morning_next_reminder: defaultReminder("2026-09-01", "morning"),
  evening_next_reminder: defaultReminder("2026-09-01", "evening"),
};

// 2026-09-01 is a Tuesday. 09:10Z == 11:10 SAST.
test("morning is due at 11:10 SAST, not a minute before", () => {
  assert.deepEqual(dueSessions(pending, new Date("2026-09-01T09:09:00Z")), []);
  assert.deepEqual(dueSessions(pending, new Date("2026-09-01T09:10:00Z")), ["morning"]);
});

test("evening joins once 20:00 SAST passes", () => {
  assert.deepEqual(dueSessions(pending, new Date("2026-09-01T18:00:00Z")), [
    "morning",
    "evening",
  ]);
});

test("a logged session is never due again", () => {
  const logged = { ...pending, morning_status: "done", morning_next_reminder: null };
  assert.deepEqual(dueSessions(logged, new Date("2026-09-01T18:00:00Z")), ["evening"]);

  const cancelled = { ...pending, morning_status: "incomplete", morning_next_reminder: null };
  assert.deepEqual(dueSessions(cancelled, new Date("2026-09-01T18:00:00Z")), ["evening"]);
});

test("a delivered reminder does not re-fire until snoozed forward", () => {
  const delivered = { ...pending, morning_next_reminder: null, evening_next_reminder: null };
  assert.deepEqual(dueSessions(delivered, new Date("2026-09-01T18:00:00Z")), []);

  const snoozed = {
    ...delivered,
    morning_next_reminder: snoozeUntil(new Date("2026-09-01T09:10:00Z")),
  };
  // 20 min into a 30 min snooze: still quiet.
  assert.deepEqual(dueSessions(snoozed, new Date("2026-09-01T09:30:00Z")), []);
  // 30 min later: due again.
  assert.deepEqual(dueSessions(snoozed, new Date("2026-09-01T09:40:00Z")), ["morning"]);
});

test("weekends are never nagged", () => {
  // 2026-09-05 Sat, 2026-09-06 Sun.
  for (const day of ["2026-09-05", "2026-09-06"]) {
    const row = {
      ...pending,
      log_date: day,
      morning_next_reminder: defaultReminder(day, "morning"),
      evening_next_reminder: defaultReminder(day, "evening"),
    };
    assert.deepEqual(dueSessions(row, new Date(`${day}T18:00:00Z`)), []);
  }
});

test("SAST date rolls over two hours ahead of UTC", () => {
  // 22:30Z on the 1st is already 00:30 on the 2nd in Johannesburg.
  assert.equal(sastParts(new Date("2026-09-01T22:30:00Z")).date, "2026-09-02");
  assert.equal(isStudyDay(sastParts(new Date("2026-09-06T09:00:00Z")).dow), false);
});
