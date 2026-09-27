# v0.13.0 — UI / UX refresh, with v0.13.1 spacing follow-up

Status: the owner accepted the signed v0.13.1 update and explicitly authorized merge on 2026-09-27: «كفو تم ادمج🫡❤». Implementation, delivery and owner acceptance are complete. PR #9 is the authoritative record of final current-head checks and the actual merge result.
Baseline: main `683c5c93ffe4674169039391e985bb9de2b24299` (previous stable v0.12.0, versionCode 15).
Branch: `feature/ui-ux-refresh`; PR #9.
Original tested source: `a5974fb6eae24d9adb6643f5bc9cce361118d6dd`.
Accepted application source: `187cf54d65a007349e185830f1af44bf283d6242` (v0.13.1 / code 17).

## Brief and decisions

Preserve all working functions and the permanent package/signature. Refresh existing screens, tabs, controls, icons, navigation and sheets. Use the owner's supplied dark .md mark with white letters and an orange dot. Fix the covering search/replace structurally, not just by recoloring it.

- Keep native Java + existing WebView renderer; no framework migration or heavyweight runtime.
- Shared colors, spacing, states and icons; compact appearance must not mean tiny touch targets.
- Search/replace stays in layout with visible document, optional replacement fields, Unicode/multiline literal matching and undo-safe replacement.
- Long sheets keep a visible fixed close action and one scrolling body; account for system/keyboard insets.
- Extremely short wide layouts place search beside the document. Short ordinary editing hides secondary chrome while the keyboard is open.
- Preserve launcher masking margins without substituting a different logo.
- main remains stable until automated checks, signed APK, owner phone acceptance and explicit merge approval.

## Work plan

- [x] Read baseline memory, progress, source tree, branches and baseline CI.
- [x] Audit and implement shared UI, home/documents/favorites/settings, reader/editor navigation and long sheets.
- [x] Implement in-layout search/replace; verify visibility, wrapping, focus, replacement and undo on a device emulator.
- [x] Integrate supplied icon with adaptive/legacy resources and align preview colors.
- [x] Address actual emulator findings, including cold IME and landscape viewport; extend compact handling to normal editing.
- [x] Run all existing smoke suites plus 277 SearchMatchState checks, JS/XML/source contracts.
- [x] Build and verify optimized ARM64 APK and separate AAB; original Android UI run passed 32 assertions and produced 13 reviewed screenshots.
- [x] Sign candidate with permanent key, verify v2/v3/certificate and packaged identity.
- [x] Update project memory, progress, README, layout-review and exact validation report.
- [x] Prepare signed v0.13.0 candidate for owner phone testing.
- [x] Receive owner phone feedback: redesign liked; Documents button/card spacing needs a fix.
- [x] Deliver signed v0.13.1 spacing update and receive owner acceptance.
- [x] Receive explicit merge approval on 2026-09-27.

Final integration is tracked by PR #9's live checks and merged state, not by assuming approval also means the merge API succeeded. Do not merge a failing current HEAD.

## Focused follow-up — Documents spacing, v0.13.1 / code 17

- [x] Inspect the owner's screenshot and current branch code. `buttonLp()` has only a 6dp top margin, while `cardLp()` has only a 9dp bottom margin: neither adds a gap at this boundary.
- [x] Add a 16dp bottom margin specifically to the Documents open button. Do not change shared button/card margins, card internals, other tabs, app data, package or signature.
- [x] Add real-layout regression checks for the gap, unchanged card spacing and 48dp touch target in light/dark Documents screens.
- [x] Increment delivered version to 0.13.1 / 17, keeping workflow assertions and visible version strings consistent.
- [x] Run release and device CI against the exact new source; inspect actual light/dark Documents screenshots. Release run `36273649564` and UI run `36273649560` succeeded; **44 runtime assertions** passed.
- [x] Sign and verify the update with the existing permanent key, record exact evidence and prepare the APK for delivery. Details: `docs/DOCUMENTS_SPACING_VALIDATION.md`.

This is an application code correction, not an image/mockup request. Use the same development branch and existing PR; no redesign restart. Owner approval now exists for this update, not for unrelated future production changes.

## Evidence and scope

`docs/DOCUMENTS_SPACING_VALIDATION.md` preserves the v0.13.1 delivery source, runs, artifacts, signed APK hash and test limits as of 2026-09-26. Its pending-acceptance wording is historical; owner acceptance followed on 2026-09-27. Fourteen actual emulator screenshots were captured; the two Documents screenshots were visually inspected for this focused fix.

`docs/UI_REDESIGN_VALIDATION.md` preserves the original v0.13.0 source/run/artifact/hash; `docs/UI_LAYOUT_REVIEW.md` explains its findings. Release run `36269131631` and UI run `36269131647` belong to that earlier source, not the current fix.

Dark/light and Arabic/English screens were reviewed during the redesign. Keyboard, previous/next, multiline replacement/undo, rotation, navigation and several sheet types have runtime checks. Owner acceptance does not prove every daily workflow or external service was individually tested. Full font-scale/OS/IME matrix remains unverified.

## Integration follow-up — 2026-09-27

- Preserve the accepted production source and APK; this follow-up records acceptance and verifies integration, not another redesign.
- Read current PR/base/head and compare delivered source to HEAD. `187cf54` to `1ddf0fc` is documentation-only.
- Release run `36274012848` succeeded. UI run `36274012837` failed on its first attempt at `landscape leaves document viewport`; its captured failure screenshot, after an additional pause, shows the intended side-by-side layout. The runner uses a fixed 1600ms rotation wait, so timing instability is possible but not assumed resolved.
- Re-run the failed check and require successful final-head release and UI checks before merge. Preserve failed-run evidence; do not disable assertions or claim a production fix based solely on a retry.
- Record final run IDs and merge result in PR #9. If already merged, do not redo this plan or ask the owner for the same approval.

## Cleanup and next step

Final release and UI builds compile direct sources; the temporary source-transport workflow was removed before the tested application-source commit. No unrelated old artifacts were deleted. Permanent applicationId/signature are unchanged.

After PR #9 is merged, v0.13.1 is the accepted baseline. No APK reinstall is needed for documentation-only integration. Future delivered builds need versionCode greater than 17 and their own acceptance cycle.
