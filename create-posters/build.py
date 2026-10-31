#!/usr/bin/env python3
"""Build the Forge 1.20.1 addon against a local, installed Forge SRG runtime (no downloads)."""
import argparse, hashlib, json, os, shutil, subprocess, zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent
NAME='create-drafting-posters-1.20.1-1.4.0.jar'

def call(args,log=None):
    result=subprocess.run(args,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
    if log:log.write_text(result.stdout,encoding='utf-8')
    print(result.stdout,end='')
    if result.returncode:raise SystemExit(result.returncode)

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--runtime',type=Path,required=True,help='Installed Forge server directory containing libraries/ and mods/.')
    p.add_argument('--create',type=Path,required=True,help='Create 1.20.1 6.0.8-medieval.4 JAR supplied by the user.')
    p.add_argument('--output',type=Path,default=ROOT/'dist')
    p.add_argument('--test',action='store_true')
    a=p.parse_args();runtime=a.runtime.resolve();source=a.create.resolve();out=a.output.resolve()
    pins=json.loads((ROOT/'dependencies.json').read_text())
    if hashlib.sha256(source.read_bytes()).hexdigest()!=pins['create_sha256']:raise SystemExit('Create does not match dependencies.json. Use the pinned 6.0.8-medieval.4 JAR.')
    libs=runtime/'libraries'
    required=['forge-1.20.1-47.4.23-server.jar','server-1.20.1-20230612.114412-srg.jar']
    jars=sorted(libs.rglob('*.jar'))
    for n in required:
        if not any(j.name==n for j in jars):raise SystemExit('Required local compile dependency missing: '+n)
    jars.sort(key=lambda j:(0 if j.name==required[0] else 1 if j.name==required[1] else 2,str(j)))
    jars=[source,*jars,*sorted((runtime/'mods').glob('*.jar'))]
    build=ROOT/'build';classes=build/'classes';testclasses=build/'tests';out.mkdir(parents=True,exist_ok=True)
    if classes.exists():shutil.rmtree(classes)
    classes.mkdir(parents=True)
    embedded=[]
    for jar in [source,*[j for j in jars if 'ponder' in j.name.lower()]]:
        with zipfile.ZipFile(jar) as z:
            for n in z.namelist():
                if n.startswith('META-INF/jarjar/') and n.endswith('.jar'):
                    dest=build/'libs'/Path(n).name;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(z.read(n));embedded.append(dest)
    cp=os.pathsep.join(map(str,dict.fromkeys([*embedded,*jars])))
    (ROOT/'classpath.txt').write_text(cp)
    # Small API declarations fill the client classes omitted by a dedicated server.
    # Only ua/posters classes are packaged below; these declarations never ship as game classes.
    call(['python3',str(ROOT/'make_stubs.py')])
    compiler=['javac'] if shutil.which('javac') else ['java','com.sun.tools.javac.Main']
    helptext=subprocess.run([*compiler,'--help'],capture_output=True,text=True).stdout
    release=['--release','17'] if '17' in helptext.split('Supported releases:')[-1] else ['-source','17','-target','17','-Xlint:-options']
    flags=[*release,'-proc:none','-encoding','UTF-8','-cp',cp,'-d',str(classes),*map(str,sorted((ROOT/'compile-stubs').rglob('*.java'))),*map(str,sorted((ROOT/'src/main/java').rglob('*.java')))]
    argfile=build/'compile.args';argfile.write_text('\n'.join('"'+f.replace('\\','\\\\').replace('"','\\"')+'"' for f in flags)+'\n')
    compiled=subprocess.run([*compiler,'@'+str(argfile)],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
    if compiled.returncode and 'release version 17 not supported' in compiled.stdout:
        release=['-source','17','-target','17','-Xlint:-options']
        flags=release+flags[2:]
        argfile.write_text('\n'.join('"'+f.replace('\\','\\\\').replace('"','\\"')+'"' for f in flags)+'\n')
        compiled=subprocess.run([*compiler,'@'+str(argfile)],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
    (build/'compile.log').write_text(compiled.stdout)
    print(compiled.stdout,end='')
    if compiled.returncode:raise SystemExit(compiled.returncode)
    shutil.copytree(ROOT/'src/main/resources',classes,dirs_exist_ok=True)
    files={p.relative_to(classes).as_posix():p.read_bytes() for p in (classes/'ua/posters').rglob('*.class')}
    files.update({p.relative_to(ROOT/'src/main/resources').as_posix():p.read_bytes() for p in (ROOT/'src/main/resources').rglob('*') if p.is_file()})
    files['META-INF/LICENSE.txt']=(ROOT/'LICENSE').read_bytes()
    files['META-INF/MANIFEST.MF']=b'Manifest-Version: 1.0\r\nImplementation-Title: Create Drafting Posters\r\nImplementation-Version: 1.4.0\r\n\r\n'
    for n,b in files.items():
        if n.endswith('.class') and int.from_bytes(b[6:8],'big')!=61:raise SystemExit('Not Java 17 bytecode: '+n)
    assert not any(n.startswith(('net/','com/','org/')) for n in files)
    jar=out/NAME
    with zipfile.ZipFile(jar,'w',compression=zipfile.ZIP_DEFLATED) as z:
        for n,b in sorted(files.items()):
            info=zipfile.ZipInfo(n,(2026,10,31,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,b)
    (out/(NAME+'.sha256')).write_text(hashlib.sha256(jar.read_bytes()).hexdigest()+'  '+NAME+'\n')
    if a.test:
        testclasses.mkdir(exist_ok=True);testcp=str(classes)+os.pathsep+cp
        call([*compiler,*release,'-proc:none','-cp',testcp,'-d',str(testclasses),*map(str,sorted((ROOT/'tests').glob('*.java')))])
        call(['java','-cp',str(testclasses)+os.pathsep+testcp,'ua.posters.ProjectionTest',str(build)],build/'test.log')
        call(['java','-Djava.awt.headless=true','-cp',str(testclasses)+os.pathsep+testcp,'ua.posters.CompatibilityTest',str(build),str(runtime/'mods')],build/'compatibility.log')
        call(['java','-Djava.awt.headless=true','-cp',str(testclasses)+os.pathsep+testcp,'ua.posters.CBCRegressionTest',str(build),str(runtime/'mods')],build/'cbc-regression.log')
        call(['java','-Djava.awt.headless=true','-cp',str(testclasses)+os.pathsep+testcp,'ua.posters.SupplementariesRegressionTest',str(build),str(runtime/'mods')],build/'supplementaries-regression.log')
    print('Built '+str(jar))
if __name__=='__main__':main()
