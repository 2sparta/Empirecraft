# Valkyrien Sails — Empirecraft test build

Minecraft 1.20.1, Mohist 71a84ff7, Forge 47.4.23, Java 21. Client and server use the same two JARs:

- `vs_sails-1.20.1-0.3.2-empirecraft.3-forge.jar`
- `valkyrienskies-120-2.4.11-empirecraft.1.jar`

Architectury 9.2.14 and Kotlin for Forge 4.12.0 are already in both packs. The VS compatibility JAR stays at `empirecraft.1`.

## Current release — arcade controls and visible anchor

`empirecraft.3` uses the ordinary helm's facing for the bow, independent of hull bounds. A/D steer directly; releasing the keys recentres the wheel and damps yaw. Strong lateral grip keeps the ship travelling along that heading. Wind still controls forward speed: a direct headwind permits a two-percent crawl instead of reversing the boat, beam wind gives reduced forward speed, and calm/doused sails have no drive.

The anchor visibly lowers with a chain, brakes translation/rotation, then applies a persistent full hold once slow. Sneak + empty-hand right-click raises it and releases its owned hold. Breaking the anchor/capstan cleans up, prior externally static ships remain static, and the temporary render entity is reconstructed from attachment state after restart.

See [the current Ukrainian guide](ARCADE.md), [the new install bundle](dist/Empirecraft-Sails-Arcade-Fix.zip), and [validation](../validation/sails-empirecraft.3.json). Install the same Sails 3 on every client and the server, with its matching ADM file. Raise anchors before downgrading to an older release.

## Previous release — wind direction fix (empirecraft.2)

The following wind-only behavior and its bundle are historical. The new arcade controller supersedes the old signed axial force and exact-beam zero-thrust rule.

The attached/repository `empirecraft.1` binary always applied sail thrust towards one end of the hull. Wind angle only changed its magnitude, leaving thrust against the flags/particles under an opposing wind. The new binary projects the signed wind vector onto the actual horizontal hull heading, uses its sign for thrust, and applies the resulting force in world coordinates. Negative wind strength reverses force, either stored end of the hull gives the same result, and pitch/heel cannot introduce vertical sail thrust.

This remains a simplified sail model: exact beam wind gives zero longitudinal thrust and changing wind can push the ship astern. It does not simulate sail trim, aerodynamic lift/tacking or apparent wind. The existing sail-type efficiency curves and square-law strength scaling are retained, with symmetric treatment of the hull ends and a smooth fade through beam wind. Wind-disabled propulsion retains its prior behavior.

See [the Ukrainian install guide](WIND-FIX.md), [the installation bundle](dist/Empirecraft-Sails-Wind-Fix.zip), and [the new validation record](../validation/sails-empirecraft.2.json). The earlier full-pack Mohist results below describe `empirecraft.1`; the new binary has separate isolated Forge/VS tests and has not been deployed to the live host.

## Implemented and fixed

- The capstan lowers a visible anchor and chain, applies strong braking and then holds the entire ship still. Sneak + empty-hand right-click raises it and releases its owned hold. Save/reload and broken capstan/anchor cleanup are handled; existing externally static ships retain their prior state.
- Anchor and capstan recipes and loot tables are present; anchor feedback has Ukrainian and English translations. This is a numerical tether; an animated world-space chain and four-player capstan animation are not implemented.
- Ship block counts are rebuilt from actual loaded blocks in bounded batches of 8192 positions. Assembly/reload, replacement with a solid block, and connected sail dousing no longer rely on stale increment/decrement totals. Only complete recounts are published; scans do not load missing chunks.
- The ordinary helm defines the bow through its placement facing, not ship bounds. A/D set a controlled yaw rate, neutral input damps rotation and recentres the wheel, lateral grip removes most sideways drift, and headwind permits a two-percent forward crawl.
- Empty helm inventories remain empty after NBT reload. Wheel stacks are limited to one; zero-size extraction, automation extraction, clearing, end stops and wheel persistence are handled. All mount slots are cleaned up, and failed mounting discards its unused seat.
- Wind queries run on the server thread in the ship's current dimension. Force queues copy finite vectors and are excluded from saves. Wind angles wrap correctly across zero, keel strength applies on both hull axes, and removing the last buoy restores the prior buoyancy factor.
- Configuration is read before validation. Malformed lines, non-finite or out-of-range numbers fail with a useful error. Non-divisor wheel steps are clamped at the end stops.
- Dedication bottles are consumed after successful assembly and retain the last bottle's custom name. Assembly first checks spawn permissions, Forge break-event cancellation and the existing Empirecraft Towny permission bridge for every block. An unavailable Towny bridge on Mohist denies relocation.
- Valkyrien Skies' three private static interface injection handlers are made public for Mixin 0.8.5. The change is confined to `MixinVibrationSystemTicker`; its operations remain enabled.

## Included optimization review

The follow-up [review and roadmap](../SAILS-REVIEW.md) contains section-based recounts, loaded-neighbour checks, idle redstone-helm cleanup and atomic wind sampling. These changes remain included in the current binary and its ADM hash. The old review remains a historical record of the source-only stage.

The new traversal regression can also run without a server installation:

```sh
python sails/test.py --cursor-only
```

## Installation

1. Stop the game server and back up its worlds, configurations, ADM ID file and mod folder.
2. Replace the Sails JAR on the server and every client with `empirecraft.3`, removing every older Sails JAR. Retain the existing `valkyrienskies-120-2.4.11-empirecraft.1.jar`; if installing from scratch, use the two named JARs from the repository.
3. Copy `server/config/vs_sails/config.cfg` and the updated ADM ID file. The ADM list is regenerated from the actual client JARs and preserves the four existing resource packs. No anticheat checks or ignored-mod settings are changed.
4. Start on a copy of the production world. Check player entry, ownership permissions, steering in both directions, anchor release, and save/reload before opening it to players.

The included configuration turns off aerodynamic wind on unrelated ships and magic-ballast righting, uses normal ballast/buoys, and limits a bottle assembly to 10000 blocks. The existing Mohist/Towny setup is retained.

## Historical verification — empirecraft.1

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

To rebuild only Sails while retaining the existing tested VS JAR, use `python sails/build.py --server-home server --sails-only`. The new build was compiled with OpenJDK 17.0.20 against Forge 47.4.23 and the pinned binaries. `python sails/test.py --prepare-wind-fixture` packages the test-only physics probe, which reproduces the old behavior and checks all force directions on a real VS body in an isolated `world_europe` test dimension. It must never be installed on the live server.

Zulu JDK 21.0.13 was used for the recorded byte-identical builds; emitted mod bytecode targets Java 17. `build.py` never starts a game server. Its output is copied to both mod folders. `test.py --prepare-fixture` also creates the temporary runtime test mod. Use that fixture only on an isolated copy: it creates a test ship, changes blocks near spawn and stops the server. It must never be installed on the production server.

The branch workflow packages the large VS JAR from the pinned original and checks it against the tested digest before committing its two copies. It does not deploy to a running host.


## Compiling the anchor renderer

The build pins the official Minecraft 1.20.1 client binary and mappings as compile-time inputs for the new client renderer. Minecraft classes are not bundled into Sails. The arcade fixture is selected with `python sails/test.py --prepare-arcade-fixture`; the old `--prepare-wind-fixture` flag now aliases it. The historical v2 helper math tests remain separate from tests of the actual arcade controller.
