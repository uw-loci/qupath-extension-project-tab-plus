# Session scope: Project Tab Plus

Repo: `qupath-extension-project-tab-plus/` -- a "Project+" tab beside QuPath's own Project
tab: multi-level metadata grouping, display profiles, jump to the current image, saved-data
indicators and checkbox-mode bulk actions. Java only, QuPath 0.7.0 floor, GPL-3.0.

## Build and install

- Build: `./gradlew clean shadowJar` (JDK 21 via the local `gradle.properties`; tests with
  `./gradlew test`).
- Install for a WSL smoke test: copy `build/libs/qupath-extension-project-tab-plus-<v>-all.jar`
  into `~/QuPath/v0.7/extensions/` and restart QuPath. Check that
  `qupath-qpsc-dev/build/install/QuPath/lib/` holds no older copy first (it shadows the
  extensions folder).
- Harness scenarios live in `harness/` and run with `tools/qp-harness/bin/qp-gui`.

## Rules

- ASCII only in every `.java`, `.properties`, `.kts`, `.css`, log line and string:
  `grep -rn '[^\x00-\x7F]' src` must return nothing. American English.
- Every user-facing string lives in `ui/strings.properties` and is read through `ui/Strings`.
- Use `qupath.fx.dialogs.Dialogs`, never `qupath.lib.gui.dialogs.Dialogs`. Never
  `GeneralTools.deleteFile(f, true)` (it falls back to permanent deletion); file and data
  removal go through the OS trash only.
- Nothing touches disk on the FX thread. Bulk jobs run serially on one cancelable thread.
- Do not modify `qupath-qpsc-dev` (QuPath source, read-only reference).

## Where the design lives

- Phase artifacts: `/home/msnelson/QPSC_Project/agent-reports/extension-team/project-tab-plus/`
  (`02_design.md` is the contract; `02a_ui_design.md` holds the dialog copy and tooltips).
- Checks only a human can make for this repo:
  `/home/msnelson/QPSC_Project/claude-reports/MANUAL_TEST_QUEUE.md`, tagged
  **Repo:** `qupath-extension-project-tab-plus`. Add an item whenever you ship something you
  could not check yourself.
