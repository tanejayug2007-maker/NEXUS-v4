# NEXUS v1.5.1 — Performance & Gesture Hotfix

Reported on the Nothing Phone (3a) with Milestone 6: the app drawer needs a slow swipe to open, normal
quick flicks sometimes do nothing, and the drawer animation feels slightly laggy.

## Root causes (from the M6 code)

| # | Cause | Where (M6) | Effect |
|---|-------|-----------|--------|
| 1 | **Velocity tracker fed only each event's newest position.** Android batches touch samples that arrive between frames into *historical* points; M6 called `tracker.addPosition(change.uptimeMillis, change.position)` and dropped them. Compose's tracker treats a gap of more than 40 ms between samples as "finger stopped" and throws away everything before it. | `HomeScreen.kt`, swipe-up `onVerticalDrag` | When a frame stalled during a quick flick (see #2), release velocity read as **~0**, so the 1100 px/s fling rule never fired. |
| 2 | **The whole drawer was composed in the middle of the gesture.** `AppDrawer` only entered composition once `drawer.value > 0.001`, i.e. on the first move past touch slop. The same frame also called `vm.poke()`, which restarted the ambient loops (orbit spin, animated background, HUD) and recomposed the home. | `HomeScreen.kt`, `if (drawerVisible ‖ drawerOpen) AppDrawer(…)` and `onDragStart` | One long frame exactly when the swipe begins, which creates the gap in #1 and a visible hitch. |
| 3 | **Distance rule needed a long drag.** Without a valid velocity, the drawer only opened past 28 % of *0.55 × screen height* (≈ 370 px on the 3a). A natural flick covers 150–300 px. | `onDragEnd` | Slow, long swipes worked and quick ones snapped back: the exact symptom. |
| 4 | **The drawer did not follow the finger.** Drag was mapped over 0.55 × screen height but the panel only moves 0.32 × screen height, so it moved at 58 % of finger speed opening and 32 % closing (header drag mapped over the full height). | `HomeScreen.kt`, `AppDrawer.kt` header | Felt sluggish and "behind" the finger. |
| 5 | **Release velocity was thrown away.** On release the overlay state changed and a `LaunchedEffect` restarted a fixed spring (stiffness 420) from rest, cancelling whatever was running. | `LaunchedEffect(drawerOpen)` | A visible "catch then accelerate" after every flick; ~400 ms to settle. |
| 6 | **Per-event coroutine hops.** Every move did `scope.launch { drawer.snapTo(v) }`. The value lands on the next dispatch, not in the frame being produced. | home and drawer drags | Adds up to a frame of lag behind the finger. |
| 7 | **Ring drags on Orbit swallowed vertical swipes.** The three rings cover a large part of the Orbit page and any drag on them past slop spun the ring and consumed the touch. | `OrbitPage.kt` | Swipe-up on Orbit often did nothing. |
| 8 | **Full-screen re-blur every frame under the drawer.** The home layer is blurred (API 31+) while the drawer shows, but orbit spin, background and HUD kept invalidating it. | `HomeScreen.kt` | GPU cost on every frame of the drawer animation and while it is open. |
| 9 | **Bounds recomputed for every icon on every frame.** `trackBounds` used `onGloballyPositioned`, which fires whenever anything above an icon moves (orbit spin, parallax, page swipe, drawer slide), and computed `boundsInWindow()` each time. Only read on tap. | `ui/components/Gestures.kt` | Main-thread work proportional to icons × frames, launcher-wide. |
| 10 | **Taps leaked through a closing drawer; a swipe during close was ignored.** | `AppDrawer.kt` | Rapid open/close felt unresponsive. |

The tests that pin #1 and #3 down are in `SwipeVelocityRegressionTest`. They feed Compose's own
VelocityTracker the M6 sample pattern (it reports 0 px/s and the old rule rejects the flick) and the
complete sample set (it reports about −2700 px/s and the drawer opens).

## Fixes

* **`ui/drawer/DrawerMotion.kt` (new, pure Kotlin, unit-tested):** 1:1 finger mapping over the drawer's
  real travel (0.32 × screen height); release decision by velocity projection, the way the system launcher
  decides. A fling faster than 400 dp/s always goes its way; otherwise the position is projected 120 ms
  ahead and compared with half-way. Also holds the vertical-swipe test used by Orbit.
* **`ui/drawer/DrawerController.kt` (new):** single owner of drawer progress. A drag writes the value
  synchronously, with no coroutine hop. A release springs from the release position *and* velocity (damping 0.9,
  stiffness 700: about 200–260 ms, no bounce). Any new drag interrupts the spring and continues from the
  drawer's actual position. Overlay changes (Back, Home, launch, search shortcut) never restart a matching
  spring or fight a finger that is down.
* **Home swipe-up (`HomeScreen.kt`):** feeds the tracker every sample, including historical ones
  (`addPointerInputChange`). The gesture now runs *outside* the scaled and blurred home layer, so distances and
  velocity are true screen values. `vm.poke()` moved to the end of the gesture.
* **Drawer pre-composed:** 1.5 s after the home appears, the drawer is composed once and kept, measured but
  *not placed* while closed. A closed drawer draws nothing and receives no touches; the first swipe only
  places and draws it.
* **Ambient paused under the drawer:** orbit spin, animated background and HUD clock stop while the drawer
  is visible, so the blurred home is static and is not re-blurred every frame. They resume where they left off.
* **Drawer close gestures (`AppDrawer.kt`):** header drag and pull-down-at-top-of-list both use 1:1
  mapping and a screen-space velocity tracker. The list's own tracker sees the panel moving with the finger
  and under-reads speed. Fling momentum hitting the top of the list no longer yanks the drawer closed: only
  a finger does.
* **Closing drawer (`DrawerGestures.kt`):** takes all touches, so nothing underneath launches, and a vertical
  drag grabs it mid-close (reopen without waiting).
* **Orbit (`OrbitPage.kt`):** a clearly vertical stroke (|dy| > 1.7 |dx|) on a ring is left to the home swipe.
  Horizontal and diagonal drags still spin the rings.
* **`trackBounds`:** keeps the layout coordinates (`onPlaced`) and computes window bounds only when a tap or
  long-press reads them.
* **Feel:** rows rise into place in a short cascade tied to drawer progress (draw-phase only, off in Battery
  Saver or with system animations off). Icons dip to 92 % under the finger and spring back on release or
  when the touch becomes a scroll. A haptic tick plays when a swipe opens the drawer. Lazy-grid items have
  stable keys and a content type.

## Measuring on the phone

Release-mode timing has to come from the device. The cloud build machine has no phone and no emulator. Use
`tools/phone-test/nexus-perf.ps1`. It is read-only and only reads `dumpsys gfxinfo`, `dumpsys meminfo` and
logcat.

1. Install the **M6** APK, set NEXUS up as you normally use it (same theme and page), then run
   `powershell -ExecutionPolicy Bypass -File tools\phone-test\nexus-perf.ps1 -Label m6` and follow the prompts:
   20 quick flick-open / flick-close pairs, 5 slow drags, a few fast list scrolls.
2. Install **v1.5.1** (same settings). Turn on **Studio › Performance › Frame stats**, which also enables the
   per-gesture `NexusPerf` log, and run the script again with `-Label v151`.
3. Compare the two reports in `tools/phone-test/reports/`: janky-frame %, 90th/95th/99th percentile frame
   time, missed vsync and slow UI thread from `gfxinfo`, plus PSS memory. v1.5.1 also logs touch-to-frame
   latency, open/close settle time and *fast flicks opened / fast flicks attempted*.

The Frame stats overlay (top-right) shows live FPS, janky frames per second and worst frame. Turn it
off again afterwards, because it keeps the frame clock running.

## Not changed

Visual design, Orbit, hexagonal icons, Radial Launcher, Core Dashboard, Control Deck, notification panel,
themes, Studio, Calculator, Settings Hub and charging animations are untouched apart from the gesture fixes
above. No new permissions, no root, no Accessibility, no system modification.

## Verification status (cloud build, 2026-10-21)

The cloud machine that made this hotfix could not reach Google's Maven repository (`dl.google.com`), so the
**Android Gradle Plugin, the full app build, lint, the release APK and emulator tests could not run there**.
What was verified:

* `DrawerMotion.kt`, `DrawerController.kt`, `DrawerGestures.kt`, the three new test classes, and a probe that
  uses every Compose API the edited `AppDrawer.kt` / `HomeScreen.kt` code calls **compile** against
  JetBrains Compose Desktop **1.7.3** (the same Compose 1.7 code as the app's BOM 2024.12.01). This caught one real
  error before it reached you: `VelocityTracker1D()` has no public no-argument constructor.
* The **17 new tests pass** (DrawerMotionTest 8, DrawerControllerTest 7, SwipeVelocityRegressionTest 2) on
  Compose Desktop 1.5.12 runtime. 1.7.3 could not be *run* because it needs `androidx.collection` from Google Maven.
* **Not yet run:** `assembleDebug`, `assembleRelease`, `lintDebug`, the existing 82 tests on the Android
  toolchain, or anything on a device. Run them in Android Studio (see INSTALL.md) before installing.
