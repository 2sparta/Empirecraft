"""Move shaped Overgeared recipes onto the pack's installed smithing stations."""
from collections import Counter
import json
from pathlib import Path

STEEL = "tfc:metal/ingot/steel"
SHEET = "tfc:metal/sheet/steel"
BLOCK = "tfc:metal/block/steel"
NUGGET = "empirecraft_armory:steel_nugget"
ALIASES = {
    "overgeared:steel_ingot": STEEL,
    "overgeared:heated_steel_ingot": STEEL,
    "overgeared:steel_plate": SHEET,
    "overgeared:steel_block": BLOCK,
    "overgeared:steel_nugget": NUGGET,
    "magistuarmory:steel_plate": SHEET,
    "hamzabus_ottoman_mod:steel_ingot": STEEL,
    "hamzabus_ottoman_mod:steel_nugget": NUGGET,
    "overgeared:iron_plate": "create:iron_sheet",
    "overgeared:copper_plate": "create:copper_sheet",
    "overgeared:copper_nugget": "create:copper_nugget",
    "overgeared:rock": "tfc:rock/loose/granite",
    "overgeared:stone_pickaxe_head": "empirecraft_tfc:stone_pickaxe_head",
    "overgeared:smithing_hammer": "immersive_smithing:iron_smithing_hammer",
    "overgeared:copper_smithing_hammer": "immersive_smithing:stone_smithing_hammer",
    "overgeared:iron_tongs": "immersive_smithing:iron_smithing_tongs",
    "overgeared:steel_tongs": "immersive_smithing:iron_smithing_tongs",
    "overgeared:wooden_tongs": "immersive_smithing:stone_smithing_tongs",
    "overgeared:smithing_anvil": "immersive_smithing:smiths_anvil",
    "overgeared:stone_anvil": "immersive_smithing:smiths_anvil",
}
for metal in ("steel", "copper", "iron"):
    for tool in ("pickaxe", "axe", "hoe", "shovel", "sword"):
        native = "wrought_iron" if metal == "iron" else metal
        target = f"tfc:metal/{tool}/{native}"
        if metal == "iron":
            target = f"minecraft:iron_{tool}"
        if (metal, tool) == ("steel", "sword"):
            target = "magistuarmory:steel_shortsword"
        ALIASES[f"overgeared:{metal}_{tool}"] = target
        ALIASES[f"overgeared:{metal}_{tool}_head"] = f"tfc:metal/{tool}_head/{native}"
    ALIASES[f"overgeared:{metal}_hammer_head"] = f"tfc:metal/hammer_head/{native}"
    ALIASES[f"overgeared:{metal}_sword_blade"] = f"tfc:metal/sword_blade/{native}"

for tool in ("axe", "pickaxe", "hoe", "shovel"):
    ALIASES[f"overgeared:golden_{tool}_head"] = f"empirecraft_tfc:golden_{tool}_head_blank"
ALIASES["overgeared:golden_sword_blade"] = "empirecraft_tfc:golden_sword_blade_blank"

FAMILIES = {
    "iron": "minecraft:iron", "copper": "minecraft:copper", "gold": "minecraft:gold",
    "steel": "forge:steel", "black_steel": "forge:black_steel",
    "silver": "forge:silver", "bronze": "forge:bronze",
    "wrought_iron": "minecraft:iron",
    "high_carbon_black_steel": "empirecraft_armory:high_carbon_black_steel",
}
METAL = {}
for metal, family in FAMILIES.items():
    for namespace in ("forge", "minecraft"):
        for kind, units in (("ingot", 9), ("nugget", 1), ("block", 81)):
            METAL[f"{namespace}:{metal}_{kind}"] = (family, units)
            prefix = "storage_blocks" if kind == "block" else kind + "s"
            METAL[f"{namespace}:{prefix}/{metal}"] = (family, units)
    for kind, units in (("ingot", 9), ("double_ingot", 18), ("sheet", 18), ("block", 81)):
        METAL[f"tfc:metal/{kind}/{metal}"] = (family, units)
METAL.update({
    NUGGET: ("forge:steel", 1),
    "create:iron_sheet": ("minecraft:iron", 9),
    "create:copper_sheet": ("minecraft:copper", 9),
    "create:copper_nugget": ("minecraft:copper", 1),
    "magistuarmory:silver_ingot": ("forge:silver", 9),
    "createbigcannons:bronze_ingot": ("forge:bronze", 9),
    "magistuarmory:small_steel_plate": ("forge:steel", 1),
})

def encode(data):
    return (json.dumps(data, ensure_ascii=False, indent=2) + "\n").encode()

def rewrite(value):
    if isinstance(value, str):
        return ALIASES.get(value, value)
    if isinstance(value, list):
        return [rewrite(child) for child in value]
    if isinstance(value, dict):
        changed = {key: rewrite(child) for key, child in value.items()}
        if isinstance(changed.get("materials"), dict):
            changed["materials"] = {ALIASES.get(key, key): amount for key, amount in changed["materials"].items()}
        return changed
    return value

def ingredient(value):
    if value.get("type") == "tfc:heatable":
        value = value["ingredient"]
    return {key: child for key, child in value.items() if key not in ("transfer_nbt", "requires_heated")}

def smithing(family, units, result, auxiliary=(), pattern="generic_tool", conditions=()):
    recipe = {
        "type": "immersive_smithing:smithing", "material": family, "metal_units": units,
        "anvil_pattern": f"immersive_smithing:{pattern}", "result": result,
    }
    if auxiliary:
        recipe["auxiliary"] = list(auxiliary)
    if conditions:
        recipe["conditions"] = conditions
    return recipe

def recipe(data, recipe_id):
    data = rewrite(data)
    if data.get("type") != "overgeared:forging":
        return data
    keys = {key: ingredient(value) for key, value in data["key"].items()}
    result = data["result"]
    if data.get("category") == "armors":
        # The armory catalog turns these source recipes into the four-stage bill of materials.
        shaped = {"type": "minecraft:crafting_shaped", "pattern": data["pattern"], "key": keys,
                  "result": result}
        if "conditions" in data:
            shaped["conditions"] = data["conditions"]
        return shaped

    counts = Counter("".join(data["pattern"]))
    metals = Counter()
    for symbol, value in keys.items():
        material = METAL.get(value.get("item") or value.get("tag"))
        if material:
            family, units = material
            metals[family] += counts[symbol] * units
    if not metals:
        # These recipes assemble finished TFC blades and heads. Keep the existing quality transfer.
        return {"type": "empirecraft_casting:quality_shaped", "pattern": data["pattern"], "key": keys,
                "result": result, **({"conditions": data["conditions"]} if "conditions" in data else {})}
    family = max(metals, key=metals.get)
    auxiliary = []
    for symbol, value in keys.items():
        material = METAL.get(value.get("item") or value.get("tag"))
        if counts[symbol] and (not material or material[0] != family):
            auxiliary.append({"ingredient": value, "count": counts[symbol], "consume_equipment": True})
    return smithing(family, metals[family], result, auxiliary, conditions=data.get("conditions", ()))


def canonical_mapping(policy):
    # Legacy steel was canonicalized in the reverse direction by the TFC bridge.
    policy["aliases"].pop(STEEL, None)
    policy["aliases"].update(ALIASES)
    policy["forbidden"] = [item for item in policy["forbidden"] if item not in ALIASES]
    policy["overrides"] = {name: recipe(data, name) for name, data in policy["overrides"].items()}
    return policy


def update_pack_data(root):
    for folder in ("kubejs", "server/kubejs"):
        kube = root / folder
        for path in (kube / "server_scripts").glob("*.js"):
            source = path.read_text()
            if "overgeared:" not in source:
                continue
            if "const EC_TYW_RECIPES = " in source or "const EC_GUNSMITH_RECIPES = " in source:
                start = source.index(" = {\n") + 3
                payload, length = json.JSONDecoder().raw_decode(source[start:])
                converted = {name: recipe(data, name) for name, data in payload.items()}
                source = source[:start] + encode(converted).decode().rstrip() + source[start + length:]
                source = substitute(source)
            elif path.name == "zzzzzz_empirecraft_progression.js":
                source = source.replace("'overgeared:steel_block'", "'" + BLOCK + "'")
                start = source.index("    if (data.type === 'overgeared:item_to_tooltype'")
                source = source[:start] + source[source.index("    var all = strings(data)", start):]
                source = source.replace("the existing Overgeared forging conversion", "the smithing integration")
            if source != path.read_text():
                path.write_text(source)
        for path in (kube / "data").rglob("*.json"):
            raw = path.read_bytes()
            if b"overgeared:" not in raw:
                continue
            data = json.loads(raw)
            if "/recipes/" in path.as_posix():
                converted = recipe(data, path.as_posix())
            elif "/tags/" in path.as_posix():
                converted = rewrite(data)
                values = []
                for entry in converted["values"]:
                    tag_id = entry if isinstance(entry, str) else entry["id"]
                    if not tag_id.startswith(("overgeared:", "#overgeared:")) and entry not in values:
                        values.append(entry)
                converted["values"] = values
            else:
                continue
            path.write_bytes(encode(converted))


def substitute(source):
    # Descending length keeps steel_ingot from changing steel_ingot_from_* IDs, which are removed.
    import re
    pattern = r'(?<![a-z0-9_/:])(?:' + '|'.join(map(re.escape, sorted(ALIASES, key=len, reverse=True))) + r')(?![a-z0-9_/])'
    return re.sub(pattern, lambda match: ALIASES[match[0]], source)
