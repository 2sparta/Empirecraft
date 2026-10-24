#!/usr/bin/env python3
"""Transform actual Create classes with actual VS mixins without launching a world."""
import argparse
import json
import hashlib
import io
import zipfile
import os
from pathlib import Path
import shutil
import subprocess

ROOT=Path(__file__).resolve().parent

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--runtime',type=Path,required=True,help='Installed Mohist/Forge runtime directory')
    p.add_argument('--vs',type=Path,required=True)
    p.add_argument('--create',type=Path,required=True)
    p.add_argument('--dependencies',type=Path,help='Optional additional test dependency JAR directory')
    args=p.parse_args()
    libs=sorted((args.runtime/'libraries').rglob('*.jar'))
    libs.sort(key=lambda f:(0 if f.name=='forge-1.20.1-47.4.23-server.jar' else 1,str(f)))
    classes=ROOT/'compat-build/classes'
    if classes.exists():shutil.rmtree(classes)
    classes.mkdir(parents=True)
    embedded=ROOT/'compat-build/embedded'
    if embedded.exists():shutil.rmtree(embedded)
    embedded.mkdir(parents=True)
    seen=set()
    def unpack(raw):
        with zipfile.ZipFile(io.BytesIO(raw)) as archive:
            for name in archive.namelist():
                if not name.endswith('.jar'):continue
                data=archive.read(name);digest=hashlib.sha256(data).hexdigest()
                if digest in seen:continue
                seen.add(digest)
                (embedded/(digest[:16]+'-'+Path(name).name)).write_bytes(data)
                unpack(data)
    unpack(args.create.read_bytes());unpack(args.vs.read_bytes())
    extra=sorted(args.dependencies.glob('*.jar')) if args.dependencies else []
    jars=[args.create,args.vs,*extra,*sorted(embedded.glob('*.jar')),*libs]
    cp=os.pathsep.join(map(str,jars))
    subprocess.run(['java','com.sun.tools.javac.Main','--release','17','-proc:none','-cp',cp,'-d',str(classes),*map(str,(ROOT/'compat').rglob('*.java'))],check=True)
    services=classes/'META-INF/services';services.mkdir(parents=True,exist_ok=True)
    (services/'org.spongepowered.asm.service.IMixinService').write_text('compatcheck.OfflineMixinService\n')
    (services/'org.spongepowered.asm.service.IGlobalPropertyService').write_text('compatcheck.OfflineProperties\n')
    config=dict(required=True,minVersion='0.8.5',package='org.valkyrienskies.mod.mixin.mod_compat.create.blockentity',compatibilityLevel='JAVA_17',refmap='valkyrienskies-120-common-refmap.json',mixins=['MixinNozzleBlockEntity','MixinEncasedFanTileEntity','MixinSchematicannonBlockEntity'],injectors=dict(defaultRequire=1))
    (classes/'create-vs-regression.mixins.json').write_text(json.dumps(config))
    for side in ['server','client']:
        run=subprocess.run(['java','-Xverify:all','-ea','-cp',str(classes)+os.pathsep+cp,'compatcheck.CreateMixinTest',side],text=True,capture_output=True)
        (ROOT/'compat-build'/('mixin-'+side+'.log')).write_text(run.stdout+run.stderr)
        print(run.stdout,end='');print(run.stderr,end='')
        if run.returncode:raise SystemExit(run.returncode)

if __name__=='__main__':main()
