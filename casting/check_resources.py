#!/usr/bin/env python3
import argparse,hashlib,json,os,subprocess,sys,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent

def main():
 p=argparse.ArgumentParser();p.add_argument('--reference-repo',type=Path,required=True);p.add_argument('--sdk',type=Path,required=True);p.add_argument('--jdk',type=Path,required=True);a=p.parse_args();artifact=ROOT/'dist/empirecraft-casting-1.3.1.jar';refs=set();pages=0;sys.path.insert(0,str(REPO/'handbook'));import layout
 with zipfile.ZipFile(artifact) as z:
  for name in z.namelist():
   if name.endswith('.json'):
    value=json.loads(z.read(name))
    if '/models/' in name:
     refs.update(v for v in value.get('textures',{}).values() if not v.startswith('#'))
     elements=value.get('elements',[])
     for i,first in enumerate(elements):
      assert all(first['to'][axis]>first['from'][axis] for axis in range(3)), 'Empty model cuboid'
      for second in elements[i+1:]:
       overlap=min(first['to'][0],second['to'][0])>max(first['from'][0],second['from'][0]) and min(first['to'][2],second['to'][2])>max(first['from'][2],second['from'][2])
       same_top=first['to'][1]==second['to'][1]
       assert not (overlap and same_top and first['faces']['up']['texture']!=second['faces']['up']['texture']), 'Conflicting visible model surfaces'

    if '/entries/metals/' in name or '/entries/food/shared_ingredients' in name:
     assert value['category'] in ['empirecraft_guidebook:metals','empirecraft_guidebook:food']
     for page in value['pages']:assert layout.lines(page['text'])<=13;pages+=1
 jars=[a.sdk/'client-original.jar',*sorted((a.reference_repo/'mods').glob('*.jar'))];available=set()
 for jar in jars:
  with zipfile.ZipFile(jar) as z:available.update(z.namelist())
 assert all('assets/'+tex.split(':')[0]+'/textures/'+tex.split(':')[1]+'.png' in available for tex in refs)
 libs=sorted((a.reference_repo/'server/libraries').rglob('*.jar'));cp=os.pathsep.join(map(str,libs));target=ROOT/'build/checks';target.mkdir(exist_ok=True)
 subprocess.run([str(a.jdk/'bin/javac'),'--release','17','-proc:none','-cp',cp,'-d',str(target),str(ROOT/'tests/PackagedTest.java')],check=True)
 out=subprocess.check_output([str(a.jdk/'bin/java'),'-ea','-cp',str(target)+os.pathsep+cp,'PackagedTest',str(artifact)],text=True)
 report=dict(status='passed',sha256=hashlib.sha256(artifact.read_bytes()).hexdigest(),markers=out.strip().splitlines(),referenced_textures=sorted(refs),bounded_book_pages=pages,graphical_client_tested=False)
 (ROOT/'audit/resources.json').write_text(json.dumps(report,indent=2)+'\n');print(out.strip());print('CASTING_RESOURCES_PASS pages='+str(pages))
if __name__=='__main__':main()
