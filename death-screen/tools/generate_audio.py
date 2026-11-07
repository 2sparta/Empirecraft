#!/usr/bin/env python3
"""Generate original, deterministic mono cues without borrowed recordings."""
import hashlib,json,math,random,struct,subprocess,tempfile,wave
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];RATE=24000
CUES={'mechanism':(2.2,95),'train':(2.8,65),'cannon':(2.5,55),'musket':(1.8,145),'bomb':(2.4,42),'steel':(2.0,720),'spikes':(1.2,1250),'steam':(2.3,420),'shock':(1.6,110),'heart':(2.8,60),'dry':(2.4,250),'ocean':(2.8,120),'volcano':(2.7,48),'magic':(2.4,520),'wood_impact':(1.5,190),'arrow':(1.4,900)}
CAPTIONS={
'en_us':['Gears grind to a halt','A train rumbles past','A cannon impact echoes','A musket shot echoes','Fragments scatter','Steel rings out','Spikes strike','Steam hisses','An electrical crackle','A final heartbeat fades','Dry wind blows','A deep underwater impact','Volcanic rocks rumble','Experience drains away','A heavy impact cracks','An arrow whistles'],
'uk_ua':['Шестерні з гуркотом зупиняються','З гуркотом проїжджає поїзд','Лунає удар гарматного ядра','Лунає постріл мушкета','Розлітаються уламки','Дзвенить сталь','Вдаряють шипи','Шипить пара','Тріщить електричний розряд','Затихає останній удар серця','Віє сухий вітер','Глухий удар під водою','Гуркоче вулканічне каміння','Витягується досвід','Тріщить від сильного удару','Свистить стріла'],
'ru_ru':['Шестерни с грохотом останавливаются','С грохотом проезжает поезд','Раздаётся удар пушечного ядра','Раздаётся выстрел мушкета','Разлетаются осколки','Звенит сталь','Ударяют шипы','Шипит пар','Трещит электрический разряд','Затихает последний удар сердца','Дует сухой ветер','Глухой удар под водой','Грохочут вулканические камни','Извлекается опыт','Треск от сильного удара','Свистит стрела']}
def canonical_ogg(raw,name):
 data=bytearray(raw);pos=0;serial=int.from_bytes(hashlib.sha256(name.encode()).digest()[:4],'little')
 while pos<len(data):
  assert data[pos:pos+4]==b'OggS';segments=data[pos+26];end=pos+27+segments+sum(data[pos+27:pos+27+segments]);data[pos+14:pos+18]=struct.pack('<I',serial);data[pos+22:pos+26]=b'\0'*4;crc=0
  for byte in data[pos:end]:
   crc^=byte<<24
   for _ in range(8):crc=((crc<<1)^0x04c11db7 if crc&0x80000000 else crc<<1)&0xffffffff
  data[pos+22:pos+26]=struct.pack('<I',crc);pos=end
 return bytes(data)
def main():
 folder=ROOT/'resources/assets/epicdeathscreen/sounds/empirecraft';folder.mkdir(parents=True,exist_ok=True)
 with tempfile.TemporaryDirectory() as tmp:
  for cue,(duration,freq) in CUES.items():
   rng=random.Random(cue);low=0.;samples=[]
   for i in range(int(duration*RATE)):
    t=i/RATE;x=t/duration;noise=rng.uniform(-1,1);low=low*.91+noise*.09;fade=min(1,t/.018)*min(1,(duration-t)/.18);tone=math.sin(2*math.pi*freq*t)
    if cue=='heart':
     pulse=sum(math.exp(-((t-at)/.045)**2) for at in [.12,.32,1.,1.23,2.12]);value=tone*pulse*.6
    elif cue in ['cannon','bomb','volcano']:
     value=(low*2+tone*.35)*math.exp(-t*(2.8 if cue!='volcano' else 1.3));value+=noise*math.exp(-t*35)*.35
    elif cue=='musket':value=noise*math.exp(-t*22)*.7+low*math.exp(-t*3)*.8
    elif cue=='steel':value=(tone*.45+math.sin(2*math.pi*freq*2.73*t)*.2)*math.exp(-t*2.4)
    elif cue=='shock':value=(tone*.3+noise*.28)*(math.sin(t*93)**8)*math.exp(-t*.9)
    elif cue in ['steam','dry']:value=(noise-low)*(.26 if cue=='steam' else .16)*math.sin(math.pi*x)**.6
    elif cue=='ocean':value=(low*1.6+math.sin(2*math.pi*(freq*t-15*t*t))*.2)*math.exp(-t*.9)
    elif cue=='magic':value=(math.sin(2*math.pi*(freq*t-70*t*t))*.3+low*.4)*math.sin(math.pi*x)
    elif cue=='mechanism':value=(tone*.18+low*.6)*(0.45+0.55*math.sin(t*41)**2)*math.sin(math.pi*x)**.6
    elif cue=='train':value=(low*.9+tone*.18)*(0.55+0.45*math.sin(t*18)**4)*math.sin(math.pi*x)
    elif cue=='wood_impact':value=(tone*.35+low*.9)*math.exp(-t*5)
    elif cue=='spikes':value=(noise*.35+tone*.15)*math.exp(-t*10)
    else:value=(noise-low)*.25*math.sin(math.pi*x)**2+tone*.12*math.exp(-((x-.8)/.08)**2)
    samples.append(max(-.72,min(.72,value*fade)))
   wav=Path(tmp)/'cue.wav';ogg=Path(tmp)/'cue.ogg'
   with wave.open(str(wav),'wb') as w:w.setnchannels(1);w.setsampwidth(2);w.setframerate(RATE);w.writeframes(b''.join(struct.pack('<h',int(v*32767)) for v in samples))
   subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-i',str(wav),'-map_metadata','-1','-fflags','+bitexact','-flags:a','+bitexact','-c:a','libvorbis','-q:a','3',str(ogg)],check=True)
   (folder/(cue+'.ogg')).write_bytes(canonical_ogg(ogg.read_bytes(),cue))
 sounds={}
 for cue in CUES:sounds['empirecraft/'+cue]=dict(subtitle='epicdeathscreen.subtitle.empirecraft.'+cue,sounds=[dict(name='epicdeathscreen:empirecraft/'+cue,stream=False)])
 (ROOT/'sounds-added.json').write_text(json.dumps(sounds,indent=2)+'\n')
 for lang,captions in CAPTIONS.items():
  p=ROOT/'resources/assets/epicdeathscreen/lang'/(lang+'.json');data=json.loads(p.read_text()) if p.exists() else {}
  data.update({'epicdeathscreen.subtitle.empirecraft.'+cue:text for cue,text in zip(CUES,captions)});p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
 print('DEATH_AUDIO_PASS cues='+str(len(CUES)))
if __name__=='__main__':main()
