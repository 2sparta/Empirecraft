#!/usr/bin/env python3
"""Verify packaged game code, political/ship history, packet bounds and the native map migration."""
import argparse,hashlib,json,os,subprocess,xml.etree.ElementTree as ET
from pathlib import Path
from build import VERSION

ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jdk',type=Path,default=Path('/workspace/.empirecraft/jdk-17'))
    parser.add_argument('--junit',type=Path,required=True)
    parser.add_argument('--survey',action='store_true',help='Check the focused bridge update built by build_survey.py')
    args=parser.parse_args();build=ROOT/'build';logs=build/'logs';logs.mkdir(parents=True,exist_ok=True)
    assert hashlib.sha256(args.junit.read_bytes()).hexdigest()=='1455afad75d4eb4f44b493febc4a1bc32bc5f02eb25679361abd55e0f6421050'
    def run(command,name):
        result=subprocess.run(list(map(str,command)),capture_output=True,text=True);(logs/(name+'.log')).write_text(result.stdout+result.stderr)
        if result.returncode:raise RuntimeError(name+'\n'+result.stdout[-6000:]+result.stderr[-8000:])
        print(name+'_PASS',flush=True);return result.stdout
    staged=build/'survey' if args.survey else ROOT/'dist'
    bridge=staged/f'empirecraft-towny-journeymap-{VERSION}.jar'
    runtime=(staged if args.survey else build)/'classpath.txt'
    cp=os.pathsep.join(map(str,[bridge,ROOT/'dist/recruits-1.20.1-1.15.2-empirecraft.1.jar',ROOT/'dist/TownyRecruits.jar',args.junit]))+os.pathsep+runtime.read_text()
    tests=build/'tests';tests.mkdir(exist_ok=True);source=REPO/'bridge/src/test/java'
    suites=['BanditTerritoriesTest','ClaimKnowledgeTest','ClaimGeometryTest','OverlaySyncTest','KnowledgeStoreTest','TownyAccessTest']
    paths=[source/'ua/empirecraft/townymap'/(n+'.java') for n in suites]+list((ROOT/'tests').glob('*.java'))
    paths+=list((source/'org').rglob('*.java'))+list((source/'com').rglob('*.java'))
    compiler=[args.jdk/'bin/javac','--release','17','-proc:none','-encoding','UTF-8']
    run([*compiler,'-cp',cp,'-d',tests,*paths],'tests-compile')
    output=run([args.jdk/'bin/java','-jar',args.junit,'execute','--class-path',str(tests)+os.pathsep+cp,
                '--select-package','ua.empirecraft.townymap','--details','summary','--reports-dir',build/'junit'],'unit-tests');print(output)
    asm=sorted((REPO/'server/libraries/org/ow2/asm').glob('*/9.7/*.jar'));asmcp=os.pathsep.join(map(str,asm));tools=build/'tools';tools.mkdir(exist_ok=True)
    run([*compiler,'-cp',asmcp,'-d',tools,ROOT/'tools/StrategyLinkageProbe.java'],'linkage-compile')
    linkage=run([args.jdk/'bin/java','-cp',str(tools)+os.pathsep+asmcp,'StrategyLinkageProbe',REPO],'linkage');print(linkage)
    reports=[ET.parse(p).getroot() for p in (build/'junit').glob('TEST-*.xml')]
    assert all(int(r.get('failures','0'))==0 and int(r.get('errors','0'))==0 for r in reports)
    report_path=staged/'build-validation.json'
    report={'bridge_sha256':hashlib.sha256(bridge.read_bytes()).hexdigest()} if args.survey else json.loads(report_path.read_text())
    report.update(status='passed',tests_passed=sum(int(r.get('tests','0')) for r in reports),linkage=linkage.strip())
    report_path.write_text(json.dumps(report,indent=2)+'\n');print('STRATEGY_MAP_CHECK_PASS')

if __name__=='__main__':main()
