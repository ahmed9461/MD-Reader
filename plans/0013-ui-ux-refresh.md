# v0.13.0 — UI / UX refresh

Status: active, not approved or merged.
Baseline: main 683c5c93ffe4674169039391e985bb9de2b24299 (v0.12.0, versionCode 15).
Branch: feature/ui-ux-refresh.

## Brief
Preserve all working features and the permanent package/signature. Refresh all existing screens, tabs, navigation, controls and sheets. Use the owner's supplied dark .md mark (white letters, orange dot), not an unrelated logo. The owner's screenshot shows search/replace obscuring the document and keyboard; this must be fixed structurally, not just recolored.

## Decisions before implementation
- Keep native Java + existing WebView renderer; no framework migration or new heavyweight runtime.
- Use shared visual tokens and consistent icons, contrast, spacing and 48dp touch targets. Compact appearance must not mean tiny touch targets.
- Prefer an inline, nonmodal find/replace panel that takes layout space, keeps the document visible, and expands replacement controls only when needed. Preserve multiline literal Unicode search, current/previous/next and undo-safe replacement.
- Review home/library/favorites/settings, reader/editor switch, editing tools, outline/bookmarks and long menus. Retain all existing entry points and actions.
- Account for keyboard, RTL/LTR, short screens/landscape, system bars and larger fonts. Selected match must scroll into the remaining viewport.
- Treat launch icon masking separately from artwork; preserve safe area for circular and squircle launchers.
- main stays stable until successful release checks, real-phone testing and explicit merge approval.

## Work plan
- [x] Read baseline project memory, progress log, repository tree, branches and baseline CI. No open PRs.
- [x] Audit concrete screen/layout implementations and record issues.
- [x] Add shared UI primitives and refresh existing surfaces.
- [ ] Replace modal search/replace with compact in-layout controls; test document visibility and replacement regressions.
- [x] Integrate supplied icon with adaptive/legacy launcher support.
- [ ] Add regression and UI/layout checks, run existing tests, build release APK/AAB.
- [ ] Update PROJECT_MEMORY.md, PROGRESS_LOG.md and README with actual results and limitations.
- [ ] Deliver test candidate; real-device review and merge remain pending.

## Validation matrix
Dark/light; Arabic/English; editor/preview; small portrait/landscape; keyboard open/closed; multiline search and replacement; no matches, first/last wrap, repeated replace, replace-all + undo; open/save/drafts/recents/favorites; all Markdown/HTML tools including titled fences; outline/bookmarks; large-document scrolling/rendering; translation/speech retained.

## Development note
A short-lived branch-only source snapshot workflow is used to transfer the exact checked-out source into the review workspace because direct network access there is unavailable. It exports tracked source only (no credentials); remove it before final delivery. It is not a source reconstruction or patching build architecture.

## Implementation / audit notes
- Replaced the covering search Dialog with a two-row in-layout FindReplaceBar and optional replacement row. Navigation hides IME without moving focus to the editor; the active range remains visibly highlighted. Short landscape space temporarily collapses secondary search controls.
- Shared UiPalette, ReaderUi and vector-style UiIcon unify native controls. Library now has home/documents/favorites/settings destinations, explicit document overflow actions, smaller reader tabs and dedicated outline/bookmark controls.
- All sheets use ResponsiveSheet: bounded width, one scroll body, visible header close, real system/IME insets. Speech settings use the same component. Existing scroll lists are unwrapped, not nested at zero height.
- Used the supplied image pixels for .md identity, square/resampled only; adaptive inset preserves launcher masking. Reader CSS follows the orange palette and offers 48px copy controls without overlaying code text.
- SearchMatchState avoids the old previous-at-first bug and preserves non-overlapping literal semantics/case counts; 277 pure-Java navigation checks passed locally alongside all existing smoke suites and JS/XML checks.
- Added dependency-free Android instrumentation and screenshot CI; execution pending. User phone acceptance and main merge pending.
- Removed the old build step that deleted unrelated previous release artifacts. Metadata now agrees on versionCode 16. No package/key change.
