#!/usr/bin/env python3
"""Build the Empirecraft Weavers' Paradise jar from the pinned upstream release.

Only data-driven content is removed (recipes, block loot, the loot-box modifier).
Item models and textures stay in place so items that already sit in an existing
world keep rendering instead of turning into missing-texture cubes.
"""
import argparse
import hashlib
import json
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent

MODERN = {
    # 19th-20th century workwear and knitwear
    "jeans_cloth": "jeans are 19th-century workwear",
    "jeans_pant_leg": "jeans are 19th-century workwear",
    "jeans_spool": "jeans are 19th-century workwear",
    "pants_jeans": "jeans are 19th-century workwear",
    "cotton_hoodie": "hooded sweatshirt is 20th-century sportswear",
    "wool_hoodie": "hooded sweatshirt is 20th-century sportswear",
    "t_shirt": "t-shirt is 20th-century underwear that became outerwear",
    "tank_top": "tank top is 20th-century underwear",
    "cotton_crop_top_long_sleeved": "crop top is modern fashion",
    "overalls": "dungarees are 19th-century workwear",
    "ushanka": "ear-flap fur hat is a 20th-century military item",
    "thigh_highs_cotton": "modern hosiery",
    "thigh_highs_silk": "modern hosiery",
    "thigh_highs_wool": "modern hosiery",
    "spinning_jenny": "industrial-revolution machine, far past the medieval period",
}

FANTASY = {
    "chromatic_bloom_fruit": "fantasy plant",
    "chromatic_dust": "fantasy reagent",
    "starbloom_fruit": "fantasy plant",
    "witchroot_fruit": "fantasy plant",
    "witchroot_fruit_cooked": "fantasy plant",
}

FLAGS = [f"flag_{name}" for name in (
    "agender", "aroace", "aromantic", "asexual", "base", "bisexual", "demiboy",
    "demigender", "demigirl", "gay", "genderfluid", "genderqueer", "intersex",
    "lesbian", "nonbinary", "pansexual", "pride", "trans", "transfeminine",
    "transmasculine",
)]

DYED = ["dye_core"]

REMOVED_ITEMS = {**MODERN, **FANTASY, **{f: "modern pride flag" for f in FLAGS},
                 **{d: "modern tie-dye/pride colour core" for d in DYED}}

REMOVED_BLOCK_LOOT = ["spinning_jenny", "chromatic_bloom"]
REMOVED_LOOT_MODIFIERS = ["armor_lootbox_modifier"]


def result_items(data):
    out = data.get("result") or data.get("output")
    items = []
    if isinstance(out, dict):
        if out.get("item"):
            items.append(out["item"])
        items.extend(entry.get("item") for entry in out.get("items", []) if isinstance(entry, dict))
    elif isinstance(out, str):
        items.append(out)
    return [i.split(":")[-1] for i in items if i and i.startswith("weaversparadise:")]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--upstream", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--audit", type=Path, default=ROOT / "audit/removed.json")
    args = parser.parse_args()

    upstream_sha = hashlib.sha256(args.upstream.read_bytes()).hexdigest()
    with zipfile.ZipFile(args.upstream) as src:
        entries = {name: src.read(name) for name in src.namelist()}

    removed_recipes, kept_recipes, kept_items = [], [], set()
    for name, blob in list(entries.items()):
        if not (name.startswith("data/weaversparadise/recipes/") and name.endswith(".json")):
            continue
        data = json.loads(blob)
        produced = result_items(data)
        hit = [item for item in produced if item in REMOVED_ITEMS]
        if hit:
            removed_recipes.append({"path": name, "produces": hit,
                                    "reason": REMOVED_ITEMS[hit[0]]})
            del entries[name]
        else:
            kept_recipes.append(name)
            kept_items.update(produced)

    removed_loot = []
    for name, blob in list(entries.items()):
        if not name.startswith("data/weaversparadise/loot_tables/blocks/"):
            continue
        block = name.rsplit("/", 1)[1][:-5]
        if block in REMOVED_BLOCK_LOOT:
            removed_loot.append(name)
            del entries[name]

    removed_modifiers = []
    for modifier in REMOVED_LOOT_MODIFIERS:
        name = f"data/weaversparadise/loot_modifiers/{modifier}.json"
        if name in entries:
            removed_modifiers.append(name)
            del entries[name]

    global_list = "data/forge/loot_modifiers/global_loot_modifiers.json"
    if global_list in entries:
        data = json.loads(entries[global_list])
        before = len(data["entries"])
        data["entries"] = [e for e in data["entries"]
                           if e.split(":")[-1] not in REMOVED_LOOT_MODIFIERS]
        assert len(data["entries"]) < before
        entries[global_list] = (json.dumps(data, indent=2) + "\n").encode()

    dangling = sorted(kept_items & set(REMOVED_ITEMS))
    assert not dangling, ("kept recipe still produces a removed item", dangling)

    audit = {
        "upstream": args.upstream.name,
        "upstream_sha256": upstream_sha,
        "mod_version": "1.6.4.1",
        "removed_items": dict(sorted(REMOVED_ITEMS.items())),
        "removed_recipe_count": len(removed_recipes),
        "removed_recipes": sorted(removed_recipes, key=lambda row: row["path"]),
        "removed_block_loot": len(removed_loot),
        "removed_loot_modifiers": removed_modifiers,
        "kept_recipe_count": len(kept_recipes),
        "kept_plushies": ["player_plushie", "player_plushie_rename_token"],
    }
    args.audit.parent.mkdir(parents=True, exist_ok=True)
    args.audit.write_text(json.dumps(audit, indent=2, ensure_ascii=False) + "\n")

    entries["META-INF/empirecraft-weaversparadise.json"] = json.dumps({
        "based_on": args.upstream.name,
        "upstream_sha256": upstream_sha,
        "removed_recipe_count": len(removed_recipes),
        "removed_items": sorted(REMOVED_ITEMS),
        "removed_loot_modifiers": removed_modifiers,
        "note": "Textures and item models kept so existing worlds still render the items.",
    }, indent=2).encode()

    order = sorted(entries, key=lambda n: (n != "META-INF/MANIFEST.MF", n))
    args.out.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.out, "w", zipfile.ZIP_DEFLATED) as dst:
        for name in order:
            dst.writestr(name, entries[name])
    print(f"PASS patched {args.out.name}: {len(removed_recipes)} recipes removed, "
          f"{len(kept_recipes)} kept, {len(removed_loot)} block loot tables, "
          f"{len(removed_modifiers)} loot modifier")


if __name__ == "__main__":
    main()
