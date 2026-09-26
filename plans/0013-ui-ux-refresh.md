# v0.13.0 — UI / UX refresh

Status: implementation and automated validation complete; signed candidate prepared; awaiting owner phone acceptance. **Not approved or merged.**
Baseline: main `683c5c93ffe4674169039391e985bb9de2b24299` (v0.12.0, versionCode 15).
Branch: `feature/ui-ux-refresh`; draft PR #9.
Tested source: `a5974fb6eae24d9adb6643f5bc9cce361118d6dd`.

## Brief and decisions

Preserve all working functions and the permanent package/signature. Refresh existing screens, tabs, controls, icons, navigation and sheets. Use the owner's supplied dark .md mark with white letters and an orange dot. Fix the covering search/replace structurally, not just by recoloring it.

- Keep native Java + existing WebView renderer; no framework migration or heavyweight runtime.
- Shared colors, spacing, states and icons; compact appearance must not mean tiny touch targets.
- Search/replace stays in layout with visible document, optional replacement fields, Unicode/multiline literal matching and undo-safe replacement.
- Long sheets keep a visible fixed close action and one scrolling body; account for system/keyboard insets.
- Extremely short wide layouts place search beside the document. Short ordinary editing hides secondary chrome while the keyboard is open.
- Preserve launcher masking margins without substituting a different logo.
- main remains stable until automated checks, signed APK, owner phone test and explicit merge approval.

## Work plan

- [x] Read baseline memory, progress, source tree, branches and baseline CI.
- [x] Audit and implement shared UI, home/documents/favorites/settings, reader/editor navigation and long sheets.
- [x] Implement in-layout search/replace; verify visibility, wrapping, focus, replacement and undo on a device emulator.
- [x] Integrate supplied icon with adaptive/legacy resources and align preview colors.
- [x] Address actual emulator findings, including cold IME and landscape viewport; extend compact handling to normal editing.
- [x] Run all existing smoke suites plus 277 SearchMatchState checks, JS/XML/source contracts.
- [x] Build and verify optimized ARM64 APK and separate AAB; Android UI run passed 32 assertions and produced 13 reviewed screenshots.
- [x] Sign candidate with permanent key, verify v2/v3/certificate and packaged identity.
- [x] Update project memory, progress, README, layout-review and exact validation report.
- [x] Prepare signed v0.13.0 candidate for owner phone testing.
- [ ] Owner tests physical-phone upgrade, desired appearance and daily workflows.
- [ ] Explicit merge approval, then final current-head checks and merge.

## Evidence and scope

`docs/UI_REDESIGN_VALIDATION.md` is the exact source/run/artifact/hash record; `docs/UI_LAYOUT_REVIEW.md` explains findings. Release run `36269131631` and UI run `36269131647` passed for the delivered source. Later documentation updates do not change that APK.

Dark/light and Arabic/English screens were reviewed. Keyboard, previous/next, multiline replacement/undo, rotation, navigation and several sheet types have runtime checks. Existing open/save/drafts, all formatting tools, outline/bookmarks, external translation/speech and large-document daily use still require owner acceptance; source preservation/smoke checks do not equal every end-to-end scenario. Full font-scale/OS/IME matrix remains unverified.

## Cleanup and next step

All temporary source-transport and patch workflows were removed. Release and UI builds compile direct sources. Unrelated old artifacts are no longer deleted by the release workflow. Permanent applicationId/signature are unchanged.

Wait for owner feedback; do not redo completed stages or call this candidate stable before phone acceptance and explicit approval.
