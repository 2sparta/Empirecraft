#!/usr/bin/env python3
"""Verify the built Makers' Marks jar: wiring, registration and remapping."""
import json
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent
JAR = REPO / "mods/empirecraft-provenance-1.0.0.jar"
SERVER = REPO / "server/mods/empirecraft-provenance-1.0.0.jar"
failures = []


def check(ok, message):
    print(("PASS " if ok else "FAIL ") + message)
    if not ok:
        failures.append(message)


def main():
    check(JAR.is_file(), "jar installed in mods/")
    check(JAR.is_file() and JAR.read_bytes() == SERVER.read_bytes(),
          "jar identical on client and server")
    with zipfile.ZipFile(JAR) as jar:
        check(jar.testzip() is None, "archive intact")
        names = set(jar.namelist())
        toml = jar.read("META-INF/mods.toml").decode()
        check('modId="empirecraft_provenance"' in toml and 'version="1.0.0"' in toml,
              "mods.toml declares empirecraft_provenance 1.0.0")
        events = jar.read("ua/empirecraft/provenance/Provenance$Events.class")
        check(b"Lnet/minecraftforge/fml/common/Mod$EventBusSubscriber;" in events,
              "handler class is registered on the Forge bus (@Mod.EventBusSubscriber)")
        check(b"PlayerEvent$ItemCraftedEvent" in events and b"ItemTooltipEvent" in events,
              "handler class references both event types")
        check(b"Lnet/minecraftforge/eventbus/api/SubscribeEvent;" in events,
              "handler methods are annotated with @SubscribeEvent")
        check(b"(Lnet/minecraftforge/event/entity/player/PlayerEvent$ItemCraftedEvent;)V" in events
              and b"(Lnet/minecraftforge/event/entity/player/ItemTooltipEvent;)V" in events,
              "one handler takes ItemCraftedEvent and one takes ItemTooltipEvent")
        check(b"getCrafting" in events and b"ua/empirecraft/provenance/Mark" in events,
              "craft handler calls Mark.stamp on the crafted stack")
        mark = jar.read("ua/empirecraft/provenance/Mark.class")
        check(b"m_128359_" in mark and b"m_41783_" in mark,
              "Mark.class calls remapped game methods (putString/getOrCreateTag)")
        lang = json.loads(jar.read("assets/empirecraft_provenance/lang/en_us.json"))
        for key in ("tooltip.empirecraft_provenance.maker", "tooltip.empirecraft_provenance.guild",
                    "tooltip.empirecraft_provenance.day", "tooltip.empirecraft_provenance.origin",
                    "guild.empirecraft_provenance.smiths", "guild.empirecraft_provenance.weavers"):
            check(key in lang, f"lang key present: {key}")
        for code in ("uk_ua", "ru_ru"):
            data = json.loads(jar.read(f"assets/empirecraft_provenance/lang/{code}.json"))
            check(set(lang) == set(data), f"{code} translations cover every key")
    print()
    print(f"{'FAILED' if failures else 'ALL PASSED'} ({len(failures)} failure(s))")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
