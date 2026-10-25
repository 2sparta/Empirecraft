#!/usr/bin/env python3
"""Build a server-only Java 17 mod against the installed Forge/Mohist libraries."""
import argparse,hashlib,os,shutil,subprocess,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent
REPO=ROOT.parent
NAME='empirecraft-content-policy-1.0.0.jar'

def classpath(roots):
    jars={f.resolve() for root in roots for f in root.rglob('*.jar') if not f.name.startswith('empirecraft-content-policy-')}
    def order(p):
        if p.name=='forge-1.20.1-47.4.23-server.jar':return 0
        if p.name in ['asm-9.7.jar','asm-tree-9.7.jar','asm-analysis-9.7.jar','asm-util-9.7.jar','asm-commons-9.7.jar','mixin-0.8.5.jar']:return 1
        return 2
    return sorted(jars,key=lambda p:(order(p),str(p)))

def compiler():
    home=os.environ.get('JAVA_HOME')
    return [str(Path(home)/'bin/javac')] if home else [shutil.which('javac')] if shutil.which('javac') else ['java','com.sun.tools.javac.Main']

def main():
    p=argparse.ArgumentParser();p.add_argument('--classpath-dir',action='append',type=Path);args=p.parse_args()
    jars=classpath(args.classpath_dir or [REPO/'server/libraries',REPO/'server/mods'])
    output=ROOT/'build/classes'
    if output.exists():shutil.rmtree(output)
    output.mkdir(parents=True)
    flags=['--release','17','-proc:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,jars)),'-d',str(output),*map(str,sorted((ROOT/'src/main/java').rglob('*.java')))]
    arg=ROOT/'build/javac.args';arg.write_text('\n'.join('"'+v.replace('\\','\\\\').replace('"','\\"')+'"' for v in flags)+'\n')
    subprocess.run([*compiler(),'@'+str(arg)],check=True)
    files={p.relative_to(output).as_posix():p.read_bytes() for p in output.rglob('*.class')}
    resources=ROOT/'src/main/resources';files.update({p.relative_to(resources).as_posix():p.read_bytes() for p in resources.rglob('*') if p.is_file()})
    files['META-INF/MANIFEST.MF']=b'Manifest-Version: 1.0\r\nMixinConfigs: empirecraft_content_policy.mixins.json\r\nImplementation-Version: 1.0.0\r\n\r\n'
    target=REPO/'server/mods'/NAME;target.parent.mkdir(exist_ok=True,parents=True)
    with zipfile.ZipFile(target,'w') as out:
        for name,data in sorted(files.items()):
            info=zipfile.ZipInfo(name,(2026,10,25,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;out.writestr(info,data)
    sha=hashlib.sha256(target.read_bytes()).hexdigest();target.with_suffix('.jar.sha256').write_text(sha+'  '+NAME+'\n');print('PASS built server content policy;',sha)

if __name__=='__main__':main()
