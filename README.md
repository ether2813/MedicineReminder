# MediMinder – Medicine Reminder for Android

A simple, minimal and beautiful medicine reminder app built with Kotlin and Jetpack Compose (Material 3).
Everything stays on your device – there is no account and no internet access.

## Features

**Medicines**
- Any kind of medicine: tablet, capsule, syrup, injection, drops, inhaler, cream, spray, patch, powder, suppository, other
- Strength (e.g. `500 mg`) and dose unit (tablets, ml, teaspoons, drops, puffs, units, mg, …) with fractional doses (½ tablet, 2.5 ml)
- Instructions: before / after / with meal, empty stomach, bedtime
- Per-medicine colour, doctor's name and notes
- Pause / resume or delete medicines; finished courses are shown as completed

**Schedules**
- Every day, specific weekdays, every N days, every N hours (times generated automatically) or as needed (PRN)
- Multiple reminder times per day, each with its own dose, plus quick presets (morning, noon, evening, night)
- Start date and end: ongoing, until a date or for N days

**Reminders & alarms**
- Exact alarms that survive reboots, app updates and time-zone/clock changes
- Two styles per medicine: a gentle **notification**, or a loud full-screen **alarm** that rings over the lock screen until you respond
- Take / Snooze / Skip directly from the notification
- Configurable snooze length and automatic repeat reminders if you don't respond
- Stock tracking with automatic count-down, "days left" estimate and refill reminders

**Tracking**
- Today view with a date strip, progress ring, doses grouped by morning/afternoon/evening/night
- Due, missed, taken and skipped states, undo, and logging of as-needed doses
- History with 7- and 30-day adherence, perfect-day streak, weekly chart and a filterable log
- Per-medicine adherence and recent history

**Settings**
- Follows the system light/dark theme (or force light/dark), optional Material You dynamic colours
- English (default) and বাংলা (Bengali), including Bengali digits and dates
- Local **export / import** of a JSON backup (merge or replace) via the system file picker
- Export dose history as CSV for spreadsheets
- Shortcuts to notification, exact-alarm, full-screen and battery-optimisation settings

## Building

Requirements: JDK 17 and the Android SDK (API 35).

```bash
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/`. Every push also builds debug and release APKs with
GitHub Actions (see `.github/workflows/android.yml`); download them from the workflow run's artifacts.

The release build is signed with the debug key so it can be sideloaded; use your own signing config
before publishing to a store.

## Project structure

```
app/src/main/java/com/mediminder/app
├── data         Room database, repositories, settings and backup (import/export)
├── domain       Pure scheduling and adherence logic (unit tested)
├── reminder     Alarm scheduling, notifications, receivers and the full-screen alarm
├── ui           Compose screens: today, medicines, editor, detail, history, settings
└── util         Locale and permission helpers
```

Translations live in `res/values/strings.xml` (English) and `res/values-bn/strings.xml` (Bengali).

> MediMinder helps you remember your medicines. It does not replace advice from a doctor or pharmacist.
