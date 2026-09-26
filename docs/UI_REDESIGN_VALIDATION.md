# MD Reader v0.13.0 — candidate validation

Date: 2026-09-26. Status: **signed candidate ready for owner phone testing; not adopted or merged**.

## Exact provenance

- Tested application-source commit: `a5974fb6eae24d9adb6643f5bc9cce361118d6dd` on `feature/ui-ux-refresh`.
- Release run: `36269131631` — **success**, including smoke/security checks, optimized ARM64 APK, separately built AAB, output verification and artifact upload.
- Device run: `36269131647` — **success**; `UI_REGRESSION_PASS: 32 assertions; screenshots captured.`
- Release artifact: `10915175891`, `MD-Reader-v0.13.0-arm64-unsigned`.
- UI evidence artifact: `10914594351`, `MD-Reader-ui-a5974fb6eae24d9adb6643f5bc9cce361118d6dd`.
- Later documentation-only commits record these results; they do not change the application sources or the delivered APK. Do not substitute artifacts from earlier development runs.
- Main remains stable v0.12.0 / code 15 at `683c5c93ffe4674169039391e985bb9de2b24299`. PR #9 remains a draft with no merge approval.

## Implemented UI scope

Shared native light/dark palette, orange identity accents, consistent icons/states/spacing and primary 48dp touch targets. Home, documents, favorites and settings are separate destinations. File cards have distinct favorite/overflow actions; document navigation and reading/editing tabs are less crowded. Bounded sheets have a fixed title/close and a scrollable body. Speech and translation settings follow the same UI system. Reader CSS and code-copy placement were updated. The actual owner-supplied `.md` artwork is used for adaptive/legacy launcher icons.

Find/replace is inside the layout, not an overlay. Replacement controls expand on demand. Literal Unicode/multiline search, previous/next wrap, selected-match visibility, current/all replacement and undo remain available. Match navigation hides the keyboard without moving focus into the editor. When a tall landscape keyboard leaves very little height, search and document share the width instead of stacking. Expanded replacement fields retain their touch size. Ordinary editing also hides secondary chrome in short keyboard space.

No production UI framework/dependency was added. Existing saving/drafts, Markdown/HTML tools, titled fences, safe HTML/CSP, large-document handling, translation and speech code remain present. Preservation in source is not a claim that every external service or every phone path was end-to-end tested.

## Checks actually performed

- Existing pure-Java transform, translation-plan, speech-plan, search/replace, GitHub-source and safe-HTML smoke suites: passed.
- New SearchMatchState suite: **277 checks passed**, including wrapping, case handling, Arabic/multiline, UTF-16 and large match counts.
- Code-fence metadata smoke test, JavaScript syntax, XML parsing, source UI contracts and diff whitespace checks: passed.
- Android API 35 emulator, x86_64, 360×780 at 160dpi, rotated to 780×360: **32 runtime assertions passed**.
- Runtime checks include library tabs; real IME visibility; portrait document not covered by search; useful viewport; query focus; previous/next wrap; selected result inside the visible viewport; replace-one and replace-all with exact undo; Arabic multiline and zero-result states; visible 48dp sheet close; rotation/data retention; landscape side-by-side search and expanded fields; ordinary landscape editing above the IME.
- Captured and visually inspected **13 actual screenshots**: home light, documents light, favorites dark, settings dark, reader dark, search with keyboard, selected far result, document menu, titled-code dialog, speech settings, landscape search, landscape editor, translation settings. These are emulator screenshots, not mockups.
- Release APK and AAB built with R8 and resource shrinking. Device instrumentation is not packaged in the release APK.

## Signed APK

Filename: `MD-Reader-v0.13.0-arm64-release.apk`

- Packaged applicationId: `app.mdreader.mobile`
- Packaged versionName: `0.13.0`; versionCode: `16`
- Packaged minSdk: `26`; targetSdk: `36`
- ABI: `arm64-v8a` only
- Signed size: **18,228,538 bytes** (18.23 MB decimal)
- Signed SHA-256: `75059342cc6a611dc8c6e36eaf1c46eb0a3d9f25e1e57e16503300bab4c06555`
- Unsigned input SHA-256: `390ce591eb136bc1f09eb5997bbd7d297fd5d49310622fb57c8ad88f44b920a8`
- Permanent certificate SHA-256: `3d7f963db1c9b5211d3a7f0c227d208108474b390802afa6891133a035bf835a`
- `apksigner verify`: successful; APK Signature Scheme **v2 and v3 verified**, permanent certificate matched.
- Identity and ABI were read back from the signed APK, not just inferred from Gradle settings.
- Release archive SHA-256 verified against GitHub artifact digest: `aecfacf12ea36b0df88cbe703c75240975f450e11ba5f736d40bbfdcd218a5d0`.
- UI archive SHA-256 verified: `af766ee8071013c5e62a2b91cffaac55f71c516b61e09f866a58692860f53b74`.
- The separately built AAB was verified by CI (56,723,666 bytes); it is not the phone-test deliverable and was not signed for distribution here.

Credentials stayed outside Git, source archives and deliverables. No replacement signing key was generated.

## Development findings resolved

Initial UI infrastructure needed the Android command-line tools on PATH. Cold-start Gboard needed a bounded wait for actual IME Insets rather than a fixed one-second pause. A real landscape run then caught a ~41dp stacked document viewport; the side-by-side layout fixed it without weakening the viewport assertion. The protection was extended to ordinary editing, and a further runtime check was added. All temporary source-transfer/patch workflows were removed; final builds compile direct sources.

## Remaining acceptance / limits

The signed release APK was not installed on the owner's physical phone here. The emulator exercises a debug build of the same source; release compilation/signing are separate checks. Samsung-specific keyboard/launcher behavior, the full supported Android-version range and all accessibility font scales remain unverified. Live paid AI/TTS requests, every SAF provider, and a new large-document performance benchmark were not run. No user credentials or paid service calls were used by the device test.

Owner should install as an **update without uninstalling** the existing app, then check existing files/favorites/drafts, open/save, search/replace and undo, the keyboard in both orientations, long menus, titled-code tools and the desired appearance. Keep main unchanged until that test and explicit merge approval.
