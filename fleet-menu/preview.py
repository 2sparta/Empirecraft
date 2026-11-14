#!/usr/bin/env python3
"""Illustrative layout render using exported Java geometry and vanilla bitmap glyphs; not an in-game capture."""
import argparse,io,json,math,zipfile
from pathlib import Path
from PIL import Image,ImageDraw
ROOT=Path(__file__).resolve().parent
NAVY='#172a35';HEADER='#203746';BRASS='#c6a56a';PAPER='#e8dfc9';CARD='#f5eddb';LINE='#cabc9d';INK='#303b3d';MUTED='#6b7169';LIGHT='#f2e7cd'
def main():
 p=argparse.ArgumentParser();p.add_argument('--minecraft',type=Path,required=True);a=p.parse_args();glyphs={}
 with zipfile.ZipFile(a.minecraft) as z:
  data=json.loads(z.read('assets/minecraft/font/include/default.json'))
  for provider in data['providers']:
   if provider['type']!='bitmap':continue
   file='assets/'+provider['file'].replace(':','/textures/');atlas=Image.open(io.BytesIO(z.read(file))).convert('RGBA');chars=provider['chars'];cw=atlas.width//len(chars[0]);ch=atlas.height//len(chars)
   for y,row in enumerate(chars):
    for x,char in enumerate(row):
     if char=='\0' or char in glyphs:continue
     tile=atlas.crop((x*cw,y*ch,(x+1)*cw,(y+1)*ch));bbox=tile.getchannel('A').getbbox();advance=0 if bbox is None else int(math.floor((bbox[2])*8/ch+0.5))+1
     tile=tile.resize((int(cw*8/ch),8),Image.Resampling.NEAREST);glyphs[char]=(tile.getchannel('A'),advance)
 def width(s):return sum(4 if c==' ' else glyphs.get(c,glyphs['?'])[1] for c in s)
 def render(sw,sh,state,path):
  geometry=next(r for r in json.loads((ROOT/'audit/layout.json').read_text())['layouts'] if r['screenWidth']==sw and r['screenHeight']==sh);f=geometry['frame'];a=geometry['content'];header=36 if f['h']<300 else 52;nav=a['x']-f['x']-14;step=22 if f['h']<300 else 30
  im=Image.new('RGB',(sw,sh),'#101a22');d=ImageDraw.Draw(im)
  def rect(x,y,w,h,c,outline=None):d.rectangle((x,y,x+w-1,y+h-1),fill=c,outline=outline)
  def text(s,x,y,c,maxw=None,center=False):
   if maxw is not None and width(s)>maxw:
    while s and width(s+'…')>maxw:s=s[:-1]
    s+='…'
   if center:x-=width(s)//2
   for char in s:
    if char==' ':x+=4;continue
    mask,advance=glyphs.get(char,glyphs['?']);im.paste(c,(x,y),mask);x+=advance
  def icon(kind,x,y,c):
   if kind==0:
    rect(x+6,y,1,11,c)
    for n in range(7):rect(x+7,y+1+n,1+n,1,c)
    rect(x,y+10,14,2,c);rect(x+2,y+12,10,2,c)
   elif kind==1:
    rect(x,y+2,14,3,c);rect(x+2,y,10,2,c);rect(x+1,y+5,2,9,c);rect(x+11,y+5,2,9,c);rect(x+3,y+11,8,3,c)
   elif kind==2:
    rect(x+1,y+1,8,4,c);rect(x+5,y+5,2,6,c);rect(x+2,y+12,10,2,c)
   elif kind==3:
    rect(x+2,y+2,10,10,None,c);rect(x+6,y,2,14,c);rect(x,y+6,14,2,c)
   elif kind==4:
    rect(x+1,y+5,8,4,c);rect(x+9,y+3,4,8,c);rect(x+5,y+9,2,5,c)
   else:rect(x+6,y+1,2,12,c);rect(x+1,y+6,12,2,c)
  def ship(x,y,color):
   rect(x+30,y,2,42,color);rect(x+14,y+10,2,33,color)
   for n in range(25):
    rect(x+33,y+4+n,1+n,1,color)
    if n<17:rect(x+16,y+13+n,1+n,1,color)
   rect(x+3,y+42,62,3,color)
   for n in range(7):rect(x+5+n,y+45+n,58-2*n,1,color)
   rect(x,y+56,25,1,color);rect(x+35,y+56,35,1,color)
  def button(label,x,y,w,h=20,primary=False):rect(x,y,w,h,'#294b50' if primary else '#eee5d0',LINE);text(label,x+w//2,y+(h-8)//2,LIGHT if primary else INK,w-14,True)
  rect(f['x']+3,f['y']+5,f['w'],f['h'],'#081017');rect(f['x'],f['y'],f['w'],f['h'],PAPER,BRASS);rect(f['x']+1,f['y']+header,nav-1,f['h']-header-1,NAVY);rect(f['x']+1,f['y']+1,f['w']-2,header-1,HEADER);rect(f['x'],f['y']+header-1,f['w'],1,BRASS)
  text('Міський флот',f['x']+38,f['y']+10,LIGHT);text('Крымос-Керчинский',f['x']+38,f['y']+24,BRASS,f['w']-240 if f['w']>550 else f['w']-54)
  icon(0,f['x']+15,f['y']+13,BRASS)
  if f['w']>550:text('ПОРТОВА КАНЦЕЛЯРІЯ',f['x']+f['w']-180,f['y']+20,'#a9b7b4',165)
  labels=['Флот міста','Кораблі базару','Аукціони','Керування','Спостереження','Новий корабель']
  for i,label in enumerate(labels):
   x=f['x']+8;y=f['y']+header+8+i*step;w=nav-14;h=step-4
   if i==(3 if state=='form' else 0):rect(x,y,w,h,'#344d57');rect(x,y,2,h,BRASS)
   icon(i,x+7,y+(h-14)//2,BRASS if i==(3 if state=='form' else 0) else LIGHT);text(label,x+27,y+(h-8)//2,BRASS if i==(3 if state=='form' else 0) else LIGHT,w-33)
  button('Закрити',f['x']+8,f['y']+f['h']-30,nav-14)
  title='Призначити капітана' if state=='form' else 'Флот міста'
  text(title,a['x'],a['y']-30,INK,a['w']);text('Заповніть дані та перевірте умови перед підтвердженням.' if state=='form' else 'Кораблі, екіпажі та морська торгівля',a['x'],a['y']-16,MUTED,a['w']);rect(a['x'],a['y']-5,a['w'],1,LINE)
  if state=='form':
   rect(a['x'],a['y'],a['w'],a['h'],CARD,LINE)
   for i,(label,value) in enumerate([('Корабель','Чорна Перлина (#43)'),('Капітан','Starbik2013')][:geometry['fieldRows']]):
    x=a['x']+8;y=a['y']+15+i*40;w=a['w']-16;text(label,x,y-12,MUTED,w);rect(x-2,y-2,w+4,23,'#fffaf0',LINE);text(value,x,y+3,INK,w-94);button('Обрати',x+w-82,y,80)
   button('Переглянути умови',a['x'],f['y']+f['h']-30,min(180,(a['w']-6)//2),primary=True);button('Назад',a['x']+a['w']-min(100,(a['w']-6)//2),f['y']+f['h']-30,min(100,(a['w']-6)//2))
  else:
   h=min(108,a['h']);rect(a['x']+1,a['y']+2,a['w'],h,'#b9b39f');rect(a['x'],a['y'],a['w'],h,CARD,LINE);rect(a['x'],a['y'],3,h,'#5d7e7b');engraving=a['w']>375 and h>=100;tw=a['w']-(100 if engraving else 20)
   text('Реєстрація нового корабля',a['x']+10,a['y']+10,INK,tw)
   words='Наведіться на блок корабля та подайте заявку з назвою.'.split();lines=['']
   for word in words:
    if width(lines[-1]+' '+word)>tw:lines.append(word)
    else:lines[-1]+=(' ' if lines[-1] else '')+word
   for i,line in enumerate(lines[:max(1,(h-49)//12)]):text(line,a['x']+10,a['y']+26+i*12,MUTED,tw)
   button('Подати заявку',a['x']+10,a['y']+h-28,min(165,a['w']-20),primary=True)
   if engraving:ship(a['x']+a['w']-83,a['y']+13,'#c9bea1')
   if state=='empty' and a['h']-h>100:
    ship(a['x']+a['w']//2-35,a['y']+h+13,'#b7b69d');text('Ваш флот починається тут',a['x']+a['w']//2,a['y']+h+78,MUTED,a['w']-12,True)
   if state=='list':
    y=a['y']+h+8
    for title,detail in [('«Чорна Перлина»','Капітан: Starbik2013; команда: 6'),('«Січ»','Капітан: jondi; команда: 3')]:
     h=min(82,a['y']+a['h']-y)
     if h<64:break
     rect(a['x'],y,a['w'],h,CARD,LINE);rect(a['x'],y,3,h,BRASS);text(title,a['x']+10,y+10,INK,a['w']-20);text(detail,a['x']+10,y+26,MUTED,a['w']-20);bw=min(165,(a['w']-26)//2);button('Керувати',a['x']+10,y+h-28,bw,primary=True);button('Морський прапор',a['x']+16+bw,y+h-28,bw);y+=h+8
  scale=2 if sw<=1000 else 1;im.resize((sw*scale,sh*scale),Image.Resampling.NEAREST).save(path)
 dest=ROOT/'preview';dest.mkdir(exist_ok=True)
 for sw,sh,state,name in [(953,500,'empty','fleet-empty'),(953,500,'list','fleet-ships'),(640,360,'form','fleet-form'),(320,240,'empty','fleet-small')]:render(sw,sh,state,dest/(name+'.png'))
 (dest/'README.md').write_text('Ілюстративні макети з геометрії FleetLayout і ванільних bitmap-гліфів Minecraft. Це не скриншоти запущеного клієнта. Приклади кораблів і гравців — демонстраційні.\n')
 print('FLEET_PREVIEW_RENDERED')
if __name__=='__main__':main()
