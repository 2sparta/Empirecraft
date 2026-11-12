#!/usr/bin/env python3
"""Package the built mod and complete source project without game/dependency JARs."""
import argparse,hashlib,json,zipfile
from pathlib import Path
import build

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--adm-list',type=Path);args=parser.parse_args()
    root=build.ROOT;version=build.VERSION;dist=root/'dist'/version
    jar=dist/('empirecraft-seasonal-leaves-'+version+'.jar')
    with zipfile.ZipFile(jar) as archive:
        assert ('version="'+version+'"') in archive.read('META-INF/mods.toml').decode()
        assert not any(n.startswith(('net/minecraft/','net/minecraftforge/','sereneseasons/','me/jellysquid/','ua/empirecraft/leaves/test/')) for n in archive.namelist())
    digest=build.sha(jar)
    assert (jar.with_suffix('.jar.sha256')).read_text()==digest+'  '+jar.name+'\n'
    row={'id':'empirecraft_seasonal_leaves','version':version,'file':jar.name,'path':'seasonal-leaves/dist/'+version+'/'+jar.name,'sides':['client','server'],'source':'local:seasonal-leaves','size':jar.stat().st_size,'sha256':digest,'sha512':hashlib.sha512(jar.read_bytes()).hexdigest(),'replaces':'empirecraft-seasonal-leaves-1.3.1.jar'}
    (root/'release.json').write_text(json.dumps([row],indent=2)+'\n')
    install={side+'/mods/'+jar.name+suffix:(jar.with_suffix('.jar.sha256').read_bytes() if suffix else jar.read_bytes()) for side in ['client','server'] for suffix in ['', '.sha256']}
    if args.adm_list:
        install['server/config/admanticheat/idfile/admanticheat_id_list.json']=args.adm_list.read_bytes()
    install['audit/evergreen-blocks.json']=(root/'audit/evergreen-blocks.json').read_bytes()
    validation=dist/'validation.json'
    if validation.exists():install['validation.json']=validation.read_bytes()
    for name in ['INSTALL-UA.md','README.md','PERFORMANCE.md','LICENSE']:install[name]=(root/name).read_bytes()
    build.jar(dist/('Empirecraft-Seasonal-Leaves-'+version+'-install.zip'),install)
    files=['README.md','INSTALL-UA.md','PERFORMANCE.md','LICENSE','.gitignore','build.gradle','settings.gradle','gradle.properties','gradlew','gradlew.bat','build.py','build-inputs.json','client-inputs.json','compat-inputs.json','smoke-runtime.json','test.py','mixin_test.py','growth_smoke.py','evergreen_smoke.py','performance_smoke.py','install.py','package.py','release.json','libs/README.md']
    for folder in ['src','gradle','tools','tests','audit']:
        files.extend(str(p.relative_to(root)) for p in (root/folder).rglob('*') if p.is_file())
    source=dist/('empirecraft-seasonal-leaves-'+version+'-source.zip')
    entries={name:(root/name).read_bytes() for name in files}
    if validation.exists():entries['dist/'+version+'/validation.json']=validation.read_bytes()
    build.jar(source,entries,executable={'gradlew'})
    for artifact in [*dist.glob('*.zip')]:artifact.with_suffix('.zip.sha256').write_text(build.sha(artifact)+'  '+artifact.name+'\n')
    manifest={p.name:{'size':p.stat().st_size,'sha256':build.sha(p)} for p in sorted(dist.iterdir()) if p.suffix in ['.jar','.zip']}
    (dist/'artifacts.json').write_text(json.dumps(manifest,indent=2)+'\n')
    print('PACKAGE_PASS',json.dumps(manifest))
if __name__=='__main__':main()
