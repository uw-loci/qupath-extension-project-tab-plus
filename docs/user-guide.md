# Project+ -- User Guide

Project+ is a second Project tab for QuPath that groups a project's images by the metadata
keys you choose, marks the image open in the viewer, shows which images carry saved
annotations, detections or TMA cores, and lets you act on many images at once. What it is
for, and whether you need it, is in the [README](../README.md#do-you-need-this); this guide
is the tab control by control.

Throughout this guide:

| Word | What it means |
|---|---|
| **entry** | one image in the project; one row in the tree |
| **group** | a tree node that holds every entry sharing one value of a grouping key |
| **grouping key** | a metadata key or built-in field the tree is nested by |
| **display profile** | a named, saved set of grouping keys and display settings |
| **current image** | the image shown in the viewer |
| **saved data** | the objects QuPath stored for an entry the last time it was saved |
| **checkbox mode** | the mode in which every row gets a checkbox |
| **checked images** | the entries whose box is ticked; what the **Actions** menu acts on |

The full [glossary](#glossary) at the end of this page has every term, including the four
destructive verbs, which are deliberately different words for different things.

Most of this guide is in collapsible sections. Your browser's find-in-page does not search
inside a collapsed one, so if you are looking for a message you saw on screen, open
[Troubleshooting](#troubleshooting) first, or expand all sections.

---

<details open>
<summary><strong>Getting started</strong> (read this first)</summary>

### Getting started

**Prerequisites.** QuPath 0.7.0 or later, the extension installed, QuPath restarted, and a
project open. Without a project the tab shows a placeholder, `No project is open.`, with
**Create project...** and **Open project...** buttons under it. A project with no images
shows `No images in this project. Use Add images... in the gear menu or File > Project.`

**Where the tab is.** In the analysis pane on the left of the QuPath window, immediately
right of the built-in **Project** tab. Drag the tab out of the pane to undock it into its own
window; everything in it works the same way undocked. By default Project+ selects its own tab
when QuPath starts with a project open (see [Settings and preferences](#settings-and-preferences)
to turn that off).

**The toolbar.** Two rows at the top of the tab:

- Row 1: the **profile combo** (which display profile is in use; a trailing ` *` means you
  have changed something since it was saved) and the **gear** menu button.
- Row 2: **Jump to current image** (a pin), **Group by** (a grid), **Checkbox mode**,
  **Refresh** (two arrows), and, at the right, the **Legend** button (a question mark) that
  explains the indicators and the search keywords. The buttons are icons; hover one for its
  name.

![The Project+ tab at 300 pixels wide, light theme, profile By patient and stain, grouped two levels deep. Group rows read Patient: 12 (2), Stain: HE (1), Stain: PPM (1), Patient: 13 (2); four image rows, each thumbnail with a red top edge and a teal right edge; the toolbar shows the pin, grid, checkbox and refresh icons.](../images/tab-grouped.png)

*The tab grouped by `Patient` then `Stain`. Each group row names its key and value and the
number of images under it. The red top edge and hatched teal right edge on each thumbnail show that
its saved data holds annotations and detections. No image is open, so the Jump button is
greyed; hover it and a tooltip says `No image is open.`*

The gear menu holds, in order: **Save profile**, **Save profile as...**, **Rename profile...**,
**Delete profile...**, **Manage profiles...**; **Edit display settings...**, **Thumbnail size**
(Hidden / Small / Medium / Large), **Indicators** (Thumbnail edges / Badges / Off);
**Create project...**, **Open project...**, **Add images...**; **Remove current image...**,
**Jump to current image (Ctrl+J)** (`Jump to current image (Cmd+J)` on macOS); **Show
built-in Project tab** (a check item), **Indicator legend...** and **Set current image
highlight color...**.

**First look.** On first open the tree is grouped by the key the built-in tab was sorting by,
if one was set for this project; otherwise it is a flat list, like the built-in tab. Group
rows read as the key, the value and the number of images under them, so a patient with three
stains and eight images reads `Patient: 12 (8)`, and the stain groups under it read
`Stain: HE (3)`. Missing values land under **(Unassigned)**; an
entry whose file location cannot be read lands under **Undefined**. Thumbnails and indicators
fill in after the tree appears; they are read in the background, and a busy strip under the
toolbar reads `Loading saved-data summaries 120 of 412` while that takes longer than a moment.

**What each row shows.** A thumbnail (size set by the profile), the image name (masked names
are honored exactly as in the built-in tab), optionally a second line of `key: value` pairs
chosen in the display settings, and the saved-data indicators drawn on the thumbnail's edges.
An entry with no saved data has its name in *italic*, the same convention QuPath's own tab
uses. The current image's row is tinted, its name is bold, and an `[open]` chip sits at the
right end of the row; when that row is hidden inside a collapsed group, the group rows above
it carry the tint and an `[open inside]` chip instead. Hover a row for the full details: its first line says `Open in viewer`,
`Open in viewer, unsaved changes`, `No saved data`, `Saved data: no objects` or the counts
(`Saved data: 12 annotations, 4,310 cells, 96 TMA cores`), followed by QuPath's usual
summary and a larger thumbnail.

**Opening an image.** Double-click an entry, or select it and press Enter. The row for the
image you opened becomes the current-image row, and the tree selects and scrolls to it if it
is visible.

**The status line**, just above the search box, reads `412 images`, `412 images, 1 open`,
`38 of 412 images shown` while a search is in force, or `412 images, 3 checked` in checkbox
mode. Messages about an action you just took (`Jumped to liver_12_HE_0.svs`,
`Refreshed: 412 images`) show there for four seconds. A warning (for example a profile that
groups by a key this project lacks) stays for eight seconds and may wrap onto a second line
in a narrow pane; the totals always fit on one. Click the status line to clear the search.

**The built-in Project tab is untouched.** Everything it does keeps working whether it is
shown or hidden; see [Hide the built-in Project tab](#hide-the-built-in-project-tab).

</details>

<details>
<summary><strong>Common tasks</strong></summary>

### Group by several metadata keys

1. Click **Group by** in the toolbar (or gear > **Edit display settings...**, or right-click
   a row and choose **Group by...**). The **Project+ - Display settings** dialog opens with
   its **Grouping** section expanded. It is not modal: leave it open and watch the tree
   regroup as you change it.
2. Click **Add level** and pick a key. The menu lists the built-in fields first
   (**Image name**, **Image type**, **File name**, **Entry ID**), then every metadata key used
   anywhere in the project, alphabetically. Keys already in use are disabled.
3. Add a second level the same way. The list reads outermost level first; **Up** and **Down**
   move the selected level outward or inward, **Remove** drops it. The tree rebuilds as
   *first key > second key > ... > entries*. With no levels the tree is flat.
4. Read the line under the list, `Groups: 12 > 31 > 96 images in 112 leaves`, before you look
   at the tree: it tells you whether you just made 400 groups of one image.
5. Choose **Expand on load**: all, first level, or none. This also applies whenever the
   grouping changes. With *first level* and two or more levels, every image sits under a
   closed inner group right after the tree builds; expand a group to reach its images, or
   choose *all* while you learn the tree.
6. Click **Save to profile** to keep the setup in the selected profile, or **Close** to keep
   the live state without saving it (the profile combo shows ` *` until you save).

![The Project+ Display settings dialog, light theme. Grouping lists 1. Patient and 2. Stain with Add level, Remove, Up and Down buttons; the count line reads Groups: 2 > 4 > 4 images in 4 leaves; Expand on load is set to all; Fields shown under the name has Scanner and Stain ticked; Appearance shows Thumbnail size set to Small; Save to profile and Close buttons at the bottom.](../images/display-settings.png)

*The display settings dialog with all three sections open: two grouping levels, the group
count line, two fields ticked, and the thumbnail size radios.*

Notes:

- Entries missing a value for any level land under **(Unassigned)** at that level, so nothing
  disappears when you add a key.
- When **Mask image names** is on (QuPath's own setting, in the row's right-click menu),
  **Image name** and **File name** are not offered as levels, because grouping by them would
  reveal the names.
- **Image type** comes from the summary QuPath writes when an image is saved; an entry that
  has never been saved groups under **(Unassigned)** for that level.
- The dialog closes itself when you switch projects, because its key list would be stale.

### Show metadata under each name

1. Open the display settings (**Group by** or gear > **Edit display settings...**).
2. Expand **Fields shown under the name** and tick the keys to show. **Image type (built-in)**
   is available too.
3. The second line of each row shows `Stain: HE | Angle: 0`, with `key: (Unassigned)` for a
   missing value. Rows grow a little taller to make room.

### Change the thumbnail size or the indicator style

Gear > **Thumbnail size** > Hidden / Small / Medium / Large, and gear > **Indicators** >
Thumbnail edges / Badges / Off. The same radios are under **Appearance** in the display
settings dialog. Both are part of the profile, so **Save profile** keeps them. **Hidden** keeps
a small square in place of the thumbnail so the indicator edges still have somewhere to be
drawn.

### Jump to the current image

1. Open any image (double-click an entry, use the built-in tab, or **File > Open...**).
2. Click **Jump to current image** in the toolbar, press Ctrl+J with focus in the tab, or
   choose **Jump to current image (Ctrl+J)** from the gear menu or **Jump to current image**
   from the right-click menu. The chord in the gear item's text is a reminder, not a global
   shortcut: Ctrl+J acts only with focus in the tab.
3. The tree expands every group above the entry, selects it and scrolls it into view. The
   status line reads `Jumped to <name>`.

Notes:

- The button is disabled when no image is open or when the open image is not an entry of
  this project, for example a file opened with **File > Open...**. Hover the disabled button
  and a tooltip says which: `No image is open.` or
  `The current image is not in this project.`
- If a search or key/value filter currently hides the entry, Jump **clears the search text and
  both filter combos**, rebuilds the tree and then jumps. The status line says so, in the
  warning color: `Search cleared to show <name>`. The automatic reselect that happens whenever
  you open an image never clears a search; it only scrolls if the row is already visible.
- If the tab is docked but not selected, Jump selects it first. If it is undocked, the jump
  runs in its window.

### Save and switch display profiles

A profile is the whole display state of the tab: grouping levels, expand-on-load, fields
shown, thumbnail size and indicator style. **Default** always exists and cannot be renamed or
deleted.

1. Set up the tab the way you want it.
2. Gear > **Save profile as...** opens **Project+ - Save profile as**; type a name such as
   `By patient` and confirm. If the name exists, **Project+ - Profile name exists** asks
   `A profile named "<name>" already exists. Replace it?` with **Replace** and **Cancel**.
   The status line confirms `Saved profile "<name>"`.
3. Later changes mark the combo ` *`. Gear > **Save profile** (or **Save to profile** in the
   display settings) writes them back to the selected profile.
4. Switch profiles from the combo. If the current one has unsaved changes,
   **Project+ - Unsaved profile changes** asks `Save your changes to profile "<name>" before
   switching?` with **Save**, **Discard** and **Cancel**.
5. Gear > **Rename profile...** and **Delete profile...** act on the selected profile; delete
   asks `Delete profile "<name>"? This cannot be undone.` in **Project+ - Delete profile**.
6. Gear > **Manage profiles...** opens **Project+ - Manage profiles**: the list in combo
   order, with **Duplicate**, **Rename...**, **Delete...**, **Reset Default** (enabled only
   on Default; restores its original settings) and **Move up** / **Move down** to reorder the
   combo. The dialog shows the path of the profiles file as selectable text.

Notes:

- Profiles are yours, not the project's: they live in one file in your QuPath user directory
  (see [Profiles file](#profiles-file-location-and-format)) and apply to every project you
  open. A profile that names a key this project does not use still works: that level groups
  everything under **(Unassigned)** and the status line warns
  `Profile "<name>" groups by "Scanner", which this project does not have`.
- The last profile you used is remembered in two places: for you, in QuPath's preferences,
  and for the project, in its metadata (the profile name as you typed it, under the key
  `_PROJECT_TAB_PLUS_PROFILE`), so a shared project reopens the way it was left. When a
  project opens, its own remembered profile wins; otherwise your last-used one; otherwise
  **Default**.
- Only the profile's *name* travels with a shared project. Its body (the grouping and
  settings) is in your own profiles file, so a colleague who opens the project on a machine
  without that profile gets **Default** and the status line warns, in the warning color,
  `Profile "<name>" is not on this machine; showing Default`. Copy the profiles file to the
  other machine, or save the profile there under the same name. Storing the profile body in
  the project is planned for a later release.
- Unsaved profile changes are dropped silently when you quit QuPath. Quitting is never delayed
  by a profile prompt.

### Read the current-image highlight

The current image's row has a tinted background, a bold name and an `[open]` chip at the
right end of the row. When you close the image or open another, the mark moves. If the row
is hidden because a group above it is collapsed, every group row on the path down to the
collapsed one is tinted and bold and ends with an `[open inside]` chip, so a glance at the
top level still tells you where the image is; expand the chain (or press **Jump to current
image**) and the mark returns to the image row alone. When a marked row is also selected,
the selection color covers the tint and the bold name plus the chip carry the state. The tint
is the theme's color by default. To use your own, right-click any row (or open the gear menu)
and choose **Set current image highlight color...**: in the **Project+ - Current image
highlight color** window untick **Use the theme color (default)** and pick a color; the rows
behind the window change as you pick, and **Close** is the only button. The same two settings
are **Custom current image highlight** and **Current image highlight color** in **Edit >
Preferences > Project+**. If no row is marked while an image is open, the
image is not an entry of this project. If Active Image Helper is installed it
marks the same image on the built-in tab in its own way; the two do not interact.

### Read the saved-data indicators

With the default **Thumbnail edges** style, each thumbnail can carry up to three colored
edges drawn inside the thumbnail:

- **top edge** (solid line, 3 px) = annotations present,
- **right edge** (diagonal hatching `////`, 5 px) = detections, cells or tiles present,
- **bottom edge** (solid line, 3 px) = TMA cores present.

The left edge is never used. Annotation and TMA edges use the colors QuPath draws those
objects in; the detections edge is a fixed teal, because QuPath has no distinct default color
for detections. Position is the primary cue; color only reinforces it. The detections edge is
hatched rather than solid so that it differs from the annotation edge by shape, not only by
color: with QuPath's default red for annotations, a solid red line and a solid teal line sit
too close for red-green color vision.

With the **Badges** style, a second line under the name shows letter chips with counts:
`A 12   D 4.3k   T 96`, with `--` for an absent type and an asterisk after a count that comes
from unsaved changes. Counts are abbreviated (999, 1.2k, 43k, 1.2M). A collapsed group row
shows three small letter chips, `[A][D][T]`, filled when any image under it has that type.

Click the **Legend** button (or gear > **Indicator legend...**) for the
**Project+ - Indicator legend** window, which reads:

```
Thumbnail edges and badges show what each image's SAVED data holds.
  Top edge / A   = annotations
  Right edge (hatched) / D = detections, cells and tiles
  Bottom edge / T = TMA cores
Solid edge or close hatching = from the last save. Dashed edge, widely
spaced hatching or * = the image is open and has unsaved changes, so the
counts come from the viewer.
No edges: no saved data (italic name), no objects, or not loaded yet.
Hover a row to see the exact counts.
Search tip: has=annotations, has=detections, has_data=false.
```

Notes:

- The indicators describe **saved** data: what was on disk the last time that image was saved.
  The current image is the exception: its edges follow the live objects in the viewer and turn
  **dashed** (the hatched detections edge spreads its marks twice as far apart) as soon as
  there are unsaved changes, then solid again when you save.
- Three states look alike at a glance (no edges) and the row tooltip tells them apart:
  `No saved data` (also italic name), `Saved data: no objects`, and `Object summary not loaded
  yet`. Project+ never opens an entry's data file to find out, because that can take seconds
  per image; it reads only the small summary QuPath writes at save time.
- QuPath has written that summary at every save since version 0.2.0, so only an entry that
  was never saved, or last saved by a QuPath older than 0.2.0, shows no edges until it is saved
  again.
- If the summary exists but cannot be read, the tooltip says
  `Saved-data summary could not be read; see log`.
- There is no way yet to save these counts as a table. A project-wide summary export (one
  row per image with its metadata and counts) is planned for a later release; until then
  the Project Metadata Browser extension exports the metadata, and the counts stay on
  screen.

### Search and filter

The search box is at the bottom of the tab, where the built-in tab keeps its filter; its
prompt reads `Search or key=value`. Press Ctrl+F (Cmd+F on macOS) with focus in the tab to go
to it; Escape clears it and returns focus to the tree. The key and value combos next to it (on a second line in a narrow pane) narrow
the list further, and all three conditions apply together.

Syntax of the search box:

| You type | It matches |
|---|---|
| `liver` | every entry whose image name contains `liver` (case-insensitive) |
| `Stain=HE` | every entry whose metadata has key `Stain` with value `HE` |
| `liver\|Stain=HE` | entries that match **every** term: `\|` separates conditions that must all hold. It does not mean "or". |
| `has_data=true` / `has_data=false` | entries with / without saved data |
| `has=annotations`, `has=detections`, `has=tma` | entries whose saved data holds that object type (detections includes cells and tiles) |

Plain text and `key=value` terms use the same rules as the search box of QuPath 0.7's own
Project tab; the `has=` keywords are Project+'s addition.

The **key** combo offers `(any key)` plus every metadata key in the project; the **value**
combo offers `(any value)` plus the distinct values of the chosen key, including
`(Unassigned)`, which matches images that lack the key. Groups with no matching image are
dropped from the tree; expansion is kept by group path. The status line reads
`38 of 412 images shown`.

### Remove the current image, and optionally move its file to the trash

The built-in tab's **Remove image...** acts on the *selected* rows; this command acts on the
*current* image, whichever rows are selected.

1. With the image open in the viewer, choose **Remove current image...** from the gear menu
   or the right-click menu. The item is disabled when no image is open or the open image is
   not in this project.
2. **Project+ - Remove current image** asks `Remove "<name>" from the project?` and explains
   that the image will be closed first. It carries two checkboxes:
   - `Move this image's saved data (annotations, detections, TMA grid) to the trash`,
     **unticked by default**. Left unticked, the entry's `data/<id>` folder stays in the
     project's data directory on disk after the entry is gone.
   - `Also move the image FILE to the trash`, unticked by default, with the file's path and
     size under it on one line (a long path is shortened in the middle; hover it for the
     whole path). While **Mask image names** is on that line reads
     `In folder <folder>  (<size>)` instead, so the dialog does not print the name it is
     hiding. When the file is not
     eligible the box is disabled and the line under it says why: `Cannot offer file
     deletion: this .vsi image has companion files that QuPath does not list.`, `Cannot offer
     file deletion: 3 other entries in this project use the same file.`, `Cannot offer file
     deletion: the file was not found.`, `Cannot offer file deletion: the image is not a
     local file.`, `Cannot offer file deletion: the image is spread over several files.` or
     `Cannot offer file deletion: the file is on a network location, which has no trash.`
3. Click **Remove**. **Cancel** is the default button, so Enter and Escape both leave the
   project as it is. If the image has unsaved changes, QuPath asks whether to save them,
   exactly as when you close an image; **Cancel** there cancels the whole removal (status
   `Removal canceled`).
4. If you ticked the file box, a **second, separate** confirmation,
   **Project+ - Move image file to trash**, shows the path, size and modification date and
   asks `Move this file to the trash?`. **Keep file** is the default and is what Enter and
   Escape choose; **Move to trash** is the other button. The dialog reminds you that
   `QuPath cannot tell whether another project uses this file.`
5. The entry leaves the project and both Project tabs refresh. The status line reads
   `Removed <name> from the project`, `Removed <name>; file moved to trash` or
   `Removed <name>; file kept at <path>`. If you ticked the saved-data box and the folder is
   still on disk afterwards (a desktop with no trash support), the status line reads
   `Removed <name>; data folder kept at <path>`. If the removal itself fails, a notification
   reads `The image could not be removed from the project; see the log (View > Show log).`
   and the entry stays.

Notes:

- **Trash, never permanent, on a local disk.** The file goes to the operating system's trash
  (Recycle Bin on Windows, Trash on macOS, the desktop trash on Linux) and can be restored
  from there. Project+ has no permanent-delete path at all. The qualifier matters on
  **network locations**: a path of the form `\\server\share\...` has no trash, so the file
  box is refused with `Cannot offer file deletion: the file is on a network location, which
  has no trash.` A **mapped network drive** (`S:\...`) looks like a local disk to QuPath and
  cannot be told apart from one, and Windows has no Recycle Bin for it, so a file there may
  be deleted outright by the operating system when you confirm the move. Do not use the file
  option on a mapped drive unless you are willing to lose the file. The same applies to the
  saved-data box in this dialog and in bulk removal when the project itself is on a share:
  the data folder is handed to the operating system's trash, and a share has none. The
  Windows share behavior has not been tested by the team.
- **Only single-file images are offered.** A file is eligible only when the entry has exactly
  one local file, the file exists, its type is on the single-file list (`.svs`, `.ndpi`,
  `.scn`, `.bif`, `.qptiff`, plain `.tif` / `.tiff`, `.jpg`, `.png`), and no other entry in
  this project uses it. A `.tif` with an `.ome` companion file beside it, or one file of a
  multi-file OME-TIFF set, is not plain. Formats that spread one image across several files or
  a folder (`.vsi`, `.mrxs`, `.ndpis`, OME companion sets, split `.czi`, Zarr) are refused
  with the reason, because moving one piece would leave the rest behind.
- **Order of operations**, so you can predict a half-finished case: the image is closed, then
  the file is moved (if you asked), then the entry is removed. If the move fails,
  **Project+ - File kept** tells you so and asks whether to remove the entry anyway (see
  [Troubleshooting](#troubleshooting)).

### Checkbox mode: check images, then remove, clear saved data or export GeoJSON

**Enter checkbox mode**

1. Click **Checkbox mode** in the toolbar. Every row gets a checkbox at its left; a group's
   box checks or unchecks every image under it that passes the current search, and shows a
   partial (indeterminate) state when only some are checked. A bar appears above the search
   box with the count (`3 of 412 checked`), **Check all shown**, **Clear** and the **Actions**
   menu.
2. Check images by hand (click the box, or press Space on the focused row), or narrow the
   list with the search box and the key/value combos and click **Check all shown** (or press
   Ctrl+Shift+N with focus in the tab). The right-click menu's **Check** / **Uncheck** act on
   the selected rows.

![The Project+ tab in checkbox mode, light theme. Every group row and image row has a ticked checkbox; the status line reads 3 images, 1 open, 3 checked; the bar below it reads 3 of 3 checked with Check all shown, Clear and Actions.](../images/checkbox-mode.png)

*Checkbox mode with every image checked. The bar above the search box shows the count, the
Check all shown and Clear buttons and the Actions menu. The empty middle of the tab is
cropped out. This capture is from an earlier build: the toolbar's checkbox-mode toggle now
shows a checkbox glyph instead of the red S, and group rows now name their key
(`Patient: 12 (2)`).*
3. Clear the search; the checks stay. Checks survive scrolling, regrouping, refresh and
   leaving checkbox mode; they are dropped only for entries that leave the project, and the
   whole set is cleared when you close the project. While the mode is off the status line
   still reads `412 images, 3 checked`, so a hidden selection is never a surprise. **Clear**
   unchecks everything, including images hidden by the search.
4. Selection (the blue row) and checked (the box) are independent, as in a mail client.

**Actions** is disabled until something is checked. It holds **Remove from project...**,
**Clear saved data...**, **Export GeoJSON...** and **Open image (exactly one checked)**,
which is enabled only when exactly one image is checked. On Windows and Linux the three bar
controls have mnemonics: Alt+C presses **Check all shown**, Alt+L **Clear**, Alt+A opens
**Actions**.

**Remove checked images from the project**

1. **Actions > Remove from project...** opens **Project+ - Remove <N> images from project**:
   `Remove <N> images from the project?`, a scrollable list of names with `(open)` after any
   that is in a viewer, and the checkbox `Move their saved data (annotations, detections, TMA
   grids) to the trash`, unticked by default. The dialog states
   `Image files are never deleted by bulk removal.`
2. If some are open, the dialog says they will be closed first and that you will be asked to
   save any changes. **Cancel** is the default button; click **Remove <N> images** to go
   ahead. Each open image is closed with QuPath's usual save prompt; **Cancel** on any of
   them stops the whole operation before anything is removed (status
   `Removal canceled; nothing was removed`).
3. Both Project tabs refresh and the status line reads `Removed <N> images`. Failures open
   **Project+ - Removal finished with errors** with one line per image,
   `<name>: could not be removed (see log)` or `<name>: project file could not be saved (see
   log)`; the full exception is in the log (**View > Show log**).

**Clear saved data of checked images**

1. **Actions > Clear saved data...** opens **Project+ - Clear saved data from <N> images**:
   `Remove all objects from the saved data of <N> images?`. The body states exactly what goes
   (`every annotation, detection, cell, tile and TMA grid`) and what stays (`Image type, stain
   settings, metadata and descriptions`), lists the names with `(open)` and
   `(no saved data, skipped)` marks, and summarizes how many will be skipped and how many are
   open and will be closed first. Its last line, `This cannot be undone: QuPath deletes its
   data.qpdata.bkp backup as soon as the new file is written.`, means what it says, and the
   dialog suggests exporting to GeoJSON first if you might need the objects again.
2. **Cancel** is the default button; click **Clear saved data** to go ahead. Open images are
   closed first through QuPath's usual save prompt; **Cancel** on any of them stops the job
   before anything is cleared.
3. The busy strip reads `Clearing saved data 4 of 12` with a **Cancel** button that stops after
   the current image; images already cleared stay cleared. The status line ends with
   `Cleared saved data for 9 images (3 skipped)`; failures open
   **Project+ - Clearing finished with errors**, one line per image with one of three
   reasons: `data file could not be read; not cleared (see log)` (the entry is left
   untouched, never overwritten with an empty file), `image not reachable; not cleared (see
   log)` (the image file is missing or unmounted, which QuPath needs to open the data) or
   `could not save (see log)` (the data was read and cleared but the new file could not be
   written, for example on a full disk or a read-only share).

Note: **clearing cannot be undone.** The dialog says so: `This cannot be undone: QuPath
deletes its data.qpdata.bkp backup as soon as the new file is written.` Project+ offers no
restore action. The body ends `If you might need the objects again, export them to GeoJSON
first.`, and that is the advice.

**Export the objects of checked images to GeoJSON**

1. **Actions > Export GeoJSON...** opens **Project+ - Export GeoJSON for <N> images**. Choose
   an **Output folder** (type a path or **Browse...**); **Export** stays disabled until the
   folder exists and is writable, and the reason shows under the field:
   `Folder does not exist. Create it, or click Browse... to pick one.` or
   `Folder is not writable. Pick another folder.`
2. Options: **Compress (.geojson.gz)** (unticked by default, like QuPath's own export; QuPath
   reads both), **Pretty-print JSON** (larger files, easier to read) and
   **Exclude measurements** (smaller files; classifications and shapes are kept). The four
   choices (folder and the three boxes) are remembered between sessions. A line above the
   options reads `One file per image: <image name>.geojson` (`.geojson.gz` with Compress
   ticked, `<entry ID>` while names are masked), and another, `An export-manifest.json beside
   the files records the options, versions and one line per file.`
3. The dialog says how many checked images have no saved data and will produce no file,
   whether an image is open with unsaved changes, and, when **Mask image names** is on,
   `Image names are masked, so files are named by entry ID and the manifest lists no image
   names.`
4. Click **Export**. One file per image, `<image name>.geojson` (or `.geojson.gz`), with
   characters that are not valid in file names removed, the name cut to 150 characters, and
   ` (<entry ID>)` added when two entries would produce the same name (names that differ only
   in case count as the same, because Windows and macOS file systems treat them so). If files
   already exist, **Project+ - Files already exist** reports
   `<k> of <N> files already exist in this folder.` with **Overwrite**, **Skip existing**
   (default) and **Cancel**.
5. The busy strip reads `Exporting GeoJSON 4 of 12` with **Cancel**. On success a notification
   and the status line read `Exported <N> images to <folder>`. If every file was skipped by
   **Skip existing**, the notification and status line read
   `Skipped <N> existing files; nothing exported`: nothing was written and nothing was
   overwritten. Failures open
   **Project+ - Export finished with errors**, listing each failed image with
   `data file could not be read (see log)`, `output file is locked or not writable (see
   log)` or `could not write the output file (see log)`, and point you at
   **View > Show log**. If the manifest itself could not be written its line reads
   `export-manifest.json: could not be written (see log)`; the GeoJSON files are still
   there.
6. Beside the files, the job writes `export-manifest.json`: the QuPath and Project+ versions,
   a timestamp, the folder, the options chosen (including whether names were masked), and
   one record per file with the entry ID, the file name, the source (`saved` for the data
   file, `live-unsaved` for the open viewer's objects), the object count and, when names are
   not masked, the image name. It is overwritten by the next export into the same folder, so
   keep it with the files; it is what maps a file back to its image after a rename or a
   masked export.

Notes:

- An image that is open in a viewer is exported from the objects in the viewer, not from its
  saved data, so unsaved changes are included. The dialog says
  `1 is open with unsaved changes; its CURRENT objects are exported.` QuPath marks a freshly
  opened image as changed, so you can see this note right after opening an image you have
  not touched; the export is still correct.
- With **Mask image names** on, the files are named `<entry ID>.geojson` and the manifest
  lists entry ID and file name only, never a real name. (QuPath's masked names are different
  every time a project is opened, so they would be useless as file names.) Turn masking off
  to export with real names.
- Each file is written to a temporary name and renamed when complete, so a crash mid-export
  never leaves a truncated `.geojson`.

**Reading the files**

- The content is QuPath's own GeoJSON `FeatureCollection`, so **File > Import objects...**
  reads it back and nothing is added by Project+.
- Coordinates are full-resolution pixel units of the image, with y increasing downward. There
  is no micrometer scaling in the file; the pixel size is in the image, not the export.
- Python, plain JSON: `json.load(gzip.open(path))` for a `.geojson.gz`, `json.load(open(path))`
  for a `.geojson`; each feature's `properties.objectType` says annotation, detection or cell.
- Python, GeoDataFrame: `geopandas.read_file(path)`.
- R: `sf::st_read(path)`.
- For a `.geojson.gz`, geopandas and sf need the path prefixed with `/vsigzip/`, or a
  decompressed copy.

### What Project+ records

Project+ keeps no record of its own. What exists:

- **The QuPath log.** Every bulk job (remove, clear saved data, export) and every single
  removal writes an INFO line to the log at start and at the end, with the kind of job, the
  counts, the options, the entry IDs involved and the list of failures. Entry IDs, not image
  names, are what the log carries, so a masked project stays masked in the log. Open it with
  **View > Show log**. QuPath writes no log file unless you ask for one: tick
  **Edit > Preferences > General > Create log files** and QuPath writes a log file into its
  user directory that then receives the same lines. A site that needs a durable record of
  who cleared what should tick it on every workstation.
- **The export manifest**, `export-manifest.json`, described above.

Nothing is written into the images' own workflow history: clearing saved data leaves no step
behind, and the project file records nothing about removals. There is no undo for a clear
and no list of removed entries beyond the log.

### Refresh

Click **Refresh** in the toolbar, press F5 with focus in the tab, or right-click and choose
**Refresh thumbnail** for one row. A full refresh re-reads the project's image list, rebuilds
the groups, gives thumbnails that failed to load one more try, re-reads every saved-data
summary that changed, and keeps your expansion, selection and checked images. The status line
reads `Refreshed: 412 images`. The tab also refreshes itself when a project opens, when an
image opens, when the built-in tab's list changes, and when you switch to it after the
project's image list changed.

</details>

<details>
<summary><strong>Advanced</strong></summary>

### Hide the built-in Project tab

Gear > **Show built-in Project tab** is a check item; untick it to hide QuPath's own tab, tick
it to bring it back at the same position. The same switch is **Hide the built-in Project tab**
in **Edit > Preferences**, category **Project+**, and it is remembered across sessions.
Hiding removes the tab from the analysis pane and nothing else: opening an image still adds it
to the project, scripts still refresh it, and the gear menu's **Create project...**,
**Open project...** and **Add images...** cover the buttons that tab had. The status line
confirms `Built-in Project tab hidden. Gear menu > Show built-in Project tab brings it back.`
or `Built-in Project tab shown.`

If the built-in tab is undocked into its own window, hiding does nothing and a notification
says: `The built-in Project tab is in its own window, so it cannot be hidden. Dock it first
(close its window), then try again.` The preference stays unticked.

### Coexistence with Active Image Helper

Both can be installed. The helper decorates the built-in tab; Project+ has its own. Both mark
the current image, each on its own tab, and removing an image from either side updates both.
One known interaction: the helper looks for the built-in tab once; if that tab is hidden when
the helper looks, its menu items stay missing from the built-in tab after you show it again,
until the next project change. Project+ applies the hide a moment after it installs itself so
the helper normally finds the tab first, but a slow start can still lose the race.
[Troubleshooting](#troubleshooting) has the recovery.

### Profiles file: location and format

`<QuPath user directory>/project-tab-plus/profiles.json`. The QuPath user directory is the
one shown under **Edit > Preferences > General > User directory**; **Manage profiles...**
shows the full path. If QuPath has no user directory yet (a fresh install), the file goes to
`<home>/QuPath/project-tab-plus/profiles.json` instead and a warning is written to the log.
Plain JSON:

```json
{
  "version": 1,
  "profiles": [
    {
      "name": "By patient",
      "grouping": ["Patient", "Stain", "builtin:imageType"],
      "expandOnLoad": "first",
      "fields": ["Stain", "Angle", "builtin:imageType"],
      "thumbnailSize": "SMALL",
      "indicators": "EDGES"
    }
  ]
}
```

Built-in fields are written with a `builtin:` prefix; metadata keys are written as they
appear in the project. The file is written atomically (a temporary file, then a rename), so a
crash cannot truncate it. It is safe to copy between machines and safe to edit by hand while
QuPath is closed. The name of the last profile you used is stored in QuPath's preferences,
and each project remembers its own last profile in its metadata under the key
`_PROJECT_TAB_PLUS_PROFILE`.

### Keyboard

All shortcuts work only while focus is inside the Project+ tab, so they never shadow QuPath's
own shortcuts in the viewer.

| Key | Does |
|---|---|
| Up / Down, Left / Right | move in the tree; collapse / expand a group |
| Enter | open the image (image row) or toggle the group (group row) |
| Space | in checkbox mode, toggle the row's box (a group: every shown image under it) |
| Ctrl+F (Cmd+F on macOS) | focus the search box and select its text |
| Escape (in the search box) | clear the search, focus the tree |
| Ctrl+J | jump to current image |
| F5 | refresh |
| Ctrl+Shift+N | in checkbox mode, check all shown |
| Ctrl+Shift+D | in checkbox mode, clear every check |
| Alt+C, Alt+L, Alt+A (Windows and Linux) | in checkbox mode, press **Check all shown**, **Clear**, open **Actions** (mnemonics) |
| Context-menu key or Shift+F10 | open the row's right-click menu |

On macOS the Ctrl chords are Cmd chords (the tooltips say so). **Check all shown** uses
Ctrl+Shift+N rather than Ctrl+Shift+A, which is QuPath's own **Create full image
annotation**. The chords are listed in the buttons' tooltips.

Delete is deliberately not bound to removal: the built-in tab does not bind it either, and a
stray Delete with focus in the tree would open a destructive confirmation you did not expect.

### Scripting

None in this release. Everything the tab does is done from its controls. The
[developer guide](developer-guide.md#scripting-api) says what a future scripting surface
would most likely expose.

</details>

<details>
<summary><strong>Settings and preferences</strong></summary>

### Settings and preferences

Global preferences live in **Edit > Preferences**, category **Project+**:

| Preference | Default | What it does |
|---|---|---|
| Hide the built-in Project tab | off | Hides QuPath's own Project tab while Project+ is installed. QuPath keeps using it internally, so nothing breaks; untick to bring it back at once. The gear menu's **Show built-in Project tab** is the same switch. See the Active Image Helper note under [Advanced](#coexistence-with-active-image-helper). |
| Custom current image highlight | off | Use the color below instead of the theme tint for the row of the image open in the viewer. Turn it on if you run another extension that colors the built-in tab and want both to match. Also set from the right-click or gear menu item **Set current image highlight color...**. |
| Current image highlight color | theme color | Color of the tint on the row of the image open in the viewer, in the Project+ tab. Leave it as the theme color unless you run another extension that colors the built-in tab and want both to match. Used only when the custom highlight above is on. |
| Open the Project+ tab on startup | on | Selects the Project+ tab when QuPath starts and a project is open. It does not take the selection away from an undocked built-in tab. Untick to keep QuPath's own tab selection. |

Everything else is part of a **display profile** (see [Save and switch display profiles](#save-and-switch-display-profiles)), saved with **Save profile** and switched with the combo:

| Profile setting | Where | Default (profile `Default`) |
|---|---|---|
| Grouping levels, in order | Display settings > Grouping; right-click > **Group by...** | none (flat). While **Default** is flat and the project has a built-in sort key (set from the built-in tab's **Sort by...**), the tree groups by that key without the key entering the profile, so the display settings dialog lists no levels and **Save profile** cannot carry the key to another project |
| Expand on load | Display settings > Grouping | first level |
| Fields shown under the name | Display settings > Fields shown under the name | none |
| Thumbnail size | gear > **Thumbnail size**; Display settings > Appearance | copied from QuPath's own project thumbnail size the first time Project+ runs (when no profiles file exists yet); after that, whatever is saved in the profile |
| Indicators | gear > **Indicators**; Display settings > Appearance | Thumbnail edges |

Remembered between sessions, outside profiles:

| What | Note |
|---|---|
| Export folder, Compress, Pretty-print, Exclude measurements | the last choices made in the **Export GeoJSON** dialog |
| Last-used profile | per user in QuPath's preferences and per project in project metadata (the name as typed, under `_PROJECT_TAB_PLUS_PROFILE`) |

Not settings, but honored:

| What | Note |
|---|---|
| Search syntax | fixed; documented under [Search and filter](#search-and-filter) |
| Mask image names | QuPath's own setting (right-click menu). Masking covers the row names, the **Image name** and **File name** grouping levels and fields (withheld while masked), the name lists in the bulk dialogs, the busy strip, the search, the export file names and the manifest. It never covers metadata values or file paths: a `Patient` value stays a group label, a row field and a filter entry, and the move-to-trash dialog shows the file's path. A blinded project must not carry identifying values in its metadata. |
| Profiles file location | fixed at `<QuPath user directory>/project-tab-plus/profiles.json`, or `<home>/QuPath/project-tab-plus/profiles.json` when QuPath has no user directory yet |

</details>

<details>
<summary><strong>Troubleshooting</strong></summary>

### Troubleshooting

Each entry: what you see, why, what to do.

**"Project+ - File kept" after choosing to move a file to the trash.**
The dialog reads `The file could not be moved to the trash.` with the path and a reason:
`the operating system reports no trash support` or `the file is in use by another program or
could not be moved`. The file was not touched. The entry is removed from the project only if you click
**Remove from project**; **Cancel** keeps it. Some Linux desktops and WSL report no trash
support. On Windows a just-closed image can stay locked for a moment: wait a few seconds and
try again, or delete the file by hand from the path shown.

**The file checkbox is disabled: "Cannot offer file deletion: ..." when removing an image.**
The image is a multi-file format (`.vsi`, `.mrxs`, `.ndpis`, OME companion sets, Zarr, a
folder), is not a local file, no longer exists, or is shared by another entry in this project
(several scenes from one `.czi` or `.lif`). The entry is still removed if you click
**Remove**; only the file offer is withheld. Delete multi-file images by hand in your file
manager, moving the main file and its companion folder together.

**"Cannot offer file deletion: the file is on a network location, which has no trash."**
The image file's path starts with `\\` (a UNC path to a share). Operating systems keep no
trash for network locations, so a move there would be a permanent delete, which Project+
never performs. The entry is still removed if you click **Remove**. To remove the file,
delete it by hand on the share, knowing it will not be recoverable. A mapped network drive
(`S:\...`) is not detected and is offered like a local disk; see the warning under
[Remove the current image](#remove-the-current-image-and-optionally-move-its-file-to-the-trash).

**The indicators are stale, or a row has no edges although I annotated it.**
Indicators read the summary QuPath writes when an image is saved, so they show the state at
the last save. The current image is the exception and tracks the viewer, with dashed edges
and widely spaced hatching while there are unsaved changes. Save the image. Hover the row: `No saved data`,
`Saved data: no objects` and `Object summary not loaded yet` are three different things that
all look like no edges. An entry saved by a QuPath version that did not write the summary
shows no edges until its next save.

**The Project+ tab is gone after undocking it.**
Right-clicking the tab and choosing **Undock tab** moves it into its own window; the tab
keeps working there. Closing that window puts the tab back into the analysis pane, as the last
tab rather than in its original place, and you can drag it back next to Project. No menu item
is needed.

**The built-in Project tab is hidden and Active Image Helper's menu items are missing.**
The helper looks for the built-in tab once; a hidden tab is not found. Tick gear >
**Show built-in Project tab**, then open or re-open a project. Or keep the built-in tab
visible if you rely on the helper's items there.

**"The built-in Project tab is in its own window, so it cannot be hidden."**
The built-in tab is undocked, so there is nothing in the pane to hide. Close its window to
dock it back into the pane, then hide it.

**Jump is disabled with "The current image is not in this project."**
The viewer shows an image that was opened with **File > Open...** rather than from the
project, or from another project. Add it to the project (gear > **Add images...**) and open it
from the tree, or open the right project.

**"Search cleared to show <name>" appeared after Jump.**
Not an error. The current image's row was hidden by your search or key/value filter, so Jump
cleared them before scrolling to it. Retype the search if you still need it.

**GeoJSON files are named "<name> (<entry ID>).geojson".**
Two entries have the same image name (or names that differ only in case), so the second file
gets the entry ID added. Nothing was overwritten. `export-manifest.json` in the same folder
maps every file to its entry.

**GeoJSON files are named "<entry ID>.geojson".**
**Mask image names** was on at export time, so the files carry entry IDs instead of names,
and the manifest lists entry ID and file name only. Turn off **Mask image names** to export
with real names. The entry ID is the number in the **Entry ID** grouping level and in the
project's `data/` folder names.

**"Folder does not exist. Create it, or click Browse... to pick one." in the export dialog.**
The path typed in **Output folder** is not a folder on this machine. Create it in your file
manager, or click **Browse...** and pick one. `Folder is not writable. Pick another folder.`
means the folder exists but you cannot write there.

**Clear saved data asked to close an image first.**
Clearing the data file under an open image would be overwritten by the next save from the
viewer, so the dialog closes open images first, with QuPath's usual save prompt. **Cancel** at that
prompt stops the whole job before anything is cleared.

**Nothing happens after a checkbox-mode action, and the log mentions a dialog.**
A confirmation is waiting behind another window. Find it in the taskbar; Dialog Position
Manager, if installed, can center it.

**"Profile "<name>" groups by "Scanner", which this project does not have".**
Not an error. The profile was saved on a project that used that key; here every image lands
under **(Unassigned)** for that level. Edit the grouping or pick another profile.

**"Profile "<name>" is not on this machine; showing Default".**
The project remembers a profile that was saved by someone else, or on another machine, and
your profiles file does not have it. The tab opened with **Default**. Copy the profiles file
from the other machine (**Manage profiles...** shows where it lives), or build the grouping
again and save it under the same name; the warning stops once a profile with that name
exists here.

**I need a record of a bulk clear or removal that happened last week.**
Project+ writes INFO lines to the QuPath log at the start and end of every bulk job and
single removal, but QuPath keeps that log in memory only. Tick **Edit > Preferences >
General > Create log files** on each workstation, and the lines are kept in a file in the
QuPath user directory. Nothing can recover a record from before the option was ticked. See
[What Project+ records](#what-project-records).

**Where to file issues.** <https://github.com/uw-loci/qupath-extension-project-tab-plus/issues>;
the bug template asks for your platform, the QuPath version, the extension version and the
lines from **View > Show log** around the time of the problem.

</details>

---

## Glossary

These are the only names for these things in Project+. If a dialog uses another word for one
of them, that is a bug.

| Term | Definition |
|---|---|
| **entry** | One image in the project; one leaf row in the Project+ tree. QuPath calls it a project image entry. |
| **current image** | The image shown in the viewer. The same word QuPath's own `current-image` style and Active Image Helper use. |
| **selected rows** | Rows you clicked; what the built-in tab's items (**Remove image...**, **Rename image...**) act on. Distinct from the current image and from checked images. |
| **group** | A tree node holding every entry that shares one value of a grouping key. Group rows are not entries. |
| **grouping key** | A metadata key, or a built-in field (**Image name**, **Image type**, **File name**, **Entry ID**), that the tree is nested by. |
| **grouping level** | One position in the ordered list of grouping keys; the first level is the top of the tree. |
| **metadata key** / **value** | A label and its text attached to an entry in the project (`Stain = HE`). Edited with **Add metadata...**. |
| **(Unassigned)** | The group for entries that have no value for the current level's key. Same label the built-in tab uses. |
| **Undefined** | The file-location value for an entry whose location cannot be read. Same label the built-in tab uses. |
| **display profile** (short: **profile**) | A named, saved set of grouping levels, expand-on-load, fields, thumbnail size and indicator style. Stored per user, not per project. |
| **current-image highlight** | The tinted, bold row with the `[open]` chip that marks the current image; the same tint with an `[open inside]` chip on the group rows above it while it is collapsed out of view. |
| **jump to current image** | The action that expands the tree to, selects and scrolls to the current image's row, clearing a search that hides it. |
| **saved data** | The objects (annotations, detections, cells, tiles, TMA grid) QuPath stored for an entry the last time it was saved. Lives in the project's data folder, not in the image file. |
| **saved-data indicators** | The thumbnail edges or badges showing which object types the saved data holds. Reflect the last save, except for the current image. |
| **not loaded yet** (indicator state) | The summary has not been read, or does not exist (never saved, or saved by an older QuPath). Not the same as "no objects". |
| **checkbox mode** | The tab mode in which every row carries a checkbox. |
| **checked images** | The entries whose checkbox is ticked; the set every **Actions** item acts on. Survives scrolling, filtering and regrouping. |
| **check all shown** | Check every entry that passes the current search and key/value filter. |
| **search** | The text box over image names and `key=value` pairs; `\|` separates terms that must all match. |
| **key/value filter** | The two combos beside the search box that show only matching entries. |
| **remove from project** | Take an entry out of the project. The image file is not touched. |
| **move saved data to the trash** | Move the entry's data folder to the operating system trash while removing it. A checkbox in the remove dialogs, unticked by default. |
| **clear saved data** | Empty an entry's saved objects (all types and the TMA grid) and save it back, leaving image type, stain settings, metadata and description. The entry stays in the project. |
| **move file to the trash** | Send the image file to the operating system trash, where it can be restored. The only kind of file deletion Project+ performs; offered for files on a local disk only (UNC paths are refused; mapped network drives cannot be detected and have no trash). "Delete" is never used for files. |
| **single-file image** | An image stored as exactly one file. Only these are eligible for move to trash. |
| **multi-file image** | An image stored as a main file plus companion files or a folder (`.vsi`, `.mrxs`, `.ndpis`, OME companion sets, Zarr). Refused for move to trash. |
| **export GeoJSON** | Write the saved objects of each checked entry to one `.geojson` (or `.geojson.gz`) file per entry in a folder you pick, plus an `export-manifest.json` that maps each file to its entry. |
| **built-in Project tab** | QuPath's own Project tab. Project+ can hide it; it never changes it. |
| **masked names** | QuPath's **Mask image names** setting, which replaces names with random ones for blinding. Project+ follows it wherever a name appears: row names, the name grouping levels and fields, the name lists in the bulk dialogs, the busy strip, the search, export file names and the manifest. Metadata values and file paths are never masked. |
