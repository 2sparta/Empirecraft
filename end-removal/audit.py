#!/usr/bin/env python3
"""Audit actual installed assets and generate high-priority recipe/tag exclusions."""
import argparse, copy, hashlib, json, re, zipfile
from pathlib import Path
from policy import *
from tag_references import optional_mod_reference

ROOT=Path(__file__).resolve().parent
REPO=ROOT.parent
MISSING=object()

def load_json(raw):
    text=raw.decode('utf-8-sig') if isinstance(raw,bytes) else raw
    # Gson accepts comments and trailing commas in mod data. Keep quoted content untouched.
    text=re.sub(r'("(?:\\.|[^"\\])*")|//[^\n]*|/\*[\s\S]*?\*/',lambda match:match[1] or '',text)
    text=re.sub(r'("(?:\\.|[^"\\])*")|,\s*(?=[}\]])',lambda match:match[1] or '',text)
    return json.loads(text)

def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2)+'\n')

def strings(value):
    if isinstance(value,str):return {value}
    if isinstance(value,list):return set().union(*(strings(v) for v in value)) if value else set()
    if isinstance(value,dict):return set().union(*(strings(v) for v in value.values())) if value else set()
    return set()

def outputs(recipe):
    result=set()
    for key,value in recipe.items():
        if key in {'result','results','output','outputs'}:result.update(strings(value))
    return result

def expand(tag,tags,trail=None):
    trail=set() if trail is None else set(trail)
    if tag in trail:return set()
    trail.add(tag);result=set()
    for value in tags.get(tag,[]):
        identity=value.get('id','') if isinstance(value,dict) else value
        if identity.startswith('#'):result.update(expand(identity[1:],tags,trail))
        elif identity:result.add(identity)
    return result

def rewrite(value, blocked, tag_items, alternatives=False):
    if isinstance(value,str):
        if value==PEARL:return PEARL_REPLACEMENT
        return MISSING if value in blocked else value
    if isinstance(value,list):
        result=[]
        for child in value:
            changed=rewrite(child,blocked,tag_items,alternatives)
            if changed is MISSING:
                if not alternatives:return MISSING
            elif alternatives and isinstance(changed,list):result.extend(changed)
            else:result.append(changed)
        return result if result else (MISSING if value else [])
    if not isinstance(value,dict):return value
    if 'tag' in value and (value['tag'] in PEARL_TAGS or value['tag'] in tag_items):
        items=tag_items.get(value['tag'],[PEARL])
        kept=sorted({PEARL_REPLACEMENT if item==PEARL else item for item in items if item==PEARL or item not in blocked})
        if not kept:return MISSING
        # A normal tag ingredient can become an explicit alternative list. Preserve custom NBT wrappers.
        if set(value)=={'tag'}:return [{'item':item} for item in kept]
        if value['tag'] in PEARL_TAGS:
            value=dict(value);del value['tag'];value['item']=PEARL_REPLACEMENT
    result={}
    for key,child in value.items():
        if key=='ingredients' and isinstance(child,list):
            changed=[]
            for slot in child:
                rewritten=rewrite(slot,blocked,tag_items,True)
                if rewritten is MISSING:return MISSING
                changed.append(rewritten)
        elif key=='key' and isinstance(child,dict):
            changed={letter:rewrite(slot,blocked,tag_items,True) for letter,slot in child.items()}
            if any(slot is MISSING for slot in changed.values()):return MISSING
        else:changed=rewrite(child,blocked,tag_items,key in {'ingredient','base','addition','template','tool','input','item'})
        if changed is MISSING:return MISSING
        result[key]=changed
    return result

def recipe(recipe,blocked,tag_items):
    if outputs(recipe)&blocked:return None
    changed=rewrite(recipe,blocked,tag_items)
    return None if changed is MISSING else changed

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--mods',type=Path,required=True);parser.add_argument('--vanilla',type=Path,required=True);parser.add_argument('--inputs',type=Path)
    args=parser.parse_args();pins=json.loads((ROOT/'inputs.json').read_text());sources={p.name:p for p in args.mods.glob('*.jar') if not p.name.startswith('empirecraft-no-end-')}
    # Rebuild from the exact pre-removal native inputs, even when the checkout already contains our outputs.
    if args.inputs:
        aliases=('chipped-forge-','chippedexttras-','Contact-forge-','FarmersDelight-')
        sources={name:path for name,path in sources.items() if not name.startswith(aliases)}
        sources.update({name:args.inputs/name for name in pins['native']})
    for name,digest in pins['native'].items():
        raw=sources[name].read_bytes();assert hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()==digest,'Unexpected native input: '+name
    blocked={'minecraft:'+name for name in VANILLA}|CONTACT|KNIVES;blocks=set();entities=set(ENTITIES)
    recipes={};advancements={};tags={kind:{} for kind in ['items','blocks','entity_types']};models=set();input_hashes={}
    for name,path in [('minecraft',args.vanilla),*sorted(sources.items())]:
        input_hashes[name]=hashlib.sha256(path.read_bytes()).hexdigest()
        with zipfile.ZipFile(path) as archive:
            for entry in archive.namelist():
                match=re.fullmatch(r'assets/([^/]+)/models/item/(.+)\.json',entry)
                if match:
                    identity=match[1]+':'+match[2];models.add(identity)
                    if end_id(identity):blocked.add(identity)
                match=re.fullmatch(r'assets/([^/]+)/blockstates/(.+)\.json',entry)
                if match and end_id(match[1]+':'+match[2]):blocks.add(match[1]+':'+match[2]);blocked.add(match[1]+':'+match[2])
                match=re.fullmatch(r'data/([^/]+)/recipes/(.+)\.json',entry)
                if match:
                    try:recipes[(match[1]+':'+match[2],entry)]=load_json(archive.read(entry))
                    except ValueError:pass
                match=re.fullmatch(r'data/([^/]+)/tags/(items|blocks|entity_types)/(.+)\.json',entry)
                if match:
                    data=load_json(archive.read(entry));key=match[1]+':'+match[3];target=tags[match[2]]
                    target[key]=([] if data.get('replace') else target.get(key,[]))+data.get('values',[])
                if re.fullmatch(r'data/[^/]+/advancements/.+\.json',entry):
                    try:advancements[entry]=load_json(archive.read(entry))
                    except ValueError:pass
                if re.fullmatch(r'assets/[^/]+/lang/en_us.json',entry):
                    for ns,identity in re.findall(r'"entity\.([a-z0-9_]+)\.([a-z0-9_/]+)"\s*:',archive.read(entry).decode('utf-8-sig')):
                        if end_id(ns+':'+identity):entities.add(ns+':'+identity)
    for data in recipes.values():
        for identity in outputs(data):
            if ':' in identity and end_id(identity):blocked.add(identity)
    # Extras register from this data file; their item models can be indirect aliases.
    with zipfile.ZipFile(sources['chippedexttras-1.4-empirecraft.2.jar']) as archive:
        for row in json.loads(archive.read('assets/chippedexttras/generated/registry.json')):
            if row['base'] in blocked:
                for key,identity in row.items():
                    if key!='base' and identity:blocked.add(identity);blocks.add(identity)
    blocks.update({'minecraft:end_portal','minecraft:end_gateway'})
    relevant={key:sorted(expand(key,tags['items'])) for key in tags['items'] if expand(key,tags['items'])&blocked}
    for key in PEARL_TAGS:relevant.setdefault(key,[PEARL])
    resources=ROOT/'src/main/resources/data';resources.mkdir(parents=True,exist_ok=True)
    generated=[];disabled=[];modified=[];pearls=[]
    def emit(relative,data):
        write(resources/relative,data);write(REPO/'server/kubejs/data'/relative,data);generated.append(relative)
    for (identity,path),original in sorted(recipes.items()):
        changed=recipe(original,blocked,relevant)
        if changed==original:continue
        namespace=identity.split(':')[0]
        if changed is None:
            changed=copy.deepcopy(original);changed['conditions']=[{'type':'forge:false'}];disabled.append(identity)
        else:
            modified.append(identity)
            if PEARL in strings(original) or any(tag in strings(original) for tag in PEARL_TAGS):pearls.append(identity)
            if namespace!='minecraft':changed.setdefault('conditions',[]).append({'type':'forge:mod_loaded','modid':namespace})
        emit(path[5:],changed)
    for path,data in advancements.items():
        if set(data.get('rewards',{}).get('recipes',[]))&set(pearls):
            changed=recipe(data,blocked,relevant)
            if changed is not None and changed!=data:emit(path[5:],changed)
    blocked_tags={kind:{key for key in values if expand(key,values)&(entities if kind=='entity_types' else blocked)} for kind,values in tags.items()}
    tag_report={}
    for kind,values in tags.items():
        for identity in sorted(blocked_tags[kind]):
            kept=[]
            for value in values[identity]:
                key=value.get('id','') if isinstance(value,dict) else value
                if key in blocked or key in entities:continue
                if key.startswith('#') and not (expand(key[1:],values)-(entities if kind=='entity_types' else blocked)):continue
                value=optional_mod_reference(value)
                if value not in kept:kept.append(value)
            ns,path=identity.split(':',1);emit(ns+'/tags/'+kind+'/'+path+'.json',{'replace':True,'values':kept});tag_report[kind+':'+identity]=kept
    policy={'replacements':{identity:replacement(identity,blocks) for identity in sorted(blocked)},'blocks':sorted(blocks),'entities':sorted(entities),'pearl_replacement':PEARL_REPLACEMENT,'ingredient_tags':relevant}
    write(ROOT/'src/main/resources/no-end.json',policy)
    write(ROOT/'audit/content.json',{'inputs':input_hashes,'scanned_jars':len(sources),'scanned_recipes':len(recipes),'blocked_items':sorted(blocked),'blocks':sorted(blocks),'entities':sorted(entities),'disabled_recipes':disabled,'modified_recipes':modified,'pearl_recipes':pearls,'cleaned_tags':tag_report})
    old=ROOT/'audit/generated-data.json'
    if old.exists():
        for stale in set(json.loads(old.read_text()))-set(generated):
            (resources/stale).unlink(missing_ok=True);(REPO/'server/kubejs/data'/stale).unlink(missing_ok=True)
    write(old,sorted(generated))
    prelude='// priority: -300\n// Generated by end-removal/audit.py; runs after existing progression/forging passes.\nvar EC_NO_END = '+json.dumps(policy,separators=(',',':'))+';\n'
    server=REPO/'server/kubejs/server_scripts/zzzzzzz_empirecraft_no_end.js';server.parent.mkdir(parents=True,exist_ok=True);server.write_text(prelude+(ROOT/'recipe-pass.js').read_text())
    client=REPO/'kubejs/client_scripts/empirecraft_no_end.js';client.parent.mkdir(parents=True,exist_ok=True);client.write_text('// Generated End and retired-knife JEI exclusions.\nJEIEvents.hideItems(event => {\n'+json.dumps(sorted(blocked))+'.forEach(id => { if (Item.exists(id)) event.hide(id) })\n})\n')
    print('PASS End audit:',len(sources),'mods;',len(recipes),'recipes;',len(blocked),'retired IDs;',len(disabled),'disabled;',len(pearls),'pearl recipe replacements')

if __name__=='__main__':main()
