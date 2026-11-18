#!/usr/bin/env python3
import sys,os,json,hashlib,subprocess,zipfile,shutil,math,struct,wave
from pathlib import Path
R=Path(__file__).resolve().parent;REPO=R.parent
sys.path.insert(0,str(REPO/'siegeworks'))
from build import SDK,JDK,REF,archive
b=R/'build';b.mkdir(exist_ok=True)
source=R/'upstream/knockedback-1.6.2.jar'
sha=hashlib.sha256(source.read_bytes()).hexdigest()
pin=R/'upstream/sha256.txt'
if pin.exists():assert pin.read_text().strip()==sha
else:pin.write_text(sha+'\n')
libs=list((REF/'server/libraries').rglob('*.jar'));asm=list((REF/'server/libraries/org/ow2/asm').rglob('*.jar'))
remapper=[SDK/'tiny-remapper.jar',SDK/'mapping-io.jar',*asm]
def run(args):subprocess.run(list(map(str,args)),check=True)
def remap(src,dst,ns1,ns2,cp):
 dst.unlink(missing_ok=True)
 run([JDK/'bin/java','-cp',os.pathsep.join(map(str,remapper)),'dev.architectury.tinyremapper.Main',src,dst,SDK/'named-srg.tiny',ns1,ns2,*cp,'--ignoreConflicts'])
with zipfile.ZipFile(source) as z:files={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
manager='net/fretux/knockedback/KnockedManager.class'
run([JDK/'bin/javac','--release','17','-cp',os.pathsep.join(map(str,asm)),'-d',b,R/'tools/Patch.java'])
modified=['net/fretux/knockedback/'+name+'.class' for name in ['KnockedManager','HelpUpHandler','NpcHelpUpHandler','NetworkHandler']]
for name in modified:
 (b/'input.class').write_bytes(files[name])
 run([JDK/'bin/java','-cp',os.pathsep.join(map(str,[b,*asm])),'Patch',b/'input.class',b/'patched.class'])
 files[name]=(b/'patched.class').read_bytes()
archive(b/'base.jar',files)
remap(b/'base.jar',b/'base-named.jar','srg','named',[SDK/'runtime-srg.jar',*libs])
cp=[SDK/'runtime-named.jar',SDK/'client-named.jar',b/'base-named.jar',REPO/'mods/player-animation-lib-forge-1.0.2-rc1+1.20.jar',*libs]
classes=b/'classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
run([JDK/'bin/javac','--release','17','-proc:none','-cp',os.pathsep.join(map(str,cp)),'-d',classes,*sorted((R/'src/main/java').rglob('*.java'))])
archive(b/'helpers-named.jar',{f.relative_to(classes).as_posix():f.read_bytes() for f in classes.rglob('*.class')})
remap(b/'helpers-named.jar',b/'helpers-srg.jar','named','srg',cp)
with zipfile.ZipFile(b/'helpers-srg.jar') as z:files.update({n:z.read(n) for n in z.namelist() if n.endswith('.class')})
files['META-INF/mods.toml']=files['META-INF/mods.toml'].replace(b'version="1.6.2"',b'version="1.6.2-empirecraft.2"')
files['META-INF/MANIFEST.MF']=files['META-INF/MANIFEST.MF'].replace(b'Implementation-Version: 1.6.2',b'Implementation-Version: 1.6.2-empirecraft.2')
for lang,text in [('en_us','Time until death: %s s'),('uk_ua','До смерті: %s с')]:
 key=f'assets/knockedback/lang/{lang}.json';data=json.loads(files.get(key,b'{}'));data['overlay.knockedback.recovery_time']=text
 data['overlay.knockedback.rescuable']='Поранений · можна врятувати [%s]' if lang=='uk_ua' else 'Wounded · can be rescued [%s]'
 files[key]=(json.dumps(data,ensure_ascii=False,indent=2)+'\n').encode()
# Locally generated, original double-beat sound; no external asset/licensing dependency.
with wave.open(str(b/'heartbeat.wav'),'wb') as w:
 w.setnchannels(1);w.setsampwidth(2);w.setframerate(22050)
 samples=[]
 for i in range(int(22050*.44)):
  t=i/22050;v=0
  for start,volume in [(0,0.75),(.17,.5)]:
   dt=t-start
   if 0<=dt<.15:v+=volume*math.sin(2*math.pi*(65*dt-65*dt*dt))*math.exp(-dt*30)
  samples.append(struct.pack('<h',int(max(-1,min(1,v))*28000)))
 w.writeframes(b''.join(samples))
run(['ffmpeg','-v','error','-y','-i',b/'heartbeat.wav','-c:a','libvorbis',b/'heartbeat.ogg'])
files['assets/knockedback/sounds/heartbeat.ogg']=(b/'heartbeat.ogg').read_bytes()
files['assets/knockedback/sounds.json']=json.dumps({'heartbeat':{'sounds':['knockedback:heartbeat']}}).encode()
files['knockedback-recovery.mixins.json']=json.dumps(dict(required=True,minVersion='0.8',package='net.fretux.knockedback.mixin',compatibilityLevel='JAVA_17',mixins=['RecoverySprint'],injectors=dict(defaultRequire=1))).encode()
files['META-INF/MANIFEST.MF']=files['META-INF/MANIFEST.MF'].rstrip()+b'\r\nMixinConfigs: knockedback-recovery.mixins.json\r\n\r\n'
files['assets/knockedback/player_animation/wounded.animation.json']=(R/'src/main/resources/wounded.animation.json').read_bytes()
out=R/'dist/knockedback-1.6.2-empirecraft.2.jar';archive(out,files)
out.with_suffix('.jar.sha256').write_text(hashlib.sha256(out.read_bytes()).hexdigest()+'  '+out.name+'\n')
with zipfile.ZipFile(source) as z:
 retained=[n for n in z.namelist() if n.endswith('.class') and n not in modified]
 assert all(files[n]==z.read(n) for n in retained)
(R/'audit/build.json').write_text(json.dumps(dict(upstream_sha256=sha,sha256=hashlib.sha256(out.read_bytes()).hexdigest(),retained_original_classes=len(retained),modified_original_classes=modified),indent=2)+'\n')
print('BUILD_PASS',out)
