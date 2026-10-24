#!/usr/bin/env python3
"""Build original vector artwork into a JourneyMap 6.0.7 theme and installable client ZIP."""
import base64
import copy
import hashlib
import json
import math
from pathlib import Path
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parent
BUILD = ROOT / "build"
PAYLOAD = BUILD / "package/payload"
THEME_DIR = "EmpireCraftAtlas"
THEME_NAME = "EmpireCraft Atlas"
INK = "#422e22"
GOLD = "#b79252"
PAPER = "#eadcbb"

# Original, deliberately simple silhouettes: readable at Minecraft's small GUI sizes.
GLYPHS = {
    "castle": '<path d="M3 20V7h3v3h3V5h2v5h3V7h3v13ZM7 20v-5h5v5M2 20h18"/><path d="M10 5V2h6l-2 2 2 1"/>',
    "tower": '<path d="M6 21V7H4V3h3v2h3V3h3v2h3V3h3v4h-2v14ZM10 21v-5h3v5M10 10h3"/>',
    "port": '<path d="M12 3v14M8 7h8M6 12H3c0 5 4 8 9 8s9-3 9-8h-3M8 17l4 4 4-4"/><circle cx="12" cy="4" r="2"/>',
    "market": '<path d="M3 9h18l-3-5H6ZM5 9v12h14V9M9 21v-7h6v7M3 10q2 4 4 0 2 4 5 0 2 4 5 0 2 4 4 0"/>',
    "mine": '<path d="m4 20 11-11M4 7q8-6 16 5l-4 2Q13 6 4 7ZM4 21l-2-2"/>',
    "abbey": '<path d="M5 21V10l7-5 7 5v11ZM10 21v-6h4v6M12 1v5M9 3h6M4 21h16"/>',
    "camp": '<path d="m2 20 10-16 10 16ZM12 4v16M8 20l4-7 4 7M8 2l8 4"/>',
    "road": '<path d="M3 21c0-7 15-10 15-18M10 21C10 14 23 10 23 3M5 7l2 1m0 5 2 1m5-5 2 1"/>',
    "banner": '<path d="M6 22V2h13l-3 5 3 5H6"/>',
    "crown": '<path d="m3 6 4 4 5-7 5 7 4-4-3 12H6ZM6 21h12"/>',
    "bridge": '<path d="M2 20V9h20v11M2 13h20M7 20v-3a5 5 0 0 1 10 0v3M4 9V5m4 4V5m4 4V5m4 4V5m4 4V5"/>',
    "battle": '<path d="m4 3 13 14m-1-3 5 5-2 2-5-5M20 3 7 16m3-2-5 5-2-2 5-5"/>',
    "about": '<circle cx="12" cy="12" r="9"/><path d="M12 10v7m0-11v1"/>',
    "actions": '<path d="M6 21V3h13l-3 5 3 5H6"/>',
    "alert": '<path d="M12 2 3 6v7c0 4 9 9 9 9s9-5 9-9V6ZM12 7v7m0 3v1"/>',
    "animals": '<path d="m5 5 3 4 4-2 4 2 3-4-1 12-6 4-6-4ZM8 12h1m6 0h1m-5 4h2"/>',
    "automap": '<path d="m3 5 6-2 6 2 6-2v16l-6 2-6-2-6 2ZM9 3v16M15 5v16m-9-9 3-2 3 2 6-3"/>',
    "biome": '<path d="m12 2-7 9h4l-6 7h8v4h2v-4h8l-6-7h4Z"/>',
    "browser": '<circle cx="12" cy="12" r="9"/><path d="m15 7-2 7-5 3 2-7ZM12 2v2m0 16v2M2 12h2m16 0h2"/>',
    "caves": '<path d="m2 20 4-12 6-6 7 7 3 11ZM8 20v-5l4-5 4 5v5"/>',
    "close": '<path d="m6 6 12 12M6 18 18 6"/>',
    "colorpalette": '<path d="m4 18 12-14 4 4-14 12H3Zm11-12 3 3M3 22h17"/>',
    "day": '<circle cx="12" cy="12" r="4"/><path d="M12 1v3m0 16v3M1 12h3m16 0h3M4 4l2 2m12 12 2 2M4 20l2-2M18 6l2-2"/>',
    "delete": '<path d="M5 6h14M8 6V3h8v3M7 6l1 15h8l1-15M10 9v8m4-8v8"/>',
    "disable": '<circle cx="12" cy="12" r="9"/><path d="m6 6 12 12"/>',
    "follow": '<path d="m12 2 8 18-8-4-8 4Z"/>',
    "grid": '<path d="M3 3h18v18H3ZM3 9h18M3 15h18M9 3v18M15 3v18"/>',
    "keys": '<path d="M3 4h18v16H3ZM6 8h2m3 0h2m3 0h2M6 12h2m3 0h2m3 0h2M7 16h10"/>',
    "layers": '<path d="m2 7 10-5 10 5-10 5ZM3 12l9 5 9-5M3 17l9 5 9-5"/>',
    "monsters": '<path d="M5 14V7l4-4h6l4 4v7l-4 2v5H9v-5ZM8 9l2 2m6-2-2 2m-4 7v3m4-3v3"/>',
    "night": '<path d="M18 3A9 9 0 1 0 21 17 10 10 0 0 1 18 3Z"/>',
    "options": '<circle cx="12" cy="12" r="4"/><path d="M10 2h4l1 4 4 1 3 3v4l-4 1-1 4-3 3h-4l-1-4-4-1-3-3v-4l4-1 1-4Z"/>',
    "pets": '<ellipse cx="12" cy="16" rx="5" ry="4"/><circle cx="5" cy="9" r="2"/><circle cx="10" cy="5" r="2"/><circle cx="16" cy="6" r="2"/><circle cx="20" cy="11" r="2"/>',
    "players": '<circle cx="12" cy="7" r="4"/><path d="M4 21v-4c0-6 16-6 16 0v4Z"/>',
    "reset": '<path d="M4 10a8 8 0 1 1 1 9M4 3v7h7"/>',
    "savemap": '<path d="M3 3h6l3 2 3-2h6v17h-6l-3 2-3-2H3ZM12 5v17M6 7h3m6 0h3M6 11h3m6 0h3"/>',
    "search": '<circle cx="10" cy="10" r="6"/><path d="m15 15 6 6"/>',
    "server": '<path d="M12 2 3 6v7c0 4 9 9 9 9s9-5 9-9V6Z"/><path d="m7 12 3 3 7-7"/>',
    "topo": '<path d="m1 21 7-16 4 8 4-11 7 19ZM5 12l3 2 2-2m4-4 2 2 2-2"/>',
    "villagers": '<path d="m2 10 10-8 10 8M5 9v12h14V9M10 21v-7h4v7"/>',
    "waypoints": '<path d="M6 22V2h13l-3 5 3 5H6"/>',
    "zoomin": '<path d="M4 12h16M12 4v16"/>',
    "zoomout": '<path d="M4 12h16"/>',
}
WAYPOINTS = ["castle", "tower", "port", "market", "mine", "abbey", "camp", "road", "banner", "crown", "bridge", "battle"]


def svg(body, width, height, view="0 0 24 24"):
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="{view}">{body}</svg>'


def save_svg(path, body, width, height, view="0 0 24 24"):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.with_suffix(".svg").write_text(svg(body, width, height, view), encoding="utf-8")


def color(value, alpha=1): return {"color": value, "alpha": alpha}
def image(w, h): return {"width": w, "height": h, **color("#ffffff")}
def label():
    return {"margin": 3, "background": color(PAPER, .94), "foreground": color(INK), "highlight": color("#7c332e"), "shadow": False}


def theme():
    control = {"useThemeImages": True, "width": 24, "height": 24, "prefix": "", "tooltipOnStyle": "§f", "tooltipOffStyle": "§f", "tooltipDisabledStyle": "§7"}
    for key in ["On", "Off", "HoverOn", "HoverOff", "Disabled"]:
        control["icon" + key] = color("#7c332e" if "Hover" in key else "#8b8069" if key == "Disabled" else INK)
        control["button" + key] = color("#ffffff")
    common = {"margin": 4, "labelTop": label(), "labelBottom": label(), "labelTopInside": False, "labelBottomInside": False,
              "compassLabel": label(), "compassPoint": image(12, 12), "compassPointLabelPad": 2, "compassPointOffset": -1.0,
              "compassShowNorth": True, "compassShowSouth": True, "compassShowEast": True, "compassShowWest": True,
              "waypointOffset": -3, "reticle": color(INK, .28), "reticleHeading": color("#7c332e", .55),
              "reticleThickness": 1, "reticleHeadingThickness": 1.5, "reticleOffsetOuter": -4, "reticleOffsetInner": -4,
              "frame": color("#ffffff"), "prefix": ""}
    circle = {**copy.deepcopy(common), "rotates": False, "rim256": image(256, 256), "mask256": image(256, 256), "rim512": image(512, 512), "mask512": image(512, 512)}
    square = copy.deepcopy(common)
    for key, w, h in [("topLeft",16,16),("top",2,16),("topRight",16,16),("right",16,2),("bottomRight",16,16),("bottom",2,16),("bottomLeft",16,16),("left",16,2)]: square[key] = image(w, h)
    return {"schema": 2, "author": "EmpireCraft", "name": THEME_NAME, "directory": THEME_DIR,
            "container": {"toolbar": {"horizontal": {"useThemeImages": True, "prefix": "h", "margin": 8, "padding": 2, "begin": image(8,28), "inner": image(28,28), "end": image(8,28)},
                                       "vertical": {"useThemeImages": True, "prefix": "v", "margin": 4, "padding": 2, "begin": image(28,8), "inner": image(28,28), "end": image(28,8)}}},
            "control": {"button": copy.deepcopy(control), "toggle": copy.deepcopy(control)},
            "fullscreen": {"background": color("#c4b08a"), "statusLabel": label()}, "icon": image(20,20),
            "minimap": {"circle": circle, "square": square}}


def artwork(base):
    for name, glyph in GLYPHS.items():
        save_svg(base / "icon" / (name + ".png"), '<g fill="none" stroke="white" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">'+glyph+'</g>', 48,48)
    for kind in ["button", "toggle"]:
        for state, fill, edge in [("off", "#dfcda4", "#947341"), ("on", "#f0e1bf", "#8c5c2c"), ("hover", "#faeccb", "#7c332e"), ("disabled", "#c9bea6", "#a99b7e")]:
            body = f'<rect x="1" y="1" width="22" height="22" rx="2" fill="{fill}" stroke="{edge}"/><path d="M4 3h16M3 4v16" stroke="#fff3d6" fill="none"/><path d="M4 21h16M21 4v16" stroke="{edge}" fill="none"/>'
            if state == "on": body += f'<path d="m10 22 2-2 2 2" fill="{edge}"/>'
            save_svg(base / "control" / f"{kind}_{state}.png", body,48,48)
    for orientation in ["h", "v"]:
        for part in ["begin", "inner", "end"]:
            w, h = ((8,28) if part != "inner" else (28,28)) if orientation == "h" else ((28,8) if part != "inner" else (28,28))
            horizontal = '<path d="M0 1h100M0 3h100M0 25h100M0 27h100"/>'
            vertical = '<path d="M1 0v100M3 0v100M25 0v100M27 0v100"/>'
            body=f'<rect width="{w}" height="{h}" fill="{INK}"/><g fill="none" stroke="{GOLD}" stroke-width=".6">'+(horizontal if orientation=="h" else vertical)+'</g>'
            if part != "inner": body += f'<circle cx="{w/2}" cy="{h/2}" r="1.5" fill="{GOLD}"/>'
            save_svg(base / "container" / f"{orientation}toolbar_{part}.png",body,w*2,h*2,f"0 0 {w} {h}")
    ring = f'<circle cx="128" cy="128" r="122" fill="none" stroke="{INK}" stroke-width="11"/><circle cx="128" cy="128" r="125" fill="none" stroke="{GOLD}" stroke-width="1.8"/><circle cx="128" cy="128" r="118" fill="none" stroke="{GOLD}" stroke-width="1.6"/>'
    for degree in range(0,360,5):
        a = math.radians(degree); long=degree%30==0
        r1,r2=119.5,124 if long else 122
        ring += f'<path d="M{128+r1*math.sin(a):.3f} {128-r1*math.cos(a):.3f}L{128+r2*math.sin(a):.3f} {128-r2*math.cos(a):.3f}" stroke="{GOLD}" stroke-width=".7"/>'
    for size in [256,512]:
        save_svg(base / "minimap/circle" / f"rim_{size}.png",ring,size,size,"0 0 256 256")
        save_svg(base / "minimap/circle" / f"mask_{size}.png",'<circle cx="128" cy="128" r="117.5" fill="white"/>',size,size,"0 0 256 256")
    compass=f'<path d="m12 1 3 7 8 4-8 3-3 8-3-8-8-3 8-4Z" fill="{PAPER}" stroke="{INK}" stroke-width="1"/><path d="m12 2 0 10-3-4Z" fill="#7c332e"/><path d="m12 12 3 3-3 7Z" fill="{GOLD}"/>'
    for shape in ["circle","square"]: save_svg(base/"minimap"/shape/"compass_point.png",compass,24,24)
    for part in ["top", "bottom", "left", "right", "topleft", "topright", "bottomleft", "bottomright"]:
        w,h=(2,16) if part in ["top","bottom"] else (16,2) if part in ["left","right"] else (16,16)
        body=f'<rect width="{w}" height="{h}" fill="{INK}"/>'
        if part in ["top","bottom"]: body+=f'<path d="M0 2h2M0 13h2" stroke="{GOLD}"/>'
        elif part in ["left","right"]: body+=f'<path d="M2 0v2M13 0v2" stroke="{GOLD}"/>'
        else: body+=f'<rect x="2" y="2" width="11" height="11" fill="none" stroke="{GOLD}"/><path d="m8 4 4 4-4 4-4-4Z" fill="{GOLD}"/>'
        save_svg(base/"minimap/square"/(part+".png"),body,w,h,f"0 0 {w} {h}")


def zip_files(target, files):
    target.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(target,"w") as archive:
        for name, data in sorted(files.items()):
            info=zipfile.ZipInfo(name,(2026,10,23,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16
            archive.writestr(info,data)


def json_file(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")


def main():
    if not shutil.which("inkscape"): raise SystemExit("Inkscape is required to rasterize the original SVG artwork.")
    if BUILD.exists(): shutil.rmtree(BUILD)
    base=PAYLOAD/"journeymap/icon/theme"/THEME_DIR
    artwork(base)
    icons=BUILD/"icons"
    for name in WAYPOINTS:
        save_svg(icons/(name+".png"),'<g fill="none" stroke="white" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">'+GLYPHS[name]+'</g>',16,16)
    subprocess.run(["inkscape","--export-type=png",*map(str,sorted(BUILD.rglob("*.svg")))],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    json_file(base/(THEME_DIR+".theme2.json"),theme())
    json_file(PAYLOAD/"journeymap/icon/theme/default.theme.config",{"directory":THEME_DIR,"filename":THEME_DIR,"name":THEME_NAME})
    pack={"pack.mcmeta":json.dumps({"pack":{"pack_format":15,"description":"EmpireCraft | Medieval Atlas waypoint icons"}},ensure_ascii=False).encode()}
    for name in WAYPOINTS: pack[f"assets/journeymap/textures/waypoint/icon/empirecraft/{name}.png"]=(icons/(name+".png")).read_bytes()
    for lang, name, tip in [("uk_ua","Імперський атлас","Замки, порти, ринки та шляхи EmpireCraft"),("en_us","Imperial Atlas","Castles, ports, markets and roads of EmpireCraft"),("ru_ru","Имперский атлас","Замки, порты, рынки и дороги EmpireCraft")]:
        pack[f"assets/journeymap/lang/{lang}.json"]=json.dumps({"waypoint.icon.set.empirecraft.name":name,"waypoint.icon.set.empirecraft.name.tooltip":tip},ensure_ascii=False).encode()
    zip_files(PAYLOAD/"resourcepacks/EmpireCraft-Medieval-Icons-1.0.0.zip",pack)
    land=["#e7d6ad","#e8d8b1","#e9dab5","#eadbb7","#eaddba","#e7d8b3","#e4d3ac","#dfcda4","#d9c59a","#d3be91","#cdb789","#c8b184","#c1a97c","#baa176","#b49a70","#ae946c","#b19a75","#b7a17e","#bea987","#c5b390","#cdbd9b","#d5c6a8","#dcd0b5","#e2d9c1","#e7dfca","#ebe4d2","#eee8d8","#f0ebdd","#f2eee1","#f4f0e5","#f5f2e9","#f7f4ec"]
    water=["#435a61","#485f66","#4e666d","#546d74","#5b747b","#637c83","#6b848b","#748d93","#7e979c","#89a1a5","#94acaf","#a0b7b8","#aec3c2","#bdcfcc","#cddbd4","#dfe5dc"]
    settings={"journeymap.core.config":{"themeName":THEME_NAME},"journeymap.fullmap.config":{"preferredMapType":"topo","showGrid":False,"showKeys":False},
              "journeymap.minimap.config":{"shape":"Circle","preferredMapType":"topo","showGrid":False,"showCompass":True,"showReticle":False},
              "journeymap.minimap2.config":{"preferredMapType":"topo","showGrid":False,"showCompass":True,"showReticle":False},
              "journeymap.topo.config":{"showContour":True,"landContour":"#9b835e","waterContour":"#4a6970","land":",".join(land),"water":",".join(water)}}
    json_file(BUILD/"package/settings.json",settings)
    # Source SVGs are build intermediates; the theme loader expects only the rendered PNGs.
    for source in PAYLOAD.rglob("*.svg"): source.unlink()
    for name in ["Apply-Atlas.ps1","Install-Windows.cmd","README.md","LICENSE"]: shutil.copyfile(ROOT/name,BUILD/"package"/name)
    if (ROOT/"preview.png").exists(): shutil.copyfile(ROOT/"preview.png",BUILD/"package/preview.png")
    output=ROOT/"dist/EmpireCraft-Medieval-Atlas-1.0.1.zip"
    zip_files(output,{str(p.relative_to(BUILD/"package")).replace("\\","/"):p.read_bytes() for p in (BUILD/"package").rglob("*") if p.is_file()})
    output.with_suffix(".zip.sha256").write_text(hashlib.sha256(output.read_bytes()).hexdigest()+"  "+output.name+"\n")
    print(output)
    print("Theme PNGs:",len(list(base.rglob("*.png"))),"| Waypoint icons:",len(WAYPOINTS))


if __name__=="__main__": main()
