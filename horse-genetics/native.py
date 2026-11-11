#!/usr/bin/env python3
"""Exercise real DLO breeding and legacy NBT in a temporary full Mohist modpack without RHG."""
import importlib.util,json,os,shlex,subprocess,zipfile
from pathlib import Path
import build
ROOT=build.ROOT;REPO=ROOT.parent;SDK=build.build.SDK;OUT=build.build.BUILD
JAVA=Path(os.environ.get('JAVA_HOME','/usr'))/'bin/java';JAVAC=JAVA.with_name('javac')
flags=shlex.split((SDK/'javac.args').read_text());cp=os.pathsep.join(map(str,[OUT/'mod-classes',*flags[flags.index('-cp')+1].split(os.pathsep)]))
classes=OUT/'native-probe';classes.mkdir(parents=True,exist_ok=True)
subprocess.run([str(JAVAC),'--release','17','-proc:none','-encoding','UTF-8','-cp',cp,'-d',str(classes),str(ROOT/'tests/EquineProbe.java')],check=True)
source=OUT/'probe-named.jar';target=OUT/'probe-srg.jar'
with zipfile.ZipFile(source,'w') as z:
 for p in classes.rglob('*.class'):z.write(p,p.relative_to(classes).as_posix())
target.unlink(missing_ok=True)
remapper=os.pathsep.join(map(str,[SDK/'tiny-remapper.jar',SDK/'mapping-io.jar',*sorted((REPO/'server/libraries/org/ow2/asm').rglob('*.jar'))]))
subprocess.run([str(JAVA),'-Xmx1G','-cp',remapper,'dev.architectury.tinyremapper.Main',str(source),str(target),str(SDK/'named-srg.tiny'),'named','srg',str(SDK/'runtime-named.jar'),'--ignoreConflicts'],check=True)
with zipfile.ZipFile(target,'a') as z:z.writestr('META-INF/mods.toml','modLoader="javafml"\nloaderVersion="[47,)"\nlicense="MIT"\n[[mods]]\nmodId="empirecraft_equine_probe"\nversion="1"\n')
# The launch helper belongs to the armor module; expose its API constants for the shared runner.
build.BUILD=OUT;build.SDK=SDK;build.NAME=build.build.NAME
spec=importlib.util.spec_from_file_location('armor_native',REPO/'layered-armor/native.py');native=importlib.util.module_from_spec(spec);spec.loader.exec_module(native);native.BUILD=OUT
result=native.run('server','/usr/bin/java',target,json.loads((REPO/'sails/smoke-runtime.json').read_text()),ROOT/'dist'/build.build.NAME,
 additional_addons=(REPO/'layered-armor/dist/empirecraft-layered-armor-1.0.2.jar',),exclude=('realistic_horse_genetics-','empirecraft-fauna-'),report_name='equine-probe.json')
print('EQUINE_NATIVE_VERIFIED',result['assertions'])
