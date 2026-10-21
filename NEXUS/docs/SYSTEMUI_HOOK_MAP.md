# Layer B — making the REAL notification shade & Quick Settings look like the NEXUS Control Deck

**Status:** research + an isolated renderer proof of concept. **Nothing here modifies your phone.** Layer A (the NEXUS
Control Deck and notification panel inside the launcher) is a NEXUS screen — it does **not** replace Android's shade.

## What the proof of concept is
Module `:sysui-style` (`NEXUS/sysui-style/`) contains the Control Deck artwork as **plain Android Drawables**:
- `NexusTileDrawable` — deck tile (cut bottom-end corner, gradient body, cyan "energy" fill and outline, one-shot light
  sweep when a tile turns on). `setQsState(0|1|2)` takes the same values as `QSTile.State.state`
  (`0` unavailable, `1` inactive, `2` active).
- `NexusCardDrawable` — notification card (angular panel, cyan→magenta outline, cyan edge bar, `arrive()` pulse).
- `DeckGeometry` — shapes, state mapping, colour maths (unit-tested: `DeckGeometryTest`).

It uses no Compose and no launcher code, because **Nothing OS 4.1's SystemUI tiles are classic Views** (your JADX
inspection found `QSTileViewImpl`, `handleStateChanged(QSTile.State)`, `getBackgroundColorForState()`, `colorActive`,
`colorInactive`, `colorUnavailable`, `QSPanel`, `QuickQSPanel`, `QuickStatusBarHeader` and a Nothing footer). A
View-based tile can carry these drawables as its background. **Studio › System › SystemUI renderer** renders them in
real `View`s inside NEXUS (verified on the emulator: tiles switch state with the animation; card pulse plays).

## Deck element → system component → what it takes

| Reference deck element | Real system component (Nothing OS 4.1, from JADX) | Change needed | Requirement | Locked bootloader? |
|---|---|---|---|---|
| Angular tiles, cyan active glow | `QSTileViewImpl` background; colours from `getBackgroundColorForState()` | Replace the tile background with `NexusTileDrawable`; call `setQsState` from `handleStateChanged` | Code hook into SystemUI (LSPosed module scoped to `com.android.systemui`) **or** a rebuilt SystemUI signed with Nothing's key | ❌ (root) / ❌ (Tier 4) |
| Tile colours only (no shape) | `colorActive` / `colorInactive` / `colorUnavailable` resources | Resource overlay | Your AAPT2 check: not overlayable → overlay must be placed in a system partition (root, e.g. Magisk systemless overlay) | ❌ (root) |
| Accent colour across QS/shade/Settings | Material You palette (`ThemeOverlayController`) | `theme_customization_overlay_packages` | one-time `adb pm grant WRITE_SECURE_SETTINGS` | ✅ — **not wanted by you** (colour filter on stock UI); kept as an option, off |
| `// CONTROL DECK` header, big clock, battery | `QuickStatusBarHeader` (+ Nothing's header/footer views) | Replace header layout/text styling | Code hook | ❌ (root) |
| LUMEN gauge (circular brightness) | Brightness slider (`BrightnessSliderView` / `ToggleSeekBar` family in View-based SystemUI) | Replace the slider view with a radial control bound to the same `BrightnessController` | Code hook (larger: new view + touch handling) | ❌ (root) |
| AUDIO gauge (media volume) | Not in stock QS; volume lives in `VolumeDialogImpl` | Add a view to QS or restyle the volume dialog | Code hook / SystemUI plugin (system-signed) | ❌ |
| POWER CELL panel | No stock equivalent in QS | Add a custom view to `QSPanel` | Code hook | ❌ |
| Notification cards (angular, cyan edge, magenta corner) | Notification row background (`NotificationBackgroundView` / `ActivatableNotificationView` in AOSP; Nothing's equivalent to be confirmed in JADX) | Draw `NexusCardDrawable` instead of the stock background; `arrive()` from the add-animation | Code hook | ❌ (root) |
| Shade background pure black + scan line | `ScrimController` / `NotificationShadeWindowView` | Override scrim colour/alpha; one-shot overlay on expand | Code hook | ❌ (root) |

**Everything that changes shape, layout or animation of the real shade requires root (code hooks) or Nothing's
platform signing key.** There is no locked-bootloader route for those, which is why Layer A exists.

## Risks of the root route (for your decision later — not proposed now)
- **Unlocking the bootloader erases all data** (photos, WhatsApp, everything not backed up).
- **Banking / UPI / Google Wallet:** Play Integrity "device"/"strong" fail on an unlocked, rooted device. Not spoofed by NEXUS.
- **Widevine** drops to L3 (lower-quality streaming).
- **OTA updates:** each Nothing OS update can rename/obfuscate SystemUI classes → hooks break, SystemUI may crash-loop.
  A safe-mode/disable path must be rehearsed before any hook is enabled.
- **Nothing features** (Camera, Glyph, Essential Space/Key): files untouched by a scoped SystemUI hook, but behaviour under
  root is **unverified** — needs a full regression on a test device.
- **Security:** root is a permanent high-privilege surface; privacy indicators and keyguard must be excluded from any hook.

## Next Layer B steps (safe, no phone changes)
1. Re-inspect the exact Nothing OS 4.1 `SystemUI.apk` from your build in JADX: confirm `QSTileViewImpl` method
   signatures, the notification row background class, and whether names are obfuscated (affects hook stability).
2. Write the hook as a separate, **not installed** LSPosed module skeleton that only calls `NexusTileDrawable` /
   `NexusCardDrawable`, with a kill switch and a SystemUI-crash watchdog — reviewed with you before anything runs.
3. Only with your separate written approval and a test device: backup, exact-firmware match, recovery path, then try it.
