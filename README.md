# MD Reader

Android Markdown reader/editor focused on correct Arabic RTL and English LTR rendering, comfortable long-document editing, and a lightweight release build.

## Current release: v0.8.0

- Open, read, edit, save, and Save As for Markdown files.
- Per-block bidirectional rendering: Arabic RTL, English LTR, code LTR.
- Markdown formatting toolbar, undo/redo, outline, quick navigation, and editor-state restore.
- Tables, syntax highlighting, Mermaid diagrams, bookmarks, recent files, favorites, and draft recovery.
- ML Kit translation plus existing AI translation options.
- Local text-to-speech plus optional OpenAI TTS.
- Search and replace for literal Unicode text, phrases, punctuation, Markdown symbols, emoji, and multiline text, with replace-current and replace-all.
- Adjustable editor fling/scroll speed from 10% to 500%, persisted between launches.
- Light/dark themes and adjustable reading font size.
- Share and Android Print / Save as PDF.
- Uses Android Storage Access Framework; no broad storage permission.
- No ads, analytics, accounts, or tracking.

## Release build

The source is stored directly under `app/src/main` and no longer depends on source chunks or patch reconstruction.

GitHub Actions workflow `Build MD Reader v0.8 Release`:

1. validates the direct-source layout;
2. fetches pinned open-source renderer assets;
3. runs Java smoke tests and reader JavaScript syntax checks;
4. builds an optimized `arm64-v8a` release APK with R8 and resource shrinking;
5. builds an optimized Android App Bundle in a separate Gradle invocation;
6. verifies ABI/output contents and uploads the release artifacts.

The direct APK is ARM64-only so a phone does not carry ML Kit native translation libraries for unrelated CPU architectures. The AAB retains the required ABIs for store-side device-specific delivery.

## Signing

Release signing is configured through environment variables and an external persistent keystore. The keystore and passwords must never be committed to Git.

Required environment variables when signing through Gradle:

- `MD_READER_KEYSTORE_PATH`
- `MD_READER_KEYSTORE_PASSWORD`
- `MD_READER_KEY_ALIAS`
- `MD_READER_KEY_PASSWORD`

Keep `applicationId` unchanged and increment `versionCode` for each release. All future releases must use the same persistent signing key to remain update-compatible with v0.8.0 and later.

## Project memory

See `PROJECT_MEMORY.md` for the current architecture, decisions, release history, acceptance criteria, size measurements, signing fingerprint, and remaining device-level verification.
