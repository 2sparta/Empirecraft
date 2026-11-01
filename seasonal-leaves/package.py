#!/usr/bin/env python3
"""Package the built mod and complete source project without game/dependency JARs."""
import hashlib,json,zipfile
from pathlib import Path
import build

def main():
    root=build.ROOT;version=build.VERSION;dist=root/'dist'/version
    jar=dist/('empirecraft-seasonal-leaves-'+version+'.jar')
    with zipfile.ZipFile(jar) as archive:
        assert ('version="'+version+'"') in archive.read('META-INF/mods.toml').decode()
        assert not any(n.startswith(('net/minecraft/','net/minecraftforge/','sereneseasons/','ua/empirecraft/leaves/test/')) for n in archive.namelist())
    digest=build.sha(jar)
    assert (jar.with_suffix('.jar.sha256')).read_text()==digest+'  '+jar.name+'\n'
    row={'id':'empirecraft_seasonal_leaves','version':version,'file':jar.name,'path':'seasonal-leaves/dist/'+version+'/'+jar.name,'sides':['server'],'source':'local:seasonal-leaves','size':jar.stat().st_size,'sha256':digest,'sha512':hashlib.sha512(jar.read_bytes()).hexdigest(),'replaces':'empirecraft-seasonal-leaves-1.2.1.jar'}
    (root/'release.json').write_text(json.dumps([row],indent=2)+'\n')
    install={'mods/'+jar.name:jar.read_bytes(),'mods/'+jar.name+'.sha256':jar.with_suffix('.jar.sha256').read_bytes()}
    for name in ['INSTALL-UA.md','README.md','LICENSE']:install[name]=(root/name).read_bytes()
    build.jar(dist/('Empirecraft-Seasonal-Leaves-'+version+'-install.zip'),install)
    files=['README.md','INSTALL-UA.md','LICENSE','.gitignore','build.gradle','settings.gradle','gradle.properties','gradlew','gradlew.bat','build.py','build-inputs.json','compat-inputs.json','smoke-runtime.json','test.py','mixin_test.py','growth_smoke.py','package.py','release.json','libs/README.md']
    for folder in ['src','gradle','tools']:
        files.extend(str(p.relative_to(root)) for p in (root/folder).rglob('*') if p.is_file())
    files.extend('tests/'+name for name in ['GrowthSmokeMod.java','SeasonCurveTest.java','VegetationPolicyTest.java','guardtest/IntegrationTest.java','guardtest/OfflineMixinService.java','guardtest/OfflineProperties.java'])
    source=dist/('empirecraft-seasonal-leaves-'+version+'-source.zip')
    build.jar(source,{name:(root/name).read_bytes() for name in files},executable={'gradlew'})
    for artifact in [*dist.glob('*.zip')]:artifact.with_suffix('.zip.sha256').write_text(build.sha(artifact)+'  '+artifact.name+'\n')
    manifest={p.name:{'size':p.stat().st_size,'sha256':build.sha(p)} for p in sorted(dist.iterdir()) if p.suffix in ['.jar','.zip']}
    (dist/'artifacts.json').write_text(json.dumps(manifest,indent=2)+'\n')
    print('PACKAGE_PASS',json.dumps(manifest))
if __name__=='__main__':main()
