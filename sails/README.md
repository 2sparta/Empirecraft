# Valkyrien Sails — Empirecraft test build

Minecraft 1.20.1, Mohist 71a84ff7, Forge 47.4.23, Java 21. Client and server use the same two JARs:

- `vs_sails-1.20.1-0.3.2-empirecraft.1-forge.jar`
- `valkyrienskies-120-2.4.11-empirecraft.1.jar`

Architectury 9.2.14 and Kotlin for Forge 4.12.0 are already in both packs. The resulting pack has **92 client JARs and 95 server JARs**.

## Implemented and fixed

- The capstan and anchor now provide a persistent, tension-only seabed tether. Place an anchor directly below the capstan, within 32 blocks. Empty-hand right-click lowers it when a loaded seabed is within a 64-block chain. Shift + right-click raises it. Destroying the controlling capstan or anchor, or changing dimensions, releases the tether. No `setStatic` flag is used.
- Anchor and capstan recipes and loot tables are present; anchor feedback has Ukrainian and English translations. This is a numerical tether; an animated world-space chain and four-player capstan animation are not implemented.
- Ship block counts are rebuilt from actual loaded blocks in bounded batches of 8192 positions. Assembly/reload, replacement with a solid block, and connected sail dousing no longer rely on stale increment/decrement totals. Only complete recounts are published; scans do not load missing chunks.
- A rudder uses signed longitudinal speed. Reverse motion reverses its steering force, and a centred rudder produces no artificial turning couple. The stern position follows the ship's longitudinal axis.
- Empty helm inventories remain empty after NBT reload. Wheel stacks are limited to one; zero-size extraction, automation extraction, clearing, end stops and wheel persistence are handled. All mount slots are cleaned up, and failed mounting discards its unused seat.
- Wind queries run on the server thread in the ship's current dimension. Force queues copy finite vectors and are excluded from saves. Wind angles wrap correctly across zero, keel strength applies on both hull axes, and removing the last buoy restores the prior buoyancy factor.
- Configuration is read before validation. Malformed lines, non-finite or out-of-range numbers fail with a useful error. Non-divisor wheel steps are clamped at the end stops.
- Dedication bottles are consumed after successful assembly and retain the last bottle's custom name. Assembly first checks spawn permissions, Forge break-event cancellation and the existing Empirecraft Towny permission bridge for every block. An unavailable Towny bridge on Mohist denies relocation.
- Valkyrien Skies' three private static interface injection handlers are made public for Mixin 0.8.5. The change is confined to `MixinVibrationSystemTicker`; its operations remain enabled.

## Source-only optimization review

The follow-up [review and roadmap](../SAILS-REVIEW.md) contains section-based recounts, loaded-neighbour checks, idle redstone-helm cleanup and atomic wind sampling. These changes are in source only; the checked-in `empirecraft.1` JARs and ADM hashes still describe the previous release. Rebuild, package and repeat Mohist/client acceptance checks before distributing an updated binary.

The new traversal regression can also run without a server installation:

```sh
python sails/test.py --cursor-only
```

## Installation

1. Stop the game server and back up its worlds, configurations, ADM ID file and mod folder.
2. Add the two named JARs from `server/mods/`, and use the matching files from `mods/` on every client. Remove any other versions of those two mods.
3. Copy `server/config/vs_sails/config.cfg` and the updated ADM ID file. The ADM list is regenerated from the actual client JARs and preserves the four existing resource packs. No anticheat checks or ignored-mod settings are changed.
4. Start on a copy of the production world. Check player entry, ownership permissions, steering in both directions, anchor release, and save/reload before opening it to players.

The included configuration turns off aerodynamic wind on unrelated ships and magic-ballast righting, uses normal ballast/buoys, and limits a bottle assembly to 10000 blocks. The existing Mohist/Towny setup is retained.

## Verification

The recorded test used the full 95-mod server pack and one temporary test fixture mod. Mohist reached `Done`, assembled a ship, passed 19 runtime assertions and shut down with exit code 0 while saving all dimensions. Tests cover recounts, solid-block replacement, connected dousing, wheel extraction/NBT, clamped angles, fail-closed assembly permissions without Towny, and reading the two exact JAR hashes plus four resource packs through the real ADM loader.

An additional 2912 pure-Java assertions cover steering direction, zero-speed/zero-angle behavior, periodic wind angles, finite inputs, bounded anchor tension and configuration bounds. Rebuilding produced the same SHA-256 for both JARs. Details are in `validation/sails-empirecraft.1.json`.

The production world, Bukkit plugin stack, graphical client login, a player operating the anchor, and positive Towny ownership permissions have **not** been exercised. The runtime reports several pre-existing antixray/musket mixin metadata warnings and a missing Artifacts data-map item; authentication-key lookup also failed in the isolated environment. These did not prevent startup or orderly shutdown. The old JourneyMap bridge already differs between client 1.1.0 and server 1.1.1; this update preserves that arrangement.

## Still unfinished

This is a tested patch and a first anchor implementation, not completion of every upstream roadmap feature. Physical sail-angle bearings, dynamic hull watertightness/bilge pumps, simulated water currents, rotated-ship rigging climbing, and full Eureka interoperability remain unimplemented or unverified. Large sail groups still use the upstream neighbour-propagation mechanism. A graphical gameplay pass is required for the new anchor controls.

## Source and rebuild

The unchanged base is Valkyrien Sails 0.3.2, with upstream source pinned to `4e0bcd291f2d16651ab6d485b492fc3c6ad66adc`. Modified and added Forge sources/resources are in `src/main/`. Upstream authors retain their rights; the LGPL-3.0 license is included. Exact original binaries, mappings and build tools are pinned by URL and SHA-256 in `build-inputs.json`.

After installing the Mohist runtime libraries and a JDK, run from the repository root:

```sh
python -m pip install -r sails/requirements.txt
python sails/build.py --server-home server
python sails/test.py
python sails/update_adm.py
python sails/update_adm.py --check
```

Zulu JDK 21.0.13 was used for the recorded byte-identical builds; emitted mod bytecode targets Java 17. `build.py` never starts a game server. Its output is copied to both mod folders. `test.py --prepare-fixture` also creates the temporary runtime test mod. Use that fixture only on an isolated copy: it creates a test ship, changes blocks near spawn and stops the server. It must never be installed on the production server.

The branch workflow packages the large VS JAR from the pinned original and checks it against the tested digest before committing its two copies. It does not deploy to a running host.

