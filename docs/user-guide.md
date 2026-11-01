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
- Row 2: **Jump to current image**, **Group by**, **Checkbox mode**, **Refresh**, and, at the
  right, the **Legend** button (a question mark) that explains the indicators and the search
  keywords.

The gear menu holds, in order: **Save profile**, **Save profile as...**, **Rename profile...**,
**Delete profile...**, **Manage profiles...**; **Edit display settings...**, **Thumbnail size**
(Hidden / Small / Medium / Large), **Indicators** (Thumbnail edges / Badges / Off);
**Create project...**, **Open project...**, **Add images...**; **Remove current image...**,
**Jump to current image**; **Show built-in Project tab** (a check item) and
**Indicator legend...**.

**First look.** On first open the tree is grouped by the key the built-in tab was sorting by,
if one was set for this project; otherwise it is a flat list, like the built-in tab. Group
rows read as the value followed by the number of images under them, so a patient with three
stains and eight images reads `Patient 12 (8)`. Missing values land under **(Unassigned)**; an
entry whose file location cannot be read lands under **Undefined**. Thumbnails and indicators
fill in after the tree appears; they are read in the background, and a busy strip under the
toolbar reads `Loading saved-data summaries 120 of 412` while that takes longer than a moment.

**What each row shows.** A thumbnail (size set by the profile), the image name (masked names
are honored exactly as in the built-in tab), optionally a second line of `key: value` pairs
chosen in the display settings, and the saved-data indicators drawn on the thumbnail's edges.
An entry with no saved data has its name in *italic*, the same convention QuPath's own tab
uses. The current image's row is tinted, its name is bold, and an `[open]` chip sits at the
right end of the row. Hover a row for the full details: its first line says `Open in viewer`,
`Open in viewer, unsaved changes`, `No saved data`, `Saved data: no objects` or the counts
(`Saved data: 12 annotations, 4,310 cells, 96 TMA cores`), followed by QuPath's usual
summary and a larger thumbnail.

**Opening an image.** Double-click an entry, or select it and press Enter. The row for the
image you opened becomes the current-image row, and the tree selects and scrolls to it if it
is visible.

**The status line**, just above the search box, reads `412 images`, `412 images, 1 open`,
`38 of 412 images shown` while a search is in force, or `412 images, 3 checked` in checkbox
mode. Messages about an action you just took (`Jumped to liver_12_HE_0.svs`,
`Refreshed: 412 images`) show there for four seconds. Click the status line to clear the
search.

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
   grouping changes.
6. Click **Save to profile** to keep the setup in the selected profile, or **Close** to keep
   the live state without saving it (the profile combo shows ` *` until you save).

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
   choose **Jump to current image** from the gear or the right-click menu.
3. The tree expands every group above the entry, selects it and scrolls it into view. The
   status line reads `Jumped to <name>`.

Notes:

- The button is disabled when no image is open (tooltip `No image is open.`) or when the open
  image is not an entry of this project, for example a file opened with **File > Open...**
  (tooltip `The current image is not in this project.`).
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
  and for the project, in its metadata, so a shared project reopens the way it was left. When
  a project opens, its own remembered profile wins; otherwise your last-used one; otherwise
  **Default**.
- Unsaved profile changes are dropped silently when you quit QuPath. Quitting is never delayed
  by a profile prompt.

### Read the current-image highlight

The current image's row has a tinted background, a bold name and an `[open]` chip at the
right end of the row. Only one row is ever marked; when you close the image or open another,
the mark moves. When that row is also selected, the selection color covers the tint and the
bold name plus the chip carry the state. The tint is the theme's color by default; to use
your own, turn on **Custom current image highlight** in **Edit > Preferences > Project+** and
pick a **Current image highlight color**. If no row is marked while an image is open, the
image is not an entry of this project. If Active Image Helper is installed it
marks the same image on the built-in tab in its own way; the two do not interact.

### Read the saved-data indicators

With the default **Thumbnail edges** style, each thumbnail can carry up to three colored
edges, three pixels wide, drawn inside the thumbnail:

- **top edge** = annotations present,
- **right edge** = detections, cells or tiles present,
- **bottom edge** = TMA cores present.

The left edge is never used. Annotation and TMA edges use the colors QuPath draws those
objects in; the detections edge is a fixed teal, because QuPath has no distinct default color
for detections. Position is the primary cue; color only reinforces it.

With the **Badges** style, a second line under the name shows letter chips with counts:
`A 12   D 4.3k   T 96`, with `--` for an absent type and an asterisk after a count that comes
from unsaved changes. Counts are abbreviated (999, 1.2k, 43k, 1.2M). A collapsed group row
shows three small letter chips, `[A][D][T]`, filled when any image under it has that type.

Click the **Legend** button (or gear > **Indicator legend...**) for the
**Project+ - Indicator legend** window, which reads:

```
Thumbnail edges and badges show what each image's SAVED data holds.
  Top edge / A   = annotations
  Right edge / D = detections, cells and tiles
  Bottom edge / T = TMA cores
Solid = from the last save. Dashed edge or * = the image is open and
has unsaved changes, so the counts come from the viewer.
No edges: no saved data (italic name), no objects, or not loaded yet.
Hover a row to see the exact counts.
Search tip: has=annotations, has=detections, has_data=false.
```

Notes:

- The indicators describe **saved** data: what was on disk the last time that image was saved.
  The current image is the exception: its edges follow the live objects in the viewer and turn
  **dashed** as soon as there are unsaved changes, then solid again when you save.
- Three states look alike at a glance (no edges) and the row tooltip tells them apart:
  `No saved data` (also italic name), `Saved data: no objects`, and `Object summary not loaded
  yet`. Project+ never opens an entry's data file to find out, because that can take seconds
  per image; it reads only the small summary QuPath writes at save time.
- QuPath has written that summary at every save since version 0.2.0, so only an entry that
  was never saved, or last saved by a QuPath older than 0.2.0, shows no edges until it is saved
  again.
- If the summary exists but cannot be read, the tooltip says
  `Saved-data summary could not be read; see log`.

### Search and filter

The search box is at the bottom of the tab, where the built-in tab keeps its filter. Press
Ctrl+F (Cmd+F on macOS) with focus in the tab to go to it; Escape clears it and returns focus
to the tree. The key and value combos next to it (on a second line in a narrow pane) narrow
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
     size under it. When the file is not eligible the box is disabled and the line under it
     says why: `Cannot offer file deletion: this .vsi image has companion files that QuPath
     does not list.`, `Cannot offer file deletion: 3 other entries in this project use the
     same file.`, `Cannot offer file deletion: the file was not found.`, `Cannot offer file
     deletion: the image is not a local file.` or `Cannot offer file deletion: the image is
     spread over several files.`
3. Click **Remove**. If the image has unsaved changes, QuPath asks whether to save them, exactly
   as when you close an image; **Cancel** there cancels the whole removal (status
   `Removal canceled`).
4. If you ticked the file box, a **second, separate** confirmation,
   **Project+ - Move image file to trash**, shows the path, size and modification date and
   asks `Move this file to the trash?`. **Keep file** is the default and is what Enter and
   Escape choose; **Move to trash** is the other button. The dialog reminds you that
   `QuPath cannot tell whether another project uses this file.`
5. The entry leaves the project and both Project tabs refresh. The status line reads
   `Removed <name> from the project`, `Removed <name>; file moved to trash` or
   `Removed <name>; file kept at <path>`.

Notes:

- **Trash, never permanent.** The file goes to the operating system's trash (Recycle Bin on
  Windows, Trash on macOS, the desktop trash on Linux) and can be restored from there.
  Project+ has no permanent-delete path at all.
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
   list with the search box and the key/value combos and click **Check all shown**. The
   right-click menu's **Check** / **Uncheck** act on the selected rows.
3. Clear the search; the checks stay. Checks survive scrolling, regrouping, refresh and
   leaving checkbox mode; they are dropped only for entries that leave the project, and the
   whole set is cleared when you close the project. While the mode is off the status line
   still reads `412 images, 3 checked`, so a hidden selection is never a surprise. **Clear**
   unchecks everything, including images hidden by the search.
4. Selection (the blue row) and checked (the box) are independent, as in a mail client.

**Actions** is disabled until something is checked. It holds **Remove from project...**,
**Clear saved data...**, **Export GeoJSON...** and, when exactly one image is checked,
**Open image**.

**Remove checked images from the project**

1. **Actions > Remove from project...** opens **Project+ - Remove <N> images from project**:
   `Remove <N> images from the project?`, a scrollable list of names with `(open)` after any
   that is in a viewer, and the checkbox `Move their saved data (annotations, detections, TMA
   grids) to the trash`, unticked by default. The dialog states
   `Image files are never deleted by bulk removal.`
2. If some are open, the dialog says they will be closed first and that you will be asked to
   save any changes. Click **Remove <N> images**. Each open image is closed with QuPath's
   usual save prompt; **Cancel** on any of them stops the whole operation before anything is
   removed (status `Removal canceled; nothing was removed`).
3. Both Project tabs refresh and the status line reads `Removed <N> images`. Failures open
   **Project+ - Removal finished with errors** with one line per image.

**Clear saved data of checked images**

1. **Actions > Clear saved data...** opens **Project+ - Clear saved data from <N> images**:
   `Remove all objects from the saved data of <N> images?`. The body states exactly what goes
   (`every annotation, detection, cell, tile and TMA grid`) and what stays (`Image type, stain
   settings, metadata and descriptions`), lists the names with `(open)` and
   `(no saved data, skipped)` marks, and summarizes how many will be skipped and how many are
   open and will be closed first. Its last line, `This cannot be undone: QuPath deletes its
   data.qpdata.bkp backup as soon as the new file is written.`, means what it says.
2. **Cancel** is the default button; click **Clear saved data** to go ahead. Open images are
   closed first through QuPath's usual save prompt; **Cancel** on any of them stops the job
   before anything is cleared.
3. The busy strip reads `Clearing saved data 4 of 12` with a **Cancel** button that stops after
   the current image; images already cleared stay cleared. The status line ends with
   `Cleared saved data for 9 images (3 skipped)`; failures open
   **Project+ - Clearing finished with errors**.

Note: **clearing cannot be undone.** The dialog says so: `This cannot be undone: QuPath
deletes its data.qpdata.bkp backup as soon as the new file is written.` Project+ offers no
restore action. If you might need the objects again, export them to GeoJSON first.

**Export the objects of checked images to GeoJSON**

1. **Actions > Export GeoJSON...** opens **Project+ - Export GeoJSON for <N> images**. Choose
   an **Output folder** (type a path or **Browse...**); **Export** stays disabled until the
   folder exists and is writable, and the reason (`Folder does not exist` / `Folder is not
   writable`) shows under the field.
2. Options: **Compress (.geojson.gz)** (ticked by default; QuPath reads both),
   **Pretty-print JSON** (larger files, easier to read) and **Exclude measurements** (smaller
   files; classifications and shapes are kept).
3. The dialog says how many checked images have no saved data and will produce no file,
   whether an image is open with unsaved changes, and, when **Mask image names** is on, that
   the file names will be masked too.
4. Click **Export**. One file per image, `<image name>.geojson` (or `.geojson.gz`), with
   characters that are not valid in file names removed and ` (<entry ID>)` added when two
   entries would produce the same name. If files already exist,
   **Project+ - Files already exist** reports `<k> of <N> files already exist in this folder.`
   with **Overwrite**, **Skip existing** (default) and **Cancel**.
5. The busy strip reads `Exporting GeoJSON 4 of 12` with **Cancel**. On success a notification
   and the status line read `Exported <N> images to <folder>`. If every file was skipped by
   **Skip existing**, the notification and status line read
   `Skipped <N> existing files; nothing exported`: nothing was written and nothing was
   overwritten. Failures open
   **Project+ - Export finished with errors**, listing each failed image and its reason, and
   point you at **View > Show log**.

Notes:

- An image that is open in a viewer is exported from the objects in the viewer, not from its
  saved data, so unsaved changes are included. The dialog says
  `1 is open with unsaved changes; its CURRENT objects are exported.`
- With **Mask image names** on, the files take the masked names. That is the point of masking,
  but it is easy to forget; the dialog says so.

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
| Ctrl+Shift+D | in checkbox mode, clear every check |
| Context-menu key or Shift+F10 | open the row's right-click menu |

**Check all shown** has no shortcut: Ctrl+Shift+A is QuPath's own **Create full image
annotation**, so it was not taken.

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
| Custom current image highlight | off | Use the color below instead of the theme tint for the row of the image open in the viewer. Turn it on if you run another extension that colors the built-in tab and want both to match. |
| Current image highlight color | theme color | Color of the tint on the row of the image open in the viewer, in the Project+ tab. Leave it as the theme color unless you run another extension that colors the built-in tab and want both to match. Used only when the custom highlight above is on. |
| Open the Project+ tab on startup | on | Selects the Project+ tab when QuPath starts and a project is open. It does not take the selection away from an undocked built-in tab. Untick to keep QuPath's own tab selection. |

Everything else is part of a **display profile** (see [Save and switch display profiles](#save-and-switch-display-profiles)), saved with **Save profile** and switched with the combo:

| Profile setting | Where | Default (profile `Default`) |
|---|---|---|
| Grouping levels, in order | Display settings > Grouping; right-click > **Group by...** | the project's built-in sort key, if one is set; else none (flat) |
| Expand on load | Display settings > Grouping | first level |
| Fields shown under the name | Display settings > Fields shown under the name | none |
| Thumbnail size | gear > **Thumbnail size**; Display settings > Appearance | copied from QuPath's own project thumbnail size on first run |
| Indicators | gear > **Indicators**; Display settings > Appearance | Thumbnail edges |

Not settings, but honored:

| What | Note |
|---|---|
| Search syntax | fixed; documented under [Search and filter](#search-and-filter) |
| Mask image names | QuPath's own setting (right-click menu); Project+ honors it in rows, grouping and export file names |
| Profiles file location | fixed at `<QuPath user directory>/project-tab-plus/profiles.json`, or `<home>/QuPath/project-tab-plus/profiles.json` when QuPath has no user directory yet |
| Last-used profile | automatic; per user in QuPath's preferences and per project in project metadata |

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

**The indicators are stale, or a row has no edges although I annotated it.**
Indicators read the summary QuPath writes when an image is saved, so they show the state at
the last save. The current image is the exception and tracks the viewer, with dashed edges
while there are unsaved changes. Save the image. Hover the row: `No saved data`,
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
Two entries have the same image name, so the second file gets the entry ID added. Nothing was
overwritten. If names are masked, every file has a masked name; turn off **Mask image names**
to export with real names.

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

**Where to file issues.** The repository issue tracker; include your platform, the QuPath
version, and the lines from **View > Show log** around the time of the problem.

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
| **current-image highlight** | The tinted, bold row with the `[open]` chip that marks the current image. |
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
| **move file to the trash** | Send the image file to the operating system trash, where it can be restored. The only kind of file deletion Project+ performs. "Delete" is never used for files. |
| **single-file image** | An image stored as exactly one file. Only these are eligible for move to trash. |
| **multi-file image** | An image stored as a main file plus companion files or a folder (`.vsi`, `.mrxs`, `.ndpis`, OME companion sets, Zarr). Refused for move to trash. |
| **export GeoJSON** | Write the saved objects of each checked entry to one `.geojson` (or `.geojson.gz`) file per entry in a folder you pick. |
| **built-in Project tab** | QuPath's own Project tab. Project+ can hide it; it never changes it. |
| **masked names** | QuPath's **Mask image names** setting, which replaces names with random ones for blinding. Project+ honors it everywhere, including grouping and export file names. |
