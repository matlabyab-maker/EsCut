# EsCut

Android video trimming project, version 1.

## Version 1 features
- Select a video using Android's document picker.
- Enter a start and end time in seconds.
- Generate a horizontal storyboard of sampled video frames; tap one frame to set the start and a second frame to set the end.
- Export an MP4 by copying supported audio/video samples without re-encoding.
- Save output under `Movies/EsCut`.

## Important limitations
- This first version trims one time range; the storyboard currently helps choose the two boundaries rather than supporting arbitrary multi-range deletion yet; multi-range deletion and scene detection are planned for later versions.
- Stream-copy trimming cannot guarantee exact frame-level cuts for every input. The cut may align to a nearby keyframe, depending on the source.
- This initial implementation targets MP4 output and common audio/video tracks. Some source codecs or containers may not be accepted by Android's `MediaMuxer`.
- It does not yet include target file size or resolution conversion; those require a separate re-encoding mode.

## Build on GitHub
The workflow is at `.github/workflows/android.yml` and runs on pushes to `main` or `master`, pull requests to those branches, or manual dispatch. It installs Java 17, Android SDK packages, and Gradle 8.13, then builds `app-debug.apk` and uploads it as an Actions artifact.

The workflow intentionally uses the installed Gradle command instead of `./gradlew`, so a missing Gradle Wrapper JAR does not block the first build. The app now opens the selected URI through a file descriptor and marks the output complete only after writing finishes.

## MGit first push checklist
1. Create an empty GitHub repository named `EsCut`.
2. Make sure the branch name in MGit matches the repository's branch (`main` is recommended).
3. Copy the contents of this ZIP into the local repository folder. The `.github/workflows/android.yml` path must be preserved.
4. In MGit, stage all files, commit, then push to `origin/main` (or the branch actually used by the repository).
5. Open GitHub's **Actions** tab. The workflow should appear after the push.

If a commit exists only locally and has not been pushed, GitHub Actions cannot see it. Do not use Reset (hard), Delete, or force-push to try to make the workflow appear.


## Version 1.2 changes
- Added an in-app video player with play/pause, a seek bar, and a current-time / total-duration display.
- Playback progress updates only while playing and is suppressed during direct user seeking, preventing the progress bar from being overwritten during dragging.
- Keeps the stream-copy output path as the default; no re-encoding is performed by the trimming code when the source tracks/container are supported.
- The player relies on Android's built-in media support, so some codecs may not play on every device.


## Version 1.2.2 — Compact storyboard
- Storyboard thumbnails are smaller and arranged as exactly 3 rows of 15 representative frames (45 samples across the full video duration).
- The storyboard remains horizontally scrollable so all 15 columns can be reached on narrow phone screens.
- Playback and stream-copy trimming logic are otherwise unchanged.


## Version 1.2.3 — GitHub Actions SDK setup fix
- Explicitly overrides android-actions/setup-android's package list so it does not request the obsolete `tools` package.
- Installs only `platform-tools`, `platforms;android-35`, and `build-tools;35.0.0`.
- No app source code or storyboard behavior changed in this fix.


### Version 1.2.4 workflow fix
- Corrected Android SDK package input to the comma-separated format expected by `android-actions/setup-android@v3`.
