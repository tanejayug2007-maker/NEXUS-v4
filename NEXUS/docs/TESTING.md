# NEXUS test plan

**Status (milestone 1, 2026-10-21):** the project builds (`assembleDebug`, `assembleRelease`) and all
57 JVM unit tests pass on Windows 11 with JDK 21. **No item below the "Automated" section has been
verified on a device yet** — no phone was attached to the build machine. Tick items off on the
Nothing Phone (3a) before trusting them.

## Automated
- [x] `gradlew testDebugUnitTest` — LayoutOps (drag/drop, folders, widgets, reflow, uninstall pruning), Search ranking, config JSON round-trip / forward compatibility, Calculator engine (exact decimals, precedence, %, trig, errors, formatting) and Calculator input editing.

## NEXUS Calculator (milestone 1)
- [ ] "NEXUS Calculator" appears in the app list (both NEXUS's drawer and Nothing Launcher) with the hexagon "=" icon; opens in its own task (shows separately in Recents).
- [ ] `0.1 + 0.2 =` shows `0.3`; `1 ÷ 3 × 3 =` shows `1`; `200 + 10 % =` shows `220`; `5 ÷ 0 =` shows "Can't divide by zero" and does not crash.
- [ ] Typing `007` shows `7`; a second `.` in one number is ignored; `5 + ×` becomes `5 ×`; `5 × −` allows a negative operand.
- [ ] Live preview (`= …`) updates while typing; half-typed input (`2 +`) shows no error.
- [ ] After `=`, a digit starts a new calculation; an operator continues from the result.
- [ ] ⌫ deletes one key (a whole `sin(`); long-press ⌫ clears; AC clears.
- [ ] SCI: `sin(30)` = 0.5 in DEG; switch to RAD, `sin(π)` = 0; 2ND shows sin⁻¹/cos⁻¹/tan⁻¹/eˣ/10ˣ/|x|/1/x; `5!` = 120; `√2`; `Ans`.
- [ ] History: results listed newest first; tap one to edit it; Clear empties it; history, DEG/RAD and SCI survive force-stop and reboot.
- [ ] Rotate mid-expression: expression kept; landscape layout shows display left, keys right.
- [ ] Long-press the result → "Result copied"; paste elsewhere gives a plain number (no grouping commas).
- [ ] Change the theme / Light mode / shape style / wording in NEXUS Studio → reopen Calculator → it follows.
- [ ] Battery Saver or "Remove animations" on → no result pulse ring; keys still respond.
- [ ] TalkBack reads keys as words ("Multiply", "Change sign") and the expression with operators spoken.

## Launcher core
- [ ] Install, open NEXUS Studio, **Set default** → system dialog appears, NEXUS selected.
- [ ] Home button from any app returns to NEXUS (not the old launcher).
- [ ] Home while already on NEXUS: closes any overlay first; second press goes to the default page.
- [ ] Back on the home screen does nothing (never shows the old launcher). Back closes drawer/menus/folders.
- [ ] Reboot → NEXUS appears, layout and theme intact.
- [ ] Install a new app → appears in drawer within ~1 s. Uninstall → removed from drawer, home, folders and dock.
- [ ] Work profile apps show a "W" badge and launch in the work profile.
- [ ] Rotate / change dark mode / change font size → no crash, state kept.

## Home, drawer, folders, widgets
- [ ] Long-press-drag an icon to an empty cell, onto another icon (folder), onto a folder (adds).
- [ ] Long-press then release without moving → menu with app shortcuts.
- [ ] Add widget: picker → bind dialog (first time) → configure activity (if any) → placed. Resize, Move, Send to page, Remove.
- [ ] Widget long-press opens its menu; normal taps still reach the widget.
- [ ] Drawer: swipe up follows the finger; drag down at top closes; search "gm" finds Gmail/Google Maps; Enter launches top hit; A–Z rail jumps.
- [ ] Hide an app; it disappears from the drawer but is still found by search.

## Effects
- [ ] Each of the 6 launch presets: app opens immediately (≤ lead time), no stutter. Note which presets your OS overrides.
- [ ] Lock → unlock with fingerprint, PIN and password → reveal plays once, never before authentication.
- [ ] Plug in while on home → charging animation; plug in while locked, unlock within the window → animation; tap dismisses; auto-dismisses.
- [ ] Battery saver ON → background static, blur off, Studio shows "Battery saver active".
- [ ] Settings › Developer › animator scale off → effects reduced.

## v2 Operator features
- [ ] Fresh install / upgrade: theme becomes Helix Prime, mode Operator, ORBIT is page 1.
- [ ] Orbit: drag each ring, it spins with inertia; swiping outside the rings still changes page / opens drawer.
- [ ] Orbit core tap → shell; hold → glyph pad. Icon tap launches; long-press → menu (does not open the drawer).
- [ ] Radial: long-press empty space on orbit/core/grid pages, flick to an app, release → launches; release in centre → nothing; top slot → edit menu.
- [ ] Shell: `help`, `open <app>`, `calc (2+3)*4`, `flash on`, `timer 1m`, `sys`, `mode civilian`, `matrix`.
- [ ] Glyph pad: draw ○ ⚡ ^ V L Z → correct action; train a glyph for an app, draw it again → launches; delete it.
- [ ] Unlock with link ritual: hold core to 100 % → reveal; release early → "LINK UNSTABLE"; Back skips; Home skips.
- [ ] Architect: swipe up opens shell, not drawer; link ritual always shown.
- [ ] Nebula renders on Android 13+ (check logcat for "shader compile failed"); Holo Grid animates; parallax follows tilt and stops when the screen is off.
- [ ] Every launch preset (10) and unlock preset (7) — note which the OS overrides.

## v3 customisation surfaces
- [ ] Studio: each section's live preview changes instantly with every chip (clock, call, lock, AOD, charging, widgets).
- [ ] Drawer: all 6 layouts open, search works in each, long-press menus work, Categories groups sensibly.
- [ ] Page transitions: all 6 while swiping home pages; no stuck alpha/offsets after settling.
- [ ] Home widgets: add each of the 6 kinds; long-press › Next style cycles 1–6; move/resize/remove; survive reboot.
- [ ] Calls: Studio › Calls › Enable → role dialog. Incoming call while locked → NEXUS call screen over the keyguard; each answer gesture works; decline; mute/speaker/hold/keypad (DTMF) in call; end. Call from Dialer. Disable role → system phone app returns.
- [ ] Lock layer: enable + overlay permission; screen on while locked → layer appears; swipe up → Android's PIN/fingerprint; fingerprint on the real sensor also unlocks and the layer closes; calibration saves; ambient dim after 10 s then sleeps after N min.
- [ ] Screen saver: Settings › Display › Screen saver › NEXUS Ambient while charging; each face; burn-in shift.
- [ ] Per-surface colours: custom call/lock/AOD/charging colours apply only to that surface.

## v4 experience, look & customisation
- [ ] Fresh install (or clear data): onboarding shows; pick User friendly + Light → plain grid, clock+search header, floating dock, normal long-press menu, swipe down opens Android's shade.
- [ ] Customize › Experience › Futuristic → orbit, HUD, dashboard, radial and technical wording return; colour mode becomes Dark.
- [ ] Light / Dark / System: every one of the 22 themes is readable in Light (text, buttons, chips, menus, drawer, Studio). Status-bar icons turn dark in Light.
- [ ] Material You theme follows the wallpaper colours (Android 12+); change wallpaper → accents change.
- [ ] Shapes: each of the 5 changes panels, chips, menus and widgets; Cut shows corner brackets only with futuristic wording.
- [ ] Dock: 5 styles; dock apps 3–7 (adding beyond the limit is refused). Badges: dot / count / none.
- [ ] Orbit: 1/2/3 rings, per-ring counts, icon size, labels, ring styles, cores, drift speed 0 = still, alternate direction, core tap/hold actions, pinned apps stay on the inner ring (Studio picker and long-press › Pin to orbit). Orbit off → page disappears.
- [ ] Radial: 4–10 slots, size, labels, home-menu slot on/off, sources Predicted / Dock / Custom (picker and long-press › Add to radial). Radial off → long-press opens the normal menu.
- [ ] Widgets: add each of the 14 kinds; cycle styles; cycle frames; Note edit; World clock cities; Photo picker (survives reboot); Calendar asks permission then lists events; Toggles torch/DND/rotate work, Wi-Fi/BT open panels; Favourites updates as you use apps.
- [ ] Backup from v3 imports without errors; new options take defaults.

## System features
- [ ] Notification feed without access → "Grant access" card; with access → grouped cards, tap opens, swipe dismisses, Clear removes clearable ones.
- [ ] Popup preview appears for new notifications while on home; "Hide notification content" redacts.
- [ ] Media playing → CORE page player; controls work.
- [ ] Control deck: flashlight toggles; DND toggles (after access); brightness dial (after Modify system settings); rotate; volume; Wi-Fi/Internet/NFC open system panels; System QS opens the real Quick Settings.
- [ ] Double-tap lock with the accessibility helper on; helpful message when off.

## Performance (record numbers, don't assume)
Measure the **release** build (`gradlew assembleRelease`); debug numbers are not meaningful. Compare User friendly vs Futuristic.
```
adb shell dumpsys gfxinfo com.nexus.launcher reset
# use the launcher for 60 s: swipe pages, open drawer, open folders
adb shell dumpsys gfxinfo com.nexus.launcher        # janky frames %, 90th/99th percentile
adb shell dumpsys meminfo com.nexus.launcher        # PSS
adb shell dumpsys batterystats --reset
# leave phone idle on home 30 min, screen on, then:
adb shell dumpsys batterystats com.nexus.launcher
```
- [ ] Cold start to first frame (Android Studio › Profiler or `adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME`).
- [ ] Frame stats overlay: swipe pages / open the drawer and watch JANK and WORST. (The overlay itself keeps frames running, so turn it off before measuring idle battery.)
