# MD Reader

Android Markdown reader and editor focused on correct Arabic RTL and English LTR rendering.

## Current stable release — v0.11.0

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

GitHub Actions validates the direct-source architecture, smoke tests, JavaScript syntax and renderer safety, then builds optimized ARM64 APK and AAB outputs, verifies ABI contents, and uploads the unsigned ARM64 test package for signing with the permanent release key.

## Release identity

- applicationId: `app.mdreader.mobile`
- stable versionName: `0.11.0`
- stable versionCode: `14`
- current feature candidate: `0.12.0` / versionCode `15`
- minSdk: 26
- compile/target SDK: 36

Release signing credentials are never committed to the repository. Future updates must keep the same applicationId and permanent signing key and increment versionCode.


## Named fenced code blocks — v0.12.0 candidate

MD Reader can show an optional filename/title in the header of a fenced code block while preserving syntax highlighting and the copy button.

~~~markdown
```python title="app.py"
print("Hello")
```
~~~

A compact shorthand is also supported:

~~~markdown
```python:app.py
print("Hello")
```
~~~

Ordinary fences such as ```python remain fully supported and render without a title.

### Editor shortcut

Inside **أدوات Markdown وHTML**, use **▣ حاوية بعنوان…**. The dialog accepts an Arabic or English title plus an optional syntax-highlighting language such as `python`, `json`, or `text`. If text is selected, MD Reader wraps that selection; otherwise it inserts a ready-to-edit placeholder.
