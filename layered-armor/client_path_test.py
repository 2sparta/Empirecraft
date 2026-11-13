#!/usr/bin/env python3
"""Reproduce the uploaded client crash and verify real Forge resource discovery."""
import argparse,hashlib,json,os,shutil,subprocess
from pathlib import Path
import build

ROOT=build.ROOT;REPO=build.REPO
def main():
    p=argparse.ArgumentParser();p.add_argument('--sdk',type=Path,required=True);p.add_argument('--java-home',type=Path,required=True);p.add_argument('--forge-loader-28',type=Path,required=True);a=p.parse_args()
    out=ROOT/'build/client-path';classes=out/'test-classes';classes.mkdir(exist_ok=True)
    cp=(out/'classpath.txt').read_text().split(os.pathsep)
    original=a.sdk/'client-original.jar'
    if original.exists():cp.append(str(original))
    source=ROOT/'tests/ArmorPackFinderTest.java';base=out/'baseline-named.jar';candidate=out/'candidate-named.jar'
    compile_cp=os.pathsep.join(map(str,[candidate,*cp]))
    subprocess.run([str(a.java_home/'bin/javac'),'--release','17','-proc:none','-encoding','UTF-8','-cp',compile_cp,'-d',str(classes),str(source),str(ROOT/'tests/ArmorLanguageTest.java')],check=True)
    loader23=next((REPO/'server/libraries/net/minecraftforge/fmlloader').rglob('fmlloader-1.20.1-47.4.23.jar'))
    cp=[p for p in cp if 'fmlloader-' not in Path(p).name]
    results=[]
    for loader in [loader23,a.forge_loader_28.resolve()]:
        for mode,addon in [('baseline',base),('fixed',candidate)]:
            artifact=REPO/'mods/empirecraft-layered-armor-1.0.3.jar' if mode=='baseline' else ROOT/'dist'/build.NAME
            if not artifact.exists():artifact=next((REPO/'disabled-mods').rglob(artifact.name))
            folder=out/'game Українська with spaces'/loader.stem/mode/'mods';folder.mkdir(parents=True,exist_ok=True)
            installed=folder/artifact.name;shutil.copyfile(artifact,installed)
            version=loader.stem.removeprefix('fmlloader-1.20.1-')
            args=[str(a.java_home/'bin/java'),'--add-exports=java.base/sun.security.util=ALL-UNNAMED','--add-opens=java.base/java.util.jar=ALL-UNNAMED','--add-opens=java.base/java.lang.invoke=ALL-UNNAMED','-ea','-cp',os.pathsep.join(map(str,[classes,loader,addon,*cp])),'ArmorPackFinderTest',str(installed),mode,version]
            run=subprocess.run(args,capture_output=True,text=True);log=out/(loader.stem+'-'+mode+'.log');log.write_text(run.stdout+run.stderr)
            marker='ARMOR_PACK_BASELINE_REPRODUCED' if mode=='baseline' else 'ARMOR_PACK_FINDER_PASS'
            if run.returncode or marker not in run.stdout:
                print((run.stdout+run.stderr)[-9000:]);raise SystemExit('Pack finder regression failed: '+str(log))
            lines=[line for line in run.stdout.splitlines() if marker in line];print('\n'.join(lines))
            results.append(dict(loader=loader.name,loader_sha256=build.sha(loader),profile=mode,markers=lines,log_sha256=build.sha(log)))
    language=subprocess.run([str(a.java_home/'bin/java'),'-ea','-cp',os.pathsep.join(map(str,[classes,candidate,*cp,loader23])),'ArmorLanguageTest',str(ROOT/'dist'/build.NAME)],capture_output=True,text=True)
    log=out/'language.log';log.write_text(language.stdout+language.stderr)
    if language.returncode or 'ARMOR_LANGUAGE_PASS' not in language.stdout:print(language.stdout+language.stderr);raise SystemExit('Language regression failed')
    print(language.stdout.strip())
    report=dict(status='passed',artifact=build.NAME,sha256=build.sha(ROOT/'dist'/build.NAME),source_sha256=build.sha(source),
        tests=results,language=[line for line in language.stdout.splitlines() if 'ARMOR_LANGUAGE_PASS' in line],graphical_client_tested=False,
        real_forge_mod_file=True,real_mod_list=True,actual_client_pack_finder_callback=True,production_world_opened=False)
    (ROOT/'audit/client-path.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
if __name__=='__main__':main()
