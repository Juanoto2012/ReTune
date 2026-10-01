# ReTune Release Notes

All notable changes to ReTune are documented here. ReTune is a fork of
[Metrolist](https://github.com/MetrolistGroup/Metrolist), itself based on InnerTune.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and ReTune uses
[semantic versioning](https://semver.org/spec/v2.0.0.html).

---

## [14.0.0] — 2026-10-01

`versionCode 153` · `minSdk 26` · `targetSdk 36` · signed with `opentune_key`

A large rework focused on three things: a calmer, monochrome interface, reliable behaviour on slow
SD cards, and a meaningful reduction in memory churn. No data is lost and the database schema is
untouched — the migration chain is exactly as it was in 13.6.3, so upgrading is in-place.

### Interface

- **Monochromatic theme.** ReTune now ships one hand-tuned neutral palette for light and dark.
  Every colour role sits on the same grey axis, so the interface no longer changes hue depending on
  wallpaper, album art or system palette.
- **Dynamic colour removed.** The "dynamic theme" toggle, the accent-colour palette picker and the
  Material You wallpaper palette are gone. The appearance screen is now just light / dark /
  follow system / pure black.
- **Minimalist shapes.** Material 3's expressive corner radii (up to 28dp) were reduced across the
  app; the global shape scale is now 4–16dp and oversized per-screen radii were tightened.
- **About screen simplified** to three facts: *Created by MO Agamy*, *Forked by JJDev*, and
  *Buy Me a Coffee*. The collaborator list, Discord, Telegram, repository and licence rows are
  removed.
- **Branding.** All user-facing "Metrolist" wording is now "ReTune" — crash-report subject, Last.fm
  sync copy, user agent, exported folder names and the app name. The `metrolist` string key is kept
  so existing translations still resolve, and a new `retune` key holds the branded value.

### Storage and SD card

- **The library database never lives on the SD card.** It is always kept on internal storage. A
  removable volume can be pulled at any moment, and SQLite in WAL mode on slow FAT32 media was the
  biggest source of both jank and corruption risk. Only rebuildable caches follow the
  "use SD card" preference.
- **Automatic adoption of existing libraries.** Users who previously ran ReTune with the database
  on the SD card get their library, playlists and history imported into internal storage on first
  launch instead of opening to an empty app. The write-ahead log is carried over so nothing
  committed after the last checkpoint is lost.
- **Lossless storage switching.** Toggling "use SD card" now copies the existing song cache,
  download cache and image cache to the new location first. Previously the directory was simply
  relocated, which silently dropped every download.
- **Robust volume detection.** SD presence now checks the volume is actually mounted *and*
  writable, rather than only asking whether the path is removable. An absent, unmounted or
  read-only card falls back to internal storage instead of throwing, so a bad card can no longer
  break startup.
- **Live card detection.** Inserting or removing the card is picked up when the storage settings
  screen resumes, with no restart required to re-evaluate availability.

### Performance and memory

- **Unbounded stream-URL generation map fixed.** `StreamUrlCache` tracked a generation counter per
  media id in a plain `HashMap` that was never pruned, growing by one entry for every distinct track
  ever played. It is now bounded exactly like the URL cache.
- **Album-art decoding made cheap.** Notification and lock-screen artwork is sub-sampled to 1024px
  on the long edge instead of being decoded at full resolution, and opaque copies are stored as
  `RGB_565`, roughly halving retained memory. The intermediate scaled bitmap is released
  immediately.
- **No more leaked coroutine scopes.** `toggleLike` / `toggleLibrary` on the song, album, artist and
  playlist entities, and the lyrics lookup helper, each allocated a fresh parent
  `CoroutineScope(SupervisorJob())` per call that nothing could ever cancel. They now use a single
  supervised scope.
- **No blocking work in service teardown.** The last playback position is persisted on a
  process-wide scope instead of via `runBlocking` inside `MusicService.onDestroy`, which had been
  stalling service restart on every track change.
- **Cheaper image pipeline.** Coil crossfade is disabled, removing a second live bitmap per list
  cell and a redraw per image while scrolling.

### Smooth on a slow SD card

- **Cache size polling made cheap.** The storage screen walked three cache trees three times per
  second on the main thread. Polling is now every 2 seconds, on an IO dispatcher, and only while the
  screen is actually resumed.
- Keeping the SQLite database on internal storage removes the largest single source of main-thread
  stalls on slow removable media.

### Dependencies

- **materialKolor removed.** The last use was a colour-ranking helper for the player's gradient
  background, which is now a 20-line local implementation. One fewer library in the release APK.

### Compatibility

- `applicationId` (`com.jjdev.retune`), all intent filters, deep links, the FileProvider authority
  and the Room schema are unchanged, so this is an in-place upgrade.
- Previous playlist exports under `Documents/MetrolistExports` remain shareable through the
  FileProvider; new exports go to `Documents/ReTuneExports`.
- Retained preference keys (`dynamicTheme`, `selectedThemeColor`) are intentionally left in place so
  existing DataStore files keep parsing.
