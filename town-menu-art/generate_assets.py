"""Original Town Hall artwork. Pillow only; no downloaded or repainted game assets."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
import json, math
ROOT = Path(__file__).resolve().parent
ASSETS = ROOT / 'src/main/resources/assets/empirecraft_menu_art/textures/gui'
NAMES = ['town','people','land','treasury','vassals','treaty','union','crown','dynasty','offers','provisions','history','back','next','refresh','close','map','parchment','invite','remove','confirm','castle','quill','name','home','compass','settings','council','ascend','bank','coin','depart','neutral','red_flag','blue_flag','yellow_flag','storage','wheat','hunger','satisfied','help','reject','locked','bottle']
GOLD='#d9b76f'; IVORY='#f7ebcd'; JADE='#92b8a4'; DARK='#122b29'; INK='#0c1c1d'; RED='#ec9a82'
S=4

def tile(name):
    im=Image.new('RGBA',(32*S,32*S),INK);d=ImageDraw.Draw(im)
    danger=name in {'close','remove','hunger','reject','red_flag'}
    base=(65,34,35) if danger else (26,55,49)
    for y in range(2*S,30*S):
        k=(y/S-2)/28
        d.line((2*S,y,30*S,y), fill=tuple(int(v-9*k) for v in base))
    def box(rect,fill,outline=None,w=1):d.rectangle(tuple(round(v*S) for v in rect),fill,outline,width=max(1,round(w*S)))
    def line(points,fill=IVORY,w=1.5):d.line([(round(x*S),round(y*S)) for x,y in points],fill,width=round(w*S),joint='curve')
    def poly(points,fill=GOLD,outline=None):d.polygon([(round(x*S),round(y*S)) for x,y in points],fill,outline)
    def oval(rect,fill=GOLD,outline=None,w=1):d.ellipse(tuple(round(v*S) for v in rect),fill,outline,width=round(w*S))
    def arc(rect,a,b,fill=GOLD,w=1.5):d.arc(tuple(round(v*S) for v in rect),a,b,fill,width=round(w*S))
    box((1,1,30,30),None,'#9c824c',.5);line([(2,2),(29,2)],'#e4c887',.5);line([(2,29),(29,29)],'#514c33',.5)
    for x,y in [(3,3),(28,3),(3,28),(28,28)]:box((x,y,x+.5,y+.5),'#ead4a4')
    a=RED if danger else IVORY
    def shield(x=10,y=8):
        poly([(x,y),(x+12,y),(x+12,y+8),(x+10,y+12),(x+6,y+15),(x+2,y+12),(x,y+8)],JADE)
        line([(x+1,y+1),(x+11,y+1)],IVORY,1)
    def crown():
        poly([(7,11),(12,15),(16,8),(20,15),(25,11),(23,23),(9,23)],GOLD)
        line([(10,20),(22,20)],IVORY,1)
        for x,y in [(7,10),(16,7),(25,10)]:oval((x-1,y-1,x+1,y+1),IVORY)
    def person(x=16,y=11,small=False):
        r=2.7 if small else 3.5
        oval((x-r,y-r,x+r,y+r),a)
        d.rounded_rectangle(tuple(round(v*S) for v in (x-r-2,y+4,x+r+2,y+10)),radius=round(2*S),fill=JADE)
    def scroll():
        box((10,7,23,24),'#dfcda4');oval((7,7,13,12),GOLD);box((8,9,12,22),GOLD)
        oval((7,20,13,25),GOLD);line([(15,12),(21,12)],'#655e45',1);line([(15,16),(21,16)],'#655e45',1)
    def flag(col):
        line([(10,7),(10,26)],GOLD,1.5);poly([(11,8),(24,8),(21,13),(24,18),(11,18)],col);oval((9,5,11,7),IVORY)
    if name in {'town','castle','home','bank'}:
        if name=='home':
            poly([(7,15),(16,7),(25,15)],GOLD);box((10,15,22,25),JADE);box((14,19,18,25),INK)
        elif name=='bank':
            poly([(6,12),(16,6),(26,12)],GOLD);line([(7,25),(25,25)],GOLD,2)
            for x in (10,16,22):line([(x,14),(x,23)],IVORY,2)
        else:
            box((8,14,24,25),JADE);box((7,9,12,24),GOLD);box((20,9,25,24),GOLD)
            for x in (7,10,20,23):box((x,7,x+2,10),GOLD)
            if name=='town':box((14,7,18,17),IVORY);poly([(13,7),(16,4),(19,7)],GOLD)
            box((14,20,18,25),INK);line([(8,26),(24,26)],GOLD,1)
    elif name in {'people','invite'}:
        if name=='people':person(10,12,True);person(22,12,True);person(16,9)
        else:person(13,11);line([(24,9),(24,17)],GOLD,2);line([(20,13),(28,13)],GOLD,2)
    elif name in {'land','map'}:
        poly([(6,10),(12,7),(20,10),(26,7),(26,23),(20,26),(12,23),(6,26)],'#d6c99f')
        line([(12,8),(12,22)],'#737b5c',1);line([(20,11),(20,25)],'#737b5c',1)
        line([(9,19),(15,13),(23,17)],'#557c69',1.5)
        if name=='land':flag('#80af8c')
        else:oval((14,12,18,16),'#965b4e')
    elif name in {'treasury','coin'}:
        for x,y in ([(10,21),(16,18),(22,21)] if name=='treasury' else [(16,16)]):
            oval((x-5,y-5,x+5,y+5),GOLD);oval((x-3.5,y-3.5,x+3.5,y+3.5),None,'#fff0bd',.7);line([(x,y-2),(x,y+2)],'#8a6d3e',1)
        if name=='treasury':line([(11,9),(14,7),(20,7),(22,10)],IVORY,1.5)
    elif name=='vassals':shield();line([(16,10),(16,19)],IVORY,1.5);line([(12,14),(20,14)],IVORY,1.5)
    elif name in {'treaty','offers','parchment'}:
        scroll()
        if name!='parchment':oval((18,19,25,26),'#a85948');line([(20,22),(21,23),(24,20)],IVORY,1)
    elif name=='union':
        for x in (12,20):oval((x-6,10,x+6,22),None,GOLD,2)
        line([(11,16),(21,16)],IVORY,1)
    elif name=='crown':crown()
    elif name=='dynasty':
        line([(16,10),(16,18),(9,18),(9,23)],GOLD,1.5);line([(16,18),(23,18),(23,23)],GOLD,1.5)
        for x,y in [(16,8),(9,24),(23,24)]:oval((x-3,y-3,x+3,y+3),IVORY if y==8 else JADE)
    elif name in {'provisions','satisfied'}:
        poly([(8,15),(11,10),(17,9),(23,12),(25,18),(23,23),(9,23),(6,20)],'#d9aa65')
        line([(12,12),(10,18)],IVORY,1.5);line([(18,12),(16,18)],IVORY,1.5)
        if name=='satisfied':oval((20,19,28,27),'#719b7b');line([(22,23),(24,25),(27,21)],IVORY,1.2)
    elif name=='history':
        oval((7,7,25,25),None,GOLD,1.5);line([(16,10),(16,16),(21,18)],IVORY,1.5)
        for x,y in [(16,7),(7,16),(25,16),(16,25)]:oval((x-.6,y-.6,x+.6,y+.6),IVORY)
    elif name in {'back','next','depart'}:
        if name=='depart':box((8,7,18,26),None,GOLD,1.5)
        pts=[(24,16),(9,16)] if name!='next' else [(8,16),(23,16)]
        line(pts,IVORY,2);poly([(9,16),(16,9),(16,23)] if name!='next' else [(23,16),(16,9),(16,23)],GOLD)
    elif name=='refresh':arc((7,7,25,25),-75,245,GOLD,2);poly([(5,11),(8,18),(12,12)],IVORY)
    elif name in {'close','remove','reject'}:
        line([(10,10),(22,22)],a,2.5);line([(22,10),(10,22)],a,2.5)
    elif name=='confirm':line([(8,16),(14,22),(25,10)],'#b1d6ac',3)
    elif name=='quill':
        poly([(8,25),(13,12),(21,6),(25,7),(24,14),(18,20)],IVORY);line([(8,26),(22,10)],GOLD,1.3);line([(17,15),(23,14)],DARK,.8)
    elif name=='name':
        poly([(7,9),(20,9),(26,16),(20,23),(7,23)],GOLD);oval((20,14,23,17),DARK);line([(10,14),(17,14)],DARK,1.2);line([(10,18),(15,18)],DARK,1.2)
    elif name=='compass':
        oval((7,7,25,25),None,GOLD,1);poly([(16,6),(12,20),(20,20)],IVORY);poly([(16,26),(12,20),(20,20)],'#8fa998');oval((14,16,18,20),GOLD)
    elif name=='settings':
        for n in range(8):
            angle=n*math.pi/4;x=16+9*math.cos(angle);y=16+9*math.sin(angle);line([(16,16),(x,y)],GOLD,3)
        oval((9,9,23,23),GOLD);oval((12,12,20,20),DARK);oval((14,14,18,18),IVORY)
    elif name=='council':
        box((9,9,23,12),GOLD);poly([(10,13),(22,13),(20,19),(12,19)],IVORY);box((14,19,18,24),JADE);box((10,25,22,26),GOLD)
    elif name=='ascend':
        for n in range(3):box((7+n*6,23-n*6,12+n*6,26),JADE)
        line([(8,17),(23,7)],GOLD,1.5);poly([(23,7),(17,8),(23,13)],IVORY)
    elif name=='neutral':line([(9,16),(23,16)],JADE,2)
    elif name.endswith('_flag'):flag({'red_flag':'#cf8676','blue_flag':'#9bbfc8','yellow_flag':GOLD}[name])
    elif name=='storage':
        box((7,11,25,25),'#9d7447');box((7,8,25,14),'#c19b5e');line([(7,14),(25,14)],INK,1)
        box((10,9,12,24),GOLD);box((21,9,23,24),GOLD);box((15,13,18,17),IVORY)
    elif name=='wheat':
        line([(12,27),(18,7)],GOLD,1.5)
        for y in (10,14,18,22):
            x=18-(y-7)*.3;poly([(x,y+2),(x-5,y-2),(x-4,y-5),(x,y)],'#d9b76f');poly([(x,y+2),(x+5,y-1),(x+5,y-4),(x+1,y)],IVORY)
    elif name=='hunger':
        poly([(16,5),(28,26),(4,26)],'#b67251');poly([(16,8),(25,24),(7,24)],'#573832');line([(16,13),(16,19)],IVORY,2);oval((15,21,17,23),IVORY)
    elif name=='help':
        oval((6,6,26,26),None,GOLD,1.3);arc((12,10,20,18),180,460,IVORY,1.5);line([(16,17),(16,19)],IVORY,1.5);oval((15,22,17,24),IVORY)
    elif name=='locked':arc((10,6,22,20),180,360,GOLD,2);box((8,14,24,26),JADE);oval((14,17,18,21),INK);line([(16,20),(16,23)],INK,1.5)
    elif name=='bottle':
        box((13,6,19,10),GOLD);poly([(13,10),(13,14),(9,19),(10,25),(22,25),(23,19),(19,14),(19,10)],'#9cbfb3');line([(12,21),(20,21)],IVORY,1)
    return im.resize((32,32),Image.Resampling.LANCZOS)

def panel():
    im=Image.new('RGBA',(256,256),(0,0,0,0));d=ImageDraw.Draw(im)
    d.rectangle((0,0,175,221),fill='#101f20')
    d.rectangle((1,1,174,220),outline='#806a43');d.rectangle((2,2,173,219),outline='#c4a366')
    # Cream label strips keep Minecraft's unmodified dark title text legible.
    for a,b in [(4,14),(125,137)]:
        for y in range(a,b+1):d.line((5,y,170,y),fill=(218-(y-a),201-(y-a),163-(y-a)))
    d.line((5,15,170,15),fill='#e2c68a');d.line((5,124,170,124),fill='#8a754a')
    for y in range(17,124):
        c=int(41-(y-17)*.055);d.line((5,y,170,y),fill=(20,c+5,c+1))
    d.rectangle((5,106,170,123),fill='#132b28');d.line((5,106,170,106),fill='#7d6a45')
    # Storage grid and player inventory use the precise vanilla hit rectangles.
    for row in range(6):
        for col in range(9):
            x,y=8+col*18,18+row*18
            d.rectangle((x-1,y-1,x+16,y+16),fill='#0d211f',outline='#425047')
            d.line((x,y+16,x+16,y+16),fill='#536150')
    for row in range(4):
        for col in range(9):
            x=8+col*18;y=140+row*18 if row<3 else 198
            d.rectangle((x-1,y-1,x+16,y+16),fill='#172a27',outline='#4a584a')
            d.line((x,y+16,x+16,y+16),fill='#62705b')
    for x,y in [(3,3),(172,3),(3,218),(172,218)]:d.rectangle((x,y,x+1,y+1),fill='#f5dfaa')
    return im

def font(size,bold=False):
    return ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans'+('-Bold' if bold else '')+'.ttf',size)

def previews(icons,p):
    out=ROOT/'build/previews';out.mkdir(parents=True,exist_ok=True)
    sheet=Image.new('RGB',(980,680),'#0d1c1d');d=ImageDraw.Draw(sheet)
    d.text((36,26),'EMPIRECRAFT / TOWN HALL',font=font(25,True),fill='#e2c183')
    d.text((36,66),'44 оригінальні іконки · латунь, пергамент і темна зелень',font=font(16),fill='#acbab0')
    for n,name in enumerate(NAMES):
        x=36+(n%8)*118;y=120+(n//8)*92
        sheet.paste(icons[n].resize((54,54),Image.Resampling.NEAREST),(x+20,y))
        d.text((x,y+60),name,font=font(11),fill='#d4d6c2')
    sheet.save(out/'icons.png')
    preview=Image.new('RGB',(1280,900),'#0d1c1d');d=ImageDraw.Draw(preview)
    d.text((58,34),'EMPIRECRAFT',font=font(20,True),fill='#9aaf9e')
    d.text((58,66),'Міська рада. Нове обличчя.',font=font(37,True),fill='#f0e3c4')
    d.text((58,122),'/t menu',font=font(18),fill='#d5b575');d.text((690,122),'/t provision',font=font(18),fill='#d5b575')
    main={4:0,10:1,11:2,12:3,13:4,14:5,15:6,16:7,28:8,30:17,32:39,34:11,45:12,48:16,49:14,53:15}
    prov={45:36,47:37,49:39,53:14}
    for left,mapping,title in [(58,main,'Місто · Empirecraft'),(690,prov,'Провізія: Empirecraft')]:
        sample=p.crop((0,0,176,222));sd=ImageDraw.Draw(sample)
        # Layout illustration built from the exact shipping textures; not a game screenshot.
        for slot,idx in mapping.items():
            x=8+(slot%9)*18;y=18+(slot//9)*18
            sample.alpha_composite(icons[idx].resize((16,16),Image.Resampling.LANCZOS),(x,y))
        big=sample.resize((528,666),Image.Resampling.NEAREST)
        preview.paste(big,(left,159),big)
        dd=ImageDraw.Draw(preview)
        dd.text((left+24,174),title,font=font(18,True),fill='#3d4636')
        dd.text((left+24,543),'Інвентар',font=font(18),fill='#3d4636')
        if mapping is main:
            # Actual hover bounds for a 16px control.
            x,y=left+8*3+18*3,159+18*3+18*3
            dd.rectangle((x-3,y-3,x+49,y+49),outline='#f3d995',width=2)
            dd.rounded_rectangle((left+100,367,left+455,449),radius=5,fill='#151d20',outline='#947b4b')
            dd.text((left+116,380),'Жителі',font=font(20,True),fill='#e4c387')
            dd.text((left+116,413),'Запрошення, виключення, помічники',font=font(14),fill='#c3cebe')
    d.text((58,850),'Прев’ю оформлення · текстури з готового мода · оригінальна сітка й керування',font=font(16),fill='#899f91')
    preview.save(out/'preview.png')

def main():
    ASSETS.mkdir(parents=True,exist_ok=True)
    icons=[tile(n) for n in NAMES];atlas=Image.new('RGBA',(256,256))
    for n,icon in enumerate(icons):atlas.alpha_composite(icon,((n%8)*32,(n//8)*32))
    atlas.save(ASSETS/'controls.png');p=panel();p.save(ASSETS/'town_hall.png')
    (ROOT/'catalog.json').write_text(json.dumps({str(742100+n):name for n,name in enumerate(NAMES)},indent=2)+'\n')
    previews(icons,p)
    print('ART_READY',len(icons))
if __name__=='__main__':main()
