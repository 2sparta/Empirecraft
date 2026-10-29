# Empirecraft Ship Cannons 1.2.0

Forge 1.20.1 / 47.4.23, VS 2.4.11-empirecraft.1, Supplementaries 1.20-3.1.44, Moonlight 1.20-2.16.37; optional Create Big Cannons 5.11.4-medieval.2 and Ritchie’s Projectile Lib 2.1.1. The same JAR is required on server and clients. No replacement of Sails or Ship Physics is needed.

## Recoil and ship heel

Shots apply projectile momentum and an overturning angular impulse in the ship’s own frame. Broadside fire heels away from the muzzle; opposite broadsides cancel. The horizontal recoil lever is amplified, including guns close to or below the centre of mass. Actual inertia, ship rotation and mass still determine the response. A 0.12-second pulse smooths the impulse; individual kicks and total recoil angular speed are capped. Existing motion is preserved. Anchored/static hulls receive no recoil; a wheeled carriage can still kick along their deck. The saved `RecoilController` attachment name and owner ID remain compatible with 1.1.0.

CBC big cannons, autocannons and drop mortars use the same controller, on fixed mounts and on carriages. Recoil is generated only after an actual projectile is accepted by the server. Ejected cases, misfires and failed entity spawns do not count. Within that successful shot, the corresponding stock VS recoil force is suppressed so it is not added twice. Other forces retain their normal path.

## Carriages on a moving deck

Wheel contact is checked against loaded ship collision shapes. VS remains the sole source of rigid deck transport; the add-on does not add another position update. Carriage gravity and drive input follow the contact plane, preventing idle downhill creep during ordinary ship heel. Driver and server use CBC’s existing vehicle authority and position packets. A small wheel recoil is synchronized through entity data so the local driver also sees it. Leaving the deck or losing support releases the ship reference; another ship’s fresh contact is not cleared. Wrench disassembly converts the block anchor and facing back to the supporting ship’s frame so it does not place the cannon in stationary world space.

## Grapeshot

The bag opens before its first whole-projectile collision step. The default 25 pellets use a uniform disk cone rather than clustering near its centre. The default minimum tangent spread is 0.25 (about 14 degrees half-angle); the radius therefore grows with range. Pellets inherit ship translation and angular point velocity once when a ship-fired bag opens.

Each pellet traces its actual segment through loaded terrain and each intersecting ship’s local frame. Tagged cloth is hittable even with an empty collision shape. It can tear up to three light layers in one step, with a shared default limit of 64 broken blocks per burst. Hard hull blocks and entities stop the added cloth damage; it does not erase a sphere around one impact. The normal CBC response to hard blocks is retained.

Before each cloth removal, the production path checks CBC’s NO_DAMAGE setting, world border, spawn protection, and Forge’s break event using a dedicated fake-player profile. Protected cloth stops the pellet and does not fall through to a second native damage attempt. Unknown/unloaded voxels stop it without loading chunks. The burst’s used block budget is saved with its NBT.

Default light materials are wool, carpets, leaves, VS Sails sails and Create sails/frames. Datapacks can add blocks to `empirecraft_ship_cannons:grapeshot_light` or exclude them using `empirecraft_ship_cannons:grapeshot_resistant`. Block entities and unbreakable blocks are never removed by the added cloth path.

## Installation and settings

See `INSTALL-UA.txt`. Settings are in `config/empirecraft_ship_cannons-common.toml` and are created/extended by Forge. Existing 1.1.0 recoil settings remain valid. Important new values:

| Setting | Default | Effect |
| --- | ---: | --- |
| rollImpulseMultiplier | 20 | Strength of heel from horizontal recoil |
| minimumRollLeverMetres | 1 | Minimum virtual height above COM |
| maximumAngularKickDegreesPerShot | 4 | Per-shot angular impulse cap |
| maximumRecoilAngularSpeedDegrees | 20 | Recoil cannot push rotation above this speed |
| bigCannonsRecoil | true | CBC recoil integration |
| carriageDeckMovement | true | Deck-relative carriage controls |
| grapeshotLightBlockDamage | true | Pellet spread and light-block penetration |
| grapeshotMinimumSpread | 0.25 | Minimum cone half-angle tangent |
| grapeshotMaximumBrokenBlocksPerBurst | 64 | Shared cloth destruction budget |

## Build and verification

```sh
python ship-cannons/build.py --server-home server
python ship-cannons/test.py
python ship-cannons/release.py
python ship-cannons/release.py --check
python ship-cannons/tests/test_release.py
python ship-cannons/package.py
```

Java 17 and the pinned modpack/Forge libraries are required. `build-inputs.json` records the source baseline and input hashes. The compiler remaps against those actual JARs and ships only this add-on’s classes. The existing Sails mapping bootstrap is reused.

See `VALIDATION.txt` for tested cases and limits. The numerical/adapter tests bootstrap vanilla registries with simulated loaded storage. Their test-only overlay supplies Forge entity persistent-data API and the FML production flag; it is never packaged in the production JAR. Sponge tests transform the shipped SRG bytecode with the real VS CBC recoil Mixin on both physical sides. No graphical client, live Mohist/Towny server, native PhysX simulation or multiplayer latency test was run. Protection integration and visual balance still need a live-world check.
