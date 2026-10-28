# EsCut — Version 1.1

Incremental update restoring the video player/preview and adding a horizontal storyboard with clickable thumbnails and a seek timeline.

- Select a video from Android's document picker.
- Preview and play/pause the selected video.
- Seek using the timeline or tap a storyboard thumbnail.
- Set the current playhead as start/end; edit times in seconds.
- Export one selected range by stream-copy (no re-encoding), where Android MediaExtractor/MediaMuxer support the source tracks.
- Output is saved under Movies/EsCut.

## Limits
Stream-copy cuts depend on keyframes and may start at the previous sync frame. This revision exports one range only; multi-range deletion/concatenation, automatic scene detection, target file size and resolution conversion are not implemented yet. Some source codec/container combinations may not mux to MP4. Device testing and a successful GitHub build still need to be confirmed.

## GitHub / MGit
Keep `.github/workflows/android.yml` in this exact path. The workflow builds on push to main/master and uses installed Gradle 8.13 rather than requiring `gradlew`. Stage all files, commit, then push to the same branch. A local-only commit will not start Actions.
