# NEXUS test plan

**Status (milestone 6, 2026-10-22):** 82 unit tests pass (78 app + 4 sysui-style). **Status (milestone 5):** 73 unit tests pass. **Status (milestone 4):** `assembleDebug assembleRelease lintDebug` succeed and all 65 JVM unit
tests pass. Milestone 4 items marked *(emulator ✓)* were verified on an Android emulator (API 37), **not** on the phone on Windows 11 with JDK 21. **No item below the "Automated" section has been
verified on a device yet** — no phone was attached to the build machine. Tick items off on the
Nothing Phone (3a) before trusting them.

## Automated
- [x] `gradlew testDebugUnitTest` — LayoutOps (drag/drop, folders, widgets, reflow, uninstall pruning), Search ranking, config JSON round-trip / forward compatibility, Calculator engine (exact decimals, precedence, %, trig, errors, formatting) and Calculator input editing.

## Milestone 7 (on the Phone (3a)) — nothing below has been run on a device or emulator yet
**Smoothness**
- [ ] All v1.5.1 drawer checks below still pass (quick flick opens, follows the finger, flick down closes, rapid open/close).
- [ ] Open Signals / Control Deck: the panel itself animates smoothly; Back returns to a home that looks exactly as before (orbit spinning again, background animating).
- [ ] Long-press an app (menu) / open a folder: home behind is still blurred (Android 12+). Drawer in Balanced: no blur behind it (dim + scale only); Ultra: blur.
- [ ] `tools\phone-test\nexus-perf.ps1 -Label m7` (Frame stats on) and compare janky % with your M6 report.

**Signals panel**
- [ ] Play YouTube Music: **one** media card (no upper PLAYBACK box, no second YouTube Music card) with app name, title, artist, album art.
- [ ] ◀◀ / ▶ ❚❚ / ▶▶ control the song; pausing in the app updates the card within a second. Tap the card → opens the player.
- [ ] Spotify / YouTube / another player: same card; a second player's own notification is still listed normally.

**Notification actions**
- [ ] WhatsApp message: card shows ▾ REPLY · ACTIONS → Reply / Mark as read (and Mute if WhatsApp offers it on your build). Inline reply arrives in the chat; Mark as read clears it.
- [ ] Instagram DM, Telegram, Google Messages: note which of Reply / Mark as read / Mute each one offers (they differ by app and version).
- [ ] An app with no actions: expanding says "No quick actions from this app"; tapping opens it.
- [ ] Group chat: expanded card lists the last messages with senders.

**Radial**
- [ ] After upgrading from M6: long-press empty home → the same apps as before, in the same places; they no longer change by themselves.
- [ ] Studio › Radial: assign an app to slot 3, move it up/down, clear a slot (stays empty on the ring), Reset to defaults, Clear all. Long-press app › Add to radial fills the first empty slot.
- [ ] "Recent" mode still works and is labelled automatic.

**Studio**
- [ ] Sections are separate cards with more space; nothing is cut off on the 3a's screen.
- [ ] Live preview switches to RADIAL / DRAWER / SIGNALS when you scroll to those sections; tapping a tab pins it; every change flashes UPDATED; collapse/expand works.

**Private Space**
- [ ] Long-press app › Set up Private Space → choose PIN (twice) or phone lock (fingerprint prompt appears).
- [ ] Move 2 apps in: they vanish from drawer, drawer search, orbit, radial, home page and dock; their notifications vanish from Signals (still in Android's shade: expected).
- [ ] Drawer ◈ PRIVATE → PIN pad / fingerprint → private apps open; launching one and returning Home locks it again.
- [ ] 5 wrong PINs → 30 s wait. Turn off the drawer chip → typing "private" in search offers it.
- [ ] Studio › Private Space › Manage (unlock) → move apps out, change PIN, switch to phone lock, Turn off (unhide all).
- [ ] Export a backup → the file has no `privateApps` entries and an empty `privatePinHash`.

## v1.5.1 — Performance & Gesture Hotfix (on the Phone (3a))
- [ ] Home (each of Orbit, Core, a grid page): **quick short flick up** opens the drawer every time (try 20). Slow drag up follows the finger exactly and opens past half-way or with a little upward speed.
- [ ] Release a slow drag below half-way with no speed → drawer drops back closed smoothly.
- [ ] Orbit: a vertical swipe starting **on a ring** opens the drawer (up) / notification panel (down). A sideways or diagonal drag on a ring still spins it.
- [ ] Drawer open: flick down on the search header, or scroll to the top of the list and keep pulling down → closes, following the finger. A long list fling that reaches the top does **not** close the drawer.
- [ ] Rapid: flick up, flick down, flick up again before the close finishes → drawer is caught and reopens; 30 fast open/close pairs → no crash, freeze or stuck half-open drawer; tapping a half-closed drawer launches nothing.
- [ ] Back and Home close the drawer; launching an app from it, then returning Home shows the home (drawer closed). Search shortcut / Core search still opens the drawer with the keyboard.
- [ ] Icons dip slightly under the finger and spring back; rows rise in with a short cascade (none in Battery Saver / with animations off). Drawer styles Grid, Categories, List, Terminal, Honeycomb and Carousel all still open and close.
- [ ] Measurements: `tools\phone-test\nexus-perf.ps1 -Label m6` with the M6 APK, then `-Label v151` with v1.5.1 (Frame stats on). Keep both reports.

## Milestone 6
- [ ] *(emulator ✓)* Swipe down on the right: Control Deck matches the reference — `// CONTROL DECK`, big clock, battery outline + %, cyan LUMEN and magenta AUDIO gauges, POWER CELL, 8 tiles, ◇ footer, pure black.
- [ ] LUMEN: drag changes real brightness (after "Modify system settings"); tap MANUAL/ADAPTIVE toggles adaptive. AUDIO: drag changes media volume; values glide when changed elsewhere.
- [ ] POWER CELL shows only values your phone reports (no TEMP/VOLT/HEALTH line if missing); DISCHARGING / CHARGING / FULL correct.
- [ ] Studio › Control center: enable Airplane, Battery saver, Hotspot, Dark mode, Glyph, Calculator, NEXUS Settings — each opens the right official screen / app; Glyph says "NOT FOUND" if Nothing's Glyph page can't be found.
- [ ] *(emulator ✓)* Upgrade from M5: deck shows the 8 reference tiles (System QS / Settings gone) unless you had customised the list.
- [ ] *(emulator ✓)* Swipe down on the left: notification panel — header, quick strip, cards with cyan edge, group counter, ▾ MORE, swipe to dismiss, arrival pulse when a notification lands while open; no heads-up over the header.
- [ ] *(emulator ✓)* Studio › System › SystemUI renderer: tiles toggle with the glow animation; NFC tile dimmed (unavailable); card replays its pulse.
- [ ] Reduced motion / Battery-saver graphics: no scan line, no pulses, everything still works.

## Milestone 5
- [ ] `tools\phone-test\nexus-phone-test.ps1` (read-only) then `-Install`: prints PASS; Nothing Launcher still default; Nothing Camera, Glyph, Essential Space untouched.
- [ ] *(emulator ✓)* Studio › System colours without the adb grant: shows the grant command, changes nothing.
- [ ] *(emulator ✓)* After `adb shell pm grant com.nexus.launcher android.permission.WRITE_SECURE_SETTINGS`: Apply → pull down Quick Settings → NEXUS cyan; Restore → original colours. **On Nothing OS: record whether QS, shade and Settings change at all.** Revoke with `pm revoke` when done.
- [ ] *(emulator ✓)* Studio › Camera capabilities › Run probe: lists every camera id; **note the verdict on the phone** (extensions present or not).
- [ ] Glyph Pad: known-glyph names are complete (e.g. "CONTROL DECK"), up to two lines.

## Milestone 4
- [ ] *(emulator ✓)* Long-press empty home space (orbit and Core pages), keep holding → radial appears; no crash. Release in centre cancels; flick + release launches.
- [ ] *(emulator ✓)* Radial opened low on the screen: its label sits above the ring, not on the dock; opened higher: label centred under the ring.
- [ ] *(emulator ✓)* HUD ticker runs below the dock, not through the icons; open the drawer / Control Deck → no ticker showing through.
- [ ] *(emulator ✓)* Back never leaves NEXUS at rest; Back/Home close the drawer, deck and feed; Home from an app returns to NEXUS.
- [ ] Cold start (force-stop NEXUS, press Home): orbit and dock icons appear quickly, no long empty-circle phase. Compare with the milestone 3 APK if you like.
- [ ] *(emulator ✓)* Studio › live preview clock shows the current time in your 12/24-hour format.
- [ ] Everything from milestones 1–3 still works (Calculator, Settings Hub, QS tiles, deck styles, screen-saver intro).

## Milestone 3
- [ ] Studio › Control center: Deck style Glass / Hex / Ring / Minimal changes the preview tiles instantly and the real Control Deck; 2 ↔ 3 columns; header switch hides the clock/battery row. Toggling a tile animates its fill / ring / underline.
- [ ] Calculator and Settings Hub share the NEXUS header ("CALC // CALCULATOR", "SYS // SETTINGS HUB") and the faint HUD grid + corner brackets in futuristic dark mode; plain background in light mode or plain wording.
- [ ] Screen saver (Settings › Display › Screen saver › NEXUS Ambient, "While charging"): on the charger the charging animation plays for the set time, then crossfades to the ambient face. Studio › Charging › "Screen saver intro" = Off skips it. Battery Saver on → no intro.
- [ ] Battery: with NEXUS as Home, open another app for 10 min, then `adb shell dumpsys batterystats com.nexus.launcher` → NEXUS should show ~no CPU/wakeups while in the background (clock/HUD/widget refresh loops now pause when Home is not visible). Return Home → clocks and HUD values are current immediately.
- [ ] Upgrade from the milestone 2 APK (install over it): your theme, layout and deck modules are kept; new deck options start at Glass / 2 columns / header on.

## NEXUS Settings Hub (milestone 2)
- [ ] "NEXUS Settings Hub" icon opens the hub in its own task. Core ring shows the real battery % (compare with the status bar); charging label appears when plugged in.
- [ ] WIFI / BT / LOC / DND segments match the real state; change one in system Quick Settings, return → hub updates.
- [ ] Every row opens a genuine Settings page or panel (note any that open the wrong page). No row shows "isn't available".
- [ ] Search: "wifi", "upi", "usb debugging", "brightness" find the right rows; "zzzz" shows "No matching setting".
- [ ] Live controls: Torch toggles the real flashlight. DND / Rotate / Auto / Brightness show **GRANT** while those optional permissions are off — **don't grant during initial testing**; tapping only opens Android's permission page (Back returns). Media volume slider changes real media volume.
- [ ] Nothing OS panel lists Nothing apps found on the phone (e.g. Essential Space) and, if present, Glyph settings pages; each opens correctly.
- [ ] Quick Settings tiles panel: "ADD" shows Android's own "Add tile?" dialog (Android 13+); Cancel adds nothing.

## Quick Settings tiles (milestone 2)
- [ ] Add each NEXUS tile (hub or QS edit mode). Tiles look like normal Nothing OS tiles with the NEXUS hex icons.
- [ ] NEXUS Torch: toggles flashlight; state stays in sync with the system flashlight tile.
- [ ] NEXUS Eco FX: on → Studio › Performance shows Battery Saver and the home background goes static; off → previous mode returns.
- [ ] Calculator / NEXUS Settings tiles: open the app and collapse the shade. **On the lock screen they first ask for Android's unlock**, never open over the keyguard.
- [ ] NEXUS Shell: unavailable with "Set NEXUS as Home" while Nothing Launcher is Home; with NEXUS as Home it opens the shell.
- [ ] Remove all NEXUS tiles → nothing NEXUS-related remains in QS.

## Control Deck (milestone 2)
- [ ] Tiles fade between on/off (no snap) and dip slightly on press; ◆ marks real toggles, ◇ marks system panels.
- [ ] Turn Wi-Fi off in the ◇ panel, close it → the Wi-Fi tile updates on return (no polling while the deck is open).
- [ ] Settings tile opens the NEXUS Settings Hub; long-press opens stock Settings.

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
