# MD Reader v0.13.1 — Documents spacing validation

Date: 2026-09-26 UTC. Status: **fixed, built, tested and signed for owner phone testing; not merged**.

## Change and scope

The owner liked v0.13.0 (9.5/10) but reported that the orange Documents open-file button touches the first file card. In `MainActivity.refreshHome()`, `buttonLp()` provided only a top margin and `cardLp()` only a bottom margin, leaving this boundary at zero.

The fix gives only the Documents open action a **16dp bottom margin**. Shared card spacing remains **9dp**, button height remains at least **48dp**, and horizontal alignment is unchanged. No other redesign, navigation, file storage or dependency change was introduced. Version is now **0.13.1 / code 17**; package and permanent signing key are unchanged.

## Exact source and CI

- Application/test/build source: `187cf54d65a007349e185830f1af44bf283d6242` on `feature/ui-ux-refresh`, draft PR #9.
- Release run `36273649564`, job `108492148074`: **success**, including Java smoke checks, JavaScript/HTML policy, source contracts, optimized ARM64 APK, separate AAB, output verification and actual artifact upload.
- Release artifact `10916531389`: `MD-Reader-v0.13.1-arm64-unsigned`.
- Device run `36273649560`, job `108492148126`: **success**.
- UI artifact `10915574979`: `MD-Reader-ui-187cf54d65a007349e185830f1af44bf283d6242`.
- `instrumentation.txt`: **UI_REGRESSION_PASS: 44 assertions; screenshots captured.**
- All final builds compile direct source; the temporary blob-preparation workflow was removed before this source commit. Later documentation-only commits do not change the delivered APK.

## Actual UI verification

The API 35 x86_64 emulator ran the same-source debug build at 360×780, density 160, with the existing rotation/keyboard scenarios. New assertions measure the actual button/card coordinates in **both light and dark Documents screens**, check the exact 16dp section gap, preserved 9dp inter-card spacing, aligned horizontal edges, and the 48dp button target.

The original navigation, search/replace, result visibility, keyboard, undo, sheet-close and landscape tests remain enabled. The new run produced **14 real emulator screenshots**. For this focused fix, `02-documents-light.png` and `02b-documents-dark.png` were opened and visually inspected: the button and first card are visibly separated in both themes. These are actual app screenshots, not generated mockups.

## Signed phone-test deliverable

Filename: `MD-Reader-v0.13.1-arm64-release.apk`

- Packaged applicationId: `app.mdreader.mobile`
- Packaged versionName: `0.13.1`; versionCode: `17`
- Packaged minSdk: `26`; targetSdk: `36`
- ABI: `arm64-v8a` only
- Signed size: **18,228,538 bytes** (18.23 MB decimal)
- Signed SHA-256: `b126ebfc1fbdecc50fdb5ddb5f2b52fa426612b4e60b5d5857e45e8f8d6220c2`
- Unsigned input SHA-256: `5711be21b577dfcfb5438a44c9cfc566fad5e1bfd307e3f8378d682981c802b9`
- Permanent certificate SHA-256: `3d7f963db1c9b5211d3a7f0c227d208108474b390802afa6891133a035bf835a`
- APK Signature Scheme **v2 and v3 verified**; one signer. The certificate was also compared with the previously delivered v0.13.0 APK and matches.
- Package/version/SDK values were read back from the signed APK's compiled manifest. ZIP integrity and arm64-only libraries were checked.
- Release archive SHA-256 matched the GitHub artifact digest: `33c0e98719d91a9009ee7394ade1957fb142a358f501a784690c64821a57552b`.
- UI archive SHA-256 matched: `4c373d71bcb30157439baaf172c8346bbd1e47653abb31e2c2ba673e74ff578c`.
- AAB was separately built and checked by release CI (56,723,649 bytes); it is not the phone-test deliverable.

The permanent PKCS12 backup was used locally, outside Git and build logs. No replacement key was generated, and no credentials are included in the APK or this report. For this PKCS12 backup, the working key password is the store password; an older separate key-password label in its notes did not unlock the key. No credential values were exposed or changed.

## Acceptance and limits

Install the APK **as an update, without uninstalling the old app**, then verify the Documents gap on the owner's phone. This fix has not yet been confirmed on that physical phone. The emulator uses a debug build of the same source; release compilation, manifest and signature verification are separate checks. No claim is made about every Android version, font scale, keyboard, SAF provider or live paid AI/TTS service.

main stays at v0.12.0 / code 15 (`683c5c93ffe4674169039391e985bb9de2b24299`). Positive feedback on v0.13.0 is not merge approval. Wait for the updated phone test and explicit approval before merging PR #9.
