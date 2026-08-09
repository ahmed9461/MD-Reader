# MD Reader

Android Markdown reader and editor focused on correct Arabic RTL and English LTR rendering.

## v0.1.0

- Open, read, edit, save and Save As for Markdown files.
- Per-block bidirectional rendering: Arabic RTL, English LTR, code always LTR.
- Markdown formatting toolbar, undo/redo, search and document outline.
- Tables, syntax highlighting and Mermaid diagrams.
- Light/dark themes and adjustable reading font size.
- Share and Android Print / Save as PDF.
- Uses Android Storage Access Framework; no broad storage permission.
- No ads, analytics, accounts or tracking.

## APK build

GitHub Actions workflow `Build MD Reader APK` rebuilds the pinned source package, fetches pinned open-source renderer assets, runs smoke tests, builds the Android debug APK, verifies it, and uploads it as the `MD-Reader-v0.1.0-debug` artifact.

Source package integrity was verified before the current build.
