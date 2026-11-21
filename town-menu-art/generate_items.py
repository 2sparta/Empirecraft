#!/usr/bin/env python3
"""Original icon art as native item models; preview retains vanilla chest coordinates."""
from pathlib import Path
from PIL import Image,ImageDraw
import json,textwrap
from generate_assets import tile,font
ROOT=Path(__file__).resolve().parent
RES=ROOT/'src/main/resources'
CAT=json.loads((ROOT/'icons.json').read_text())

def extra(name):
    scale=4;im=Image.new('RGBA',(128,128),'#122b29');d=ImageDraw.Draw(im)
    def box(p,fill=None,outline=None,w=1):d.rectangle(tuple(round(x*scale) for x in p),fill,outline,width=round(w*scale))
    def line(p,fill,w=1.5):d.line([(round(x*scale),round(y*scale)) for x,y in p],fill,width=round(w*scale),joint='curve')
    def poly(p,fill):d.polygon([(round(x*scale),round(y*scale)) for x,y in p],fill)
    box((1,1,30,30),outline='#9c824c',w=.5);line([(2,2),(29,2)],'#e4c887',.5)
    for x,y in [(3,3),(28,3),(3,28),(28,28)]:box((x,y,x+.5,y+.5),'#ead4a4')
    if name=='recruitment':
        for reflect in (False,True):
            p=[(8,7),(11,8),(23,22),(21,24),(8,10)]
            if reflect:p=[(32-x,y) for x,y in p]
            poly(p,'#f7ebcd' if not reflect else '#b4c8b6')
            p=[(18,21),(22,17)] if not reflect else [(14,21),(10,17)];line(p,'#d9b76f',2)
            p=[(22,22),(26,26)] if not reflect else [(10,22),(6,26)];line(p,'#d9b76f',2)
    else:
        poly([(9,14),(10,9),(15,6),(21,8),(24,14),(23,22),(19,25),(10,23)],'#b8cbb9')
        box((14,15,24,21),'#122b29');line([(17,14),(17,23)],'#d9b76f',1.5);line([(10,24),(23,24)],'#d9b76f',1.5)
    return im.resize((32,32),Image.Resampling.LANCZOS)

def previews(icons):
    out=ROOT/'build/previews-1.1';out.mkdir(parents=True,exist_ok=True)
    image=Image.new('RGB',(1168,900),'#132321');d=ImageDraw.Draw(image)
    d.text((40,24),'МІСЬКІ ІКОНКИ · EMPIRECRAFT',font=font(28,True),fill='#e8d6ab')
    d.text((40,68),'Нові предметні значки у звичайному вікні скрині',font=font(18),fill='#b7c9b8')
    sets=[(40,'/t menu','Місто · Назва міста',{4:'town',10:'people',11:'land',12:'treasury',13:'vassals',14:'treaty',15:'union',16:'crown',28:'dynasty',30:'offers',32:'provisions',34:'history',45:'back',47:'treaty',48:'map',49:'refresh',50:'recruitment',51:'garrison',53:'close'}),(608,'/t provision','Провізія: Назва міста',{45:'storage',47:'wheat',49:'satisfied',53:'refresh'})]
    for left,command,title,slots in sets:
        panel=Image.new('RGB',(176,222),'#c6c6c6');p=ImageDraw.Draw(panel)
        p.rectangle((0,0,175,221),outline='#373737');p.line((1,1,174,1,174,220),fill='#fff',width=1)
        for row in range(10):
            for col in range(9):
                if row<6:y=18+row*18
                elif row<9:y=140+(row-6)*18
                else:y=198
                x=8+col*18
                p.rectangle((x-1,y-1,x+16,y+16),fill='#8b8b8b');p.line((x-1,y+16,x-1,y-1,x+16,y-1),fill='#373737');p.line((x,y+16,x+16,y+16,x+16,y),fill='#fff')
        for slot,name in slots.items():panel.paste(icons[name].resize((16,16),Image.Resampling.LANCZOS),(8+(slot%9)*18,18+(slot//9)*18))
        image.paste(panel.resize((528,666),Image.Resampling.NEAREST),(left,136))
        d.text((left,108),command,font=font(18),fill='#dbbd80');d.text((left+22,150),title,font=font(17),fill='#404040');d.text((left+22,520),'Інвентар',font=font(17),fill='#404040')
    d.text((40,828),'46 іконок · українські назви · рекрути й гарнізони також оформлені',font=font(18),fill='#d2d7c6')
    d.text((40,862),'Прев’ю розкладки з готових моделей предметів; не знімок запущеної гри.',font=font(15),fill='#93aa99')
    image.save(out/'preview.png');image.save(ROOT/'preview-1.1.png')
    sheet=Image.new('RGB',(1376,900),'#132321');d=ImageDraw.Draw(sheet);d.text((28,22),'ІКОНКИ МІСЬКОГО МЕНЮ',font=font(26,True),fill='#ead8ac')
    for n,(name,row) in enumerate(CAT.items()):
        x=28+(n%8)*168;y=88+(n//8)*131;sheet.paste(icons[name].resize((64,64),Image.Resampling.NEAREST),(x+42,y))
        for j,line in enumerate(textwrap.wrap(row['label'],20)):d.text((x,y+74+j*16),line,font=font(12),fill='#d4d8c6')
    sheet.save(out/'icons.png');sheet.save(ROOT/'icons-1.1.png')

def main():
    assets=RES/'assets/empirecraft_menu_art'
    icons={n:(extra(n) if n in {'recruitment','garrison'} else tile(n)) for n in CAT}
    for name,icon in icons.items():
        p=assets/'textures/item'/f'{name}.png';p.parent.mkdir(parents=True,exist_ok=True);icon.save(p)
        p=assets/'models/item'/f'{name}.json';p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps({'parent':'minecraft:item/generated','textures':{'layer0':'empirecraft_menu_art:item/'+name}},indent=2)+'\n')
    language={'item.empirecraft_menu_art.'+n:r['label'] for n,r in CAT.items()}
    for code in ['uk_ua','en_us','ru_ru']:
        p=assets/'lang'/f'{code}.json';p.parent.mkdir(exist_ok=True);p.write_text(json.dumps(language,ensure_ascii=False,indent=2)+'\n')
    p=ROOT/'src/main/java/ua/empirecraft/menuart/IconCatalog.java'
    p.write_text('package ua.empirecraft.menuart;\n\nimport java.util.List;\n\n/** Stable registry names, generated from icons.json. */\npublic final class IconCatalog {\n    private IconCatalog() {}\n    public static final List<String> NAMES = List.of(\n        '+',\n        '.join(json.dumps(n) for n in CAT)+'\n    );\n}\n')
    previews(icons);print('NATIVE_ICON_ASSETS_PASS',len(CAT))
if __name__=='__main__':main()
