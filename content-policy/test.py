#!/usr/bin/env python3
"""Verify installed native Mixin targets and policy against the actual pinned mods."""
import argparse,os,shutil,subprocess
from pathlib import Path
from build import ROOT,REPO,classpath,compiler

def main():
    p=argparse.ArgumentParser();p.add_argument('--classpath-dir',action='append',type=Path);args=p.parse_args()
    jars=classpath(args.classpath_dir or [REPO/'server/libraries',REPO/'server/mods'])
    tests=ROOT/'build/test-classes'
    if tests.exists():shutil.rmtree(tests)
    tests.mkdir(parents=True)
    cp=os.pathsep.join(map(str,[ROOT/'build/classes',ROOT/'src/main/resources',*jars]))
    subprocess.run([*compiler(),'--release','17','-proc:none','-encoding','UTF-8','-cp',cp,'-d',str(tests),*map(str,(ROOT/'src/test/java').rglob('*.java'))],check=True)
    services=tests/'META-INF/services';services.mkdir(parents=True)
    (services/'org.spongepowered.asm.service.IMixinService').write_text('guardtest.OfflineMixinService\n')
    (services/'org.spongepowered.asm.service.IGlobalPropertyService').write_text('guardtest.OfflineProperties\n')
    home=os.environ.get('JAVA_HOME');java=str(Path(home)/'bin/java') if home else 'java'
    for name in ['ContentPolicyTest','ContentMixinTest']:
        subprocess.run([java,'-ea','-cp',str(tests)+os.pathsep+cp,'guardtest.'+name],check=True)

if __name__=='__main__':main()
