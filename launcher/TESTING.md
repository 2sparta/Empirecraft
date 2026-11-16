# In-game menu 0.2.0, 2026-11-16

The Java 17 build includes the original castle hall, fitted character preview, skin/cape entry points and a required Quick Skin preview-fitting mixin. The current addon SHA-256 is recorded in `native/dist/empirecraft-launcher-client-0.2.0.jar.sha256`.

`PATH=/path/to/jdk17/bin:$PATH python -m pytest launcher/tests -q`: **52 passed**. The Java layout harness exercises eight GUI sizes and checks navigation/model/control separation plus editor head, right-edge and toolbar clearance. Packaging checks verify native resources, the client-only Quick Skin dependency and that no Quick Skin classes were bundled.

Real graphical results, captures and scope: [audit/menu](audit/menu/README.md). A disposable public launcher Potato pack is used with the newly built addon substituted; this does not boot the private repository's newest full gameplay pack. No connection to the live server is made. No FPS or server performance improvement is claimed.

The signed public channel requires the corresponding distribution PR to be merged before existing launchers fetch the new menu with Verify Pack. Existing launcher binaries' initial Quick Play is intercepted by the native connection guard; newly built launcher source opens the home screen directly.

---

# Release validation, 2026-11-15

* `python -m pytest launcher/tests -q`: **42 passed**, Python 3.13.5/Linux. Covers signed manifests, tampering, rollback/equivocation/expiry, Windows-safe paths, redirects, corrupt/partial cache objects, bundled objects, transactional updates, repair, user settings, profile differences and fixed server arguments.
* Actual complete profile installations from the verified bundled object set, network calls forbidden: Potato **112** JARs / 239 files, Balanced **114** / 241, High **115** / 242. Every object hash checked. Oculus appears only in High; the gameplay pack remains present. See `audit/offline-install.json`.
* Real Tk widget selection and geometry checked on Linux at 1120×760 and 980×720. The packaged Linux executable opens and its diagnostics command works. Short idle sample: **58 MB RSS**, **1% of one core** over 2 seconds. This describes only the launcher and is not a game benchmark. See `audit/desktop.json`.
* Windows x64 CPython 3.12.16, Tk, native cryptography and psutil ran through **Wine 11.22**. The packaged application switched all profiles, verified Ed25519 signatures and installed the full Potato profile (112 mods) without network access. The actual compiled `Empirecraft.exe` bootstrap also opened the GUI. See `audit/windows-wine.json` and screenshots.
* Windows full archive CRC and object count checked; private release/deploy keys and server paths absent. SHA-256 recorded in `audit/windows-package.json` and the adjacent checksum file. CPython runtime hash matches the official Astral release asset; Windows wheels match `windows-wheels.lock.json`.
* Native Minecraft client addon compiled with Java 17 against the pack's real Forge/Minecraft APIs and remapped to 1.20.1 SRG names. Required connection/weather mixin targets checked against the real mapping table. A graphical Minecraft boot with those mixins is **not yet tested**.
* Public GitHub Actions run [43709603130](https://github.com/2sparta/EmpireCraftLauncher/actions/runs/43709603130) succeeded: Windows and Linux tests, actual Tk GUI selection/geometry, native packaging/artifacts, and a fresh official Java17/Minecraft/Forge installation. This also exposed and verified the Windows Path-to-string fix in disk diagnostics.
* Real unauthenticated public-channel installation passed: High downloaded its complete signed pack from GitHub/Modrinth; Potato and Balanced reused the verified content-addressed cache. No private GitHub token or fallback bundle was used. See `audit/public-channel.json`.

Still needed before calling this production-ready: physical Windows test; first Minecraft launch and login with the current server pack; optional High shaderpack/Create compatibility; real laptop measurements of frame times, RAM pressure, disk waits and server TPS. No FPS gain or server-side performance improvement has been measured.

The client source and signed pack channel are published in `2sparta/EmpireCraftLauncher`; no GitHub token is needed by players. Private source/server files and signing/deploy keys are excluded. Additional network installation results for the public channel are recorded in `audit/public-channel.json` when available.

## 0.1.2: unavailable Windows performance counters

Regression coverage injects the exact `PdhAddEnglishCounterW` RuntimeError while monitoring a real child process. The failed swap metric is queried once and recorded as unavailable; CPU/RAM sampling continues. Additional real-child tests verify that unexpected monitoring failures preserve both successful and nonzero game exits, and that a failed report write cannot stop the child.
