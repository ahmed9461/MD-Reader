# UI layout review — v0.13.0 candidate

Owner brief: improve all existing UI while retaining working functionality; use the supplied .md artwork. Active plan: plans/0013-ui-ux-refresh.md. main remains v0.12.0; no merge approval yet.

## Findings and fixes

- Original modal search/replace covered the document and keyboard. Replaced with an in-layout panel and expandable replacement controls; selected match is highlighted and scrolled into the remaining viewport.
- Home/recents/favorites were a long combined page. Added four clear destinations, per-page scroll state, file cards and distinct favorite/more actions.
- Toolbar/header buttons were crowded and inconsistent. Shared palette/icons/states and 48dp targets replace inconsistent treatments without adding a production dependency.
- Long modal bodies and nested weighted scrolling could hide actions. Shared bounded sheets keep the title/close fixed and make the body scroll; speech settings uses the same system.
- Updated launcher uses the actual owner-supplied .md image with adaptive safe margins; preview colors and code-copy placement are consistent.
- First device run had a cold IME startup after the old one-second wait. Test now waits for actual IME Insets for at most eight seconds; the assertion was retained.
- Second device run passed portrait IME, previous/next, visible match, replacement/undo, multiline Arabic and sheet checks. It caught a real landscape issue: a tall IME left ~97dp above the keyboard and a stacked search left only ~41dp of document.
- Fixed the landscape issue by laying search and document side by side when remaining height is under 170dp and width over 600dp. Replacement stays available when expanded; 48dp fields are retained. Normal vertical layout is restored when space returns or search closes.
- Without IME on short screens, secondary navigation hides but replacement options remain available.
- Voice action buttons now explicitly retain a minimum 48dp height.

## Review boundaries

These are implementation decisions and evidence from development runs, not a claim that the current candidate is fully validated. The final exact commit/run IDs, signed APK hash, screenshot inspection and remaining phone checks are recorded in UI_REDESIGN_VALIDATION.md after successful reruns. No AI/TTS paid calls, owner keys, account changes or stable-branch changes were part of emulator tests.
