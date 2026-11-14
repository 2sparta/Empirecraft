#!/usr/bin/env python3
"""Run Patchouli's actual client JSON adapters against the packaged book."""
import hashlib,json,os,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];REPO=ROOT.parent;sys.path.insert(0,str(ROOT))
import build

def main():
    sdk=REPO.parent/'work/tfc/toolchain';jdk=REPO.parent/'work/tfc/jdk';work=ROOT/'build/client-template';work.mkdir(exist_ok=True)
    asm=sorted((REPO/'server/libraries/org/ow2/asm').rglob('*.jar'));libs=sorted((REPO/'server/libraries').rglob('*.jar'))
    remapper=[sdk/'tiny-remapper.jar',sdk/'mapping-io.jar',*asm];named=work/'patchouli-named.jar'
    original=next((REPO/'mods').glob('Patchouli-*.jar'))
    if not named.exists():subprocess.run([str(jdk/'bin/java'),'-cp',os.pathsep.join(map(str,remapper)),'dev.architectury.tinyremapper.Main',str(original),str(named),str(sdk/'named-srg.tiny'),'srg','named',str(sdk/'runtime-srg.jar'),*map(str,libs),'--ignoreConflicts'],check=True)
    cp=[named,sdk/'runtime-named.jar',sdk/'client-named.jar',sdk/'client-original.jar',*libs]
    subprocess.run([str(jdk/'bin/javac'),'--release','17','-proc:none','-cp',os.pathsep.join(map(str,cp)),'-d',str(work),str(ROOT/'tests/ClientTemplateTest.java')],check=True)
    jar=ROOT/'dist'/build.NAME
    run=subprocess.run([str(jdk/'bin/java'),'-cp',os.pathsep.join(map(str,[work,*cp])),'ClientTemplateTest',str(jar)],capture_output=True,text=True)
    (work/'result.log').write_text(run.stdout+run.stderr)
    if run.returncode or 'HANDBOOK_CLIENT_TEMPLATE_PASS' not in run.stdout:print((run.stdout+run.stderr)[-6000:]);raise SystemExit('Native Patchouli client parsing failed')
    markers=[line for line in run.stdout.splitlines() if 'HANDBOOK_CLIENT_TEMPLATE_PASS' in line];print('\n'.join(markers))
    (ROOT/'audit/client-template.json').write_text(json.dumps({'status':'passed','sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'patchouli_sha256':hashlib.sha256(original.read_bytes()).hexdigest(),'markers':markers,'graphical_client_tested':False},indent=2)+'\n')
if __name__=='__main__':main()
