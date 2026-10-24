#!/usr/bin/env python3
"""Extract exact installed recipe fixtures and native steel sources; no runtime claims."""
from pathlib import Path
import argparse,hashlib,json,re,tomllib,zipfile
ROOT=Path(__file__).resolve().parent
REPO=ROOT.parent

def extract(jars):
    recipes={};tags={};assets=set();bad=[];mods=[];versions={};hashes={}
    for prefix in ['create-','epic-knights','Locks-Steel','overgeared']:
        path=next(jars.glob(prefix+'*.jar'))
        hashes[path.name]=hashlib.sha256(path.read_bytes()).hexdigest()
        with zipfile.ZipFile(path) as z:
            metadata=tomllib.loads(z.read('META-INF/mods.toml').decode())
            for mod in metadata.get('mods',[]):versions[mod['modId']]=mod['version']
            assets.update(n for n in z.namelist() if n.startswith('assets/') and '/models/item/' in n)
            for name in z.namelist():
                match=re.match(r'^data/([^/]+)/recipes/(.+)\.json$',name)
                if match:
                    try:recipe=json.loads(z.read(name))
                    except ValueError:bad.append(name);continue
                    if recipe.get('type'):recipes[match[1]+':'+match[2]]=recipe
                match=re.match(r'^data/([^/]+)/tags/items/(.+)\.json$',name)
                if match:
                    data=json.loads(z.read(name));key=match[1]+':'+match[2]
                    if data.get('replace'):tags[key]=[]
                    tags.setdefault(key,[]).extend(data.get('values',[]))
    return dict(recipes=recipes,tags=tags,assets=sorted(assets),bad_upstream_json=bad,versions=versions,jar_sha256=hashes)

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--jars',type=Path,default=REPO/'mods');args=p.parse_args()
    data=extract(args.jars)
    (ROOT/'build').mkdir(exist_ok=True)
    (ROOT/'build/fixtures.json').write_text(json.dumps(data,ensure_ascii=False))
    print('Loaded',len(data['recipes']),'recipes from',data['versions'])
    print('Recorded existing malformed upstream JSON:',data['bad_upstream_json'])
if __name__=='__main__':main()
