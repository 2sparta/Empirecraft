# EmpireCraft World Rules 1.0.0: validation

Target: Minecraft 1.20.1 / Mohist 71a84ff7 / Forge 47.4.23 / CombatLog 2.8.4, with the repository's 92 baseline server mods. New JAR is server-only. Production Towny requires Java 21.

## Automated evidence

- Compiled SRG Java 17 bytecode against the installed patched Mohist server, Forge, CombatLog and exact mod JARs.
- 11,341 policy/NBT assertions: all enchantment metadata removed, names/durability/quality preserved, wine metadata retained, potion effects cleared; repeated vein conversion simulations never exhaust ore; stable eligibility; real SavedData NBT roundtrip, negative coordinates, independent quotas and cooldown rollback resistance.
- 15 Towny reflection assertions using isolated Bukkit/Towny fixtures: claims, world filtering, negative coordinates, thread check, absent/disabled/replaced/throwing/recovered plugin. This checks reflective contracts, not live Towny startup.
- Mixin 0.8.5 successfully transforms actual installed ItemStack, BrewingStandBlockEntity, EnchantmentMenu and CombatLog CombatConfig. ASM verifies every concrete transformed method. The test also rejects unexpected ServerChunkCache loading calls from renewal code.
- Asset check verifies configured plant blockstates, every market item model, bong ingredients/result, exact recipe condition keys, false-condition recipe overrides, and corrected Vinery taiga IDs against the baseline JARs.
- KubeJS replacement passes JavaScript syntax check. Source/resource JAR rebuilt twice with an identical checksum; package checksums verified.

No full Mohist launch or multiplayer gameplay was available here. The CombatLog AIR parsing defect and explicit cannon exemption are verified in code; the user's complete cannon failure has not been reproduced in game. No claim that all imported-map resources have been inspected: see `world-rules/RESOURCE-AUDIT.md`.

## Required gameplay checks on a copy of the server

1. Start with Java 21, check all four Mixins load and no KubeJS error originates from `5_enchantments.js`. Confirm the configured Bukkit world names and Towny readiness.
2. Tag two players in combat. Load/open/aim/fire a Supplementaries cannon in wilderness and in an authorized claim. Check command/logout restrictions still apply. In another player's claim, verify claim guard still prevents block/object destruction, mob damage without permission and player damage when claim PvP is off.
3. Check an existing enchanted sword, stored enchanted book, named/damaged/quality item, old chest contents, mob gear and fished/traded loot. Confirm enchantments disappear, ordinary metadata remains and `/enchant` fails. Check table use, anvil book application and new librarian trades; normal repair must still work.
4. Test old/new brewing stands with fuel, ingredients, a partial brew and hopper input. No vanilla brew completes. Existing vanilla potions become water, splash/lingering become empty bottles, tipped arrows ordinary arrows. Test a water-bottle use and the bong crafting recipe, including bucket return.
5. Ferment wine in Vinery and beer in a keg; use Farmer's Respite's own vessel and Caupona cooking. Confirm their normal effects/aging and Overgeared smithing/quality.
6. In an eligible unclaimed test chunk, use a known multi-block lapis vein; leave players nearby and wait for stochastic sampling. Verify exactly one block converts to Meadow salt ore, small 1–2-block veins stay intact, inventory lapis is untouched. Restart and verify the same chunk never converts a second ore. Loaded connected neighboring chunks may contribute to the reserve; unloaded ones must not be loaded by this mod.
7. Verify claims/unclaims and Towny disable/API failure never authorize unknown terrain. Natural changes must stop with no online player or unloaded target. In wilderness observe suitable plants, air-only placement, native growth, per-chunk cap and persisted cooldown. Check older builds/farms remain untouched.
8. With normal wandering-trader spawning enabled, sample traders until seed/sapling/raw-material offers appear; verify IDs, costs, stock and normal plant propagation. Rotation means one trader is not a complete catalogue.
