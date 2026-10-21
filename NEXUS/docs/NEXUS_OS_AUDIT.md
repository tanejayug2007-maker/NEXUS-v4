# NEXUS OS — technical audit, feasibility map and roadmap

Target: **Nothing Phone (3a)** (Asteroids / AsteroidsIND), **Nothing OS 4.1 / Android 16**, bootloader
**locked**, no root, verified boot green. Audit date: 2026-10-21 (milestone 1).

> **Milestone 2 update (2026-10-21):** see §10 for what changed. Everything below is still
> build-verified only, not device-verified.

> **How this audit was done.** Source-level review of the NEXUS V4 project plus a real build on the
> Windows machine (JDK 21, AGP 8.7.3, Gradle 8.11.1). No phone was connected over ADB, so **nothing in
> this document has been verified on the device**. The reference screenshots mentioned in the brief were
> not in the repository or this conversation — the visual baseline used here is the V4 code itself
> (Helix Prime theme, orbit/radial/HUD components). Put the screenshots in `docs/reference/` so later
> milestones can be checked against them.

---

## 1. The one-paragraph answer

Without unlocking the bootloader, NEXUS can own **every screen it draws itself** (home, drawer, Studio,
calculator, dialer/in-call UI *if you choose to make it the phone app*, screen saver, an optional layer in
front of the lock screen) and can **influence the system's colours** through Android's own Material You
engine. It **cannot** restyle the layout, shapes or animations of the real notification shade, Quick
Settings, status bar, secure lock screen, AOD, volume panel, power menu, recents, Settings or the
Nothing Camera. Those are Tier 3/4: they need root (which requires a bootloader unlock, which factory-resets
the phone and will very likely break banking/UPI attestation) or Nothing's signing key. Anything in this
project that *looks* like those surfaces is a NEXUS screen and is labelled as such.

---

## 2. Implementation tiers

| Tier | Meaning | Available on your phone today? |
|---|---|---|
| **1** | Ordinary app, no special permission | Yes |
| **2** | User-granted special access, an Android role, or an ADB/Shizuku-level setting | Yes, with your approval per item |
| **3** | Root / Magisk / LSPosed hooks or modified system partitions | **No** — needs bootloader unlock (wipes data; Play Integrity "strong" fails) |
| **4** | Platform/Nothing signing key, vendor cooperation or proprietary source | **No** |

---

## 3. Feature-by-feature audit

Status key: **Works (build)** = implemented and compiles, not yet device-verified · **Sim** = a NEXUS
screen that resembles a system surface · **Partial** · **Missing** · **Not possible** (at that tier).

### A. Launcher (Module 1)
| Feature | Status | Tier | Notes |
|---|---|---|---|
| Default HOME activity, singleTask, Back never exits, Home → default page | Works (build) | 1 | `MainActivity`, `LauncherViewModel.onBack/onHomePressed`. Lifecycle reviewed: receivers start/stop with `onStart/onStop`, no services, no wake locks. |
| Orbit Home (1–3 rings, inertia, cores, pinned apps) | Works (build) | 1 | `ui/home/OrbitPage.kt` |
| Radial launcher (4–10 slots, sources) | Works (build) | 1 | `ui/home/RadialSelector.kt` |
| CORE dashboard, HUD chrome, holo grid / nebula (AGSL) backgrounds | Works (build) | 1 | AGSL needs Android 13+ (fine on Android 16) |
| Hex / holo / glyph / glass icon frames, 6 shapes, icon packs (ADW/Nova) | Works (build) | 1 | `core/IconEngine.kt`, LRU cache trimmed on memory pressure |
| Grid, folders, dock styles, drag & drop, pages | Works (build) | 1 | `LayoutOps` has 10 unit tests |
| App drawer: 6 layouts, fuzzy search, A–Z, hide apps | Works (build) | 1 | |
| Real AppWidgetHost + 14 NEXUS widgets × 6 frames | Works (build) | 1 | |
| Notification badges | Works (build) | 2 | Needs notification access |
| Launch transitions (10 presets) | Partial | 1 | Launcher-side flourish always runs; the *window* animation may be overridden by Nothing OS gesture navigation. App-**closing** animations need the system recents (Quickstep), which third-party launchers cannot provide → **Not possible** at Tier 1. |
| Gestures, glyph pad, NEXUS//SHELL | Works (build) | 1–2 | Lock/recents gestures need the optional accessibility helper |
| Neural-link unlock ritual, unlock reveals | Works (build) | 1 | Runs **after** Android's real unlock; never part of authentication |
| Charging animation | Partial | 1 | Shown inside NEXUS only; cannot appear over the secure lock screen |
| Multiple modes (User-friendly / Futuristic; Civilian / Operator / Architect) | Works (build) | 1 | |

### B. Notification shade & Quick Settings
| Feature | Status | Tier | Notes |
|---|---|---|---|
| NEXUS notification feed (grouped cards, swipe dismiss, popups, media) | **Sim** | 2 | `NotificationListenerService`. A launcher view; the real shade is untouched. |
| NEXUS Control Deck (torch, DND, brightness, rotate, volume) | **Sim** with real controls | 2 | Real toggles where Android allows; Wi-Fi/BT/mobile data open system panels because Android 10+ forbids third-party toggling. |
| NEXUS Quick Settings **tiles inside the real QS panel** | **Works (build) — milestone 2** | 1 | 5 `TileService` tiles (Torch, Eco FX, Calculator, Settings Hub, Shell). Real tiles in the real panel, drawn in Nothing OS's tile style. |
| Real shade/QS colours | Experiment | 2 | Material You seed colour — see §5 |
| Real shade/QS layout, tile shapes, animations, notification card style | Not possible | 3 / 4 | Needs LSPosed hooks on `QSTileViewImpl`, `QSPanel`, `QuickStatusBarHeader`, `NotificationStackScrollLayout` (root) or a platform-signed SystemUI overlay. Your AAPT2 finding (no overlayable resources) rules out a non-root RRO. |

### C. Status bar
| Feature | Status | Tier | Notes |
|---|---|---|---|
| NEXUS status strip on home (battery, network, time) | **Sim** | 1 | Home screen only; optional immersive mode hides the real bar on home |
| Real status bar icons, clock, battery style | Not possible | 3 / 4 | Privacy indicators must never be hidden or restyled; any Tier 3 work must exclude them |

### D. Lock screen & authentication
| Feature | Status | Tier | Notes |
|---|---|---|---|
| NEXUS lock layer (clocks, notifications, fingerprint visual) | **Sim**, experimental, off by default | 2 | `LockLayerActivity` with `showWhenLocked`; swipe calls `requestDismissKeyguard`, so Android's PIN/biometric check always runs. Fingerprint art is decoration over the real sensor. **Needs device verification that emergency call stays reachable** before daily use. |
| Replacing keyguard, custom lock clock in the real keyguard | Not possible | 4 | Lock-screen clock plugins require system signing |
| Biometric visuals | Decoration only | 1 | Never fakes or replaces authentication |

### E. Charging & always-on
| Feature | Status | Tier | Notes |
|---|---|---|---|
| 7 charging styles (inside NEXUS) | Works (build) | 1 | Triggered by `ACTION_POWER_CONNECTED`, no background service |
| Battery temperature | Works (build) | 1 | From `BatteryManager` sticky intent (documented source) |
| NEXUS Ambient screen saver (6 faces, burn-in shift) | Works (build) | 2 | `DreamService`; user enables it in Settings › Display › Screen saver; only while charging/docked |
| Real AOD replacement | Not possible | 4 | Nothing's AOD is SystemUI/doze |

### F. Settings
| Feature | Status | Tier | Notes |
|---|---|---|---|
| NEXUS Studio (launcher customisation) | Works (build) | 1 | |
| NEXUS Settings Hub (NEXUS-styled index that deep-links into real Settings pages) | **Works (build) — milestone 2** | 1–2 | Interim answer to "real Settings redesign": every row opens the genuine page (`Settings.ACTION_WIFI_SETTINGS`, `ACTION_BLUETOOTH_SETTINGS`, `ACTION_DISPLAY_SETTINGS`, Settings panels…). No fake toggles. |
| Reskinning stock Settings | Not possible | 4 | Settings is a platform-signed priv-app; only the Material You colours (§5) reach it |

### G. Camera
| Feature | Status | Tier | Notes |
|---|---|---|---|
| Reskin Nothing Camera | Not possible / not allowed | 4 | Modifying the proprietary APK breaks its signature, cannot be installed over the system app without root, and isn't permitted by its licence. **The Nothing Camera stays untouched.** |
| Launch Nothing Camera from NEXUS with a NEXUS transition | Works (build) | 1 | Same as any app launch |
| Optional "NEXUS Lens" camera companion (CameraX) | Missing | 1 | Feasible, with honest limits: it will **not** have Nothing's processing (their HDR/night/portrait pipeline, Ultra XDR). It can use Camera2 **Extensions** (Night/HDR/Bokeh) *only if* the phone exposes them — to be probed on-device with `CameraExtensionCharacteristics`. Telephoto/ultrawide access depends on what the HAL exposes to third-party apps (logical multi-camera). |

### H. Calculator
| Feature | Status | Tier | Notes |
|---|---|---|---|
| NEXUS Calculator | **Works (build) — new in milestone 1** | 1 | See §7 |

### I. Phone, contacts, messages
| Feature | Status | Tier | Notes |
|---|---|---|---|
| NEXUS in-call screen + dialer (6 styles) | Works (build), **experimental, off by default** | 2 | Requires *you* to make NEXUS the default phone app (ROLE_DIALER). Never automatic. Emergency calls must be tested on device before relying on it; reverting is Settings › Apps › Default apps › Phone. **Recommendation: keep Nothing's Phone app as default until §8 tests pass.** |
| Call log / contacts UI | Missing | 2 | Needs `READ_CALL_LOG` / `READ_CONTACTS`; low priority |
| SMS app | Not started — deliberately | 2 | Becoming the SMS role is high-risk for OTPs/UPI; not planned |

### J. Remaining system interfaces
| Surface | Tier | Verdict |
|---|---|---|
| Volume panel, power menu | 4 | Not replaceable. (Overlay/accessibility imitations are fragile and not pursued.) |
| Recents / app switcher | 4 | Third-party launchers use the system recents |
| Install/permission dialogs | 4 | Must never be imitated or covered — security screens |
| Keyboard | 1 | A NEXUS IME is possible; or use Gboard's custom theme (zero risk) |
| Clock/alarms, calendar, gallery, file manager, weather | 1 | Feasible as NEXUS apps later; launcher already launches the real ones |
| Glyph interface | 2 | Nothing's public **Glyph Developer Kit** — verify (3a) support before building |

---

## 4. Gap: "futuristic launcher" → "whole-phone NEXUS"

What closes the gap **without root**, in impact order:
1. **System colour** — Material You seed set to NEXUS cyan (§5). One change that recolours the real QS,
   shade, Settings and Material You apps. Highest impact per risk.
2. **Pair with Nothing's own options** — dark theme, Nothing font, monochrome/themed icons.
3. **NEXUS-drawn apps** for the screens you use most — calculator (done), Settings hub, clock, weather.
4. **Real QS tiles** (`TileService`) for NEXUS actions: shell, glyph pad, Studio, calculator.
5. **Screen saver + optional lock layer** for the charging/lock moments.

What would need Tier 3 (root) — documented, **not** started (Phase 6 below).

---

## 5. Research track (Module 5) — first experiment

`research/system-palette/nexus-system-palette.ps1` (not part of any APK):
- Writes Android's own `Settings.Secure theme_customization_overlay_packages` key — the same key
  Settings › Wallpaper & style writes — with a NEXUS seed colour. No root, no system files.
- Refuses to run unless exactly one device with codename `Asteroids*` is connected; prints the build
  fingerprint; backs up the previous value before writing; `-Restore` puts it back.
- **Unverified on Nothing OS 4.1.** Nothing may ignore or overwrite it. I have **not** run it — it touches
  your real phone, so it waits for your go-ahead.

Rollback without the script: pick any colour in Settings › Wallpaper & style, or
`adb shell settings delete secure theme_customization_overlay_packages`.

---

## 6. Architecture

Current: one Gradle module (`:app`), package-layered (`core` has no UI deps; `ui/*` on top). Milestone 1
adds `apps/calculator` as a self-contained package (engine + input model are pure Kotlin; only the
Activity/ViewModel touch Android).

Target module split (to be done incrementally so nothing breaks):

```
:design          theme engine (NexusTheme, palettes, shapes, glass/HUD components)   ← extract first
:core            models, ConfigStore, LayoutOps, Search, IconEngine
:launcher        HOME activity, home/drawer/widgets/notifications UI          (Module 1)
:studio          NEXUS Studio                                                   (Module 3)
:apps:calculator NEXUS Calculator (could ship as its own APK once :design exists) (Module 2)
:system          public-API integrations: TileService, notification listener, controls (Module 4)
research/        ADB scripts and privileged proofs-of-concept, never packaged       (Module 5)
app/src/test …   JVM tests; later :validation with macrobenchmark + baseline profile (Module 6)
```

Why the calculator lives in the launcher APK for now: it reuses the theme engine and Studio settings
without duplicating ~1,000 lines, while still being its own task with its own icon. Once `:design` is
extracted it can become a separate APK with no code change in the engine.

---

## 7. Milestone 1 — what changed

| File | Change |
|---|---|
| `app/src/main/java/com/nexus/launcher/apps/calculator/CalcEngine.kt` | **New.** Exact BigDecimal engine: precedence, right-assoc powers, implicit ×, desk-calculator %, auto-closing parentheses, sin/cos/tan + inverses (DEG/RAD), ln/log, √, x!, π, e, Ans; typed errors (syntax, ÷0, domain, overflow); grouped/scientific formatting. |
| `…/apps/calculator/CalcInput.kt` | **New.** Immutable input-editing rules: no `007`, one decimal point per number, operator replacement, negative operands, smart `( )`, ± on the current operand, token-wise ⌫, continue-from-result after `=`. |
| `…/apps/calculator/CalculatorViewModel.kt` | **New.** State, live preview, history (100 entries, private SharedPreferences), DEG/RAD + SCI persisted, expression survives rotation/process death. |
| `…/apps/calculator/CalculatorActivity.kt` | **New.** NEXUS UI: glass display, cut-corner keys with press "charge", standard + scientific (2ND layer) keypads, portrait/landscape, history sheet, long-press to copy, one result pulse (skipped in Battery Saver/reduced motion), TalkBack labels. Follows Studio theme, light/dark, shape and wording settings. |
| `app/src/main/AndroidManifest.xml` | Calculator activity: own launcher icon + task; `APP_CALCULATOR` category (no role/default change). |
| `res/drawable/ic_calc_foreground.xml`, `res/mipmap-anydpi-v26/ic_calculator.xml`, `res/values/strings.xml` | Icon and label. |
| `app/src/test/…/apps/calculator/CalcEngineTest.kt`, `CalcInputTest.kt` | **New.** 21 tests. |
| `research/system-palette/nexus-system-palette.ps1` | **New.** Research experiment (§5). |
| `docs/NEXUS_OS_AUDIT.md`, `docs/TESTING.md`, `README.md` | This audit; calculator checklist; build notes. |

No existing launcher code was modified, so existing behaviour is unchanged.

**Verified here:** `assembleDebug` ✔, `assembleRelease` (R8) ✔, `testDebugUnitTest` 57/57 ✔.
**Not verified:** anything on the phone.

---

## 8. Findings to act on (from the review)

1. **Build JDK.** Android Studio's bundled JBR on this machine is **JDK 25**, which Gradle 8.11.1 cannot
   run on ("What went wrong: 25.0.3"). Builds work with the **JBR 21** in `C:\Users\yugar\.jdks\jbr-21.0.11`.
   In Studio: *Settings › Build, Execution, Deployment › Build Tools › Gradle › Gradle JDK* → jbr-21.
   From a terminal: `set JAVA_HOME=C:\Users\yugar\.jdks\jbr-21.0.11` before `gradlew`.
2. **Optional dialer role** — device-test emergency calling (e.g. by dialling your carrier's test/short
   code, **not** an actual emergency number) before ever enabling it.
3. **Optional lock layer** — verify the emergency-call button on Android's bouncer is reachable from it
   and that it never delays the fingerprint unlock.
4. `targetSdk` is 35 on an Android 16 (API 36) phone. Fine for now (no behaviour gaps found); raising it
   needs an AGP upgrade and a predictive-back pass.

---

## 9. Roadmap

| Phase | Next concrete steps |
|---|---|
| 1 Audit & stabilise | ✔ this document, baseline build, JDK fix documented. ▢ On-device run of `docs/TESTING.md` "Launcher core". |
| 2 Launcher | Device pass; fix what the pass finds; release-build performance numbers; compare to the reference screenshots. |
| 3 NEXUS apps | ✔ Calculator. ▢ NEXUS Settings hub (real deep links). ▢ Clock/timer. ▢ Camera companion feasibility probe (extensions, lenses) before any UI. |
| 4 Safe integrations | ▢ QS `TileService` tiles. ▢ Glyph Developer Kit check. ▢ Charging/AOD battery measurements. |
| 5 System research | ▢ Run the palette experiment (with your OK). ▢ Pull & document `SystemUI.apk` view hierarchy for the QS panel (read-only, `adb shell dumpsys activity` / layout inspector on a debug-enabled build where possible). |
| 6 Privileged (only with explicit authorisation) | Requires: full backup, exact-firmware match, tested recovery path, and accepting a factory reset + likely loss of banking/UPI/Play Integrity. Scoped LSPosed module limited to `com.android.systemui` QS/notification views, never keyguard or privacy indicators. **Not recommended on a daily phone.** |
| 7 Optimise & verify | Macrobenchmark module, baseline profile generation, `gfxinfo`/`batterystats` numbers in this doc. |

### About unlocking the bootloader (for the record)
Unlocking **erases all user data** (photos, WhatsApp, everything not backed up). After unlock, Play
Integrity "strong" verdict fails; many banking/UPI apps and some DRM content may stop working, and OTA
updates become complicated. Relocking with modified partitions can brick the device and must never be
done. None of this is required for anything in Phases 1–5.

---

## 10. Milestone 2 — what changed (2026-10-21)

### Functional vs simulated vs privileged — milestone 2 additions
| Feature | Category | What it really does |
|---|---|---|
| NEXUS Settings Hub | **Functional** (NEXUS app) | NEXUS-styled index of ~45 real Settings destinations across 10 categories + Nothing apps/Glyph pages discovered on the phone. Each row opens the **genuine** page (SYSTEM) or Android's floating panel (PANEL); rows the phone can't open are hidden. It does **not** replace or reskin Android Settings. |
| Hub live controls | **Functional** | Torch, media volume (no permission); DND, auto-rotate, adaptive brightness, brightness (only after you grant the optional permission — shown as GRANT, never auto-requested). |
| Hub status core | **Functional** (read-only) | Real battery %, charging, temperature, health; Wi-Fi/BT/location/DND state. |
| QS tiles ×5 | **Functional**, real system integration | Live in the real QS panel. NEXUS sets icon/label/state only; Android draws the tile. Launching tiles ask for Android's unlock first on the lock screen. |
| "Add tile" buttons | **Functional** | Android 13+ `requestAddTileService` → Android's own confirmation dialog. |
| Control Deck | **Simulated panel** with real controls | Still a NEXUS overlay inside the launcher, not the system QS. Now event-driven (no 1.5 s polling), animated tile states, ◆/◇ honesty markers, Settings → Hub. |
| SystemUI probe | Research, **read-only** | Inventories this firmware's theming surface; changes nothing. |
| System palette | Research, privileged-lite (ADB) | Unchanged from M1; still unverified, not run. |
| Real shade / status bar / keyguard / AOD restyle | **Requires privileged access** (Tier 3/4) | Not attempted. See `research/README.md`. |

### Files
| File | Change |
|---|---|
| `apps/settingshub/SettingsCatalog.kt` | **New.** Pure catalog: categories, entries, ordered intent actions with public fallbacks, capability badges, accent-insensitive search. |
| `apps/settingshub/SettingsHubActivity.kt` | **New.** Hub UI (HUD core ring, search, legend, live controls, category panels, Nothing OS discovery, tile offers); resolves every row on-device before showing it. |
| `system/tiles/NexusTiles.kt` | **New.** Torch, Eco FX, Calculator, Settings Hub, Shell tiles; PendingIntent launch on Android 14+; `unlockAndRun` on the lock screen; work only while QS is open. |
| `ui/control/ControlCenter.kt` | Polling loop → resume/action-driven re-reads; animated tile energy/press; LIVE ◆ markers; Settings → Hub. |
| `call/CallManager.kt`, `ui/fx/Shaders.kt`, `AndroidManifest.xml` | Real lint errors fixed: notification-permission check, API-33 annotation, optional telephony feature. |
| `app/lint-baseline.xml`, `app/build.gradle.kts` | Lint baseline (pre-existing false positives); version 1.1.0-m2 (code 2). |
| `gradle/gradle-daemon-jvm.properties` | Gradle daemon pinned to JDK 21 — builds even when Studio/terminal default to JDK 25. |
| `res/drawable/ic_tile_*.xml`, `ic_hub_foreground.xml`, `mipmap-anydpi-v26/ic_settings_hub.xml`, `values/strings.xml` | Icons and labels. |
| `research/README.md`, `research/systemui-probe/nexus-systemui-probe.ps1` | **New.** Research module overview and read-only probe. |
| `tools/make-source-zip.ps1` | **New.** Reproducible source ZIP. |
| `docs/INSTALL.md` | **New.** Safe install guide. |
| `app/src/test/.../SettingsCatalogTest.kt` | **New.** 6 tests (unique ids, public fallback on every row, panel/LIVE invariants, category coverage, search). |

**Verified here:** clean `assembleDebug` ✔, `assembleRelease` (R8) ✔, `lintDebug` ✔ (no new errors), 63/63 unit tests ✔,
packaged manifest checked with `aapt2` (3 launcher icons, 5 QS tiles).
**Not verified:** anything on the phone.

### Not done in milestone 2 — and why
- **Matching the reference screenshots** (home, app-open animations, widgets, theme customisation): the
  screenshots are still not in the project or the conversation, so I could not compare against them and
  did not guess at redesigns. Put them in `docs/reference/` and they become the spec for milestone 3.
- **App-opening animation tuning** needs on-device frame measurements first (Nothing OS may override the
  window animation); see the Performance section of `docs/TESTING.md`.

---

## 11. Milestone 3 — what changed (2026-10-21)

| Feature | Category | What it really does |
|---|---|---|
| Control Deck styles (Glass/Hex/Ring/Minimal), columns, header | **Simulated panel** with real controls | Customises NEXUS's own deck. The real Quick Settings panel is unchanged (its tiles remain Tier 3/4 to restyle). |
| Shared NEXUS app chrome | **Functional** (NEXUS apps only) | Calculator and Settings Hub use one header + static HUD backdrop. Does not affect other apps or system screens. |
| Screen-saver charging intro | **Functional** (Tier 2, user-enabled screen saver) | Runs only inside Android's screen saver while charging. Does **not** replace the system charging animation, lock screen or AOD (Tier 4). |
| Visibility-gated refresh loops | Performance fix | `produceWhileVisible` / `WhileVisibleEffect` (`ui/components/VisibleWork.kt`) stop delay()-based loops when the host is stopped. Applied to `rememberNow`, HUD memory readout, system-stats, world clock, date and calendar widgets, ambient faces and burn-in shift. |

### Files
`core/Models.kt` (DeckStyle, deckStyle/deckColumns/deckHeader, dreamChargeIntroSec), `ui/control/ControlCenter.kt`
(styled `Tile`, columns, header), `ui/settings/StudioScreen.kt` (deck style + preview, columns, header,
screen-saver intro), `ui/components/AppChrome.kt` (**new**), `ui/components/VisibleWork.kt` (**new**),
`apps/calculator/CalculatorActivity.kt` and `apps/settingshub/SettingsHubActivity.kt` (shared chrome),
`lock/NexusDreamService.kt` (charging intro), `ui/home/Dashboard.kt`, `ui/fx/HudChrome.kt`,
`ui/widgets/NexusModules.kt`, `ui/widgets/NexusModules2.kt`, `ui/ambient/AmbientFace.kt` (gated loops),
`ConfigSerializationTest.kt` (+2 tests: upgrade from M2 config, deck round-trip), `docs/reference/README.md`,
version 1.2.0-m3 (code 3).

**Verified here:** clean debug + release build, lint (no new errors), 65/65 unit tests. **Not verified on the phone.**

### Not done in milestone 3 — and why
- **Orbit Home / Radial / Core / drawer / lock-screen visuals "to match the screenshots"**: the screenshots did
  not arrive (no image attachments; nothing in `docs/reference/`). Redesigning without them would be guessing.
- **System UI research**: no new on-device data — the read-only probe (`research/systemui-probe`) has not been
  run yet. Its report is what decides the next research step; nothing new can be learned from the PC side.
- **Lock screen**: the real keyguard remains Tier 4. The optional NEXUS lock layer (a simulation in front of
  the keyguard) is unchanged and stays off by default.
