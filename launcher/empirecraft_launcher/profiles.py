"""Client visuals only; the server remains authoritative for simulation/rules."""
from pathlib import Path
import json
import re
import psutil
from .security import atomic_write

PROFILES = {
    "potato": dict(title="Potato", description="Слабкий ноутбук · мінімум ефектів", distance=5, simulation=4, entities=0.5, particles=2, graphics=0, mipmap=0, blend=0, ao=False, shadows=False, clouds="false", fps=60, reduced=True),
    "balanced": dict(title="Balanced", description="Чіткий світ · помірне навантаження", distance=8, simulation=5, entities=0.75, particles=1, graphics=1, mipmap=2, blend=2, ao=True, shadows=True, clouds="fast", fps=90, reduced=False),
    "high": dict(title="High", description="Більше деталей · Oculus для шейдерів", distance=12, simulation=6, entities=1.0, particles=0, graphics=1, mipmap=4, blend=3, ao=True, shadows=True, clouds="true", fps=120, reduced=False),
}

def recommended_ram(profile: str, total: int | None = None) -> int:
    total = psutil.virtual_memory().total // 1024**2 if total is None else total
    budget = max(2048, total - max(2560, int(total * .30)))
    return int(min(budget, {"potato": 4096, "balanced": 6144, "high": 8192}[profile]) // 256 * 256)

def lso_config(reduced: bool) -> bytes:
    flag = "false" if reduced else "true"
    threshold = "-1000.0" if reduced else "10.0"
    return (f'[hud.temperature]\n"Temperature Display Mode" = "SYMBOL"\n"Heat Temperature Overlay" = {flag}\n"Cold Temperature Overlay" = {flag}\n"Breathing Sound Enabled" = {flag}\n"Cold Breath Temperature Threshold" = {threshold}\n').encode()

def update_lso(path: Path, reduced: bool):
    defaults = lso_config(reduced).decode().splitlines()
    replacements = {line.split(" = ", 1)[0]: line for line in defaults[1:]}
    original = path.read_text(encoding="utf-8").splitlines() if path.is_file() else []
    output, pending, target, found = [], dict(replacements), False, False
    for line in original:
        if re.match(r"^\s*\[", line):
            if target:
                output.extend(pending.values())
                pending.clear()
            target = line.strip() == "[hud.temperature]"
            found = found or target
        if target and "=" in line:
            key = line.split("=", 1)[0].strip()
            if key in replacements:
                line = replacements[key]
                pending.pop(key, None)
        output.append(line)
    if target:
        output.extend(pending.values())
    if not found:
        output.extend(["", *defaults])
    atomic_write(path, ("\n".join(output) + "\n").encode())

def apply_options(game: Path, profile: str, overrides: dict):
    p = PROFILES[profile]
    distance = max(3, min(20, int(overrides.get("distance", p["distance"]))))
    fps = max(30, min(240, int(overrides.get("fps", p["fps"]))))
    reduced = bool(overrides.get("reduced", p["reduced"]))
    values = dict(renderDistance=distance, simulationDistance=p["simulation"], entityDistanceScaling=p["entities"], particles=p["particles"], graphicsMode=p["graphics"], mipmapLevels=p["mipmap"], biomeBlendRadius=p["blend"], ao=p["ao"], entityShadows=p["shadows"], renderClouds=p["clouds"], maxFps=fps, enableVsync=False)
    path = game / "options.txt"
    existing = {}
    if path.is_file():
        for line in path.read_text(encoding="utf-8").splitlines():
            if ":" in line:
                key, value = line.split(":", 1)
                existing[key] = value
    existing.setdefault("lang", "uk_ua")
    # Keep TFC place-item V free; preserve an explicit existing player binding.
    existing.setdefault("key_key.knockedback.carry", "key.keyboard.right.control")
    existing.update({k: str(v).lower() for k, v in values.items()})
    atomic_write(path, ("\n".join(f"{k}:{v}" for k, v in existing.items()) + "\n").encode())
    update_lso(game / "config/legendarysurvivaloverhaul/legendarysurvivaloverhaul-client.toml", reduced)
    iris = game / "config/iris.properties"
    if profile != "high" or not iris.exists():
        atomic_write(iris, b"enableShaders=false\n")
    # Native client guard reads only this strict enum, never an arbitrary server URL.
    atomic_write(game / "config/empirecraft-launcher-profile.json", json.dumps({"profile": profile, "reducedEffects": reduced, "deathEffects": bool(overrides.get("death_effects", True))}).encode())
