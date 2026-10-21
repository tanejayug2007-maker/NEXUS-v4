# NEXUS research module (Module 5) — real system UI, non-invasive

Nothing in this folder is built into the NEXUS APK. These are separate ADB scripts you run on purpose,
one at a time. None needs root or an unlocked bootloader; none modifies system files.

| Script | Changes the phone? | Purpose |
|---|---|---|
| `systemui-probe/nexus-systemui-probe.ps1` | **No** — read-only | Inventory of your exact firmware's theming surface → `reports/<build>.txt` |
| `system-palette/nexus-system-palette.ps1` | Yes, one Secure setting, with backup + `-Restore` | Try the NEXUS cyan as the Material You seed colour for the real QS/shade/Settings |

Run the **probe first**; its report decides which experiments are worth doing.

## Surface-by-surface status (Nothing Phone (3a), Android 16, locked bootloader)

| Surface | Non-root route | What it can change | What it can't | Status |
|---|---|---|---|---|
| Quick Settings | `TileService` (in the APK) | Adds real NEXUS tiles; icon, label, subtitle, on/off state | Tile shape, panel layout, animations | **Implemented** (milestone 2) |
| QS / shade / Settings colours | Material You seed (`system-palette`) | Accent and neutral palettes Android derives from the seed | Shapes, layouts, fonts, motion | Script ready, **unverified** on Nothing OS |
| Notification cards | — | — | Card style, grouping, heads-up look | Tier 3/4. NEXUS feed inside the launcher is a **simulation** |
| Status bar | — | — | Icons, clock, battery style | Tier 3/4. Privacy indicators must never be altered |
| Lock screen | Wallpaper; Nothing's own clock choices; optional NEXUS lock layer (**simulation** in front of the keyguard) | Background image; layer visuals | The keyguard itself, its clock plugin, the bouncer | Keyguard: Tier 4 |
| Charging (lock screen / AOD) | Screen saver (`DreamService`) while charging | Full NEXUS visuals while docked/charging with screen on | System charging ripple, AOD | AOD: Tier 4 |
| Overlays (RRO) | — | — | Your AAPT2 check found no overlayable SystemUI resources, and third-party overlays need platform signing or system placement | Tier 3/4 |

## Rules for anything beyond this table (Phase 6)
Root-based work (LSPosed hooks on `QSTileViewImpl`, `QSPanel`, `QuickStatusBarHeader`) needs an
unlocked bootloader, which **erases the phone** and will very likely break Play Integrity (banking,
UPI). It will not be started without: a full backup, an exact firmware match, a tested recovery path,
and your explicit written go-ahead.
