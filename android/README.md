# Lesson Log — Android app

A native client for the same log the web app uses. It exists for one reason:
**a web push cannot be relied on to arrive at a particular time.** Android may
hold a normal-priority push until the device next wakes, and a browser page can
never do more than a heads-up banner.

This app schedules its own alarms locally, so a reminder fires at the wall clock
time with no network involved, and takes over the screen when it does.

## What makes the reminder hard to ignore

| Mechanism | Effect |
|---|---|
| `AlarmManager.setExactAndAllowWhileIdle` | Fires at the exact minute, even in Doze. No FCM in the path. |
| Full screen intent → `AlarmActivity` | Shows over the lock screen and turns the display on. |
| Channel at `IMPORTANCE_HIGH`, `USAGE_ALARM` sound, `setBypassDnd` | Rings and vibrates like an alarm rather than filing quietly. |
| `BootReceiver` | Alarms do not survive a reboot; this puts them back. |

## Sharing state with the web app

Both clients talk to the same Supabase project. Rows are read from `lesson_log`
and **every** change is posted to the `session-action` edge function rather than
written directly, so the snooze cap, the 23:59 lock and the weekend rules stay
enforced in exactly one place. Tick a session on the laptop and the phone will
not nag about it — the alarm asks the server before it rings.

`Rules.kt` mirrors only the *scheduling* half of
`supabase/functions/_shared/rules.ts`, so the app knows when to ring without a
round trip. `RulesTest.kt` covers the same cases as `rules.test.ts` to stop the
two drifting apart.

If the network is down when an alarm fires, it rings anyway. A needless nudge is
a smaller failure than a missed one.

## Building

Needs the Android SDK. On this machine it lives at
`/opt/homebrew/share/android-commandlinetools` and is recorded in
`local.properties`.

```bash
cd android && ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

```bash
cd android && ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ./gradlew testDebugUnitTest
```

## Installing on the phone

Either transfer the APK and open it (allow "install unknown apps" for whatever
opened it), or with USB debugging on:

```bash
adb install -r lesson-log.apk
```

It is a debug build signed with the local debug key, so it installs side by side
with nothing else and needs no Play Store account.

## After installing

1. Allow notifications when asked.
2. Check **Alarms & reminders** is allowed for Lesson Log — Android 12+ can
   revoke exact alarms, and without them the nudge drifts. The app has a link
   for this at the bottom of the screen.
3. Battery: set the app to **Unrestricted** so nothing throttles the alarm.

The web app stays exactly as it is for the laptop.
