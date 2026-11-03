#!/usr/bin/env python3
"""Generate 128 original standard 64x64 Minecraft skins. Requires Pillow; no upstream artwork is copied."""
from pathlib import Path
from PIL import Image,ImageDraw
import hashlib,json,random
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'src/main/resources/assets/townyrecruits/textures/entity/human'
SKIN=[(240,203,169),(220,174,135),(198,143,101),(174,119,79),(146,94,63),(116,75,54),(91,59,45),(211,161,126)]
HAIR=[(37,30,27),(67,44,30),(103,65,34),(145,88,44),(181,125,64),(214,175,105),(113,57,36),(117,119,115),(174,173,157),(58,47,42)]
EYES=[(62,103,118),(80,110,73),(97,69,46),(97,94,118),(127,100,50),(56,70,78)]
CLOTH=[(69,88,107),(83,103,74),(119,74,64),(107,86,121),(122,107,72),(58,96,99),(112,109,100),(104,69,89)]
TRIM=[(181,150,88),(151,148,132),(94,75,52),(181,164,129),(99,119,122),(144,95,63)]
def shade(c,n):return tuple(max(0,min(255,v+n)) for v in c[:3])
def region(im,rect,color,rng,noise=3):
 x,y,w,h=rect
 for py in range(y,y+h):
  for px in range(x,x+w):im.putpixel((px,py),shade(color,rng.randint(-noise,noise))+(255,))
def cube(im,u,v,w,h,d,color,rng):
 faces={'top':(u+d,v,w,d),'bottom':(u+d+w,v,w,d),'right':(u,v+d,d,h),'front':(u+d,v+d,w,h),'left':(u+d+w,v+d,d,h),'back':(u+2*d+w,v+d,w,h)}
 for key,rect in faces.items():region(im,rect,shade(color,{'top':8,'bottom':-12,'right':-8,'left':-3,'back':-5}.get(key,0)),rng)
 return faces

def skin(index):
 rng=random.Random(0x454d50495245+index*104729)
 tone=SKIN[index%len(SKIN)];hair=HAIR[(index//8+index*3)%len(HAIR)];eyes=EYES[rng.randrange(len(EYES))];cloth=CLOTH[rng.randrange(len(CLOTH))];trim=TRIM[rng.randrange(len(TRIM))]
 style=(index//8)%8;beard=(index//16+index)%7
 im=Image.new('RGBA',(64,64),(0,0,0,0));draw=ImageDraw.Draw(im)
 head=cube(im,0,0,8,8,8,tone,rng)
 # Hair has its own side/back shapes instead of recolouring an identical face.
 for key in ['top','right','left','back']:
  x,y,w,h=head[key]
  if key=='top':region(im,head[key],hair,rng,7);continue
  depth={'right':4,'left':4,'back':6}[key]
  if style in [2,5]:depth=7
  if style==6:depth=2
  if style==7:depth=3
  region(im,(x,y,w,depth),hair,rng,6)
  for px in range(x,x+w):
   if rng.random()<0.4 and depth<7:im.putpixel((px,y+depth),shade(hair,-5)+(255,))
 # Front hair: crop, side part, fringe, long sides, widow's peak, curls, shaved, receding.
 for x in range(8):
  depth=[1+(x%3==0),1+(x<3),1+(x%3),1+(x==0 or x==7),1+(x in [3,4]),1+rng.randrange(2),0,1+(x in [0,7])][style]
  for y in range(depth):im.putpixel((8+x,8+y),shade(hair,rng.randint(-7,7))+(255,))
 if style in [2,3,5]:
  for x in [0,7]:
   for y in range(2,6 if style==3 else 4):im.putpixel((8+x,8+y),shade(hair,-4)+(255,))
 # Brows, whites and coloured irises are independent of beard/hair style.
 for x in [1,2,5,6]:im.putpixel((8+x,10),shade(hair,-4)+(255,))
 for x in [1,2,5,6]:im.putpixel((8+x,11),(232,228,211,255))
 for x in [2,5]:im.putpixel((8+x,11),eyes+(255,))
 im.putpixel((11,12),shade(tone,-12)+(255,));im.putpixel((12,13),shade(tone,10)+(255,))
 for x in [3,4]:im.putpixel((8+x,14),shade(tone,-37)+(255,))
 if beard in [1,2,3,4]:
  for y in range(5 if beard==3 else 6,8):
   for x in range(1,7):
    if y==7 or x in [1,2,5,6] or beard in [2,3]:im.putpixel((8+x,8+y),shade(hair,rng.randint(-5,9))+(255,))
 if beard in [4,5]:
  for x in [2,3,4,5]:im.putpixel((8+x,13),shade(hair,-3)+(255,))
 if beard==6:
  for x,y in [(1,5),(6,5),(2,6),(5,6),(3,7),(4,7)]:im.putpixel((8+x,8+y),shade(tone,-23)+(255,))
 if index%9==0:
  for x,y in [(1,4),(2,5),(5,4),(6,5)]:im.putpixel((8+x,8+y),shade(tone,-24)+(255,))
 if index%11==0:
  for y in range(2,6):im.putpixel((14,8+y),shade(tone,26)+(255,))
 # Raised hair fringe and sideburns on the optional head overlay (still original pixels).
 if style in [1,2,3,4,5]:
  for x in range(8):
   if (x+index)%3==0:im.putpixel((40+x,8),shade(hair,8)+(255,))
  if style==3:
   draw.rectangle((32,8,33,13),fill=hair+(255,));draw.rectangle((54,8,55,13),fill=hair+(255,))
 body=cube(im,16,16,8,12,4,cloth,rng)
 # Tunics, padded shirts, collars, seams, straps, belts and buckles.
 for face in ['front','back']:
  x,y,w,h=body[face];draw.line((x,y+8,x+w-1,y+8),fill=(65,48,34,255));draw.line((x,y+9,x+w-1,y+9),fill=(82,60,39,255))
  for row in [3,6]:
   if index%3==0:draw.line((x,y+row,x+w-1,y+row),fill=shade(cloth,-14)+(255,))
 x,y,_,_=body['front'];draw.rectangle((x+3,y+8,x+4,y+9),fill=(183,156,87,255))
 draw.line((x+2,y,x+3,y+2),fill=trim+(255,));draw.line((x+5,y,x+4,y+2),fill=trim+(255,))
 for row in range(3,8):
  if row%2==0:im.putpixel((x+4,y+row),trim+(255,))
 if index%4==0:
  for row in range(8):im.putpixel((x+min(7,row),y+row),trim+(255,))
 pants=shade(cloth,-24);boot=(51+index%4*8,43+index%3*7,35+index%3*5)
 for u,v in [(0,16),(16,48)]:
  leg=cube(im,u,v,4,12,4,pants,rng)
  for face in ['front','right','left','back']:
   lx,ly,lw,lh=leg[face];region(im,(lx,ly+7,lw,5),boot,rng,3);draw.line((lx,ly+7,lx+lw-1,ly+7),fill=trim+(255,))
 for u,v in [(40,16),(32,48)]:
  arm=cube(im,u,v,4,12,4,cloth,rng)
  for face in ['front','right','left','back']:
   ax,ay,aw,ah=arm[face];region(im,(ax,ay+9,aw,3),tone,rng,2);draw.line((ax,ay+8,ax+aw-1,ay+8),fill=trim+(255,))
  region(im,arm['bottom'],tone,rng,2)
 return im

def front(im):
 p=Image.new('RGBA',(16,32),(0,0,0,0))
 for src,dst in [((8,8,16,16),(4,0)),((20,20,28,32),(4,8)),((44,20,48,32),(0,8)),((36,52,40,64),(12,8)),((4,20,8,32),(4,20)),((20,52,24,64),(8,20))]:p.alpha_composite(im.crop(src),dst)
 p.alpha_composite(im.crop((40,8,48,16)),(4,0));return p

def main():
 OUT.mkdir(parents=True,exist_ok=True)
 preview=Image.new('RGB',(1024,1168),(24,29,34));d=ImageDraw.Draw(preview);manifest={}
 for i in range(128):
  im=skin(i);path=OUT/f'skin_{i}.png';im.save(path,optimize=True)
  manifest[path.name]=hashlib.sha256(path.read_bytes()).hexdigest()
  x=(i%16)*64;y=(i//16)*146
  preview.paste(front(im).resize((64,128),Image.Resampling.NEAREST),(x,y),front(im).resize((64,128),Image.Resampling.NEAREST))
  d.text((x+22,y+130),str(i),fill=(199,207,210))
 (ROOT/'assets').mkdir(exist_ok=True);preview.save(ROOT/'assets/skins-preview.png')
 (ROOT/'assets/skins-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
 print('Generated 128 original 64x64 skins and preview')
if __name__=='__main__':main()
