#!/usr/bin/env python3
"""Install a separate checksum-pinned Forge test runtime."""
import argparse,hashlib,subprocess,urllib.request,shutil
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('directory',type=Path)
a=p.parse_args();root=a.directory.resolve();root.mkdir(parents=True,exist_ok=True)
installer=root/'forge-installer.jar'
expected='75cfcb11f60cc641dc83757c1357c08c9bdba255ec529feb115c5e89a59cf75a'
if not installer.exists():
    relative='net/minecraftforge/forge/1.20.1-47.4.23/forge-1.20.1-47.4.23-installer.jar'
    failures=[]
    for base in ['https://maven.minecraftforge.net/', 'https://files.minecraftforge.net/maven/']:
        url=base+relative
        try:
            request=urllib.request.Request(url,headers={'User-Agent':'Mozilla/5.0 Empirecraft/1.0 (Forge integration tests)'})
            with urllib.request.urlopen(request,timeout=90) as r: installer.write_bytes(r.read())
            break
        except Exception as error:
            failures.append(str(error))
            if shutil.which('curl'):
                result=subprocess.run(['curl','-fL','--retry','2','--max-time','120','-o',str(installer),url])
                if result.returncode==0:break
    else:raise SystemExit('Unable to download pinned Forge installer: '+ '; '.join(failures))
assert hashlib.sha256(installer.read_bytes()).hexdigest()==expected,'Forge installer checksum mismatch'
subprocess.run(['java','-jar',str(installer),'--installServer'],cwd=root,check=True)
