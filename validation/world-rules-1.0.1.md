# EmpireCraft World Rules 1.0.1: validation

Target: Minecraft 1.20.1 / Mohist 71a84ff7 / Forge 47.4.23 / CombatLog 2.8.4. Server-only Java 17 bytecode; the server's Towny still requires Java 21.

## Automated evidence

- Built against the exact repository Mohist/Forge runtime and installed mod JARs.
- 32 policy/NBT assertions: existing no-magic behavior, safe air/tall/water placement, rice habitat, sand/cactus support, farm/building exclusion, climate boundaries, independent tree/crop timers, negative chunk coordinates, cooldown boundaries and time rollback. Real NBT loads old PlantTimes while ignoring/removing SaltChunks and adding TreeTimes.
- 15 production Towny reflection assertions: claimed/unclaimed chunks, world filtering, negative coordinates, thread enforcement, absent/disabled/replaced/throwing/recovered plugin.
- Real Mixin transformation plus ASM verification of ItemStack, BrewingStandBlockEntity, EnchantmentMenu and CombatLog CombatConfig still passes. Renewal bytecode uses getChunkNow rather than loading/generation APIs.
- Asset/config integration checks pass for all 33 plant blocks, all 21 sapling blocks, every one of the eight Vinery grape varieties and 41 market/native item references. All configured IDs are present in the exact installed JAR assets; old salt settings and Meadow trade entries are absent. Recipe conditions, bong ingredients, corrected taiga seeds and disabled Rustic potions remain validated.
- Reproducible JAR build and ZIP entry/checksum verification. The package contains the replacement JAR and updated renewal configuration.

A complete live Mohist server and multiplayer gameplay were not run. Asset presence and policy tests do not prove every native plant will grow under every biome/season/light setting.

## Gameplay checks on a copy of the server

1. Stop the server, remove the old world-rules JAR, install 1.0.1 and the new common config, retain actual Bukkit world names, then restart fully. Check the startup count (33 plants / 21 saplings when all relevant mods are installed) and normal Towny readiness with Meadow absent.
2. Stay near loaded wilderness with appropriate temperate/hot/cold biomes. Up to four distinct chunks are sampled every five seconds; plants appear in batches up to three, capped at 20 columns, with a one-minute successful-pass cooldown. Sampling is random, not a guarantee for each chunk at every interval.
3. Check all eight grape variants across their native biomes; ripe berry/grape bushes, both halves of lemons/sunflowers, source-water rice with an air upper half, cranberry by water and prickly pear on existing cactus. Other plants/buildings and farmland must not be replaced.
4. In open suitable wilderness, observe fruit saplings (up to two rooted trees/saplings per chunk, including existing trees, two-minute independent cooldown). Adjacent 3x3 chunks must also be loaded wilderness at planting time. Confirm normal native tree growth/light/space/season behavior; there is no forced instant mature-tree generation.
5. Claim the target or one of its neighboring chunks. No planting in the claim; neighboring claims block new saplings. Disable Towny or make its API unavailable: renewal must stop. Unloaded chunks and worlds outside the list must remain untouched.
6. Restart with legacy empirecraft_natural_renewal.dat. Crop timers remain, new tree timers persist separately; no lapis/deepslate-lapis conversion occurs at any point. Previously converted world blocks are not automatically reconstructed.
7. Regression-check the existing no-enchantment/no-brewing rules, mod wine/beer effects, combat cannon exemption, claim guard and wandering trader offers using the 1.0.0 gameplay checklist, excluding its obsolete salt-conversion step.
