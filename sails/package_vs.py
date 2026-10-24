#!/usr/bin/env python3
"""Package the exact tested VS patch without rebuilding Minecraft or Sails."""
from pathlib import Path
import hashlib,json,os,shutil,subprocess,urllib.request,zipfile
ROOT=Path(__file__).resolve().parent
REPO=ROOT.parent
EXPECTED='7f6260926a6be99fba2c2bb46e27eb2c5b582042ec2692dfb94295fec9dcfa0a'
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def main():
    build=ROOT/'build';build.mkdir(exist_ok=True)
    item=json.loads((ROOT/'build-inputs.json').read_text())['downloads']['vs-original.jar']
    original=build/'vs-original.jar'
    if not original.exists() or sha(original)!=item['sha256']:
        temporary=build/'vs-original.download'
        try:
            with urllib.request.urlopen(item['url'],timeout=60) as response:temporary.write_bytes(response.read())
        except Exception:subprocess.run(['curl','-fsSL','--max-time','90','-o',temporary,item['url']],check=True)
        if sha(temporary)!=item['sha256']:raise SystemExit('Original VS checksum mismatch')
        os.replace(temporary,original)
    asm=next((REPO/'server/libraries/org/ow2/asm/asm/9.9.1').glob('*.jar'))
    tools=build/'tools';tools.mkdir(exist_ok=True)
    java=Path(os.environ['JAVA_HOME'])/'bin/java' if os.environ.get('JAVA_HOME') else 'java'
    javac=Path(os.environ['JAVA_HOME'])/'bin/javac' if os.environ.get('JAVA_HOME') else 'javac'
    subprocess.run(list(map(str,[javac,'--release','17','-cp',asm,'-d',tools,ROOT/'tools/PatchVS.java'])),check=True)
    output=build/'valkyrienskies-120-2.4.11-empirecraft.1.jar'
    subprocess.run(list(map(str,[java,'-cp',os.pathsep.join(map(str,[tools,asm])),'PatchVS',original,output])),check=True)
    if sha(output)!=EXPECTED:raise SystemExit('VS patch differs from the tested build; use Zulu JDK 21.0.13')
    with zipfile.ZipFile(output) as z:assert z.testzip() is None
    for folder in [REPO/'mods',REPO/'server/mods']:
        folder.mkdir(parents=True,exist_ok=True);shutil.copy2(output,folder/output.name)
        (folder/(output.name+'.sha256')).write_text(EXPECTED+'  '+output.name+'\n')
    print('Packaged the tested Valkyrien Skies patch:',EXPECTED)
if __name__=='__main__':main()
