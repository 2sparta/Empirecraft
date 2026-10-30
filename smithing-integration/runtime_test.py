#!/usr/bin/env python3
"""Run regression contracts in the installed Rhino, with the same Fastutil version as the crash log."""
from pathlib import Path
import hashlib,json,os,shutil,subprocess,urllib.request
ROOT=Path(__file__).resolve().parent
REPO=ROOT.parent
RHINO='rhino-forge-2001.2.3-build.10.jar'
RHINO_SHA='fed2211429301bf043864183cab9ab8e92d4cc4dbb9e488ce6c75217c54584a6'
# Maven Central coordinates and fixed SHA-256. Test-only downloads stay in ignored build/.
DEPENDENCIES=[
 ('fastutil-8.5.12.jar','it/unimi/dsi/fastutil/8.5.12/fastutil-8.5.12.jar','b5543ee08d062d551cf0a5c9bc0fb70588b0382079029ba48941fa9c9be8a5d4'),
 ('commons-lang3-3.12.0.jar','org/apache/commons/commons-lang3/3.12.0/commons-lang3-3.12.0.jar','d919d904486c037f8d193412da0c92e22a9fa24230b9d67a57855c5c31c7e94e'),
 ('gson-2.10.jar','com/google/code/gson/gson/2.10/gson-2.10.jar','0cdd163ce3598a20fc04eee71b140b24f6f2a3b35f0a499dbbdd9852e83fbfaf'),
]

def run(jars):
    rhino=jars/RHINO
    assert hashlib.sha256(rhino.read_bytes()).hexdigest()==RHINO_SHA,'Rhino differs from the uploaded server version'
    deps=ROOT/'build/deps';deps.mkdir(parents=True,exist_ok=True)
    paths=[rhino]
    for name,url,sha in DEPENDENCIES:
        path=deps/name
        if not path.exists():
            with urllib.request.urlopen('https://repo.maven.apache.org/maven2/'+url,timeout=90) as response:data=response.read()
            assert hashlib.sha256(data).hexdigest()==sha,'Dependency SHA-256 mismatch: '+name
            path.write_bytes(data)
        assert hashlib.sha256(path.read_bytes()).hexdigest()==sha,'Corrupt test dependency: '+name
        paths.append(path)
    classes=ROOT/'build/rhino-classes';classes.mkdir(exist_ok=True)
    home=os.environ.get('JAVA_HOME')
    java=str(Path(home)/'bin/java') if home else 'java'
    javac=[str(Path(home)/'bin/javac')] if home else ([shutil.which('javac')] if shutil.which('javac') else [java,'com.sun.tools.javac.Main'])
    cp=os.pathsep.join(map(str,paths))
    subprocess.run([*javac,'--release','17','-encoding','UTF-8','-cp',cp,'-d',str(classes),str(ROOT/'tests/RhinoRuntimeTest.java')],check=True)
    subprocess.run([java,'-ea','-cp',str(classes)+os.pathsep+cp,'RhinoRuntimeTest',str(REPO)],check=True)
    assert json.loads((ROOT/'build/rhino-recipes.json').read_text())==json.loads((ROOT/'build/recipes.json').read_text()),'Rhino and Node recipe results differ'
    print('PASS all resulting recipes agree between installed Rhino and the independent Node contract suite')
    return dict(rhino='2001.2.3-build.10',rhino_sha256=RHINO_SHA,fastutil='8.5.12',checks=['both original failures reproduced','real Java map/list interop','all installed recipe fixtures equal Node output','retired table override stays absent'])

if __name__=='__main__':run(REPO/'mods')
