# EsCut

Android video trimming project, version 1.

## Version 1 features
- Select a video using Android's document picker.
- Enter a start and end time in seconds.
- Export an MP4 by copying supported audio/video samples without re-encoding.
- Save output under `Movies/EsCut`.

## Important limitations
- This first version trims one time range; multi-range deletion and scene detection are planned for later versions.
- Stream-copy trimming cannot guarantee exact frame-level cuts for every input. The cut may align to a nearby keyframe, depending on the source.
- This initial implementation targets MP4 output and common audio/video tracks. Some source codecs or containers may not be accepted by Android's `MediaMuxer`.
- It does not yet include target file size or resolution conversion; those require a separate re-encoding mode.

## Build on GitHub
The workflow is at `.github/workflows/android.yml` and runs on pushes to `main` or `master`, pull requests to those branches, or manual dispatch. It installs Java 17, Android SDK packages, and Gradle 8.13, then builds `app-debug.apk` and uploads it as an Actions artifact.

The workflow intentionally uses the installed Gradle command instead of `./gradlew`, so a missing Gradle Wrapper JAR does not block the first build.

## MGit first push checklist
1. Create an empty GitHub repository named `EsCut`.
2. Make sure the branch name in MGit matches the repository's branch (`main` is recommended).
3. Copy the contents of this ZIP into the local repository folder. The `.github/workflows/android.yml` path must be preserved.
4. In MGit, stage all files, commit, then push to `origin/main` (or the branch actually used by the repository).
5. Open GitHub's **Actions** tab. The workflow should appear after the push.

If a commit exists only locally and has not been pushed, GitHub Actions cannot see it. Do not use Reset (hard), Delete, or force-push to try to make the workflow appear.


## SDK license fix
The GitHub Actions workflow accepts Android SDK licenses non-interactively with `yes | sdkmanager --licenses` before installing required SDK packages. The “Wrong version in preinstalled sdkmanager” line from setup-android is normally a setup warning; the action downloads command-line tools for use in the workflow.

## Workflow fix: obsolete `tools` package
The Android setup step explicitly installs `platform-tools` only and accepts SDK licenses non-interactively. It no longer asks `sdkmanager` to install the removed legacy package named `tools`, which caused `Warning: Failed to find package 'tools'` and failed the setup action.
