# Validation — 1.0.1, 2026-11-01

## Reproduced bug and regression checks

- Original 1.0.0 failed while reading the provided workspace worlds: `Truncated entity region: .../world/entities/r.-1.0.mca`. There are 66 entity-region files in that snapshot, including two zero-byte files.
- 1.0.1 completes the same read-only census successfully. Empty files remain untouched. This is a file-level reproduction, not a running-server test.
- Regression tests cover an empty region alongside populated regions, a non-empty corrupt header, no partial ledger commit after failure, and a successful retry after repair.
- Hire-policy regression distinguishes `census=false` at `0/100` from an exhausted cap, absent nation, owned recruit and unavailable Towny.
- Cleanup regressions cover deleted `tr_` and legacy `ec_` faction IDs, outgoing/incoming diplomatic relations and embargo entries; unrelated manual faction records remain intact.
- ADM roundtrip preserves every other mod row and resource pack; only TownyRecruits version/checksum and required integrity metadata change.

## Passed

- Java 17 compilation against the actual Mohist/Forge 1.20.1 47.4.23 runtime, with the exact uploaded mod/plugin JAR hashes below. No API stubs are packaged or used for main compilation.
- Pure policy tests: baseline/additional-town limits, zero and fixed overrides, UUID identity, symmetric enemies, accepted alliances, neutrality and conflict priority.
- Reflection contract tests against Towny 0.101.2.0: nation/member/territory APIs and the concrete event classes used by the bridge.
- Actual Minecraft NBT roundtrip: nation overrides, counts retained for unloaded units, retirement on player departure and nation deletion, retirement surviving restart.
- A synthetic compressed `.mca` entity-region migration test: recruit discovery, idempotent tracking and byte-for-byte unchanged input region.
- Real Sponge Mixin transformation and ASM BasicVerifier analysis for all 23 target classes, including both upkeep goals, hire/transfer/payment hooks, faction controls, diplomacy and 13 client-to-server mutation packet handlers.
- Uploaded Musket and Boomsticks SHA-256 sidecars match their JAR files.

## Pinned input SHA-256

```text
5c0c9252b332babf7e7cf5fc9a5a6f08018df5b9f86827853db2cb3f39b127ce  recruits-1.20.1-1.15.0.jar
c21dd5c8e63401fe5f14be77a33e96afa727b9c8214a7f85c2c4f67b761645a6  Towny-0.101.2.0.jar
4e213daae42d92606639dd98c340e2274ffaa460a4832850d468900ee326a5ad  musketmod-1.20.1-forge-1.5.4.jar
b5ba292cde703946a18cd18c87c93ae970eef057dbbeff8c6b6243aa4397c3c9  medieval_boomsticks-no_stars (8).jar
```

## Not yet validated

- Booting this new integration in a complete running hybrid server and joining graphical clients.
- The full gameplay checklist in README, including animation appearance and chest access at a live claim border.
- TPS/load measurements with large armies.
- Arclight, Magma or other Forge/Bukkit hybrid implementations.
- Coexistence with other Recruits integration mods or scoreboard controllers. Remove competing integrations before testing.
- Importing an old, populated additional world after the first completed census. Initial migration includes currently loaded Bukkit worlds; later imported worlds must have their old recruits loaded/adopted before relying on their count.

Tests show bytecode compatibility and policy/persistence behavior; they do not establish production gameplay compatibility.
