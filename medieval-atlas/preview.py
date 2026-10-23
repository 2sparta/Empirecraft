#!/usr/bin/env python3
"""Render an explicitly labelled asset preview; this is not an in-game screenshot."""
import base64
import math
from pathlib import Path
import subprocess
from build import ROOT, PAYLOAD, GLYPHS, WAYPOINTS, INK, GOLD, PAPER


def glyph(name,x,y,size,color=INK):
    return f'<g transform="translate({x} {y}) scale({size/24})" fill="none" stroke="{color}" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">{GLYPHS[name]}</g>'


def text(x,y,label,size=16,color=INK,extra=""):
    return f'<text x="{x}" y="{y}" font-size="{size}" fill="{color}" {extra}>{label}</text>'


def png(path,x,y,w,h):
    data=base64.b64encode(path.read_bytes()).decode()
    return f'<image xlink:href="data:image/png;base64,{data}" x="{x}" y="{y}" width="{w}" height="{h}"/>'


def main():
    body=f'<rect width="1440" height="1000" fill="#eee5cf"/><rect x="22" y="22" width="1396" height="956" fill="none" stroke="{GOLD}"/><rect x="29" y="29" width="1382" height="942" fill="none" stroke="{GOLD}" stroke-opacity=".4"/>'
    body+=text(60,74,"E M P I R E C R A F T",16,extra='font-family="DejaVu Sans" letter-spacing="3"')
    body+=text(58,136,"Імперський атлас",54,extra='font-family="DejaVu Serif"')
    body+=text(61,170,"Пергамент. Чорнило. Бронза. Землі вашої держави.",17,"#7c6851")
    body+=f'<path d="M60 194h1320" stroke="{GOLD}"/>'
    # Demonstration terrain in the actual topographic palette, with matching border opacity.
    body+='<defs><clipPath id="map"><rect x="60" y="222" width="914" height="590" rx="1"/></clipPath><clipPath id="mini"><circle cx="1190" cy="384" r="108"/></clipPath></defs>'
    land='<rect width="1000" height="650" fill="#94acaf"/>'
    coast="M-10-10H1010V570L910 530 850 570 790 520 710 550 660 520 620 560 570 490 510 485 470 420 410 430 350 370 280 390 230 330 140 300 90 240 0 270Z"
    land+=f'<path d="{coast}" fill="#eadbb7" stroke="#4a6970" stroke-width="2"/>'
    land+='<path d="M40 0 60 100 140 145 200 110 250 160 280 220 350 220 420 270 485 245 530 300 600 320 655 380 760 355 830 420 1000 385V0Z" fill="#dfcda4"/>'
    land+='<path d="M190 0 250 60 290 150 360 140 405 210 470 185 550 250 610 245 665 310 730 270 790 345 860 300 960 350 1000 340V0Z" fill="#d3be91"/>'
    land+='<path d="M390 0 350 80 460 120 520 100 590 180 660 160 730 210 790 150 880 230 1000 190V0Z" fill="#c1a97c"/>'
    land+='<path d="M480 0 530 60 620 40 690 115 770 70 860 140 940 80 1000 110V0Z" fill="#e2d9c1"/>'
    for i in range(7):
        y=50+i*36
        points=' '.join(f'{x},{y+math.sin(x/90+i*.8)*19+x*.22:.1f}' for x in range(0,1001,20))
        land+=f'<polyline points="{points}" fill="none" stroke="#9b835e" stroke-width=".7" stroke-opacity=".5"/>'
    land+='<path d="M580 0Q500 110 560 180T490 300Q480 360 520 395T570 490" fill="none" stroke="#89a1a5" stroke-width="9"/>'
    land+='<path d="M185 0 205 130 360 205 405 340 510 485 570 490 620 560 660 520 710 550 790 520 850 570 910 530 1000 570V0Z" fill="#7c332e" fill-opacity=".12" stroke="#7c332e" stroke-width="1.5"/>'
    land+='<path d="M205 130 360 205 405 340 510 485" fill="none" stroke="#7c332e" stroke-width="1.5"/>'
    for x,y in [(290,260),(680,220),(760,390),(130,200),(390,110)]:
        for dx in range(0,55,18): land+=f'<path d="m{x+dx} {y+12} 6-12 6 12m-6-12v18" stroke="#7f805e" stroke-width="1" fill="none"/>'
    body+=f'<g clip-path="url(#map)"><g id="terrain" transform="translate(60 222) scale(.914 .908)">{land}</g>'
    body+=text(520,626,"ВІЗАНТІЙСЬКІ ЗЕМЛІ",22,"#743f35",extra='font-family="DejaVu Serif" letter-spacing="2"')
    body+=text(124,380,"ПІВНІЧНІ ВОЛОДІННЯ",17,"#6d704c",extra='font-family="DejaVu Serif" letter-spacing="1"')
    body+=text(160,736,"Понтійське море",22,"#4a6970",extra='font-family="DejaVu Serif" font-style="italic" letter-spacing="3"')
    for name,x,y,label in [("crown",620,536,"Царгород"),("castle",312,408,"Північна фортеця"),("port",458,643,"Старий порт"),("market",752,450,"Торговий двір")]:
        body+=f'<circle cx="{x+12}" cy="{y+12}" r="18" fill="{PAPER}" fill-opacity=".92"/>'+glyph(name,x,y,24)
        body+=text(x-18,y+48,label,15,extra='font-family="DejaVu Serif"')
    body+='</g>'
    body+=f'<rect x="60" y="222" width="914" height="590" fill="none" stroke="{INK}" stroke-width="4"/><rect x="65" y="227" width="904" height="580" fill="none" stroke="{GOLD}"/>'
    # Actual native toolbar PNGs with their matching, ink-tinted vector icons.
    base=PAYLOAD/'journeymap/icon/theme/EmpireCraftAtlas'
    body+=f'<rect x="345" y="237" width="330" height="45" rx="3" fill="{INK}" stroke="{GOLD}"/>'
    for i,name in enumerate(['zoomin','zoomout','follow','topo','layers','waypoints','options','close']):
        x=354+i*40;body+=png(base/'control/button_off.png',x,243,32,32)+glyph(name,x+3,y=246,size=26)
    body+=f'<rect x="999" y="222" width="381" height="590" fill="#e7dcc3" stroke="{GOLD}" stroke-opacity=".5"/>'
    body+=text(1027,259,"КОМПАС МАНДРІВНИКА",13,extra='font-family="DejaVu Sans" letter-spacing="1.6"')
    body+='<g clip-path="url(#mini)"><g transform="translate(1080 280) scale(.3)">'+land+'</g></g>'
    body+=png(base/'minimap/circle/rim_256.png',1072,266,236,236)
    body+=text(1184,276,"N",13)+text(1184,506,"S",13)+text(1055,390,"W",13)+text(1312,390,"E",13)
    body+=glyph('follow',1178,374,24,'#7c332e')
    body+=text(1027,553,"ПОЗНАЧКИ ХРОНІСТА",13,extra='font-family="DejaVu Sans" letter-spacing="1.6"')
    labels=['Замок','Вежа','Порт','Ринок','Копальня','Монастир','Табір','Шлях','Прапор','Столиця','Міст','Битва']
    for i,(name,label) in enumerate(zip(WAYPOINTS,labels)):
        x=1020+i%4*88;y=580+i//4*72
        body+=glyph(name,x+22,y,24)+text(x+35,y+45,label,11,extra='text-anchor="middle" font-family="DejaVu Sans"')
    body+=text(60,858,"01  ШТАТНА ТЕМА JOURNEYMAP",13,extra='font-family="DejaVu Sans" letter-spacing="1"')
    body+=text(60,887,"Пергаментні кнопки та бронзове обрамлення",17)
    body+=text(580,858,"02  ТОПОГРАФІЧНА ПАЛІТРА",13,extra='font-family="DejaVu Sans" letter-spacing="1"')
    body+=text(580,887,"Теплий рельєф і приглушені кольори води",17)
    body+=f'<path d="M60 918h1320" stroke="{GOLD}"/>'
    body+=text(60,947,"Ескіз із готових текстур теми · Демонстраційна місцевість · Не скриншот гри",13,"#7c6851",extra='font-family="DejaVu Sans"')
    out=ROOT/'preview.svg'
    out.write_text(f'<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="1440" height="1000" viewBox="0 0 1440 1000" font-family="DejaVu Serif">{body}</svg>',encoding='utf-8')
    subprocess.run(['inkscape',str(out),'--export-type=png','--export-filename='+str(ROOT/'preview.png')],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    print(ROOT/'preview.png')


if __name__=='__main__':main()
