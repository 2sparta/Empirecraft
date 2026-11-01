#!/usr/bin/env python3
"""Compile Java 17 SRG code against a real Forge/hybrid 1.20.1 installation, without bundled API stubs."""
import argparse,hashlib,os,shutil,subprocess,zipfile
from pathlib import Path
r=Path(__file__).resolve().parent
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--classpath-dir',type=Path,action='append',required=True)
a=p.parse_args()
jars=sorted({f.resolve() for d in a.classpath_dir for f in d.rglob('*.jar') if f.is_file() and not f.name.startswith('townyrecruits-')})
jars.sort(key=lambda p:(0 if p.name.startswith('forge-1.20.1-') and p.name.endswith('-server.jar') else 1,str(p)))
for fragment in ['server-1.20.1-20230612.114412-srg.jar','recruits-1.20.1-1.15.0.jar','musketmod-1.20.1-forge-1.5.4.jar']:
 if not any(f.name==fragment for f in jars):raise SystemExit('Missing real dependency: '+fragment)
classes=r/'build/classes'
if classes.exists():shutil.rmtree(classes)
classes.mkdir(parents=True)
compiler=str(Path(os.environ['JAVA_HOME'])/'bin/javac') if os.environ.get('JAVA_HOME') else 'javac'
flags=['--release','17','-proc:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,jars)),'-d',str(classes),*map(str,sorted((r/'src/main/java').rglob('*.java')))]
argfile=r/'build/javac.args';argfile.write_text('\n'.join('"'+s.replace('\\','\\\\').replace('"','\\"')+'"' for s in flags))
subprocess.run([compiler,'@'+str(argfile)],check=True)
files={str(f.relative_to(classes)):f.read_bytes() for f in classes.rglob('*.class')}
res=r/'src/main/resources';files.update({str(f.relative_to(res)):f.read_bytes() for f in res.rglob('*') if f.is_file()})
files['META-INF/MANIFEST.MF']=b'Manifest-Version: 1.0\r\nMixinConfigs: townyrecruits.mixins.json\r\nImplementation-Version: 1.0.1\r\n\r\n'
if (r/'LICENSE').exists():files['META-INF/LICENSE']= (r/'LICENSE').read_bytes()
(r/'dist').mkdir(exist_ok=True);out=r/'dist/townyrecruits-1.0.1.jar'
with zipfile.ZipFile(out,'w') as z:
 for name,data in sorted(files.items()):
  i=zipfile.ZipInfo(name.replace(os.sep,'/'),(2026,11,1,0,0,0));i.compress_type=zipfile.ZIP_DEFLATED;i.external_attr=0o100644<<16;z.writestr(i,data)
out.with_suffix('.jar.sha256').write_text(hashlib.sha256(out.read_bytes()).hexdigest()+'  '+out.name+'\n')
print(out)
