# Working with ReTune as an AI agent

ReTune is a 3rd party YouTube Music client written in Kotlin, forked from Metrolist (which is
itself based on InnerTune). It uses Jetpack Compose with Material 3.

## Project layout

| Path | Contents |
| --- | --- |
| `app/src/main/kotlin/com/metrolist/music/` | The app. UI is Compose-only; `res/layout` holds widget RemoteViews only. |
| `app/src/main/kotlin/com/metrolist/innertube/` | Networking and YouTube data models. |
| `innertube/` | The `com.metrolist.innertube` library module. |
| `app/schemas/` | Exported Room schemas, one JSON per version (currently `38.json`). |

The Kotlin package is still `com.metrolist.music`. That is the namespace and application
identity; do not rename it, or upgrading installs break.

## Rules for working on the project

1. Always pull the latest changes from `main` before starting your work to minimize merge conflicts.
2. Commit names should be clear and follow the format: `type(scope): short description`. For example: `feat(ui): add dark mode support`. Including the scope is optional.
3. All new string edits go in `app/src/main/res/values/metrolist_strings.xml`, NOT `strings.xml`. Do not touch other `strings.xml` or `metrolist_strings.xml` files in the project. ONLY edit the default (English) `metrolist_strings.xml` file, DO NOT EDIT OTHER LANGUAGES — the 60+ translated copies are maintained by translators.
4. You are to follow best practices for Kotlin and Android development.
5. DO NOT EDIT THE APP'S DATABASE SCHEMA. See "Data safety" below for the full rule.
6. The user-facing brand is **ReTune**. Any new user-facing string, comment, URL or export folder
   must say ReTune, not Metrolist. Historical string *keys* such as `metrolist` are kept so
   existing translations keep resolving — change the value, add new keys instead of renaming.

## AI-only guidelines

1. You are strictly prohibited from making ANY changes to the readme/markdown files, including this one, unless a human explicitly asks for it in the current request.
2. Unless explicitly requested, you are not allowed to commit, push, or merge any changes to any branch. If you are explicitly requested and authorized to commit/push/merge, you have the right to do so; the responsibility then lies with the author who requested it.
   - You should absolutely NOT use any commands that would modify the git history, do force pushes (except for rebases on your own branch), or delete branches without explicit instructions from a human.
3. Always follow the guidelines and instructions provided by human contributors.
4. Ensure the absolutely highest code quality in all contributions, including proper formatting, clear variable naming, and comprehensive comments where necessary.
5. Comments should be added only for complex logic or non-obvious code. Avoid redundant comments that simply restate what the code does.
6. Prioritize performance, battery efficiency, and maintainability in all code contributions. Always consider the impact of your changes on the overall user experience and app performance.
7. If you have any doubts ask a human contributor. Never make assumptions about the requirements or implementation details without clarification.
8. If you do not test your changes using the instructions in the next section, you will be faced with reprimands from human contributors and may be asked to redo your work. Always ensure that you test your changes thoroughly before asking for a final review.
9. You are not allowed to bump the version of the app unless a human explicitly asks for it in the current request. When you do bump it, bump **both** `versionCode` and `versionName` in `app/build.gradle.kts`. `BuildConfig.BASE_VERSION_NAME` is derived from `versionName` automatically.
10. Never commit signing keys. `app/keystore/`, `*.keystore` and `*.jks` are git-ignored on purpose.

## Interface guidelines

The UI is deliberately **minimalist and monochrome**:

- The app ships exactly one colour scheme, defined in `app/src/main/kotlin/com/metrolist/music/ui/theme/Theme.kt` as `ReTuneDarkColors` and `ReTuneLightColors`. Do not reintroduce dynamic colour, Material You, wallpaper palettes, or accent-colour pickers.
- All colour roles stay on a neutral grey axis. `error` is the single desaturated exception, so destructive states stay legible.
- `ReTuneShapes` keeps corner radii between 4dp and 16dp. Do not introduce radii above 16dp.
- Prefer removing UI over adding it. The About screen is intentionally only Buy Me a Coffee, Created by MO Agamy and Forked by JJDev.
- `PlayerBackgroundStyle` (blur / gradient from artwork) is a user-selectable content feature, not theming. Leave it alone.

## Data safety

Losing a user's library is the worst possible regression. When touching anything in this area:

- **Do not change the Room schema or the version number.** `MusicDatabase` is at version 38 with
  auto-migrations for every step from 2 upwards, plus four hand-written migrations. Add to the
  chain, never rewrite it.
- **The library database must stay on internal storage.** `AppModule.provideInternalDatabase`
  deliberately ignores the "use SD card" preference. A removable volume can vanish mid-session and
  SQLite in WAL mode on slow FAT32 media is both sluggish and corruption-prone. Only rebuildable
  caches (`exoplayer`, `download`, `coil`) may follow that preference.
- **Never silently relocate user data.** `StorageSettings.requestStorageLocation` copies cache
  content to the new location before switching. If you add another movable directory, add it to
  `MOVABLE_CACHE_DIRS`.
- `adoptLegacyExternalDatabase` imports a pre-14.0.0 SD-card database (plus its `-wal`) into
  internal storage on first launch. Do not remove it until 14.0.0 adoption is no longer relevant.
- Storage paths must go through `StorageUtils`, which verifies the volume is mounted and writable
  and falls back to internal storage. Do not call `Environment.getExternalStorageDirectory()` or
  build paths by hand.
- Disk polling must run on `Dispatchers.IO` and be gated on `repeatOnLifecycle(RESUMED)`. Walking
  a cache tree on a slow SD card is expensive.

## Memory and coroutine guidelines

- **Never create a `CoroutineScope` inside a function that can be called repeatedly.** Use the
  `@ApplicationScope` provider from `di/AppModule.kt` or `GlobalIoScope` from
  `utils/CoroutineScopes.kt`. A bare `CoroutineScope(Dispatchers.IO)` has no parent job, so nothing
  can cancel it and every call leaks one.
- **Never use `runBlocking` on a UI or service lifecycle callback.** It was removed from
  `MusicService.onDestroy` for a reason.
- Any new collection-backed map keyed by a growing value (track id, URL, path) must be bounded. See
  `StreamUrlCache` for the LRU pattern.
- Decode images with sub-sampling. `CoilBitmapLoader` caps artwork at 1024px on the long edge and
  uses `RGB_565` for opaque copies. Do not `recycle()` a bitmap that Coil's memory cache may still
  reference.
- `MaterialTheme.colorScheme` is static; a scheme can be cached with `remember` and no palette
  derivation is needed at runtime.

## Building and testing your changes

1. **Toolchain.** The build runs on JDK 17+ (`gradlew.bat` works with
   `C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot`). Compilation targets Java 21 via
   `jvmToolchain(21)`, which Gradle auto-provisions into `~/.gradle/jdks` because
   `gradle/gradle-daemon-jvm.properties` pins `toolchainVersion=21`. A full clean release build
   takes roughly 10–20 minutes on a typical workstation; a single `compileFossDebugKotlin` takes
   about 10 minutes. Budget for it and use the Gradle daemon (do not pass `--no-daemon`) when
   iterating.

   PowerShell example:

   ```powershell
   $env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot"
   .\gradlew.bat :app:compileFossDebugKotlin
   ```

2. Build the debug APK to check for compilation errors:

   ```powershell
   .\gradlew.bat :app:assembleFossDebug
   ```

3. If the build is not successful, review the error messages, fix the issues in your code, and try
   building again.

4. Release build with signing:

   ```powershell
   .\gradlew.bat :app:assembleFossRelease
   ```

   Output: `app/build/outputs/apk/foss/release/app-foss-release.apk`

   Variants are `foss` (F-Droid friendly, updater, no Cast), `gms` (adds Cast) and `izzy`
   (F-Droid compliant, no updater, no Cast). The default is `foss`.

5. Install a debug build on a device or emulator and ask a human to verify the features you touched:

   ```powershell
   & "$env:ANDROID_HOME\platform-tools\adb.exe" install -r app\build\outputs\apk\foss\debug\app-foss-debug.apk
   ```

## Signing

Release signing uses a JKS keystore at `app/keystore/release.keystore` (git-ignored). If it is
missing, generate it with `keytool` from a JDK 17+ install. It must be **JKS**, not PKCS12: PKCS12
forces the key password to equal the store password, and ReTune uses two different ones.

```powershell
New-Item -ItemType Directory -Path "app\keystore" -Force | Out-Null
& "C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot\bin\keytool.exe" `
  -genkeypair -v -keystore "app\keystore\release.keystore" -storetype JKS `
  -alias "opentune_key" -keyalg RSA -keysize 4096 -validity 10000 `
  -dname "CN=ReTune, OU=ReTune, O=ReTune, L=Unknown, ST=Unknown, C=US"
```

Then enter the store password and the key password at the prompts.

`app/build.gradle.kts` reads the signing settings in this order — `local.properties`, then the
environment, then the built-in defaults:

| `local.properties` key | Environment variable | Default |
| --- | --- | --- |
| `RETUNE_STORE_FILE` | `STORE_FILE` | `keystore/release.keystore` |
| `RETUNE_STORE_PASSWORD` | `STORE_PASSWORD` | `JNTX_Store_Secure_771` |
| `RETUNE_KEY_ALIAS` | `KEY_ALIAS` | `opentune_key` |
| `RETUNE_KEY_PASSWORD` | `KEY_PASSWORD` | `JNTX_Key_Secure_224` |
| `RETUNE_STORE_TYPE` | `STORE_TYPE` | `JKS` |

`local.properties` is also where `LASTFM_API_KEY` and `LASTFM_SECRET` go.

Verify a produced APK is signed with the expected key:

```powershell
& "$env:ANDROID_HOME\build-tools\37.0.0\apksigner.bat" verify --print-certs `
  app\build\outputs\apk\foss\release\app-foss-release.apk
```

Expect `certificate DN: CN=ReTune, OU=ReTune, O=ReTune, L=Unknown, ST=Unknown, C=US` and the
v2/v3 schemes to verify. (v1/JAR signing is intentionally absent: `minSdk` is 26.)

## Release process

1. Confirm the change set builds: `.\gradlew.bat :app:assembleFossDebug`.
2. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
3. Add an entry at the top of `release notes.md`.
4. Produce the signed APK: `.\gradlew.bat :app:assembleFossRelease`.
5. Verify the signature with `apksigner` as shown above and report the output path.
