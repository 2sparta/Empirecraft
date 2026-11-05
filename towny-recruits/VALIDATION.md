# Towny Recruits 1.1.0 tactical validation

Java 17 compilation passed against real Forge/Mohist 47.4.23, Recruits 1.15.0, Supplementaries and the official Minecraft 1.20.1 client remapped to SRG. Client SHA-1: `0c3ec587af28e5a785c0b4a7b8a30f9a8f78f838`; official client mappings SHA-1: `6c48521eed01fe2e8ecdadbd5ae348415f3c47da`. No API stubs or downloaded game libraries are included in the production JAR.

JAR SHA-256: `98d1f7abf5a8fad23a76f66f9ca7401f5f3e749cdcb80d40ac3b3194283167a6`.

- Passed 1,536 formation/count cases (1–128), distinct slots, centered layouts, rotations, outward square sides and two pike ranks.
- Integrated 255 valid trajectories with the same move/drag/gravity update as Moonlight; checked unreachable arcs and fuse bounds, uniform disk scatter, 18,000-tick cooldown boundary and shared bomb capacity.
- Round-tripped every mode/formation request and a mixed-validity 128-unit preview with actual FriendlyByteBuf; rejected oversized groups, invalid modes, duplicate groups and non-finite coordinates.
- Executed actual compiled bomb/container/slot hook bodies in a CPU harness with fixture inventories: mixed bomb types, one-slot and second-slot limits, equipment rejection, pickup conservation (64 → 1 + 63), reinsertion after removal, and unrelated inventory behavior. This harness is not a Minecraft server boot.
- Applied the real Mixin transformer and ASM verification to Entity, Slot, RecruitSimpleContainer, all three native equipment-slot classes, CommandEvents, CommandScreen and HumanoidModel, in addition to the existing 23 server targets and human renderer.
- Passed existing Towny policy/reflection, census, real NBT, appearance, skin generation and compiled renderer-hook regression checks. Complete output: [test-results.txt](test-results.txt).

A complete running hybrid server with graphical clients has **not** been tested. March cohesion under live navigation, visual pike/item poses, Copycats slope appearance, claim-boundary explosions and large-army TPS still require the acceptance scenarios in [TACTICS.md](TACTICS.md). Automated geometry and bytecode checks do not establish live gameplay behavior.

The installed copies, ADM, lock and update ZIP are checked by `validation/check_installed_releases.py`, `validation/reconcile_modpack.py` and package byte comparisons when preparing the release.

---

# Validation — 1.0.4, 2026-11-01

## Height balance 1.0.4

- User confirmed that 1.0.3 runs and supplied an in-game screenshot showing too many recruits shorter than the player.
- Main height band moved from 1.75–1.90 to 1.86–1.94, close to the player's rendered humanoid silhouette. New intended weights: 92% near player, 5% moderately taller, 2% visibly shorter, 1% very tall.
- Deterministic 300,000 UUID sample passes: 92.02%, 4.94%, 2.06%, 0.99%, respectively; all 128 skins remain reachable; height endpoints remain 1.50–2.50.
- Tests enforce the short-recruit proportion below 2.2%, the main band above 91.5%, stable UUID serialization, and coherent render/collision/eye factors.
- Renderer and size event hooks are byte-for-byte unchanged from working 1.0.3. Existing identity gets a new deterministic height when the entity joins a world; skin selection is unchanged.
- Full graphical 1.0.4 gameplay has not been run in this environment.

## Client crash regression

- User log: Forge client 47.4.28, Recruits 1.15.0, TownyRecruits 1.0.2 failed at EntityRenderersEvent.RegisterRenderers with NoClassDefFoundError for `org.spongepowered.asm.synthetic.args.Args$1`.
- The new regression was run against the compiled 1.0.2 hooks and detected that exact generated dependency. The same check passes after replacement with typed variable hooks in 1.0.3.
- The actual transformed Recruits human renderer passes ASM verification and contains no generated Args references. Hook order is checked: entity capture → pose scale → native renderer → restoration.
- A CPU-only runtime harness uses the actual compiled hook bodies (renamed to a plain test class), resolves their dependencies and executes scale/restoration with the real PoseStack/JOML classes, including nested rendering and a redundant restore. Matrices restore exactly within float tolerance; the 128 resource paths initialize successfully.
- The harness is not a complete graphical Minecraft/Forge boot. A full boot of the user's exact client, in-game appearance/equipment and mounted poses remain unverified in this environment.

## Appearance checks

- 128 unique original 64x64 RGBA skins pass PNG/UV coverage and deterministic regeneration checks; all checksums are pinned in assets/skins-manifest.json.
- 300,000 UUIDs: all 128 skins reachable, identical results after UUID serialization, height range 1.5000..2.5000, 92.02% in 1.86..1.94, 4.94% moderately taller, 2.06% visibly short, 0.99% very tall.
- Policy checks verify collision/eye scale and 32-pixel standard humanoid render-height calibration.
- The actual RecruitHumanRenderer client-side Mixin transformation and ASM BasicVerifier checks pass, including texture selection and balanced pose-stack scale/restoration. The 23 server target checks still pass independently.
- Main compilation uses real runtime classes; the renderer hook does not bundle client API stubs. No layer-buffer argument capture or generated Args class is needed.
- Skin gallery inspected as a flat preview. A live graphical client test of the new skins, equipment animations, collision/navigation clearance and mounting poses remains pending.

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
