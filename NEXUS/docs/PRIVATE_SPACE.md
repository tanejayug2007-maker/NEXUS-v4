# NEXUS Private Space (milestone 7)

**This is a launcher-level private space, not Android's system Private Space.** It hides apps inside
NEXUS and puts a lock in front of them. It does not create a separate Android user profile, does not
encrypt app data, and does not stop the apps from running.

## What it does

* Apps you move into it disappear from every NEXUS surface: app drawer, drawer search, Orbit rings,
  the radial launcher, home pages, folders, the dock, the Favourites widget, the shell and the Glyph pad.
  Their home-screen spot is kept (not deleted) and comes back if you move the app out again.
* The space opens only after:
  * a **NEXUS PIN** (4–12 digits), stored as a salted PBKDF2-SHA256 hash, never the PIN itself.
    Wrong PINs back off: after 5 attempts 30 s, doubling up to 15 min (per launcher process), **or**
  * the **phone lock**: Android's own BiometricPrompt (fingerprint, face, or the phone's PIN/pattern/password).
    NEXUS only learns "succeeded". Needs the normal `USE_BIOMETRIC` permission (granted at install, no prompt).
* Nothing about the space (not even how many apps) shows before unlocking. Leaving NEXUS (launching an
  app, Home, Recents, screen off) locks it again. Studio's Private Space settings are behind the same lock.
* Entry: the **◈ PRIVATE** chip in the drawer header, or (if you turn the chip off) type **"private"** in
  drawer search. Long-press any app › **Move to Private Space** (offered only once a lock is set up).
* Notifications of private apps are kept out of the NEXUS Signals panel and popups (switchable).
* **Backups never contain it:** an exported backup has no private app list and no PIN hash; importing a
  backup keeps this phone's current Private Space. Studio › Reset all keeps it too.

## What it does not do (Android / Nothing OS limits, no root)

* The apps stay normally installed. **Android Settings › Apps, the Play Store, Recents, the share sheet
  and any other launcher still list them.** A launcher cannot hide an app from the system.
* Their notifications still appear in **Android's notification shade and on the lock screen**. NEXUS
  only controls its own panel.
* App data is not separately encrypted and the apps keep running in the background as usual.
* For a real isolated, encrypted profile use **Android's own Private space** (Android 15+:
  Settings › Security & privacy › Private space, if Nothing OS offers it on your build). A third-party
  launcher can show that profile only with a permission reserved for the default launcher on newer
  Android versions. NEXUS does not use it in this milestone, so system Private space apps are managed
  from Settings, not from NEXUS.
* Wrong-PIN back-off resets if Android kills the launcher process.
