#!/usr/bin/env python3
"""Build contract extension on the supplied 1.9.0 baseline, preserving its TownMenu binaries.
No downloads, source recovery, deployment or removal of installed plugins is performed.
"""
import hashlib, os, pathlib, subprocess, shutil, zipfile
ROOT=pathlib.Path(__file__).resolve().parent
REPO=ROOT.parent
BASE=REPO/'server/plugins/empirecraft-feudal-1.9.0.jar'
BASE_SHA='db76c1fcdda8e288fcc25194dbb5f6af40aade335207c2449ce6e8d98a5f2a5d'
VERSION='1.10.0'
CHANGED=('ContractTerms','ContractService','ContractBook','ContractMenus','FeudalPlugin','FeudalService','RealmService','CommerceService','FeudalCommands')
def main():
    assert hashlib.sha256(BASE.read_bytes()).hexdigest()==BASE_SHA, 'Use the supplied unmodified 1.9.0 baseline'
    home=pathlib.Path(os.environ.get('JAVA_HOME','/workspace/toolchains/jdk-17.0.16+8'))
    javac=str(home/'bin/javac') if (home/'bin/javac').exists() else 'javac'
    java=str(home/'bin/java') if (home/'bin/java').exists() else 'java'
    libraries=sorted((REPO/'server/libraries').rglob('*.jar'))
    cp=os.pathsep.join(map(str,[BASE,REPO/'server/plugins/Towny-0.101.2.0.jar',*libraries]))
    build=ROOT/'build/contracts'
    if build.exists():shutil.rmtree(build)
    build.mkdir(parents=True)
    source=ROOT/'src/main/java/ua/empirecraft/feudal'
    subprocess.run([javac,'-proc:none','--release','17','-encoding','UTF-8','-cp',cp,'-d',str(build),*[str(source/(c+'.java')) for c in CHANGED]],check=True)
    tests=sorted((ROOT/'src/test/java/ua/empirecraft/feudal').glob('*Tests.java'))
    subprocess.run([javac,'-proc:none','--release','17','-encoding','UTF-8','-cp',str(build)+os.pathsep+cp,'-d',str(build),*map(str,tests)],check=True)
    for name in ('ContractTests','FeudalTests','CurrencyTests','RealmCommerceTests','ProvisionTests','PoliticsTests','PoliticalRegressionTests','PoliticsProvisionTests','CurrencyMarketTests','CurrencyExchangeTests','TradeTownyTests','KingdomTests','CommandLanguageTests','ChatTitleTests','ItemPlanningTests'):
        subprocess.run([java,'-ea','-cp',str(build)+os.pathsep+cp,'ua.empirecraft.feudal.'+name],check=True)
    target=ROOT/f'dist/empirecraft-feudal-{VERSION}.jar';target.parent.mkdir(exist_ok=True)
    replacements={p.relative_to(build).as_posix():p.read_bytes() for p in build.rglob('*.class') if 'Tests' not in p.name and 'Test$' not in p.name}
    # Exclude all compiled test classes and their anonymous classes.
    testnames={p.stem for p in tests}
    replacements={n:b for n,b in replacements.items() if n.split('/')[-1].split('$')[0].removesuffix('.class') not in testnames}
    with zipfile.ZipFile(BASE) as base,zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as out:
        contents={n:base.read(n) for n in base.namelist() if not n.endswith('/')}
        for name in CHANGED:
            for n in list(contents):
                if n=='ua/empirecraft/feudal/'+name+'.class' or n.startswith('ua/empirecraft/feudal/'+name+'$'):del contents[n]
        contents.update(replacements)
        descriptor=contents['plugin.yml'].decode().replace('version: 1.9.0','version: '+VERSION)
        contents['plugin.yml']=descriptor.encode()
        for name,data in sorted(contents.items()):
            entry=zipfile.ZipInfo(name,(1980,1,1,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED;entry.external_attr=0o644<<16;out.writestr(entry,data)
    digest=hashlib.sha256(target.read_bytes()).hexdigest();target.with_suffix('.jar.sha256').write_text(digest+'  '+target.name+'\n')
    with zipfile.ZipFile(BASE) as base,zipfile.ZipFile(target) as result:
        assert result.read('ua/empirecraft/feudal/TownMenu.class')==base.read('ua/empirecraft/feudal/TownMenu.class')
        assert not any('ContractTests' in n for n in result.namelist())
        assert result.testzip() is None
    print('READY',target,'SHA256',digest)
if __name__=='__main__':main()
