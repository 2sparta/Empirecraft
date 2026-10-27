#!/usr/bin/env python3
import argparse,hashlib,json,os,subprocess,zipfile
from pathlib import Path
from build import ROOT,POLICY,REMOVED,TYPES,EXPORTS,refs

def main():
    p=argparse.ArgumentParser();p.add_argument('--input',type=Path,required=True);p.add_argument('--classpath-dir',type=Path,action='append',required=True);a=p.parse_args()
    jar=ROOT/'dist'/POLICY['file'];report=json.loads((ROOT/'audit/build.json').read_text())
    assert hashlib.sha256(jar.read_bytes()).hexdigest()==report['sha256']
    with zipfile.ZipFile(a.input) as z:original={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
    with zipfile.ZipFile(jar) as z:files={n:z.read(n) for n in z.namelist()}
    changed=set(report['patched_classes'])|set(report['updated_resources'])|{'META-INF/MANIFEST.MF'}
    for name,raw in original.items():
        if name in files and name not in changed:assert files[name]==raw,'Unrelated content changed: '+name
    for name,raw in files.items():
        if not name.endswith('.json') or not any(t in name for t in ['/recipes/','/tags/','/advancements/','/loot_modifiers/']):continue
        try:data=json.loads(raw)
        except ValueError:
            assert raw==original[name];continue
        assert not refs(data)&REMOVED,(name,refs(data)&REMOVED)
        if '/recipes/' in name:assert data.get('type') not in TYPES,name
    for item in ['clay_tool_cast','steel_ingot','iron_arrow','steel_arrow','alloy_furnace']:
        assert 'assets/overgeared/models/item/'+item+'.json' in files,item
    assert 'data/overgeared/recipes/clay_tool_cast.json' in files
    assert 'data/overgeared/recipes/iron_pickaxe_head_from_cast_smelting.json' in files
    cp=[ROOT/'build/classes',*[p for root in a.classpath_dir for p in root.rglob('*.jar')]]
    cp=os.pathsep.join(map(str,cp))
    subprocess.run(['java','com.sun.tools.javac.Main','--release','17','-proc:none','-cp',cp,'-d',str(ROOT/'build/classes'),str(ROOT/'tests/PolicyTest.java')],check=True)
    subprocess.run(['java','-cp',cp,'PolicyTest'],check=True)
    subprocess.run(['java','com.sun.tools.javac.Main',*EXPORTS,'-cp',str(ROOT/'build/tool'),'-d',str(ROOT/'build/tool'),str(ROOT/'tests/Audit.java')],check=True)
    subprocess.run(['java',*EXPORTS,'-cp',str(ROOT/'build/tool'),'Audit',str(jar)],check=True)
    report['checks'].update(resources='passed',retained_content='passed',recipe_reload_policy='passed',native_registry_audit='passed')
    (ROOT/'audit/build.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print('PASS native resources, exact preservation, retained casting alternatives and integrity')

if __name__=='__main__':main()
