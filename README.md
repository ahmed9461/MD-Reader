# MD Reader

Native Android Markdown reader/editor with Arabic RTL and English LTR content support.

## UI refresh candidate — v0.13.0

`feature/ui-ux-refresh` contains the UI candidate (versionCode 16). **main remains the phone-tested v0.12.0 / code 15. The candidate is not adopted or merged.**

- Four library destinations: home, documents, favorites and settings.
- Consistent native light/dark surfaces, orange accents, icons and accessible controls.
- Compact top navigation and reader/editor tabs with outline and bookmark actions.
- In-layout find/replace: document stays visible, replacement controls expand on demand, previous/next reveal the selected result without focusing the editor.
- Bounded sheets with a scrollable body and a persistent heading/close action.
- The owner's supplied `.md` artwork is used for the adaptive/legacy launcher icon.
- No framework migration or new production UI dependency.

See `PROJECT_MEMORY.md`, `plans/0013-ui-ux-refresh.md` and the candidate validation report under `docs/` for implementation and verification status. The complete pre-refresh memory is preserved verbatim in `docs/history/PROJECT_MEMORY-v0.12.0.md`.

## Existing capabilities retained

Open, read, edit, save/Save As via Android SAF; per-block RTL/LTR; Markdown formatting and undo/redo; full literal Unicode multiline search/replace; outline, bookmarks and quick navigation; tables, code highlighting and Mermaid; safe HTML; sharing and Android Print/PDF; recents, favorites, drafts and reading/editor position; local ML Kit and configured AI translation; local speech and OpenAI TTS; kinetic editor scrolling with a 10–500% speed setting.

Single Enter remains a visible preview line break. Task answers retain their explicit checkmark and green highlight. There are no ads, analytics, tracking, account requirement or broad-storage permission.

## Architecture and checks

Direct Java sources under `app/src/main`, plus the existing WebView renderer. Builds do not reconstruct sources or apply patches. Release CI checks pure Java smoke suites, JavaScript, HTML policy, UI source contracts, optimized ARM64 APK and a separately built multi-ABI AAB. Device CI uses a dependency-free instrumentation runner and an API 35 emulator; it produces actual UI screenshots and checks search visibility, keyboard/focus, replacement/undo, sheet dismissal and rotation. Emulator results do not replace owner phone acceptance.

## Release identity

- applicationId: `app.mdreader.mobile`
- stable: versionName `0.12.0`, versionCode `15`
- UI candidate: versionName `0.13.0`, versionCode `16`
- minSdk 26; compile/target 36
- Permanent release signature; credentials stay outside Git.

## Named code blocks — retained from v0.12.0

~~~markdown
```python title="app.py"
print("Hello")
```
~~~

The shorthand `python:app.py` and ordinary fences are also supported. In **أدوات Markdown وHTML**, **▣ حاوية بعنوان…** wraps the selection or inserts a placeholder. The title may be Arabic or English and syntax-highlighting language is optional.

## Phone acceptance before merge

Install the signed candidate as an update without uninstalling the existing app. Check existing documents/favorites/drafts, open/save, both themes, the keyboard and previous/next search, multiline replacement and undo, long sheets, titled code tools, outline/bookmarks and portrait/landscape. Report issues with screenshots. Do not merge before explicit approval.
