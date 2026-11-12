#!/usr/bin/env python3
"""Verify actual volume logic, config bounds and byte identity outside the four changed classes."""
import argparse,hashlib,json,os,shlex,subprocess,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent
def main():
    p=argparse.ArgumentParser();p.add_argument('--java-home',type=Path,required=True);a=p.parse_args()
    build=ROOT/'build/hotfix';flags=shlex.split((build/'javac.args').read_text());cp=os.pathsep.join([str(build/'classes'),flags[flags.index('-cp')+1]])
    dest=build/'tests';dest.mkdir(exist_ok=True)
    subprocess.run([str(a.java_home/'bin/javac'),'--release','17','-proc:none','-cp',cp,'-d',str(dest),str(ROOT/'tests/FloodModelTest.java'),str(ROOT/'tests/ConfigTest.java')],check=True)
    markers=[]
    for mainclass in ['FloodModelTest','com.quintonc.vs_sails.config.ConfigTest']:
        result=subprocess.run([str(a.java_home/'bin/java'),'-cp',str(dest)+os.pathsep+cp,mainclass],check=True,text=True,capture_output=True)
        print(result.stdout,end='');markers.extend(result.stdout.splitlines())
    target=ROOT/'dist/vs_sails-1.20.1-0.3.2-empirecraft.19.1-forge.jar';report=json.loads((ROOT/'passenger-puddles-build.json').read_text())
    assert hashlib.sha256(target.read_bytes()).hexdigest()==report['sha256']
    with zipfile.ZipFile(ROOT/'native-inputs/vs_sails-1.20.1-0.3.2-empirecraft.19-forge.jar') as old,zipfile.ZipFile(target) as new:
        unchanged=[n for n in old.namelist() if n.endswith('.class') and n not in report['overlaid_classes']]
        assert len(unchanged)==report['unchanged_class_count'] and all(old.read(n)==new.read(n) for n in unchanged)
    report['pure_tests']=markers;report['native_sync_proof']='tfc-integration/audit/current-update.json'
    (ROOT/'passenger-puddles-validation.json').write_text(json.dumps(report,indent=2)+'\n')
    print('SAILS_HOTFIX_TEST_PASS unchanged_classes='+str(len(unchanged)))
if __name__=='__main__':main()
