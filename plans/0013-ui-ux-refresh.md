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
- [ ] Audit concrete screen/layout implementations and record issues.
- [ ] Add shared UI primitives and refresh existing surfaces.
- [ ] Replace modal search/replace with compact in-layout controls; test document visibility and replacement regressions.
- [ ] Integrate supplied icon with adaptive/legacy launcher support.
- [ ] Add regression and UI/layout checks, run existing tests, build release APK/AAB.
- [ ] Update PROJECT_MEMORY.md, PROGRESS_LOG.md and README with actual results and limitations.
- [ ] Deliver test candidate; real-device review and merge remain pending.

## Validation matrix
Dark/light; Arabic/English; editor/preview; small portrait/landscape; keyboard open/closed; multiline search and replacement; no matches, first/last wrap, repeated replace, replace-all + undo; open/save/drafts/recents/favorites; all Markdown/HTML tools including titled fences; outline/bookmarks; large-document scrolling/rendering; translation/speech retained.

## Development note
A short-lived branch-only source snapshot workflow is used to transfer the exact checked-out source into the review workspace because direct network access there is unavailable. It exports tracked source only (no credentials); remove it before final delivery. It is not a source reconstruction or patching build architecture.
