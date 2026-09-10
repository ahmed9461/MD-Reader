# MD Reader

Android Markdown reader and editor focused on correct Arabic RTL and English LTR rendering.

## Current test release — v0.8.1

- Open, read, edit, save and Save As for Markdown files.
- Per-block bidirectional rendering: Arabic RTL, English LTR, code always LTR.
- Markdown formatting toolbar, undo/redo, search and document outline.
- Tables, syntax highlighting and Mermaid diagrams.
- Light/dark themes and adjustable reading font size.
- Share and Android Print / Save as PDF.
- Recent files, favorites, draft recovery, reading position and editor position restore.
- Local ML Kit translation plus configured AI translation options.
- Local speech and OpenAI TTS options.
- Single Enter is rendered as a visible line break in preview.
- Full literal Unicode search/replace with previous/next, replace current and replace all.
- Quick navigation and bookmarks for long documents.
- Kinetic editor scrolling: swipe and release to keep gliding; touch again to stop.
- Adjustable post-release editor fling speed from 10% to 500%.
- Long bottom-sheet menus are scrollable.
- Search/replace sheet is compact and requests keyboard resize instead of reserving a large empty area.
- Uses Android Storage Access Framework; no broad storage permission.
- No ads, analytics, accounts or tracking.

## Architecture

`MainActivity.java` and the rest of the application are normal direct source files under `app/src/main/`. The release build does not reconstruct source from chunks or apply a patch chain.

The editor uses `FlingEditText` + Android `OverScroller`/`VelocityTracker` for kinetic vertical scrolling while preserving ordinary tap/long-press text editing behavior as much as possible.

See `PROJECT_MEMORY.md` for the project source of truth, implementation decisions, release rules and device-test findings.

## Release build

GitHub Actions workflow `Build MD Reader v0.8.1 Release` validates the direct-source architecture and v0.8.1 editor/dialog fixes, runs smoke tests and JavaScript syntax checks, builds optimized ARM64 APK and AAB outputs, verifies ABI contents, and uploads the unsigned release package for signing with the permanent release key.

## Release identity

- applicationId: `app.mdreader.mobile`
- versionName: `0.8.1`
- versionCode: `11`
- minSdk: 26
- compile/target SDK: 36

Release signing credentials are never committed to the repository. Future updates must keep the same applicationId and permanent signing key and increment versionCode.
