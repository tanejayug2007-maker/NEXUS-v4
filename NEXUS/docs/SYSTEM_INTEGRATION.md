# Track B — real system-wide integration on Nothing OS 4.1 (Phone (3a), locked bootloader)

Builds on the earlier research in `Downloads\NEXUS-OS-stock-feasibility\NEXUS-OS\docs\`
(`06-SYSTEMUI.md`: SystemUI patch design against Android 17 source, never compiled; `09-STOCK-BASED-FEASIBILITY.md`:
approaches 1–3, protections quoted from Android source). This page adds new evidence from milestone 5 and the
per-feature answer you asked for. **Nothing here was run on your phone.**

> **Milestone 6 decision:** you don't want stock Android Quick Settings with a cyan colour filter. The System colours
> option stays available but off; the target is the NEXUS Control Deck look, mapped to SystemUI in
> [`SYSTEMUI_HOOK_MAP.md`](SYSTEMUI_HOOK_MAP.md).

## Correction to `09-STOCK-BASED-FEASIBILITY.md`
`09` concluded *"No adb-only or Shizuku-only system theming. Root is needed."* That is true for **creating** overlays
(`"Non-root shell cannot fabricate overlays"`), but there is a second route it missed: the Secure setting
`theme_customization_overlay_packages`. SystemUI's own `ThemeOverlayController` reads it and builds the Material You
overlays **itself** (as the system). Milestone 5 verified on a stock Android emulator (API 37):
the in-app **Studio › System colours › Apply** wrote the setting, the **real, unmodified Quick Settings** turned NEXUS
cyan, and **Restore** returned it exactly to `null`. Writing a Secure setting needs `WRITE_SECURE_SETTINGS`, granted once
over USB (`pm grant`, no root). Whether Nothing OS honours the key is **not yet verified**.

## Tiers
1 = ordinary app · 2 = user-granted permission/role, or a one-time adb grant · 3 = root / unlocked bootloader ·
4 = Nothing's platform signing key or source.

## Per-feature answer
"Locked?" = achievable with the bootloader locked. Risk columns describe the risk **of the achievable route**.

| System feature | What NEXUS can genuinely do (locked) | Technical requirement | Locked? | Security | Banking / UPI | OTA | Nothing features | Status |
|---|---|---|---|---|---|---|---|---|
| **Quick Settings tiles** | Add real NEXUS tiles (Torch, Eco FX, Calculator, Hub, Shell) | `TileService` | ✅ Tier 1 | none | none | none | none | **Shipped (M2)** |
| **QS / shade / Settings colours** | Recolour the real QS, shade, Settings, Material You apps | `theme_customization_overlay_packages` + one-time `pm grant WRITE_SECURE_SETTINGS` | ✅ Tier 2 | low: permission scoped to Secure settings; revocable | none (no system change) | none; an OTA may reset colours | Nothing may override when wallpaper/colour changes | **Prototype (M5)**, emulator ✓, phone ❓ |
| **QS tile shapes, panel layout, animations** | — | Hook `Tile()` in `qs/panels/ui/compose/infinitegrid/Tile.kt` (see `06`) | ❌ Tier 3/4 | root = permanent attack surface | Play Integrity device/strong **fail** | each OTA can bootloop SystemUI | camera/Essential untouched on disk, interaction **unknown** | Design written (`06`), not compiled |
| **Notification cards / heads-up look** | NEXUS notification feed inside the launcher (simulation) | Notification access; real cards need `NotificationBackgroundView` hook | Feed ✅ Tier 2 · real ❌ Tier 3/4 | feed: reads notifications (user-granted) | none | none | none | Feed shipped (V4); real = design only |
| **Status bar** | Home-screen NEXUS strip (simulation); hide real bar on home only | `PhoneStatusBarView` / `HomeStatusBarViewBinder` hooks | ❌ Tier 3/4 | must never hide privacy indicators | fail | breaks per OTA | unknown | Not attempted |
| **Lock screen** | NEXUS lock layer *in front of* the keyguard (simulation; Android still unlocks) | `showWhenLocked` activity + overlay permission; real = `ClockRegistry` clock provider (system-signed) | Layer ✅ Tier 2 · real ❌ Tier 4 | layer verified not to bypass PIN (M4 emulator test) | none | none | none | Layer shipped (V4), off by default |
| **Fingerprint visuals** | Decorative animation on the NEXUS lock layer only | real: `DeviceEntryIconView`, `UdfpsControllerOverlay` | Layer ✅ · real ❌ Tier 3/4 | visual only — authentication stays in the HAL | — | — | — | Layer shipped |
| **Volume panel** | Nothing honest without privileges. Accessibility-based "volume panel" apps intercept keys and draw over everything | Accessibility service + overlay (imitation) or SystemUI `VolumeDialog` plugin (system-signed) | Imitation ⚠ Tier 2 · real ❌ Tier 4 | imitation needs Accessibility (high privilege) | none | none | none | **Not built** — imitation not recommended |
| **Power menu** | — | `GlobalActionsDialogLite` in SystemUI | ❌ Tier 4 | security-sensitive (emergency, lockdown) | — | — | — | Not attempted |
| **Charging UI** | NEXUS charging animation inside NEXUS; screen-saver intro on the charger | `ACTION_POWER_CONNECTED`, `DreamService`; real ripple = `WiredChargingRippleController` | ✅ Tier 1–2 · real ❌ Tier 3/4 | none | none | none | none | Shipped (V4, M3) |
| **Settings app** | NEXUS Settings Hub: real Settings pages behind a NEXUS index; colours via the palette route | Settings intents; real restyle = platform-signed overlay | Hub ✅ Tier 1 · restyle ❌ Tier 4 | none | none | none | none | Hub shipped (M2) |
| **Camera** | Keep Nothing Camera; NEXUS launch transitions; probe what a NEXUS front end could use | `CameraManager` + Camera2 Extensions; Nothing's processing is inside its own app/service | Probe ✅ Tier 1 | none (no camera opened, no permission) | none | none | Nothing Camera never touched | **Probe shipped (M5)**; front end only if the probe shows vendor extensions |

### Camera — what the probe decides
A futuristic NEXUS camera must not replace Nothing's processing with an inferior generic pipeline. The only way a
third-party camera can use the vendor's Night/HDR/Bokeh processing is **Camera2 vendor extensions**. Run
**Studio › Camera capabilities › Run probe** on the phone:
- **Extensions listed** → a NEXUS camera front end could request that processing; worth prototyping.
- **None listed** → any third-party camera gets plain frames; the honest design is to keep the Nothing Camera as the
  camera and make NEXUS's part the launch experience (e.g. hyperspace transition into Nothing Camera), not a replacement.

## Rules kept
No root, bootloader unlock, flashing or system-component replacement. Tier 3/4 work stays design-only until you
approve it separately in writing, with backup, exact firmware match and a tested recovery path (see `09` §5 and
`04-SAFETY-AND-RECOVERY.md`).
