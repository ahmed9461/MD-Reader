# MD Reader

Native Android Markdown reader/editor with Arabic RTL and English LTR content support.

## Owner-approved release — v0.13.1

The owner accepted the signed v0.13.1 update and explicitly authorized merging PR #9 on 2026-09-27. This release includes the UI refresh and the Documents spacing correction (versionCode 17). PR #9 records the final checks and merge result; v0.12.0 / code 15 is the previous stable baseline.

- Four library destinations: home, documents, favorites and settings.
- Consistent native light/dark surfaces, orange accents, icons and accessible controls.
- Compact top navigation and reader/editor tabs with outline and bookmark actions.
- In-layout find/replace: document stays visible, replacement controls expand on demand, previous/next reveal the selected result without focusing the editor.
- Bounded sheets with a scrollable body and a persistent heading/close action.
- The owner's supplied `.md` artwork is used for the adaptive/legacy launcher icon.
- A 16dp gap separates the Documents open-file button from the first card; existing card spacing and touch targets are preserved.
- No framework migration or new production UI dependency.

See `PROJECT_MEMORY.md`, `plans/0013-ui-ux-refresh.md` and `docs/DOCUMENTS_SPACING_VALIDATION.md` for implementation and delivery evidence. The complete pre-refresh memory is preserved verbatim in `docs/history/PROJECT_MEMORY-v0.12.0.md`.

## Existing capabilities retained

Open, read, edit, save/Save As via Android SAF; per-block RTL/LTR; Markdown formatting and undo/redo; full literal Unicode multiline search/replace; outline, bookmarks and quick navigation; tables, code highlighting and Mermaid; safe HTML; sharing and Android Print/PDF; recents, favorites, drafts and reading/editor position; local ML Kit and configured AI translation; local speech and OpenAI TTS; kinetic editor scrolling with a 10–500% speed setting.

Single Enter remains a visible preview line break. Task answers retain their explicit checkmark and green highlight. There are no ads, analytics, tracking, account requirement or broad-storage permission.

## Architecture and checks

Direct Java sources under `app/src/main`, plus the existing WebView renderer. Builds do not reconstruct sources or apply patches. Release CI checks pure Java smoke suites, JavaScript, HTML policy, UI source contracts, optimized ARM64 APK and a separately built multi-ABI AAB. Device CI uses a dependency-free instrumentation runner and an API 35 emulator; it produces actual UI screenshots and checks search visibility, keyboard/focus, replacement/undo, sheet dismissal and rotation. Emulator results do not replace owner phone acceptance or prove every Android/keyboard/font-scale combination.

## Release identity

- applicationId: `app.mdreader.mobile`
- Owner-approved release: versionName `0.13.1`, versionCode `17`
- Previous stable release: versionName `0.12.0`, versionCode `15`
- minSdk 26; compile/target 36
- Permanent release signature; credentials stay outside Git.
- A subsequent delivered update must use a versionCode greater than 17.

## Named code blocks — retained from v0.12.0

~~~markdown
```python title="app.py"
print("Hello")
```
~~~

The shorthand `python:app.py` and ordinary fences are also supported. In **أدوات Markdown وHTML**, **▣ حاوية بعنوان…** wraps the selection or inserts a placeholder. The title may be Arabic or English and syntax-highlighting language is optional.

## Future update acceptance

Install signed updates without uninstalling the existing app. Check existing documents/favorites/drafts, open/save, both themes, the keyboard and previous/next search, multiline replacement and undo, long sheets, titled code tools, outline/bookmarks and portrait/landscape. Report issues with screenshots. Future releases still require their own explicit owner approval before merge; approval of v0.13.1 does not authorize unrelated changes.
