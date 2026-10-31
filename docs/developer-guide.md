# Project+ -- Developer Guide

For contributors, reviewers and the maintainer. What the tab does for a user is in the
[user guide](user-guide.md); this page is how it is built, how it is tested and how it is
released. Terms (entry, current image, checked images, saved data) are defined once in the
user guide's [glossary](user-guide.md#glossary) and used the same way here.

<details open>
<summary><strong>Building from source</strong></summary>

### Building from source

```bash
./gradlew clean shadowJar
```

- Gradle 9.2.1 on the wrapper. JDK 17 through 25 build it; the bytecode target is
  `release(21)` because QuPath 0.7 runs on Java 21.
- Compiled against QuPath 0.7.0, which is also the minimum supported version. Two 0.7-only
  APIs are relied on, `GeneralTools.moveToTrash(File)` and
  `qupath.lib.gui.panes.ProjectEntryPredicate`; there is no 0.6 build.
- Output: `build/libs/qupath-extension-project-tab-plus-<version>-all.jar`. Drop it into
  `~/QuPath/v0.7/extensions/` (or your platform's QuPath extensions directory) and restart
  QuPath; extensions do not load on the fly.
- The build script is copied from `qupath-extension-project-metadata-browser`: shadow 8.3.5
  (nothing relocates, so do not move to 9.x), `qupath-conventions`, spotbugs, the Gradle 9
  `testRuntimeOnly("org.junit.platform:junit-platform-launcher")` line (without it `test`
  dies with *Failed to load JUnit Platform*), and the JVM-25 attribute workaround for QuPath's
  published metadata. Leave each of these alone unless you know why it is there.
- Unit tests: `./gradlew test`. Formatting: `./gradlew spotlessApply` before a push.
- Smoke test from WSL: the monorepo's `WSL_LAUNCH.md` explains launching the dev QuPath with
  WSLg so the tab renders on the Windows desktop.

</details>

<details>
<summary><strong>Architecture overview</strong></summary>

### Architecture overview

Package root `qupath.ext.projecttabplus`, Maven group `io.github.michaelsnelson`, automatic
module `io.github.michaelsnelson.extension.projecttabplus`. Layout as in the metadata
browser (`core/`, `model/`, `ui/`) plus `preferences/` as in class-visibility. A generated
`codemap/codemap.json` at the repo root, when present, lists every class and its imports.

| Class | Responsibility |
|---|---|
| `ProjectTabPlusExtension` | Entry point. Creates the tab on the FX thread, inserts it after the built-in Project tab, registers the `Project+` preference category, wires the refresh triggers, and applies "hide built-in tab" in a second `Platform.runLater` after install so Active Image Helper's lookup normally runs first. |
| `ui/ProjectTabPlusPane` | The tab content (`BorderPane`, style class `project-tab-plus`, never `project-browser`): toolbar rows, busy strip, `TreeView`, status line, search bar, checkbox-mode bar. Owns the `ObservableSet<String>` of checked entry IDs. |
| `ui/EntryTreeCell` | `TreeCell<Row>`: checkbox (mode on), thumbnail canvas with indicator edges, name, fields line; style classes `ptp-current-image` and `no-saved-data`. Reads nothing from disk on the FX thread. Group branch renders label, image count, tri-state box and collapsed-group chips. |
| `ui/DisplaySettingsDialog` | Non-modal: grouping levels with Add/Remove/Up/Down, live group-count line, fields checkboxes, appearance radios. Applies live; closes on project switch. |
| `ui/ProfileManagerDialog` | Window-modal Manage profiles dialog plus the Save-as and Rename input dialogs. |
| `ui/RemoveImageDialogs`, `ui/BulkDialogs` | Dialogs D1-D7 and the overwrite prompt, text verbatim from the design, every one through `qupath.fx.dialogs.Dialogs.builder()` with an owner. |
| `ui/IndicatorLegendDialog` | Non-modal legend text with three labeled swatches. |
| `ui/Strings` + `strings.properties` | Every user-facing string, ASCII only. |
| `ui/ThemeColors` | `ladder()` accent, muted, warning and the teal detections color (lifted from QuIET). |
| `project-tab-plus.css` | The current-image row tint, thumbnail drop shadow, dashed-edge pseudo-class. No hardcoded white or black. |
| `model/Row` (sealed: `GroupRow`, `ImageRow`) | Tree value type. `ImageRow` holds entry ID, name, a metadata snapshot and the `ObjectPresence`. |
| `model/GroupingKey`, `model/GroupingSpec` | Ordered grouping levels: metadata key or built-in (`IMAGE_NAME`, `FILE_NAME`, `ENTRY_ID`, `IMAGE_TYPE`); `(Unassigned)` / `Undefined` defaults; name levels hidden while names are masked; first level from the project's `_SORT_KEY`. |
| `model/TreeBuilder` | Pure `build(entries, spec, predicate) -> root GroupRow` plus an entry-ID-to-path index. One bucket pass per level, runs off the FX thread. Unit-tested. |
| `model/EntryFilter` | Wraps `ProjectEntryPredicate.createIgnoreCase(text)` for the 0.7 syntax, strips and handles the `has=annotations\|detections\|tma` tokens itself (they need `ObjectPresence`), and adds the key/value combos. Unit-tested. |
| `model/DisplayProfile`, `model/ProfileStore` | The `profiles.json` schema; atomic write (temp file + `ATOMIC_MOVE`) to `<QuPath user dir>/project-tab-plus/profiles.json`, or `<home>/QuPath/project-tab-plus/profiles.json` with a logged warning when QuPath has no user directory; `Default` always present; last-used profile in a PathPrefs key and per project under `_PROJECT_TAB_PLUS_PROFILE`. Unit-tested with a temp dir. |
| `core/ObjectPresence`, `core/SummaryReader` | Gson POJO for `summary.json` (`hierarchy.objectTypeCounts`, `imageType`), parsed on the summary thread, cached by file mtime; states UNKNOWN / NONE / PRESENT with counts. Never calls `readHierarchy()` in a sweep. A live reader on the current image's hierarchy, debounced, sets the dashed (unsaved) state. |
| `core/ThumbnailLoader` | One daemon thread; `entry.getThumbnail()` off FX; failed-server memo by entry ID, cleared on Refresh. |
| `core/JumpToCurrent` | `project.getEntry(imageData)` -> index -> expand parents -> select -> scroll in `Platform.runLater`; clears the search and combos when the row is hidden. |
| `core/RemoveImageCommand` | Close viewers (`qupath.closeViewer`, abort on cancel) -> `imageData.close()` -> optional `FileTrash` -> `project.removeImage(entry, dataToTrash)` -> `syncChanges()` -> `qupath.refreshProject()`. |
| `core/DeleteEligibility` | Pure: one URI, scheme `file`, exists, extension on the allow-list, no `.companion.ome` sibling or `.ome.tif` set for `.tif`/`.tiff`, no other entry shares the URI; else a reason enum mapped to the D1 inline texts. Unit-tested. |
| `core/FileTrash` | Checks `Desktop.isSupported(MOVE_TO_TRASH)` first so D3 can name "no trash support", then `GeneralTools.moveToTrash(File)` on the bulk thread. Result: moved / unsupported / failed. Never a permanent delete; `GeneralTools.deleteFile(f, true)` is the call NOT to make. |
| `core/BulkJobs` (`BulkRemove`, `BulkClearData`, `BulkExportGeoJson`) | Serial, cancelable, progress callback, per-entry failure list, one summary. Clear = `readImageData` -> `clearAll()` -> `saveImageData` -> `close()`. Export = `PathIO.exportObjectsAsGeoJSON` with `stripInvalidFilenameChars(name)` and ` (<entryId>)` on collision; entries open in a viewer are exported from a snapshot of the live hierarchy taken on the FX thread before the job starts, every other entry from `readHierarchy()`. Clear leaves no backup: `saveImageData` deletes `data.qpdata.bkp` once the new file is written. |
| `core/RefreshCoordinator` | Debounced rebuild (150 ms filter, 250 ms built-in root hook) fed by `projectProperty`, `imageDataProperty`, the built-in TreeView's `rootProperty()` (looked up once via `.project-browser`, trigger only), Refresh/F5 and an image-list diff on tab selection. Preserves expansion by group path, selection and checked IDs. |
| `preferences/ProjectTabPlusPreferences` | `hideBuiltInProjectTab` (false), `useCustomHighlightColor` (false, meaning the theme tint) and `currentImageHighlightColor`, `selectTabOnStartup` (true), `lastProfile`; preference category `Project+` via `PropertyItemBuilder`. |

Everything in `model/` and `core/` except the two command classes is plain Java without
`QuPathGUI`, which is what makes it unit-testable.

### Threads

| Thread | Does |
|---|---|
| FX thread | `setRoot`, cell updates, dialogs. Nothing else. |
| `ptp-thumbnail` (1 daemon) | Thumbnails; failed-server memo. |
| `ptp-summary` (1 daemon) | `summary.json` parse, mtime cache; drives the `Loading saved-data summaries i of N` strip. |
| `ptp-tree` (1 daemon, latest wins) | Grouping and filter rebuilds; the display settings count line. |
| `ptp-bulk` (1 daemon, one job at a time, cancelable) | Remove, clear, export; also `imageData.close()` and `FileTrash` for the single remove. |

### Data flow

1. Project opens -> `RefreshCoordinator` -> `TreeBuilder` with the profile named in
   `_PROJECT_TAB_PLUS_PROFILE` (else last-used, else `Default`) -> `setRoot` -> visible
   cells request thumbnails and summaries.
2. Image opens -> `ptp-current-image` moves to its row -> live hierarchy listener attached ->
   select and scroll if visible (never clears a filter).
3. Save -> `summary.json` mtime changes -> cache miss on the next visible update -> indicator
   refresh; the current image's row drops the dashed state.
4. Profile switch -> D8 if modified -> `ProfileStore` -> rebuild -> PathPrefs `lastProfile`
   and project `_PROJECT_TAB_PLUS_PROFILE` written.
5. Checkbox-mode action -> D4/D5/D6 -> `BulkJobs` on `ptp-bulk` -> busy strip with Cancel ->
   notification on success or D7 on failures -> `syncChanges()` + `qupath.refreshProject()`.

### Coexistence rules

- Root style class `project-tab-plus`; Active Image Helper's CSS is scoped to
  `.project-browser`, so neither tab styles the other.
- Hide = remove the built-in `Tab` and keep the reference; show = re-insert at the remembered
  index. An undocked built-in tab gets notification D11 and the preference stays unticked.
- Removal from either tab converges through `qupath.refreshProject()` and the root hook.
- Deliberately not used: `GeneralTools.deleteFile(f, true)` (permanent-delete fallback),
  `CheckBoxTreeItem` / `CheckBoxTreeCell` (state lost on rebuild), anything private in
  `ProjectBrowser`, any dependency on another extension's jar.

</details>

<details>
<summary><strong>Tests and harness</strong></summary>

### Tests and harness

- `./gradlew test` runs the JUnit suite: `TreeBuilder`, `EntryFilter`, `ProfileStore`,
  `DeleteEligibility`, `SummaryReader` and the export naming.
- End-to-end scenarios live in `harness/` in this repo and run through the monorepo's
  `tools/qp-harness` (`bin/qp-gui` drives a hidden `QuPathGUI`; `bin/qp-dataset synthetic-tme`
  builds a ground-truthed project to run them on). Scenarios planned for this release: tab
  insert / hide / restore, remove-current with a dirty viewer (cancel path), bulk clear with
  one open entry, export with a name collision, profile round-trip through `profiles.json`.
  <!-- TODO(dev): list the scenario class names once they exist in harness/. -->
- Checks a human still has to make (trash support on Windows and macOS, dashed edges on a
  HiDPI display, summary sweep timing on a network share) are queued in the monorepo's
  `claude-reports/MANUAL_TEST_QUEUE.md` under this repo's name.

</details>

<details>
<summary><strong>Scripting API</strong></summary>

### Scripting API

None in 0.1.0. Everything the tab does is reachable from its controls only. If a scripting
surface is added later, the natural shape is: apply a named profile, return the checked entry
IDs, and run the three checkbox-mode actions headlessly. This section stays so the heading
set matches the other extensions.

</details>

<details>
<summary><strong>Contributing</strong></summary>

### Contributing

- Issues: say which platform and QuPath version, and paste the relevant lines from
  **View > Show log**.
- Pull requests run `./gradlew spotlessApply test` first.
- ASCII only in code, logs, dialog strings and `strings.properties` (production QuPath runs
  on Windows with cp1252; a Unicode arrow in a log line has hung workflows before). Markdown
  may use Unicode.
- American English.
- Never add a permanent-delete path. The only file deletion is `FileTrash`, and it goes to the
  operating system trash or fails.
- Any change to a dialog body or tooltip updates the matching user-guide and troubleshooting
  text in the same commit. They are one artifact in two forms.
- Keep the four destructive verbs apart: remove from project, move saved data to the trash,
  clear saved data, move file to the trash. "Delete" is never used for files or data folders.

</details>

<details>
<summary><strong>Releasing</strong></summary>

### Releasing

1. Bump the version in `build.gradle.kts`, tag `vX.Y.Z` and push the tag. The release workflow
   builds and attaches the `-all.jar`.
2. `.github/workflows/notify-catalog.yml` dispatches `extension-release` to
   `uw-loci/qupath-catalog-mikenelson`, whose workflow prepends the new release to this
   extension's entry in `catalog.json`. It needs the org-level `CATALOG_DISPATCH_TOKEN`
   secret. After the first release verify that the workflow ran, that the catalog got an
   `Auto-bump <repo> -> <tag>` commit within about a minute, and that the new entry's
   `main_url` returns HTTP 200 and matches the published asset name.
3. If the secret did not reach this repo, hand-edit `catalog.json` on the catalog's default
   branch (`master`): **prepend** the new release and keep every prior entry. Replacing the
   first entry drops the installed version from the list and QuPath can then neither match
   the installed extension nor offer the update.
4. `version_range.min` must be `0.7.0` (the compile floor) and at or below every QuPath
   version in use; no `max`.
5. The catalog's raw URL is cached for about five minutes and QuPath fetches it once per
   process, so to see the update offered: wait five minutes, fully restart QuPath, then
   **Help > Check for updates (web)**.
6. Run the `/lawyers` license review before the first tag, because of the mixed GPL-3.0 and
   Apache-2.0 lifts listed in the README.
7. Add every platform claim the release does not verify to the monorepo's
   `claude-reports/MANUAL_TEST_QUEUE.md`, tagged with this repo, and keep the README's
   "not yet verified" sentence truthful.

</details>
