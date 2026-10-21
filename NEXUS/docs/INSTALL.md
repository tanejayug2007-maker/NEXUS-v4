# Installing NEXUS on the Nothing Phone (3a) — safe test install

This installs NEXUS as an **ordinary app**. It does not unlock the bootloader, root, flash anything,
change system partitions or erase data. Uninstalling it removes everything it added.

## 0. What stays off during initial testing
NEXUS never turns these on by itself — each needs a button in NEXUS Studio **and** your confirmation in
an Android dialog. During initial testing **don't press them**:

| Optional feature | Where it would be enabled | Leave it |
|---|---|---|
| NEXUS gesture helper (Accessibility) | Studio › System | Off |
| Notification access (feed, badges, media) | Studio › System | Off |
| Lock layer / "Display over other apps" | Studio › Lock | Off |
| Default phone app (call screen) | Studio › Calls | Off — Nothing's Phone stays default |
| Modify system settings, DND access, location | Studio › System | Off |

Everything else (home, drawer, orbit, radial, calculator, Settings Hub, Quick Settings tiles) works
without them. Features that need one show a "grant" button instead of failing.

## 1. Prepare the phone (one time)
1. **Settings › About phone** → tap **Build number** 7 times → enter your PIN. "Developer options" is now on.
2. **Settings › System › Developer options** → turn on **USB debugging**.
   (This does not unlock anything and can be turned off afterwards.)

## 1b. Easiest safe route: the test script (USB)
From the `NEXUS` folder on the PC, with the phone connected and USB debugging allowed:
```
powershell -ExecutionPolicy Bypass -File tools\phone-test\nexus-phone-test.ps1
powershell -ExecutionPolicy Bypass -File tools\phone-test\nexus-phone-test.ps1 -Install -Apk ..\dist\milestone5\NEXUS-m5-debug.apk
```
The first command only **reads** (default Home app, Nothing packages, camera app, Glyph packages, storage).
`-Install` installs NEXUS as an ordinary app (no permissions granted, launcher not changed), then re-checks and prints
**PASS** only if your Home/Phone/Camera apps and every Nothing/Glyph package are unchanged.
`-Uninstall` removes NEXUS. Reports go to `tools\phone-test\reports\`.

## 2. Install the APK — choose one

**A. With a USB cable (recommended)**
1. Connect the phone, unlock it, accept **"Allow USB debugging?"** for this computer.
2. In PowerShell on the PC:
   ```
   & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices
   & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r "C:\path\to\NEXUS-debug.apk"
   ```
   `devices` must list one phone as `device`. `install` must print `Success`.

**B. Without a cable**
Copy the APK to the phone (USB file transfer, Drive, …), open it in Files, allow "Install unknown apps"
for Files when asked, then **Install**. (Turn that allowance off again afterwards in Settings › Apps ›
Special app access › Install unknown apps.)

Or from Android Studio: open the `NEXUS` folder, pick the phone in the device list, press ▶ Run.

## 3. First test — without changing your launcher
New icons appear in Nothing Launcher's app list: **NEXUS Studio**, **NEXUS Calculator**
and (milestone 2 builds) **NEXUS Settings Hub**. Try them first; your home screen is unchanged.

Quick Settings tiles (milestone 2 builds): pull down Quick Settings › pencil/edit › drag the **NEXUS**
tiles in.

## 4. Try NEXUS as the home screen (reversible)
NEXUS Studio › **Set default** → choose NEXUS. Press Home.
**To go back:** Settings › Apps › Default apps › Home app › **Nothing Launcher**.
(If NEXUS ever misbehaves as home, that Settings path always works; Settings is reachable from the
Quick Settings gear.)

## 5. Report problems
Note what you did and what happened. A log helps:
```
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" logcat -d -t 2000 > nexus-log.txt
```
(The log can contain notification titles/app names from your phone — skim it before sharing.)

## 6. Uninstall completely
Restore Nothing Launcher as Home (step 4), remove NEXUS tiles from Quick Settings, then
Settings › Apps › NEXUS › Uninstall, or `adb uninstall com.nexus.launcher`.
Optionally turn off USB debugging / Developer options.

## Debug vs release APK
The **debug** APK is for testing and is noticeably slower (Compose debug builds are 2–5× slower).
Judge smoothness only on the **release** APK (`NEXUS-release.apk`). Both are signed with the same
development key, so one can be installed over the other.
