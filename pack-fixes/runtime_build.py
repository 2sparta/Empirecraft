#!/usr/bin/env python3
"""Overlay runtime fixes on the exact installed, already filtered Pack Fixes release."""
import argparse,hashlib,json,os,shutil,subprocess,zipfile
from pathlib import Path
import build
from ponder_policy import generate
ROOT=build.ROOT;REPO=build.REPO
ADDED=['PaletteEntries','PonderSafety','TickStallReporter','JeiInvocations','mixin/PaletteEntriesMixin','mixin/HorseJumpReadMixin','mixin/client/PonderSceneSafetyMixin','mixin/client/CreateDescriptionSafetyMixin','mixin/client/CreateJeiEmptyFilterMixin']
def main():
    p=argparse.ArgumentParser();p.add_argument('--sdk',type=Path,required=True);p.add_argument('--java-home',type=Path,required=True);a=p.parse_args()
    generate(a.sdk/'client-original.jar');work=ROOT/'build/runtime';work.mkdir(parents=True,exist_ok=True)
    base=REPO/'mods/empirecraft-pack-fixes-1.0.1.jar'
    if not base.exists():base=next((REPO/'disabled-mods').rglob(base.name))
    classes=work/'classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
    libs=sorted((REPO/'server/libraries').rglob('*.jar'));nested=sorted((REPO/'tfc-integration/build/nested').glob('*.jar'))
    cp=[a.sdk/'runtime-named.jar',a.sdk/'client-named.jar',base,*nested,*libs]
    java=a.java_home/'bin/java';sources=[ROOT/'src/main/java/ua/empirecraft/packfixes'/(name+'.java') for name in ADDED]
    subprocess.run([str(a.java_home/'bin/javac'),'--release','17','-proc:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,cp)),'-d',str(classes),*map(str,sources)],check=True)
    named=work/'additions-named.jar';srg=work/'additions-srg.jar';srg.unlink(missing_ok=True)
    build.archive(named,{p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')})
    asm=sorted((REPO/'server/libraries/org/ow2/asm').rglob('*.jar'));remapper=[a.sdk/'tiny-remapper.jar',a.sdk/'mapping-io.jar',*asm]
    subprocess.run([str(java),'-cp',os.pathsep.join(map(str,remapper)),'dev.architectury.tinyremapper.Main',str(named),str(srg),str(a.sdk/'named-srg.tiny'),'named','srg',*map(str,cp),'--ignoreConflicts'],check=True)
    with zipfile.ZipFile(base) as z:entries={n:z.read(n) for n in z.namelist()}
    before=dict(entries)
    with zipfile.ZipFile(srg) as z:entries.update({n:z.read(n) for n in z.namelist() if n.endswith('.class')})
    for path in ['META-INF/mods.toml','empirecraft_pack_fixes.mixins.json','ponder-safety.json']:entries[path]=(ROOT/'src/main/resources'/path).read_bytes()
    output=ROOT/'dist'/build.NAME;build.archive(output,entries);output.with_suffix('.jar.sha256').write_text(build.sha(output)+'  '+output.name+'\n')
    retained=[n for n in before if n.endswith('.class')]
    assert all(entries[n]==before[n] for n in retained),'Existing fixes changed'
    report={'baseline':base.name,'baseline_sha256':build.sha(base),'file':output.name,'sha256':build.sha(output),'retained_classes':len(retained),'retained_classes_byte_identical':True,'added_classes':sorted(n for n in entries if n.endswith('.class') and n not in before),'changed_resources':sorted(n for n in before if entries[n]!=before[n])}
    (ROOT/'audit/runtime-build.json').write_text(json.dumps(report,indent=2)+'\n')
    print('RUNTIME_FIXES_BUILD_PASS',output.name,report['sha256'])
if __name__=='__main__':main()
