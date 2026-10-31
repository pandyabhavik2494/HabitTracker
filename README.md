# Habit Tracker

An Android app (Kotlin, Jetpack Compose) for closing four habits every day, made for the
Galaxy Z Fold: the cover screen and the inner screen each get their own layout.

- **10k steps** from Health Connect (Google Health / Fit, the watch, the phone)
- **Under 1,500 kcal** from MyFitnessPal's food entries in Health Connect, sealed when you close the kitchen (or at 8:30 PM)
- **Weigh-in** from the scale's app (VeSync) in Health Connect, or typed in
- **Bath**, a long-press in the app, on the widget or on a notification

All four close the Seal for a Perfect Day, which earns a sticker pack from *Purrfect Days, Series 1*:
24 animated cat breeds, with secret cats and a Legendary one that only a comeback earns. Duplicates
become swaps; 3 swaps save a missed day.

Data syncs from Health Connect when the app opens, when you pull down on Today, and every 15 minutes
in the background.

## Layout

- `core/`: the rules (habits, streaks, swaps, packs, secret cats, coaching text) as plain Kotlin with unit tests.
- `app/`: Room storage, the Health Connect reader, the screens, the widgets, notifications and background work.
- `tools/cats/`: how the sticker art in `app/src/main/assets/cats.json` is made from the approved mock.
- `.github/workflows/android.yml`: unit tests, the release build, and emulator tests with screenshots at
  both Fold screen sizes. Each build is published as a release `build-N`; the file installed on the phone
  is re-signed with the app's own key.
