"""Pack data connecting Immersive Jewelry and Tanning to the installed recipe set."""
import collections
import json
from pathlib import Path
import re
import subprocess
from zipfile import ZipFile


TANNING = "immersive_tanning"
JEWELRY = "immersive_jewelry"
PACK = "empirecraft_immersive"
GEMS = {
    "amethyst": "minecraft:amethyst_shard",
    "diamond": "minecraft:diamond",
    "emerald": "minecraft:emerald",
    "lapis": "minecraft:lapis_lazuli",
    "quartz": "minecraft:quartz",
    **{material: f"tfc:ore/{material}" for material in ("ruby", "sapphire", "topaz", "opal", "pyrite")},
}
TFC_GEMS = [gem for gem, source in GEMS.items() if source.startswith("tfc:")]
SIZES = ("small", "medium", "large")
RAW_HIDES = {
    "naturalist:fur": ("small", "insulating"),
    "alexsmobs:bear_fur": ("large", "insulating"),
    "alexsmobs:bison_fur": ("large", "insulating"),
    "slavicarmory:fur": ("medium", "insulating"),
}
LEATHERS = ("minecraft:leather", "medieval_boomsticks:finished_leather",
            "epic_knights__japanese_armory:japanleather")
LEATHER_INPUTS = {
    "minecraft:leather", "forge:leather", "c:leathers",
    "epic_knights__japanese_armory:japanleather",
}
STRIPS = {"magistuarmory:leather_strip", "magistuarmory:leather_strips"}
# These two protective coats use an existing wool coat as their base.
CLOTH_BASES = {f"thirtyyearswar:baroque_{name}" for name in
               ("coat", "apron_skirt", "breeches", "riders_breeches", "skirt")}
BASELINE = "0b09199a749061def31d022349bfa9da090b1af9"


def encode(value):
    return (json.dumps(value, indent=2, ensure_ascii=False) + "\n").encode()


def ingredient_key(ingredient):
    return ingredient.get("item", ingredient.get("tag", ""))


def recipe_ingredients(recipe):
    if "pattern" in recipe:
        return [recipe["key"][symbol] for row in recipe["pattern"] for symbol in row if symbol != " "]
    return recipe["ingredients"]


def epic_knights_leather(mods):
    """Select finished garments whose original bill of materials is leather/textile."""
    candidates = collections.defaultdict(list)
    root = mods.parent
    managed_overrides = set(subprocess.check_output(
        ["git", "ls-tree", "-r", "--name-only", BASELINE, "kubejs/data"], cwd=root, text=True,
    ).splitlines())
    for jar in sorted(mods.glob("epic-knights-*.jar")):
        with ZipFile(jar) as source:
            for filename in sorted(source.namelist()):
                match = re.fullmatch(r"data/([^/]+)/recipes/(.+)\.json", filename)
                if not match:
                    continue
                try:
                    pack_path = "kubejs/" + filename
                    raw = (subprocess.check_output(["git", "show", f"{BASELINE}:{pack_path}"], cwd=root)
                           if pack_path in managed_overrides else source.read(filename))
                    recipe = json.loads(raw)
                except ValueError:
                    # Some upstream JSON recipes are malformed and already fail vanilla parsing.
                    continue
                if recipe.get("type") not in {"minecraft:crafting_shaped", "minecraft:crafting_shapeless",
                                                   "empirecraft_casting:quality_shaped"}:
                    continue
                result = recipe.get("result")
                if not isinstance(result, dict) or result.get("count", 1) != 1:
                    continue
                item = result.get("item", "")
                if ":" not in item:
                    continue
                name = item.split(":")[1]
                if name.endswith(("_decoration", "_model")):
                    continue
                inputs = recipe_ingredients(recipe)
                keys = [ingredient_key(i) for i in inputs if isinstance(i, dict)]
                has_leather = any(key in LEATHER_INPUTS | STRIPS for key in keys)
                is_gambeson = "gambeson" in name and "chained" not in name
                is_kimono = name.startswith("kimono_")
                if not (has_leather or is_gambeson or is_kimono):
                    continue
                clothing = re.search(r"(?:hat|cap|hood|helmet|tunic|coat|waistcoat|chestplate|gambeson|kaftan|pants|trousers|leggings|breeches|skirt|boots?|shoes|sandals)$", name)
                if not clothing:
                    continue
                if len(keys) != len(inputs):
                    continue
                if any(not (key in LEATHER_INPUTS | STRIPS | CLOTH_BASES
                            or re.search(r"wool|cloth|fabric|felt|dye|string", key)) for key in keys):
                    continue
                armor_slot = (
                    "boots" if name.endswith(("boot", "boots", "shoes", "sandals")) else
                    "helmet" if name.endswith(("hat", "cap", "hood", "helmet")) else
                    "leggings" if name.endswith(("pants", "trousers", "leggings", "breeches", "skirt"))
                    else "chestplate"
                )
                full = sum(key in LEATHER_INPUTS for key in keys)
                strips = sum(key in STRIPS for key in keys)
                cost = max(1, full + (strips + 1) // 2)
                auxiliary = []
                for key, count in collections.Counter(keys).items():
                    if key not in LEATHER_INPUTS | STRIPS:
                        kind = "tag" if any(i.get("tag") == key for i in inputs) else "item"
                        auxiliary.append(dict(ingredient={kind: key}, count=count))
                candidates[item].append((f"{match[1]}:{match[2]}", cost, auxiliary, armor_slot, recipe.get("conditions", [])))
    return candidates


def generate(mods, jewelry, tfc):
    files, overrides = {}, {}
    def out(path, value):
        files[path] = encode(value)
    def override(path, value):
        overrides[path] = encode(value)
    def tag(namespace, path, values, category="items"):
        out(f"data/{namespace}/tags/{category}/{path}.json", {"replace": False, "values": list(values)})
    def recipe(path, value):
        out(f"data/{PACK}/recipes/{path}.json", value)

    for gem, source in GEMS.items():
        values = [source]
        if gem in TFC_GEMS:
            values.append(f"tfc:gem/{gem}")  # Existing cut stock can enter the new wheel.
        tag("forge", f"gems/{gem}", values)
    tag(JEWELRY, "gem_sources", ["#forge:gems/opal", "#forge:gems/pyrite"])
    tag(JEWELRY, "polishing_dusts", ["#tfc:gem_powders"])
    for gem, template, base, sparkle in [
        ("opal", "quartz", "#86BCD0", "#FFF8EF"),
        ("pyrite", "topaz", "#B18D4B", "#FFE6A0"),
    ]:
        with ZipFile(jewelry) as source:
            data = json.loads(source.read(f"data/{JEWELRY}/{JEWELRY}/gems/{template}.json"))
        data.update(ingredient={"tag": f"forge:gems/{gem}"}, base_color=base, sparkle_color=sparkle)
        data.update(hardness=6, brittleness=0.63 if gem == "pyrite" else 0.53)
        out(f"data/{JEWELRY}/{JEWELRY}/gems/{gem}.json", data)
    for locale, names in {"uk_ua": ("Опал", "Пірит"), "en_us": ("Opal", "Pyrite")}.items():
        out(f"assets/{JEWELRY}/lang/{locale}.json", {
            f"gem.{JEWELRY}.{gem}": name
            for gem, name in zip(("opal", "pyrite"), names)
        })

    for size in SIZES:
        recipe(f"hide_mounting/{size}_tfc", {
            "type": f"{TANNING}:hide_mounting", "size": size,
            "ingredient": [{"item": f"tfc:{size}_{stage}_hide"}
                           for stage in ("raw", "soaked", "scraped", "prepared", "sheepskin")],
        })
    for item, (size, trait) in RAW_HIDES.items():
        recipe("hide_mounting/" + item.replace(":", "/"), {
            "type": f"{TANNING}:hide_mounting", "size": size, "trait": f"{TANNING}:{trait}",
            "ingredient": [{"item": item}],
        })
    # Binding leather and scraps are not full sheets and the bench cannot stitch them.
    override(f"data/{PACK}/tags/items/finished_leather.json", {
        "replace": True, "values": [*LEATHERS, {"id": "#forge:leather", "required": False}],
        "remove": [f"{TANNING}:binding_leather", f"{TANNING}:leather_scraps"],
    })
    tag(TANNING, "leather_ingredients", list(LEATHERS))
    tag(TANNING, "bark_sources", [{"id": "#tfc:logs", "required": False}], "blocks")
    tag(TANNING, "tannin_sources", ["farmersdelight:tree_bark"])

    disable = ["tfc:barrel/tannin", "tfc:crafting/sandpaper"]
    with ZipFile(tfc) as source:
        disable.extend(n.removeprefix("data/tfc/recipes/").removesuffix(".json")
                       for n in source.namelist() if re.fullmatch(r"data/tfc/recipes/[^/]+_cut\.json", n))
        parchment = json.loads(source.read("data/tfc/recipes/crafting/parchment.json"))
    disable = [value if ":" in value else f"tfc:{value}" for value in disable]
    for size in SIZES:
        disable.extend(f"tfc:barrel/{size}_{step}" for step in ("leather", "prepared_hide", "soaked_hide"))
        disable.extend((f"tfc:scraping/{size}_soaked_hide", f"tfc:crafting/{size}_treated_hide"))
    disable.extend(f"tfc:leather_knapping/{name}"
                   for name in ("helmet", "chestplate", "leggings", "boots", "horse_armor", "saddle"))
    disable.extend(f"minecraft:{name}" for name in (
        "leather_helmet", "leather_chestplate", "leather_leggings", "leather_boots", "leather_horse_armor"))
    override("data/tfc/tags/items/leather_knapping.json", {"replace": True, "values": []})
    parchment["ingredients"][0] = [
        {"item": "tfc:treated_hide"}, {"tag": f"{TANNING}:finished_leather"},
        {"tag": f"{PACK}:finished_leather"},
    ]
    override("data/tfc/recipes/crafting/parchment.json", parchment)

    armor = epic_knights_leather(mods)
    for item, routes in sorted(armor.items()):
        for source_id, leather, auxiliary, seam, conditions in routes:
            # Reuse the original recipe ID so alternate recipes cannot bypass sewing.
            namespace, path = source_id.split(":", 1)
            converted = {
                "type": f"{TANNING}:leatherworking", "leather": leather,
                "auxiliary": auxiliary, "result": {"item": item}, "seam": f"{TANNING}:{seam}",
            }
            if conditions:
                converted["conditions"] = conditions
            override(f"data/{namespace}/recipes/{path}.json", converted)
    # Give the pack's canonical silver ingot a route to the fallback casting frames.
    with ZipFile(jewelry) as source:
        for frame in ("ring_blank", "amulet_frame", "bracelet_blank", "circlet_frame"):
            original = json.loads(source.read(f"data/{JEWELRY}/recipes/fallback/{frame}_silver.json"))
            original.pop("pattern")
            original.pop("key")
            original["type"] = "minecraft:crafting_shapeless"
            original["ingredients"] = [{"item": "magistuarmory:silver_ingot"}, {"item": "minecraft:clay_ball"}]
            original["conditions"] = [{"type": f"{JEWELRY}:fallback_blanks"}]
            recipe(f"silver_frames/{frame}", original)
    field_guide(tfc, overrides)
    hidden = ["tfc:sandpaper", *[f"tfc:gem/{gem}" for gem in TFC_GEMS]]
    hidden.extend(f"tfc:{size}_{step}_hide" for size in SIZES for step in ("soaked", "scraped", "prepared"))
    rules = "// TFC treatment and polishing are handled by the Immersive stations.\n"
    rules += "ServerEvents.recipes(event => {\n"
    rules += "  " + json.dumps(sorted(disable)) + ".forEach(id => event.remove({id: id}))\n})\n"
    overrides["server_scripts/zzzzzzzzzzzzzz_empirecraft_immersive.js"] = rules.encode()
    jei = "JEIEvents.hideItems(event => {\n  " + json.dumps(sorted(hidden)) + ".forEach(id => event.hide(id))\n})\n"
    overrides["client_scripts/empirecraft_immersive_legacy.js"] = jei.encode()
    return files, overrides, {"gem_sources": GEMS, "armor": sorted(armor), "disabled_recipes": sorted(disable)}


def field_guide(tfc, overrides):
    """Keep the existing links to the TFC chapters and replace the retired instructions."""
    messages = {
        "uk_ua": (
            "Необроблені самоцвіти з родовищ TFC можна одразу встановити на огранювальне колесо Immersive Jewelry.",
            "Рубін, сапфір, топаз, опал і пірит мають власні кольори та параметри огранювання. Старі огранені самоцвіти TFC теж приймає колесо.",
            "Вибери огранювання й оброби камінь. На ювелірному столі встанови його в оправу. Рецепти оправ і інструментів є в JEI та посібнику ювеліра.",
            "Порошки для склоробства, як і раніше, можна перемолоти на жорнах.",
            "Натягни сиру шкуру на раму Immersive Tanning і очисть ножем шкіряра. Підійдуть також шкури TFC і хутро інших модів.",
            "Наповни дубильний чан водою та додай кору. Опусти очищену шкуру в чан і дочекайся завершення дублення.",
            "Поверни вологу шкуру на раму. Вибери тип обробки, підготуй шкуру та залиш її сушитися.",
            "Візьми готову шкіру й шило. На столі шкіряра вибери викрійку, підготуй деталі та проший їх. Для деякого одягу Epic Knights потрібна ще й тканина.",
            "Шкіру дублять корою у чані Immersive Tanning. Дивись $(l:mechanics/leather_making)Обробку шкіри$().",
        ),
        "en_us": (
            "Mount rough TFC gemstones directly on the Immersive Jewelry Lapidary Wheel.",
            "Ruby, sapphire, topaz, opal and pyrite have their own colors and cutting properties. The wheel accepts previously cut TFC stones too.",
            "Choose a cut and work the stone. Set it in a metal frame at the Jeweler's Bench. JEI and the Jeweler's Handbook list the frames and tools.",
            "A quern still produces gem dust for glassmaking.",
            "Mount a raw hide on the Tanner's Rack and flesh it with a Tanner's Knife. The rack also accepts TFC hides and pelts from other mods.",
            "Fill the Tanner's Vat with water and add bark. Put in the fleshed hide and let it steep.",
            "Mount the wet tanned hide back on the rack. Choose a finish, treat the hide and let it dry.",
            "Bring finished leather and an awl to the Leatherworker's Bench. Pick a pattern, cut the pieces and stitch them. Some Epic Knights garments also need cloth.",
            "Use bark and water in the Immersive Tanning vat. See $(l:mechanics/leather_making)Leather Making$().",
        ),
    }
    with ZipFile(tfc) as archive:
        for locale, strings in messages.items():
            prefix = f"assets/tfc/patchouli_books/field_guide/{locale}/entries/mechanics/"
            for topic, icon, content in (
                ("gems", "immersive_jewelry:lapidary_wheel", strings[:4]),
                ("leather_making", "immersive_tanning:tanners_rack", strings[4:8]),
                ("barrels", None, strings[8:]),
            ):
                path = prefix + topic + ".json"
                entry = json.loads(archive.read(path))
                pages = [{"type": "patchouli:text", "text": text} for text in content]
                if topic == "barrels":
                    for index, page in enumerate(entry["pages"]):
                        if page.get("recipe") == "tfc:barrel/tannin":
                            entry["pages"][index] = dict(pages[0], anchor="tannin")
                else:
                    entry.update(icon=icon, pages=pages)
                    entry.pop("extra_recipe_mappings", None)
                overrides[path] = encode(entry)
