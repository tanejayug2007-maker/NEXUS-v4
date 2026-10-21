# NEXUS — futuristic launcher for Nothing Phone

A real Android **Home app** (not a wallpaper or a full-screen demo) written in Kotlin + Jetpack Compose.
OLED-black holographic UI, dot-matrix typography, cinematic launch / unlock / charging effects, a
notification feed, a spaceship-style control deck and a full Customization Studio.

> **Status (milestone 4, 2026-10-21): builds cleanly** — `assembleDebug`, `assembleRelease`, `lintDebug` and all 65 unit
> tests pass on Windows 11 with JDK 21. Features have **not yet been verified on the phone**; follow
> [`docs/TESTING.md`](docs/TESTING.md) before relying on any of them.
> **What NEXUS can and cannot change on Nothing OS without root:** [`docs/NEXUS_OS_AUDIT.md`](docs/NEXUS_OS_AUDIT.md).

## Milestone 4 — the original V4 design, fixed and faster

No redesign: the original NEXUS V4 look (holo-frame hex icons, filled orbit rings, original radial,
Core, deck and drawer) is unchanged. This milestone fixes what running the real app on an Android
emulator exposed:

* **Crash fixed:** long-pressing the home screen (radial launcher) crashed NEXUS on its first frame.
* **Dock / ticker:** the HUD telemetry ticker no longer runs through the dock icons, and pauses under the
  drawer, Control Deck and other overlays instead of showing through them.
* **Radial label** is centred under the ring and moves above it when the ring is opened near the dock.
* **Faster icons on cold start:** concurrent requests share one render and on-screen icons render before
  the background prewarm (≈90 → ≈75 renders; visible icons ready ≈1.25 s in a debug build on the emulator).
* **Studio live preview** shows the real time (was a fixed "20:50").
* Hardening against the same crash class in charging, link-sync, background and app-chrome effects.

## Milestone 3 — Control Deck styles, shared app chrome, charging screen saver, battery

* **Control Deck customisation** (Studio › Control center): 4 tile styles — Glass, Hex, Ring (energy
  ring fills as the tile turns on), Minimal (charging underline) — with live preview; 2 or 3 columns;
  clock/battery header on/off. Still a NEXUS panel inside the launcher, not the system Quick Settings.
* **One look across NEXUS apps**: shared header and static HUD backdrop (grid, corner brackets, accent
  glow) used by Calculator and Settings Hub; follows theme, light/dark and wording settings.
* **Charging on the screen saver**: NEXUS Ambient plays your charging animation for a few seconds when it
  starts on the charger, then settles into the low-power ambient face (Studio › Charging).
* **Battery**: clock, HUD, system-stats, world-clock, date and calendar refresh loops now pause whenever
  their screen isn't visible (they used to keep running while NEXUS sat in the background as Home).
* Reference screenshots: put them in [`docs/reference/`](docs/reference/README.md) — they have not been
  received yet, so Orbit/Radial/Core/drawer were **not** redesigned in this milestone.

## Milestone 2 — Settings Hub, Quick Settings tiles, Control Deck

* **NEXUS Settings Hub** (own icon): a NEXUS-styled front door to the *real* Android/Nothing settings.
  Every row is badged **LIVE** (NEXUS changes it), **PANEL** (Android's floating panel) or **SYSTEM**
  (opens the genuine page). Live HUD core with battery and radio status; search; Nothing apps and Glyph
  pages discovered on the phone. It does not replace or reskin Android Settings.
* **5 real Quick Settings tiles**: NEXUS Torch, Eco FX (low-power NEXUS graphics), Calculator,
  NEXUS Settings, NEXUS Shell. They live in the real QS panel in Nothing OS's own tile style.
* **Control Deck**: no background polling, animated tile states, ◆ LIVE / ◇ PANEL markers, Settings → Hub.
* **Research module** (`research/`): read-only SystemUI probe + the M1 palette experiment.
* Install safely: [`docs/INSTALL.md`](docs/INSTALL.md).

## Milestone 1 — NEXUS Calculator

A real standalone calculator with its own icon ("NEXUS Calculator") and task: exact decimal arithmetic
(0.1 + 0.2 = 0.3), scientific mode (trig in DEG/RAD with inverses, ln/log, √, x², xʸ, x!, π, e, Ans),
desk-calculator percent, live result preview, persistent history, copy result, portrait and landscape.
It follows your NEXUS Studio theme, light/dark mode, shape style and wording.

---

## v4 — your launcher, your way

**Pick a personality on first launch** (change it any time in *Customize › Experience*):

| | **User friendly** | **Futuristic** |
|---|---|---|
| Looks like | A clean, normal phone launcher | The NEXUS cockpit |
| Home | 4×6 grid, clock + search header, floating dock | CORE dashboard, orbit rings, HUD |
| Long-press | Normal menu | Radial launcher |
| Swipe down | Android's notification shade | NEXUS notification feed |
| Animations | System default | Hyperspace launch, iris unlock, link ritual |
| Wording | Plain ("Settings", "Notifications") | Technical ("// SIGNALS", "STUDIO") |
| Theme | Material You (your wallpaper colours) | Helix Prime |

The switch applies a full set of options in one tap. **Every option can still be changed on its own**, so
you can have, for example, a light Material-You theme *with* the orbit page, or a dark futuristic theme
with a plain grid.

**New in v4**
* **Light / Dark / System** appearance for every theme (accents are automatically darkened in light mode
  so text stays readable — checked by a unit test).
* **22 themes** — 12 futuristic, 10 everyday (Material You, Classic Blue, Graphite, Paper, Ocean, Sakura,
  Forest, Sunset, Midnight OLED, Mono Pill). Material You follows your wallpaper on Android 12+.
* **Look & feel:** 5 shape styles (cut corners, rounded, soft, sharp, pill), plain or futuristic wording,
  home header (none / clock / clock + search / search), 5 dock styles + 3–7 dock apps, badge style
  (dot / count / none), default widget frame.
* **Orbit home settings:** on/off, 1–3 rings, apps per ring, icon size, app names, ring names, 5 ring
  styles, 4 centre cores (reactor, clock, battery, minimal), drift on/off + speed + alternate direction,
  what tapping / holding the core does, and **pinned apps**.
* **Radial launcher settings:** on/off, 4–10 slots, size, labels, the "home menu" slot, and which apps
  appear (predicted, your dock, or a **custom list** you pick).
* **14 widget types** (was 6): Clock (8 styles), Battery, Weather, Media, System, Signals, plus new
  Search bar, Quick toggles, World clock, Note, Calendar (next 24 h), Photo, Favourite apps, Date —
  each with **6 frames** (glass, solid, outline, neon, pill, none).
* **Clock styles:** added Classic and Analog (8 total). **Backgrounds:** added Gradient and Aurora.
  **Page transition:** added Slide.
* **Speed:** frame-capped ambient animations (30 fps, 60 in Ultra), icon pre-warming on start, a
  **baseline profile** (ahead-of-time compiled hot paths via `profileinstaller`), and a Compose stability
  config so unchanged parts of the screen are skipped. See *Performance* below — **install the release
  build** for real-world speed.

---

## v3 — everything customisable

Every surface has **6+ selectable styles** and its own colour (Studio shows a live preview of each):

| Feature | Styles | Where |
|---|---|---|
| Clock | Dot Matrix · Holo Ring · Segment · Stacked · Binary · Orbital | CORE page, clock widget, lock layer, always-on |
| App drawer | Grid · List · Honeycomb · Carousel (3D cover-flow) · Categories · Terminal | Studio › Drawer |
| Page transition | Tilt 3D · Cube · Depth · Carousel · Flip · Glitch | Studio › Drawer |
| App launch | 10 presets (Hyperspace, Singularity, Hex Dissolve, Data Stream, Portal…) | Studio › Motion |
| Unlock reveal | 7 presets (Iris Scan, Boot Sequence, Hologram…) | Studio › Motion |
| Charging | Energy Reactor · Quantum Ring · Electric Pulse · Battery Core · Plasma Circuit · **Liquid Fill** · **Particle Vortex** | Studio › Charging |
| Call screen | Holo Ring · Radar · Waveform · Glitch · Orbit · Minimal + answer gesture (Slide · Hold · Drag-to-ring) | Studio › Calls |
| Lock layer fingerprint | Scan Ring · Ripple · Hex Lock · Radar Sweep · Particle Gather · Glitch Iris | Studio › Lock |
| Always-on | Dot Clock · Holo Ring · Edge Pulse · Constellation · Glyph Matrix · Data Minimal | Studio › Always-on |
| Home widgets | Clock · Battery · Weather · Media · System · Signals — 6 styles each | Long-press home › Add widget |

### How the system-level features work (honest version)
* **Calls** – Android only allows custom call screens from the **default phone app**. Studio › Calls › Enable makes
  NEXUS the phone app (InCallService + a dialer). Telecom still rings; call history & contacts stay in your old apps.
  Switch back any time in Settings › Apps › Default apps › Phone.
* **Lock screen + fingerprint** – no app can replace the secure lock screen or Nothing OS's sensor animation. NEXUS can
  show an optional **lock layer** *in front of it* when the screen turns on (needs "Display over other apps"). Unlocking
  is still Android's: swipe up / tap the ring and Android's own fingerprint/PIN check runs. The fingerprint animation is
  drawn by NEXUS at a position you calibrate over the real sensor.
* **Always-on** – the system AOD can't be replaced. NEXUS faces run as Android's **screen saver** while charging
  (Settings › Display › Screen saver › *NEXUS Ambient*), or the lock layer can dim into ambient mode for N minutes
  (keeps the screen on: costs battery; burn-in shift moves content every minute).

---

## v2 "HELIX" — what's new

NEXUS v2 is deliberately **harder to use**: it rewards learning it, like a cockpit.

* **Interface modes** (Studio › Interface, or shell `mode …`):
  * **Civilian** – the v1 grid + dashboard. Easiest.
  * **Operator** (default) – ORBIT page, radial launcher, HUD, gyroscope depth, neural-link unlock ritual.
  * **Architect** – no app drawer at all; swipe up opens the shell. Apps live in orbit, glyphs, the radial or commands.
* **ORBIT page** – a reactor core with apps on three counter-rotating rings (*Predictive* / *Primary* / *Archive*). Drag a ring to spin it with inertia. No labels — you learn where things are. Tap the core → shell, hold the core → glyph pad.
* **Radial launcher** – long-press empty space, keep holding, flick toward one of 7 apps, release. Top slot = edit menu, centre = cancel.
* **NEXUS//SHELL** – a real command line: `open`, `apps`, `calc 2^10/4`, `flash on`, `dnd off`, `bright 40`, `vol 60`, `timer 5m`, `alarm 07:30`, `theme horizon`, `mode architect`, `fx singularity`, `sys`, `battery`, `glyph`, `train`, `matrix`, `help`… with autocomplete and history.
* **Glyphs** – draw symbols to launch things. Built-ins: ○ shell · ⚡ torch · ^ control deck · V signals · L lock · Z app index. Train your own glyph for any app (on-device $1-style recognizer).
* **Neural-link unlock ritual** – after Android's real unlock, hold the pulsing core to "sync" before home materialises. Back skips it; it is not security.
* **Predictive ring** – learns which apps you open at each hour (counts stored only on the phone; wipe in Studio).
* **New visuals** – GPU (AGSL) nebula background, Tron-style **Holo Grid**, HUD with reticles, edge rulers, live compass heading and telemetry ticker, gyroscope parallax, 3D page tilt, new **Helix Prime** and **Horizon** themes.
* **New app-open effects** – Hyperspace Jump (GPU warp streaks), Singularity, Hex Dissolve, Data Stream (+ the 6 from v1).
* **New unlock reveals** – Iris Scan, Boot Sequence, Hologram Projection (+ the 4 from v1).
* **Haptic signatures** – distinct vibration waveforms for launch, unlock, link, glyph match/fail and charging.

**Getting unstuck:** NEXUS Studio is always openable from any launcher's app list; in the shell type `mode civilian`.

**What still can't change:** a launcher only controls its own screens. Other apps, the lock screen, the system
notification shade and Quick Settings keep Nothing OS's look. For a more futuristic *whole phone*, pair NEXUS
with Nothing OS's own options (Settings › Display › dark theme, Nothing font, Glyph Interface) and an
icon pack.

---

## 1. Build the APK (Windows + Android Studio)

1. Install **Android Studio** (Ladybug 2024.2 or newer). During setup let it install the Android SDK
   (API 35) and JDK.
2. Unzip `NEXUS.zip` somewhere without spaces in the path, e.g. `C:\dev\NEXUS`.
3. Android Studio → **File › Open** → select the `NEXUS` folder → **Trust project**.
4. Wait for **Gradle Sync** to finish (first time downloads ~1 GB of dependencies).
   **Gradle JDK must be 17 or 21.** Newer Android Studio builds bundle JDK 25, which Gradle 8.11.1 can't
   run on (the error is just "What went wrong: 25.0.3"). Fix: *Settings › Build, Execution, Deployment ›
   Build Tools › Gradle › Gradle JDK* → `jbr-21`. For `gradlew.bat` in a terminal, first run
   `set JAVA_HOME=C:\Users\<you>\.jdks\jbr-21.0.11` (or any JDK 21).
5. **Build › Build App Bundle(s) / APK(s) › Build APK(s)**.
   The APK appears at `app\build\outputs\apk\debug\app-debug.apk`.

> **For daily use, build the release APK** — Compose debug builds run several times slower and will
> stutter. **Build › Generate Signed App Bundle / APK** is not needed: release is pre-signed with the
> debug key, so just run `gradlew.bat assembleRelease` and install
> `app\build\outputs\apk\release\app-release.apk`. (Uninstall the debug build first if installed — the signatures match, so an update also works.)

Command line alternative (from the project folder): `gradlew.bat assembleDebug`
Run the unit tests: `gradlew.bat testDebugUnitTest`

If the build fails, the error panel names the file and line — paste it into Claude and it can be fixed.

## 2. Install and make it your launcher

**USB (recommended):** enable *Developer options › USB debugging* on the phone, connect it, press the
green ▶ Run button in Android Studio. Or `adb install -r app-debug.apk`.

**Without a cable:** copy `app-debug.apk` to the phone and open it (allow "Install unknown apps" for your
file manager).

Then:
1. Open **NEXUS Studio** from your current launcher's app list.
2. Tap **Set default** (or Settings › Apps › Default apps › Home app › NEXUS).
3. Press **Home**. You are now on NEXUS.
4. In Studio › System, grant the optional permissions you want (see §4).

To go back to Nothing Launcher: Settings › Apps › Default apps › Home app.

**Nothing OS tip:** Settings › Apps › NEXUS › Battery → *Unrestricted* keeps the notification feed and
charging detection responsive. A default launcher is rarely killed, but this removes any doubt.

## 3. What works, and what Android does not allow

| Area | Implementation | Status |
|---|---|---|
| Default launcher | `HOME` intent filter, `RoleManager.ROLE_HOME`, singleTask, Back never exits, Home returns to default page, survives reboot | Implemented |
| App discovery & launch | `LauncherApps` (incl. work profile, live install/uninstall/update) | Implemented |
| Home screen | CORE dashboard page + multiple grid pages, dock, page tilt transition | Implemented |
| Drag & drop | Long-press-drag icons; drop on app = folder, on folder = add; "Move" placement mode for widgets / cross-page | Implemented |
| Folders | Spring expansion from icon, rename inline, remove apps | Implemented |
| Widgets | Real `AppWidgetHost`, bind permission flow, configure activities, resize, move, remove | Implemented |
| App shortcuts | Long-press menu shows app shortcuts (needs NEXUS to be default) | Implemented |
| App drawer | Finger-tracking open, blurred backdrop (Android 12+), fuzzy search (prefix/word/initials/subsequence, accent-insensitive), A–Z rail, web search, hide apps | Implemented |
| Icons | Holo-frame / Glyph (uses Android 13 themed-icon layer) / Glass / Original; 6 shapes; size; labels; accent tint; ADW/Nova icon packs; per-app image + name | Implemented |
| Themes | 7 presets (Quantum Black, Electric Cyan, Plasma Purple, Titanium Silver, Neural Blue, Cyber Green, Stealth Mode), HSV + hex color editor, save/delete custom looks | Implemented |
| Backgrounds | Dot grid, procedural circuit with data pulses, particles, void, system wallpaper | Implemented |
| Typography | Live dot-matrix renderer for numerals, 5 display + 5 body families (system fonts), scale, weight, tracking | Implemented |
| App-open animations | 6 presets. Window transition via `ActivityOptions` (clip-reveal / scale-up / custom anim) + launcher-side Canvas flourish. Launch delay is configurable 0–150 ms (0 in Battery Saver) | Implemented — **OS may override** the window part (some gesture-nav builds force their own animation) |
| Post-unlock reveal | 4 presets, staggered per-module, triggered on `USER_PRESENT` / resume after screen-off | Implemented — **runs after** the secure lock screen, never replaces it |
| Charging animation | 5 presets, accurate % / source / temperature, duration & intensity | Implemented — shown **inside NEXUS** when plugged in while it is visible, or on unlock within N seconds. **Cannot appear over the lock screen** |
| Notification feed | `NotificationListenerService`: grouped cards, open, swipe-dismiss, clear all, popups, badges, content redaction | Implemented — a **launcher view**; the real shade is untouched and one tap away |
| Media controls | Active `MediaSession` on CORE page (play/pause/next/prev, artwork) | Implemented (needs notification access) |
| Status strip | Time, battery segments, network type + signal, alert count; optional immersive home | Implemented — **home screen only**; other apps keep the system status bar |
| Control center | Real toggles: flashlight, Do Not Disturb, brightness dial, auto-rotate, media volume dial. Official panels: Wi-Fi, Internet/mobile data, NFC, volume, Bluetooth & location settings, system Quick Settings | Implemented — Android 10+ forbids third-party Wi-Fi/BT/data toggles, so those open the system panel (marked ◇) |
| Gestures | Swipe up, split swipe-down (left/right half), double tap, pinch in/out → 14 actions (incl. shell, glyph pad); haptics off/light/strong | Implemented — *Lock screen* and reliable *Recents* need the optional accessibility helper |
| Weather | Open-Meteo, approximate location, 30 min cache | Implemented (optional) |
| Customization Studio | Every option above + live preview with Launch/Unlock/Charge buttons, JSON export/import, reset | Implemented |
| Performance modes | Auto / Ultra / Balanced / Battery Saver; auto-downgrade on power-saver, heat (thermal API) or system "remove animations" | Implemented |
| Frame stats overlay | Measured FPS, janky frames/s, worst frame | Implemented (developer toggle) |

**Not possible for any normal launcher (and therefore not attempted):** replacing the secure lock screen,
drawing over the keyguard, replacing the system notification shade or status bar inside other apps,
silently toggling Wi-Fi/Bluetooth/mobile data on Android 10+. NEXUS uses no root and no private APIs
except one optional reflection fallback (`StatusBarManager.expandNotificationsPanel`) that is used only if
the accessibility helper is off; if Android blocks it, NEXUS shows a message instead.

## 4. Permissions

| Permission | Why | Required? |
|---|---|---|
| `QUERY_ALL_PACKAGES` | List every app (launcher use case) | Yes (auto) |
| `REQUEST_DELETE_PACKAGES` | "Uninstall" in the long-press menu | Yes (auto) |
| `EXPAND_STATUS_BAR` | Open the real shade / Quick Settings from a gesture | Auto |
| `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` | Status strip & control-center state (read-only) | Auto |
| Notification access (special) | Feed, badges, media, popups | Optional |
| Accessibility helper (special) | Double-tap to lock, open shade/QS/recents. Reads **no** screen content | Optional |
| Modify system settings (special) | Brightness dial, auto-rotate toggle | Optional |
| Do Not Disturb access (special) | DND toggle | Optional |
| Approximate location + Internet | Weather module only | Optional |
| Read calendar | Calendar widget only (asked when you tap it) | Optional |

All data stays on the phone. NEXUS has no analytics and no network access except the optional weather call.

## 5. Project structure

```
app/src/main/java/com/nexus/launcher/
  MainActivity.kt          HOME activity: window, launch transitions, widgets binding, unlock/charge triggers
  LauncherViewModel.kt     UI state, overlays, layout editing, persistence
  NexusApp.kt              process singletons (no DI framework)
  core/                    pure-ish logic: models, DataStore, LayoutOps, Search, apps, icons, system monitor, weather
  system/                  SystemControls (public APIs only), TorchController, accessibility helper
  notifications/           NotificationListenerService + NotificationHub + media sessions
  ui/theme                 palette, typography, icon-shape outline
  ui/fx                    dot matrix, backgrounds, launch / unlock / charging effects
  ui/components            glass panels, controls, icons, gestures, frame stats
  ui/home                  home root, grid (drag & drop), CORE dashboard, status strip, folders, menus
  ui/drawer ui/widgets ui/notifications ui/control ui/settings
  apps/calculator          NEXUS Calculator: pure-Kotlin engine + input model, ViewModel, Compose UI
research/                  ADB experiments for the real system UI — never packaged into the APK
app/src/test/              LayoutOps, Search, config, glyph, console, experience and light-palette tests
```

Modules are packages inside one Gradle module to keep the first build simple; each package has no
dependency on the UI layers above it, so they can be split into Gradle modules later.

## 6. Performance design

* Icons are composed once off the main thread and cached in a byte-sized LRU (trimmed on memory pressure).
* Every continuous animation reads its clock **inside draw / graphicsLayer** lambdas → no per-frame recomposition.
* Ambient backgrounds stop when NEXUS is not visible; in Balanced they animate ~7 s after interaction then rest.
* Battery-level receiver is registered only while the launcher is in the foreground; no services, no wake locks, no polling outside the open control deck (1.5 s, only while visible).
* Blur uses `RenderEffect` on Android 12+ and is disabled in Battery Saver.
* Ambient clocks are **frame-capped** (30 fps, 60 in Ultra) and only publish a new time once per frame budget.
* Icons for the home screen and drawer are **pre-warmed** on a background thread right after start.
* `baseline-prof.txt` + `profileinstaller` → the launcher's code is compiled ahead of time on install, so
  the first scrolls aren't interpreted. `compose_stability.conf` marks the immutable models as stable.
* **Fastest setup:** User-friendly experience or Performance mode *Battery/Balanced*, animated background off,
  blur off. Heaviest: Ultra + Nebula/Warp shader backgrounds + HUD + 3-ring orbit.
* Measure, don't trust: enable *Studio › Performance › Frame stats overlay*, and see `docs/TESTING.md` for
  `dumpsys gfxinfo` / `batterystats` commands.

## 7. Adding your own fonts

The fonts offered are Android system families (always available, zero APK cost). To use e.g. Orbitron or
Space Grotesk: put the `.ttf` in `app/src/main/res/font/`, then in `ui/theme/NexusTheme.kt` map a
`DisplayFont` entry to `FontFamily(Font(R.font.your_font))`.
