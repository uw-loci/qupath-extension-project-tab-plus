# QuPath Project+

A second Project tab for QuPath, for projects with hundreds of images.

A QuPath extension, version 0.1.0. Requires **QuPath 0.7.0 or later**.

## Overview

Project+ is a second Project tab for QuPath. It shows the images in your project as a tree
grouped by the metadata keys you choose, in the order you choose, so a project of several
hundred images can be read as *patient > stain > angle* instead of one long list. The image
open in the viewer is marked so you can find it at a glance, and a button takes you straight
to it. Each entry shows whether its saved data holds annotations, detections or a TMA grid, so
you can see which images still need work without opening them. Named display profiles
remember a grouping and its settings for next time. A checkbox mode lets you check many
images by search or by metadata value and then remove them from the project, clear their
saved objects, or export those objects to GeoJSON in one step.

It is for anyone who runs projects too large to scroll: graduate students working through a
slide list, scientists and pathologists juggling several staining batches, and lab heads
reviewing what has been done. It adds a tab; it does not change QuPath's own Project tab,
which you can keep or hide.

## Do you need this?

**Probably not, for a 20-image project with one metadata key.** The built-in Project tab's
**Sort by...** already groups a list by one key, and it is open, familiar and fast.

Project+ earns its place when you group by two or more keys, when you lose the current image
in a long tree, when you want to see which images have been annotated without opening each
one, or when you need to act on many entries at once. Below that point a second tab is not
worth the screen space.

## What it adds, and what it never does

- **Grouping by several metadata keys**, in any order, with `(Unassigned)` for images that
  lack a value.
- **The current image marked** with a tinted, bold row and an `[open]` chip (the group rows
  above it take the tint and an `[open inside]` chip while it is collapsed out of view), and a
  **Jump to current image** button that expands the tree and scrolls to it. The tint follows
  the theme, or a color of your own from **Set current image highlight color...** in the
  right-click or gear menu (also in Edit > Preferences > Project+).
- **Saved-data indicators** on each thumbnail: solid top edge for annotations, hatched (`////`)
  right edge for detections, cells and tiles, a row of half circles (half TMA cores) along the
  bottom for TMA cores. The three differ by shape, not only by color. A dashed edge, widely
  spaced hatching and outlined half circles mean the current image has unsaved changes.
- **Display profiles**: named, saved sets of grouping, fields, thumbnail size and indicators,
  stored per user and applied to any project.
- **Remove current image**, with an optional, separately confirmed move of the image file to
  the operating system trash.
- **Checkbox mode**: check images by hand, by search or by metadata value, then remove them,
  clear their saved data, or export their objects to GeoJSON.

It never:

- permanently deletes a file. Its only file deletion is a move to the operating system
  trash, from where the file can be restored. One case it cannot detect: a mapped network
  drive looks like a local disk, Windows keeps no Recycle Bin for it, and a file there may be
  deleted outright; read the
  [user guide](docs/user-guide.md#remove-the-current-image-and-optionally-move-its-file-to-the-trash)
  before using the file option on a share. UNC `\\server\share` paths are refused outright;
- changes the built-in Project tab's data or behavior. It can hide that tab and show it
  again; nothing else;
- moves image files in bulk. Bulk removal takes entries out of the project and leaves every
  image file where it is;
- edits object data except through the two named actions, **Clear saved data...** and
  **Remove from project...**, each behind its own confirmation.

Limits that matter at scale (the per-keystroke file check on large projects, the memory held
by thumbnails, network shares, which platforms were verified) are listed in the user guide
under [Limits and known issues](docs/user-guide.md#limits-and-known-issues).

## Installation

**Manual install (now).** Project+ is not yet in an extension catalog. Download
`qupath-extension-project-tab-plus-<version>-all.jar` from the
[Releases page](https://github.com/uw-loci/qupath-extension-project-tab-plus/releases), then
drag it onto the QuPath window, or copy it into your QuPath extensions directory. Restart
QuPath either way: it does not load new extensions on the fly.

**Catalog install (after the first release).** Once 0.1.0 is released it will be listed in
the catalog `https://github.com/uw-loci/qupath-catalog-mikenelson`. In QuPath, open
**Extensions > Manage extensions**, add that catalog if it is not already listed, and install
*Project+* from it. Installing from the catalog is what lets QuPath tell you when a new
version is out; a hand-installed jar gets no update notifications. To update later, open
**Extensions > Manage extensions**, click the gear on the Project+ row (tooltip *Update
extension*), choose the version and click **Update**, then restart QuPath.

## Quick start

A *metadata key* is a label-and-value pair attached to an image in the project, such as
`Stain = HE`. To add one, right-click an image in either Project tab and choose
**Add metadata...**.

1. **Open a project.** In QuPath, choose **File > Project > Open project...** and pick a
   folder that holds a `project.qpproj` file. (A *project* is QuPath's folder for a set of
   images plus the annotations you save on them. If you have none yet, **File > Project >
   Create project...** makes an empty one, then **Add images...** puts files into it.)
2. **Click the Project+ tab.** It sits in the panel on the left of the QuPath window, right
   next to the built-in **Project** tab. If you do not see it, the extension is not installed
   or QuPath has not been restarted since you installed it.
3. **Click Group by** (the grid icon, second from the left in the toolbar's second row; its
   tooltip reads `Choose which metadata keys group the list, and in what order.`). The
   **Project+ - Display settings (Default)** dialog opens; the title names the profile in use.
4. **Click Add level** and pick a metadata key, for example `Patient`; add `Stain` the same
   way. If the project has no keys yet, **Add level** shows only the four built-in fields and
   a grayed line `This project has no metadata keys yet.`; add a key to at least one image
   and reopen the menu.

   Group rows now read as key, value and count: `Patient: 12 (2)` is patient 12 with two
   images. Images with no value for a key sit under **(Unassigned)**. On first open, Project+
   starts from the key the built-in tab was already sorting by, if any.

   With the default **Expand on load** of *first level*, images under a second-level group
   stay hidden until you expand it; choose *all* while you learn the tree.
5. **Open an image and find it again.** Double-click any entry to open it in the viewer. Its
   row is now tinted, bold and marked `[open]`. Scroll anywhere, then click
   **Jump to current image** in the tab's toolbar (or press Ctrl+J): the tree expands and
   scrolls back to it, and the status line above the search box reads `Jumped to <image
   name>`.
6. **Save the setup as a profile.** Open the gear menu and choose **Save profile as...**,
   give it a name such as `By patient and stain`, and it appears in the profile combo (the
   dropdown at the top of the tab). The status line confirms `Saved profile "By patient and
   stain"` for a few seconds, and the ` *` after the combo name disappears. Next session, pick
   the profile from the combo and the same grouping, fields and thumbnail size come back.

That is the everyday loop. The [user guide](docs/user-guide.md) covers the rest: reading the
indicators on each entry, removing the current image, and checkbox mode for acting on many
images at once.

![The Project+ tab on Windows, light theme, profile Default with a trailing asterisk, grouped three levels deep. Group rows read base_image: MetroHealth_FNA_PDAC_1 (7), acquired_on_microscope: PPM (5), angle: -7.0 (1), and so on; each image row shows a thumbnail, the image name and a modality: ppm line; some thumbnails carry a red top edge and a hatched teal right edge; one row is tinted, bold and marked [open]; the status line reads 52 images, 1 open.](images/tab-grouped.png)

*The Project+ tab on Windows, grouped by `base_image`, then `acquired_on_microscope`, then
`angle`, with `modality` shown as a field under each name. The red top edge and hatched teal
right edge on a thumbnail mean that image's saved data holds annotations and detections. The
open image's row is tinted, bold and marked `[open]`. The ` *` after `Default` in the profile
combo means the grouping has not been saved to the profile yet.*

![The same tab with the angle: 90.0 group collapsed. The rows base_image: MetroHealth_FNA_PDAC_1 (7), acquired_on_microscope: PPM (5) and angle: 90.0 (1) are bold and end with an [open inside] chip; the collapsed angle row also shows the chips A D -.](images/ancestor-highlight.png)

*The same project with the open image's group collapsed: every group row on the path down
to it is tinted, bold and marked `[open inside]`, and the collapsed group shows `A D -`, meaning
an image under it has annotations and detections but no TMA cores.*

## Where things are

| What | Where |
|---|---|
| The **Project+** tab | QuPath's analysis pane, immediately right of the built-in **Project** tab. Right-click the tab and choose **Undock tab** to move it into its own window. One more tab makes the pane's tab row overflow at the default width, so **Workflow** moves into the dropdown arrow at the right end of the row. Widen the pane, or hide the built-in tab (see below), to get the room back. |
| Profile combo and gear menu | First toolbar row inside the tab. The gear holds profile commands, **Edit display settings...**, **Thumbnail size**, **Indicators**, **Create project...** / **Open project...** / **Add images...**, **Remove current image...**, **Jump to current image (Ctrl+J)** (`Cmd+J` on macOS), **Show built-in Project tab**, **Indicator legend...** and **Set current image highlight color...**. |
| Jump, Group by, Checkbox mode, Refresh, Legend buttons | Second toolbar row. |
| Search box and key/value filter | Bottom of the tab, where the built-in tab keeps its filter. |
| Right-click menu | Any row: the built-in tab's items plus **Jump to current image**, **Remove current image...**, **Check** / **Uncheck** (in checkbox mode), **Group by...** and **Set current image highlight color...**. |
| Preferences | **Edit > Preferences**, category **Project+**. |

![The gear menu open over the tab on Windows. Items in order: Save profile (grayed), Save profile as..., Rename profile... (grayed), Delete profile... (grayed), Manage profiles...; Edit display settings..., Thumbnail size, Indicators; Create project..., Open project..., Add images...; Remove current image... (grayed), Jump to current image (Ctrl+J) (grayed); Show built-in Project tab (checked), Indicator legend..., Set current image highlight color.... Behind it, collapsed group rows show chips such as A D - and - - -.](images/profile-menu.png)

*The gear menu. No image is open, so **Remove current image...** and **Jump to current image
(Ctrl+J)** are grayed; the profile is **Default**, so **Rename profile...** and **Delete
profile...** are grayed too. Collapsed groups behind the menu show `A D -` or `- - -`, one
letter per object type that some image under them holds.*

## Coexistence with other extensions

**Active Image Helper** decorates the built-in Project tab; Project+ has its own tab. Both
mark the current image, each in its own tab, and removing an image from either side updates
both. One known interaction: if the built-in tab is hidden when QuPath starts, the helper
cannot find it and its menu items stay missing from the built-in tab until the next project
change after you show the tab again. The user guide's
[troubleshooting section](docs/user-guide.md#troubleshooting) has the recovery steps. If the
helper's three features on the built-in tab are all you want, install the helper instead.

**Project Metadata Browser** is the table view of the same metadata. Project+ reads metadata
to group and filter; it edits it only through the per-image **Add metadata...** item (the built-in tab's
**Add metadata**).

## Documentation

- [User guide](docs/user-guide.md) -- every control, the search syntax, the glossary, settings
  and troubleshooting.
- [Developer guide](docs/developer-guide.md) -- building, architecture, threads, tests and
  releasing.

## Reporting a problem

File an issue at <https://github.com/uw-loci/qupath-extension-project-tab-plus/issues>. The
bug template asks for your platform, the QuPath version, the extension version and the lines
from **View > Show log** around the time of the problem. Release notes are in
[CHANGELOG.md](CHANGELOG.md).

**Built and tested on Linux and Windows; macOS not yet verified for this release.** The
Windows run (Windows 11, QuPath 0.7.0, 150% display scale) exercised the Recycle Bin step;
Linux desktops and WSL often report no trash support, so that path was exercised on Linux.
Mapped network drives and projects on a share were not tested on either.

## Building

```bash
./gradlew clean shadowJar
```

Gradle 9.2.1 on the wrapper. It builds on JDK 21 (bytecode target 21); the Gradle daemon is
pinned to JDK 21 in `gradle.properties`. CI also installs JDK 25 for the compiler toolchain
QuPath 0.7.0's settings plugin asks for. The jar is written to `build/libs/`.
Details in the [developer guide](docs/developer-guide.md).

## Source, prior art and attribution

**Inspiration, not code.** [Active Image Helper](https://github.com/alexmpdx/qupath-extension-active-image-helper)
by alexmpdx (GPL-3.0) adds a current-image highlight, "Scroll to current image" and
"Remove current image" to QuPath's built-in Project tab. Project+'s jump, highlight and
remove-current-image features were inspired by it and were written fresh against the QuPath
APIs rather than copied. The two extensions coexist; see above.

**Code lifted with attribution.** The repository is GPL-3.0, so each of these is allowed.
None of the source repositories puts a license header in its files, so none was stripped;
instead, each lifted file here opens with a two-line provenance comment naming the source
repository, its license and the date it was modified for Project+.

- [`uw-loci/qupath-extension-project-metadata-browser`](https://github.com/uw-loci/qupath-extension-project-metadata-browser)
  (GPL-3.0): the project model, the metadata-key union and the build layout.
- [`uw-loci/qupath-extension-dialog-manager`](https://github.com/uw-loci/qupath-extension-dialog-manager)
  (Apache-2.0): the atomic JSON-file write used for the profiles file.
- [`uw-loci/qupath-extension-class-visibility`](https://github.com/uw-loci/qupath-extension-class-visibility)
  (Apache-2.0): the analysis-pane tab wiring and undock handling.
- [`uw-loci/qupath-extension-image-export-toolkit`](https://github.com/uw-loci/qupath-extension-image-export-toolkit)
  (QuIET, Apache-2.0): theme-aware colors (`ThemeColors`) and collapsible sections
  (`SectionBuilder`).
- [`uw-loci/qupath-extension-classify-object-subset`](https://github.com/uw-loci/qupath-extension-classify-object-subset)
  (Apache-2.0): the delayed busy indicator.

**QuPath itself** (Pete Bankhead and contributors): the built-in `ProjectBrowser` is the
behavior baseline this tab re-implements (thumbnail loading, masked names, context actions),
and the `current-image` and `no-saved-data` style conventions it already uses are what
Project+ builds on.

## License

[GNU General Public License v3.0](LICENSE)

Copyright (C) 2026 Michael S. Nelson, Laboratory for Optical and Computational
Instrumentation (LOCI), University of Wisconsin-Madison.

This extension depends on [QuPath](https://qupath.github.io), which is licensed under GPL v3,
so the running combination of QuPath + this extension is itself GPL v3. Licensing under the
same terms keeps the ecosystem consistent and is what allows the GPL-3.0 lifts above.

## AI-Assisted Development

This project was developed with assistance from [Claude](https://claude.ai) (Anthropic).
Claude was used as a development tool for code generation, architecture design, debugging,
and documentation throughout the project.

## Author

Michael S. Nelson, Laboratory for Optical and Computational Instrumentation (LOCI),
University of Wisconsin-Madison.

## Citing

If Project+ was part of a published workflow, cite QuPath as its own README asks
(<https://github.com/qupath/qupath#citing>) and name this extension with its version and
repository URL, for example *QuPath Project+ 0.1.0,
https://github.com/uw-loci/qupath-extension-project-tab-plus*. The installed version is shown
in **Extensions > Manage extensions**.
