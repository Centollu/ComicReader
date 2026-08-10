# AGENTS.md — ComicReader

This file helps AI agents and contributors quickly locate code when making changes.
UI strings, comments and commit messages in this project are written in **Spanish**.

## Project overview

Native Android comic reader (`.cbz` / `.cbr` archives). It scans local folders (and
optionally NFS shares served over HTTP), extracts pages into a cache directory, and lets
the user read comics page by page with zoom/pan gestures. Reading progress is persisted to a
local Room database and surfaced in a History tab.

- Package: `com.centollu.comicreader` (`applicationId` and namespace are identical)
- Min SDK 26, target/compile SDK 37, Java 17
- UI: Compose (Material 3) + Navigation-less tab switching (no navigation-compose graph; tabs are held in `MainActivity`)
- DB: Room (`comic_library.db`), entities `comics` and `reading_history`
- Archives: `commons-compress` (ZIP) + `junrar` (RAR)
- Images: Coil (`io.coil-kt:coil-compose`)
- Concurrency: coroutines + `StateFlow` (one `UiState` data class per screen's ViewModel)

## Build & test commands (Windows PowerShell)

```powershell
.\gradlew.bat assembleDebug            # build debug APK
.\gradlew.bat testDebugUnitTest        # run unit tests (JUnit4)
.\gradlew.bat installDebug             # build + install on connected device
```

**Gotcha — auto-incremented `versionCode`:** `app/build.gradle.kts` wires
`afterEvaluate { preBuild.dependsOn("incrementVersionCode") }`, so **every build bumps
`app/version.properties`** (`versionCode=76`, etc.). Do not edit that file by hand; the
project does this on purpose. `versionName` is derived as `1.0.<versionCode>`.

## Project structure

```
comic-reader/
├── app/
│   ├── build.gradle.kts            # module config + all dependencies + versionCode logic
│   ├── version.properties          # auto-generated versionCode (do not hand-edit)
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/centollu/comicreader/
│       │   │   ├── ComicApplication.kt
│       │   │   ├── MainActivity.kt
│       │   │   ├── data/
│       │   │   │   ├── model/          # Room entities
│       │   │   │   └── repository/     # DAO, DB (migrations), repository wrapper
│       │   │   ├── ui/
│       │   │   │   ├── components/     # reusable composables
│       │   │   │   ├── folders/        # "Carpetas" tab (file explorer)
│       │   │   │   ├── history/        # "Historial" tab
│       │   │   │   ├── library/        # "Biblioteca" tab (home)
│       │   │   │   ├── reader/         # full-screen reader
│       │   │   │   └── settings/       # "Ajustes" tab (NFS, grid, cache)
│       │   │   └── util/               # parsers, extraction/cache, NFS, prefs, storage
│       │   └── res/                    # themes, strings (Spanish), launcher icons
│       └── test/java/.../util/         # unit tests (JUnit4)
├── build.gradle.kts                 # root: plugin versions (AGP/Kotlin/KSP/compose)
├── settings.gradle.kts              # module list + repos (google, mavenCentral, jitpack)
├── gradle.properties
└── gradle/wrapper/
```

Ignore `app/build/`, `.gradle/`, `build/` — generated output.

## Source map (what lives where)

### Entry points
- `app/src/main/java/com/centollu/comicreader/MainActivity.kt` — permissions request
  (MANAGE_EXTERNAL_STORAGE on R+, READ/WRITE on older), immersive mode, tab Scaffold with
  bottom navigation, and the `Screen` sealed class (`Library`/`History`/`Folders`/`Settings`).
  It also hosts the **Reader overlay** state: when `activeReadingComicId != null`, the whole
  content is swapped for `ReaderScreen`.
- `app/src/main/java/com/centollu/comicreader/ComicApplication.kt` — minimal `Application`
  subclass (currently only calls `super.onCreate`).

### Data layer — `data/`
- `data/model/ComicDocument.kt` — Room entity `comics`. Fields: `_id`, `filePath`,
  `title`, `issueNumber`, `series`, `authors`, `publisher`, `storyArc`, `coverFilename`,
  `pageCount`, `addedTimestamp`, `isNfs`.
- `data/model/ReadingHistoryDocument.kt` — Room entity `reading_history`. Stores
  `lastPageOpened`, `totalPages`, `isCompleted`, `lastReadTimestamp`.
- `data/repository/ComicDao.kt` — all SQL (Flows for comics/history, upserts, metadata
  update, deletes).
- `data/repository/ComicDatabase.kt` — Room DB singleton `comic_library.db`, **version 3**,
  with `MIGRATION_1_2` and `MIGRATION_2_3` (both added `issueNumber` columns). Bump version
  + add a migration here whenever an entity changes.
- `data/repository/ComicRepository.kt` — the only layer used by ViewModels. Wraps the DAO
  and composes history upserts (`saveReadingProgress` sets `isCompleted` when
  `pageIndex >= totalPages - 1`).

### Util layer — `util/`
- `util/ComicExtractor.kt` — **the core archive engine.** Handles format detection by magic
  bytes, cover-only quick scan (`extractOnlyCover`), full extraction with incremental
  `onPageExtracted` callback (`extractComic`), partial-extraction marker (`.partial-extraction`),
  `ComicInfo.xml` reading, thumbnail generation, and the **FIFO extraction cache**
  (`cacheDir/extracted/<comicId>`, max size from `app_prefs`, default 5 GB). Depends on
  `NaturalOrderComparator` (defined at the bottom of this same file). Constants like
  `SUPPORTED_IMAGE_EXTENSIONS` and `COVER_PREVIEW_MAX_PAGES` are here.
- `util/ComicTitleParser.kt` — parses `"Title 003 (Digital) (extra)"` → title + `issueNumber`
  from the **filename**. Has unit tests; keep them green when changing it.
- `util/ComicInfoParser.kt` — parses `ComicInfo.xml` (XMLPullParser) into `ComicInfoFields`
  and applies them to a `ComicDocument`.
- `util/AppPrefs.kt` — SharedPreferences: library grid columns (1–6) and scanned folder list.
- `util/NfsManager.kt` — NFS server config persistence and
  `getInputStreamForPath()` (opens `nfs://`/`http(s)://` via `URL`, local paths as `File`).
  Also `scanLocalDirectory`. Note: NFS support is **HTTP-based**, there is no NFS protocol client.
- `util/StorageVolumes.kt` — enumerates Android storage volumes for the folder picker.

### UI layer — `ui/`

**Tab | Screen / ViewModel | Purpose**
- Library | `library/LibraryScreen.kt` + `library/LibraryViewModel.kt` | The home tab.
  Grid of comics (columns from settings), search bar with filter chips
  (`ALL`, `TITLE`, `AUTHOR`, `SERIES`, `PUBLISHER`, `ARC`) and sort options
  (`NAME`, `PATH`, `DATE`) — **see `getFilteredComics()` in the ViewModel**. Long-press menu
  per comic (change cover, edit metadata, delete), FAB to add a file / scan a folder,
  refresh dialog (rescan folders + `updateAllMetadata` reading ComicInfo.xml), edit-metadata
  dialog, `FolderPickerDialog` (line ~403), `ComicGridItem` (line ~593) and
  `CoverSelectorDialog` (line ~736).
- History | `history/HistoryScreen.kt` + `history/HistoryViewModel.kt` | Recent reads
  ordered by `lastReadTimestamp` desc; resume reading at the saved page; delete item / clear all.
- Folders | `folders/FoldersScreen.kt` + `folders/FoldersViewModel.kt` | File explorer over
  storage volumes that opens `.cbz`/`.cbr` files directly; `FoldersViewModel.openComic()`
  registers the comic in the library if missing (`ensureComicInLibrary`).
- Reader | `reader/ReaderScreen.kt` + `reader/ReaderViewModel.kt` | **Full-screen paged
  reader.** `HorizontalPager`, pan/zoom gestures (1x–8x), tap-to-page corners when not
  zoomed, double-tap to zoom/reset, extraction progress overlay. `ZoomableImage` (line ~235)
  is the gesture host. `ReaderViewModel.loadComic()` extracts pages incrementally (pages
  stream into `pageFiles` while reading) and calls `saveProgress()` on every page change.
- Settings | `settings/SettingsScreen.kt` | NFS server IP/path/enable switch, grid columns
  slider, cache size slider (triggers `trimCache`), about card. Reads/writes via
  `NfsManager`, `AppPrefs`, `ComicExtractor` (cache getters/setters).
- Components | `components/VerticalSliderBar.kt` | Vertical scroll-bar knob for lazy
  lists/grids (drag thumb → `scrollToItem`).

## Flow: library scanning (frequent change area)

`LibraryViewModel`:
1. `addComicFromFile()` — copies a picked file into `filesDir`, parses filename, runs
   `extractOnlyCover` (page count + thumbnail) and applies ComicInfo metadata.
2. `scanLocalPath()` / `rescanFolders()` — walks folders (recursively, `.cbz`/`.cbr`),
   adds missing comics via `addComicFilesInFolder()`, and in `rescanFolders()` deletes
   library entries whose file no longer exists **only if the file is under a scanned root**.
3. `updateAllMetadata(context)` — re-reads `ComicInfo.xml` for every comic with progress
   % + ETA (`metadataProgress`, `metadataEta`).

Scan roots are persisted in `AppPrefs.getScannedFolders()`.

## Flow: reader (frequent change area)

- `ReaderViewModel.loadComic(comicId, initialPageIndex)` — fetches comic from DB, streams
  extraction through `onPageExtracted` into `pageFiles` (sorting with `NaturalOrderComparator`),
  then maps `initialPageIndex` → `currentPageIndex` and saves initial history progress.
- Page changes flow `ReaderScreen` → `viewModel.onPageChanged(newPageIndex)` → `saveProgress`.
- Extraction reuse: `ComicExtractor.extractComic` keeps fully-extracted dirs in cache and
  resumes partial extractions (see `PARTIAL_MARKER_FILENAME`).

## Flow: search & metadata (frequent change area)

- Filtering/sorting lives in `LibraryViewModel.getFilteredComics()`; UI chips/sort menu in
  `LibraryScreen` (lines ~150–200).
- Metadata sources, in order: `ComicTitleParser` (filename) → `ComicInfo.xml`
  (`ComicExtractor.readComicInfoXml` + `ComicInfoParser`). Manual edits go through
  `LibraryViewModel.updateComicMetadata()` → `ComicDao.updateComicMetadata()`.

## Conventions to follow

- **Language:** Spanish for UI strings, comments, and error messages. New composables,
  `UiState` data classes etc. follow this.
- **Layered access:** ViewModels → `ComicRepository` only (never touch `ComicDao` directly
  outside `ComicRepository`, except migrations). `ComicDatabase.getInstance()` is a singleton.
- **State:** one `data class <X>UiState` per ViewModel, exposed as `StateFlow`; screens read
  `uiState` and collectStateWithLifecycle / LaunchedEffect.
- **async:** use `viewModelScope.launch(Dispatchers.IO)` for disk/archive work; never block
  the main thread with archive extraction.
- **Room changes:** bump `ComicDatabase.version`, add a `Migration`, register it in the
  builder, update `ComicDao`/`ComicRepository`/`entities` consistently.
- **Tests:** add/update `app/src/test/java/.../util/` JUnit4 tests when touching pure
  parsing logic (`ComicTitleParserTest`, etc.). Unit tests live in `src/test`, not `src/androidTest`.
- **Resources:** strings in `res/values/strings.xml` (Spanish); theme in
  `res/values/themes.xml`. Titles of tabs are defined in `MainActivity.Screen`.

## Known gotchas / pending fixes

- **Settings About card says “Motor BD: MongoDB Realm (Local)” but the app now uses
  **Room/SQLite** (`ComicDatabase`). That text is stale (`SettingsScreen.kt`, About card) —
  left as-is intentionally; fix when touching Settings.
- `versionCode` auto-increments on every build (see “Build & test commands”). Don't refactor
  this without telling the user, it's intentional.
- NFS is HTTP-based (`NfsManager.getInputStreamForPath`): the IP/path must be reachable via
  `http://<ip>/<path>`; there is no real NFS protocol support.
- The reader exposes only a subset of gestures by design (single-finger pan switches page
  only outside zoom; tap zones only when not zoomed). Resist “simplifying” these without
  checking `ZoomableImage`.