#!/usr/bin/env python3
"""Resource-only rebuilds of pinned modules; preserve every original Java class."""
import hashlib,json,re,zipfile
from pathlib import Path
from filtering import entries

ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent
MODULES=[
 ('seasonal-calendar','empirecraft-calendar-1.2.2.jar','1.2.3','empirecraft-calendar-1.2.3.jar'),
 ('seasonal-leaves','empirecraft-seasonal-leaves-1.3.3.jar','1.3.4','empirecraft-seasonal-leaves-1.3.4.jar'),
 ('end-removal','empirecraft-no-end-1.0.0.jar','1.0.1','empirecraft-no-end-1.0.1.jar'),
 ('pack-fixes','empirecraft-pack-fixes-1.0.0.jar','1.0.1','empirecraft-pack-fixes-1.0.1.jar')]
def sha(raw):return hashlib.sha256(raw).hexdigest()
def main():
    pins=json.loads((ROOT/'inputs.json').read_text());report=[]
    for source,old,version,name in MODULES:
        candidates=[REPO/'server/mods'/old,REPO/'mods'/old,*sorted((REPO/'disabled-mods').rglob(old))]
        baseline=next(p for p in candidates if p.is_file() and sha(p.read_bytes())==pins[old])
        with zipfile.ZipFile(baseline) as z:before={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
        files=entries(before)
        resources=REPO/source/'src/main/resources'
        # These modules' complete source resources match the pinned classes. The calendar
        # also contains generated tags, so retain/filter those rather than dropping them.
        for p in resources.rglob('*'):
            if p.is_file():files[p.relative_to(resources).as_posix()]=p.read_bytes()
        files=entries(files)
        toml=files['META-INF/mods.toml'].decode()
        files['META-INF/mods.toml']=re.sub(r'(?m)^(version\s*=\s*)"[^"]+"',lambda m:m[1]+'"'+version+'"',toml).encode()
        if 'META-INF/MANIFEST.MF' in files:
            manifest=files['META-INF/MANIFEST.MF'].decode()
            manifest=re.sub(r'(?m)^Implementation-Version:[^\r\n]*','Implementation-Version: '+version,manifest)
            files['META-INF/MANIFEST.MF']=manifest.encode()
        classes={n:b for n,b in before.items() if n.endswith('.class')}
        assert classes=={n:b for n,b in files.items() if n.endswith('.class')}
        target=ROOT/'build/libs'/name;target.parent.mkdir(parents=True,exist_ok=True)
        with zipfile.ZipFile(target,'w') as z:
            for n,b in sorted(files.items()):
                i=zipfile.ZipInfo(n,(2026,11,13,0,0,0));i.compress_type=zipfile.ZIP_DEFLATED;i.external_attr=0o100644<<16;z.writestr(i,b)
        digest=sha(target.read_bytes());target.with_suffix('.jar.sha256').write_text(digest+'  '+name+'\n')
        report.append(dict(file=name,source=source,sha256=digest,baseline=old,baseline_sha256=pins[old],unchanged_classes=len(classes),
            removed_resources=sorted(before.keys()-files.keys()),changed_resources=sorted(n for n in files.keys()&before.keys() if files[n]!=before[n])))
    (ROOT/'audit').mkdir(exist_ok=True);(ROOT/'audit/resource-overlays.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print('RESOURCE_CLEANUP_BUILD_PASS',[(r['file'],r['unchanged_classes']) for r in report])
if __name__=='__main__':main()
