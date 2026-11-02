---
name: Bug report
about: Something in the Project+ tab did not behave as the user guide says
title: ''
labels: bug
assignees: ''
---

**What happened**

What you did, what you expected, and what you saw instead. Name the dialog or button by its
on-screen text.

- The exact text of any dialog, notification or status-line message (copy it; the user
  guide's Troubleshooting section is searched by that text).
- Which action: Remove current image / checkbox-mode Remove / Clear saved data /
  Export GeoJSON / Jump / grouping or profile change / other.

**Environment**

- Operating system: (Windows / macOS / Linux, and the version)
- QuPath version: (**Help > About**)
- Project+ version: (**Extensions > Manage extensions**, or the jar file name)
- Where the image files and the project live: (local disk / network share / mapped drive)
- Was the image open in the viewer at the time? (yes / no / several open)
- Was **Mask image names** on? (yes / no)
- Display profile in use, and whether the status line showed a profile warning

**Log lines**

Paste the lines from **View > Show log** around the time of the problem. Lines from Project+
carry `projecttabplus` in the logger name. Entry IDs in those lines are fine to share; check
that nothing else in the paste identifies a patient. If the problem is a bulk job, include
the two lines that start `Project+ ... started:` and `... finished:` (they carry the counts,
options and entry IDs). For an export problem, also attach `export-manifest.json` from the
output folder (it holds no image names when masking was on).

```
(paste here)
```

**Screenshot** (optional)
