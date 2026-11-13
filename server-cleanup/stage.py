#!/usr/bin/env python3
"""Stage exact matching modules and prove the scope of their class/resource changes."""
import hashlib,json,shutil,tomllib,zipfile
from pathlib import Path
from filtering import REMOVED

ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent
SPECS=[
 ('seasonal-calendar','empirecraft-calendar-1.2.2.jar',ROOT/'build/libs/empirecraft-calendar-1.2.3.jar'),
 ('seasonal-leaves','empirecraft-seasonal-leaves-1.3.3.jar',ROOT/'build/libs/empirecraft-seasonal-leaves-1.3.4.jar'),
 ('end-removal','empirecraft-no-end-1.0.0.jar',ROOT/'build/libs/empirecraft-no-end-1.0.1.jar'),
 ('pack-fixes','empirecraft-pack-fixes-1.0.0.jar',ROOT/'build/libs/empirecraft-pack-fixes-1.0.1.jar'),
 ('world-rules','empirecraft-world-rules-1.0.3.jar',REPO/'world-rules/build/libs/empirecraft-world-rules-1.0.4.jar'),
 ('tfc-integration','empirecraft-tfc-integration-1.0.2.jar',REPO/'tfc-integration/build/libs/empirecraft-tfc-integration-1.0.3.jar'),
 ('handbook','empirecraft-handbook-1.0.1.jar',REPO/'handbook/dist/empirecraft-handbook-1.0.2.jar')]
def sha(raw):return hashlib.sha256(raw).hexdigest()
def contents(path):
    with zipfile.ZipFile(path) as z:return {n:z.read(n) for n in z.namelist() if not n.endswith('/')}
def contains_version(spec,version):
    def key(value):return tuple((list(map(int,value.strip().split('.')))+[0,0,0])[:3])
    actual=key(version);spec=spec.strip()
    if spec in {'','*'}:return True
    if ',' not in spec:return actual==key(spec.strip('[]'))
    lower,upper=spec[1:-1].split(',')
    return (not lower or actual>=key(lower) if spec[0]=='[' else not lower or actual>key(lower)) and (not upper or actual<=key(upper) if spec[-1]==']' else not upper or actual<key(upper))
def main():
    target=ROOT/'build/staged';target.mkdir(parents=True,exist_ok=True)
    for p in target.glob('*.jar'):p.unlink()
    rows=[];compatibility=[]
    allowed={'world-rules':{'ua/empirecraft/worldrules/RulesConfig.class','ua/empirecraft/worldrules/SeedMarket.class'},
             'tfc-integration':{'ua/empirecraft/tfc/Integration.class'}}
    for source,old,p in SPECS:
        baseline=REPO/'server/mods'/old
        if not baseline.exists():baseline=next((REPO/'disabled-mods').rglob(old))
        before,after=contents(baseline),contents(p)
        a={n:b for n,b in before.items() if n.endswith('.class')};b={n:v for n,v in after.items() if n.endswith('.class')}
        normalized=dict(a);renames=[]
        if a.keys()!=b.keys():
            old_only=a.keys()-b.keys();new_only=b.keys()-a.keys()
            assert source=='world-rules' and len(old_only)==len(new_only)==1,(source,old_only,new_only)
            old_hook=next(iter(old_only));new_hook=next(iter(new_only))
            assert old_hook.startswith('ua/empirecraft/nativecut/') and old_hook.endswith('/PolicyHooks.class') and new_hook.endswith('/PolicyHooks.class')
            old_name=old_hook.removesuffix('.class').encode();new_name=new_hook.removesuffix('.class').encode()
            normalized={new_hook if n==old_hook else n:raw.replace(old_name,new_name) for n,raw in a.items()}
            assert normalized[new_hook]==b[new_hook],'Native guard logic changed'
            renames.append(dict(previous=old_hook,current=new_hook,body_identical_after_owner_rename=True))
        changed={n for n in normalized if normalized[n]!=b[n]}
        assert changed<=allowed.get(source,set()),(source,changed)
        for name,raw in after.items():
            assert not any('/'+ns+'/' in '/'+name for ns in REMOVED),(p.name,name)
            if name.endswith(('.json','.class','.toml')):
                assert not any((ns+':').encode() in raw for ns in REMOVED),(p.name,name)
        meta=tomllib.loads(after['META-INF/mods.toml'].decode());mod=meta['mods'][0]
        assert not any(dep.get('mandatory') and dep['modId'] in REMOVED for deps in meta.get('dependencies',{}).values() for dep in deps)
        shutil.copyfile(p,target/p.name)
        rows.append(dict(id=mod['modId'],version=mod['version'],file=p.name,sha256=sha(p.read_bytes()),
            sides=['server'] if source=='world-rules' else ['client','server'],source='local:'+source))
        compatibility.append(dict(source=source,baseline_file=old,baseline_sha256=sha(baseline.read_bytes()),file=p.name,
            sha256=sha(p.read_bytes()),changed_classes=sorted(changed),unchanged_classes=sum(n in b and a[n]==b[n] for n in a),renamed_native_guards=renames,
            removed_resources=sorted(before.keys()-after.keys())))
    (ROOT/'audit').mkdir(exist_ok=True)
    (ROOT/'audit/compatibility.json').write_text(json.dumps(compatibility,ensure_ascii=False,indent=2)+'\n')
    versions={r['id']:r['version'] for r in rows};checks=[]
    selected=[]
    for jar in (REPO/'server/mods').glob('*.jar'):
        with zipfile.ZipFile(jar) as z:
            if 'META-INF/mods.toml' not in z.namelist():continue
            data=tomllib.loads(z.read('META-INF/mods.toml').decode())
            if not any(m['modId'] in versions or m['modId'] in REMOVED for m in data['mods']):selected.append((jar.name,data))
    for jar in target.glob('*.jar'):
        with zipfile.ZipFile(jar) as z:selected.append((jar.name,tomllib.loads(z.read('META-INF/mods.toml').decode())))
    for file,data in selected:
        for dependencies in data.get('dependencies',{}).values():
            for dep in dependencies:
                if dep['modId'] not in versions:continue
                spec=dep.get('versionRange','');version=versions[dep['modId']]
                assert contains_version(spec,version),(file,dep['modId'],spec,version)
                checks.append(dict(file=file,dependency=dep['modId'],range=spec,version=version))
    (ROOT/'audit/dependencies.json').write_text(json.dumps(checks,ensure_ascii=False,indent=2)+'\n')
    (ROOT/'release.json').write_text(json.dumps(dict(mods=rows,removed_server_mods=sorted(REMOVED)),ensure_ascii=False,indent=2)+'\n')
    print('SERVER_CLEANUP_STAGE_PASS mods='+str(len(rows))+' changed_classes='+str(sum(len(r['changed_classes']) for r in compatibility)))
if __name__=='__main__':main()
