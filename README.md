# Victoria Launcher

An open source alternative to [Niagara Launcher](https://niagaralauncher.app) — a
minimal, list-based Android home screen.

![Victoria Launcher](docs/banner2.png)

## This fork

Built on upstream 0.84.1 as 0.85.0-fork. Everything new is listed in
[changelogs/108.txt](fastlane/metadata/android/en-US/changelogs/108.txt); the short version:

- **A search that answers.** Sums, unit conversions (including knots and
  Beaufort, bar, N/mm², kN and Nm), and steel weights: `plate 2000x1000x20`,
  `pipe 508x20x6000`, `round 50`. It offers to call, text, open a link, start a
  timer (`timer 10m`) or set an alarm (`alarm 7:30`), finds apps by initials,
  forgives typos, and flips a coin or rolls `2d6` when asked. Switch on
  **Settings → App list → Search bar in the app list** to use any of it.
- **Pause before opening.** Mark an app from its long-press menu, and it opens
  only after a short countdown that shows how often you opened it or let it go
  today.
- **A notification line under favorites**, off by default: who it is from, or
  who and what it says (#121).
- **Uninstall** in the long-press menu, **double-tap the wallpaper to lock**,
  and **wind on the clock widget** (Beaufort and direction, or m/s, km/h, knots,
  mph).
- Fixes for the open upstream issues #113, #106, #116, #119 and #114, and the
  Private Space row moved to the end of the list (#120).

This build is signed differently from the F-Droid one, so Android will not
install it over it: export your settings first (Settings → Backup → Export settings), uninstall,
install this, and import them again.

## Install

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="70">](https://f-droid.org/packages/dev.victorialauncher/)

Or grab the APK from [Releases](../../releases). Both carry the same signature,
so you can move between them without uninstalling.

Once it is installed, pick Victoria Launcher under
**Settings → Apps → Default apps → Home app**.

Requires Android 8.0 (API 26) or newer.

## Getting started

The home screen starts almost empty on purpose — everything on it is put there by
you.

- **Long-press the wallpaper** for the menu: favorites, widgets, edit layout,
  settings.
- **Swipe in from either edge** for the A-Z list, then slide along the letters to
  jump to one. Tap the edge and let go to just open it.
- **Long-press any app**, on either screen, to rename it, change its icon, hide
  it, or file it in a folder.
- **Edit layout** gives you drag handles for the order and steppers for every
  gap, height and margin.

Everything else lives in Settings, which has a search box over the whole of it.

Two things are worth knowing before you go looking. Double-tapping the A-Z strip
to lock the screen needs an accessibility service, and if that toggle is greyed
out you have to allow restricted settings from App info first — Android blocks it
for anything installed outside a store. And **Export settings** is how you keep
your setup: Android's own backup deliberately skips this app, because the file
names every app you have arranged and a backup that runs without the app cannot
leave a private space out of it.

Work profiles are picked up automatically. A private space needs Android 15 or
later and Victoria set as your default home app.

## Changelog

Per-release notes live in
[fastlane/metadata/android/en-US/changelogs](fastlane/metadata/android/en-US/changelogs),
and are shown on each [GitHub release](../../releases) and on the app's F-Droid
page.

## Reporting something

One thing per issue. A ticket with nine requests in it gets one reply covering nine
things, and the eight you did not care about bury the one you did.

For a bug, say what you did, what you expected, and what happened — and which version, on
which phone. Most of what looks broken turns out to depend on a setting, so say if you
have changed any. A screenshot or a recording is worth more than any description of how
something looks or moves.

For a request, say what you are trying to do and not only the feature you have in mind.
There is often already a way, and where there is not, knowing the goal tends to change the
shape of the answer.

## Build

You need JDK 17 and an Android SDK with platform 35.

```sh
./gradlew assembleDebug
```

Translations are very welcome — see [TRANSLATING.md](docs/TRANSLATING.md).

See [CONTRIBUTING.md](docs/CONTRIBUTING.md) for the full build and contribution
notes, and [ARCHITECTURE.md](docs/ARCHITECTURE.md) for how the code fits together.

## License

[GPL-3.0-or-later](LICENSE)
