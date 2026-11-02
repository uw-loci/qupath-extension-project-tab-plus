# Changelog

All notable changes to Project+ are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow semantic versioning.

## 0.1.0 (unreleased)

First release. Requires QuPath 0.7.0 or later. Built and tested on Linux and Windows; macOS
not yet verified (see the README).

### Added

- A second **Project+** tab beside QuPath's own Project tab, with the built-in tab left
  untouched and an option to hide it.
- Grouping of the project's images by any number of metadata keys or built-in fields
  (image name, file name, entry ID, image type), in any order, with `(Unassigned)` for
  missing values and group rows that name their key (`Patient: 12 (2)`).
- The current image marked with a tinted, bold row and an `[open]` chip, and
  **Jump to current image** (Ctrl+J) that expands the tree and scrolls to it, clearing a
  search that hides it.
- Saved-data indicators on every thumbnail (solid top edge annotations, hatched right edge
  detections, solid bottom edge TMA cores) read from the summary QuPath writes at each save,
  with a badge style and a legend; the open image tracks the viewer with dashed edges and widely spaced
  hatching while it has unsaved changes.
- Named display profiles (grouping, expand-on-load, fields, thumbnail size, indicators)
  stored per user in `profiles.json`, with the last-used profile remembered per user and per
  project.
- Search with QuPath 0.7's own `name` and `key=value` syntax plus `has=annotations`,
  `has=detections` (or `has=cells`), `has=tma` and `has_data=`, and key/value filter combos.
- **Remove current image...**, with an optional, separately confirmed move of the image
  file to the operating system trash, and a busy strip (`Closing and removing <name>`)
  while the image closes. Offered only for single-file images on a local disk; UNC network
  paths are refused because they have no trash.
- Checkbox mode: check images by hand, by search or by group, then **Remove from
  project...**, **Clear saved data...** or **Export GeoJSON...** for all of them (and
  **Open image (exactly one checked)**), each
  behind its own confirmation with **Cancel** as the default button, each cancelable from
  the busy strip, each reporting per-image failures.
- GeoJSON export writes `export-manifest.json` beside the files (versions, options,
  timestamp, and per file the entry ID, file name, source, object count and, when names are
  not masked, the image name). While **Mask image names** is on, files are named
  `<entry ID>.geojson`. Export options are remembered between sessions; compression is off
  by default, as in QuPath.
- INFO log lines at the start and end of every bulk job and single removal, with counts,
  options and entry IDs (never image names).
- Preferences: hide the built-in tab, custom current-image highlight color, select the tab
  on startup.
- Keyboard: Ctrl+F, Escape, Enter, Space, Ctrl+J, F5, Ctrl+Shift+N (check all shown),
  Ctrl+Shift+D (clear checks), all scoped to the tab.
- Unit tests for the tree builder, filter, profile store, delete eligibility, summary reader,
  export naming, export manifest and bulk jobs, plus six hidden-GUI harness scenarios under
  `harness/`.

### Not in this release

Raised in testing and deferred to a later version:

- Project-wide summary table export (CSV of each image's metadata and object counts).
- Storing the profile body in the project, so a shared project carries its grouping to
  machines that lack the profile. For now only the name travels, and the status line warns
  when it is missing.
- Caching per-entry file checks across filter rebuilds (one `Files.exists` per entry per
  keystroke today; measured at 205 ms for 800 entries on a local disk on the hidden-GUI
  harness, 150 ms of it the debounce; untested on a share).
- A bound on the thumbnail cache (one image per entry for the life of the project).
- Detecting mapped network drives, which cannot be told apart from local disks; a file on
  one may be deleted outright by Windows when moved to the trash.
- A scripting API.

The full list of limits is in the user guide under
[Limits and known issues](docs/user-guide.md#limits-and-known-issues).
