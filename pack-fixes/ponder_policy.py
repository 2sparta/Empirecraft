"""Audit actual Create Ponder schematics and descriptions against installed content policy."""
import gzip,hashlib,importlib.util,io,json,re,struct,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent

def nbt(raw):
    stream=io.BytesIO(gzip.decompress(raw) if raw[:2]==b'\x1f\x8b' else raw)
    def number(fmt):return struct.unpack('>'+fmt,stream.read(struct.calcsize(fmt)))[0]
    def string():return stream.read(number('H')).decode('utf-8')
    def payload(kind):
        if kind in range(1,7):return number({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[kind])
        if kind==7:return stream.read(number('i'))
        if kind==8:return string()
        if kind==9:
            subtype=number('B');size=number('i');return [payload(subtype) for _ in range(size)]
        if kind==10:
            out={}
            while True:
                subtype=number('B')
                if not subtype:return out
                key=string();out[key]=payload(subtype)
        if kind in [11,12]:return [number('i' if kind==11 else 'q') for _ in range(number('i'))]
        raise ValueError('Unknown NBT kind '+str(kind))
    kind=number('B');string();return payload(kind)
def content_ids(value):
    if isinstance(value,dict):
        # Block-entity type IDs such as create:simple_kinetic are not item/block IDs.
        if isinstance(value.get('Name'),str):yield value['Name']
        if value.get('id') in {'minecraft:blaze','minecraft:ghast','minecraft:magma_cube','minecraft:wither_skeleton','minecraft:piglin','minecraft:zombified_piglin','minecraft:piglin_brute','minecraft:hoglin','minecraft:zoglin','minecraft:strider'}:yield value['id']
        for v in value.values():yield from content_ids(v)
    elif isinstance(value,list):
        for v in value:yield from content_ids(v)
def generate(minecraft_client=None):
    create=next((REPO/'mods').glob('create-1.20.1-*-medieval.*.jar'))
    removed=set(json.loads((REPO/'content-trim/src/main/resources/removed-content.json').read_text())['replacements'])
    removed.update(json.loads((REPO/'end-removal/src/main/resources/no-end.json').read_text())['replacements'])
    removed.update(json.loads((REPO/'native-content/audit/policy.json').read_text())['removed'])
    removed.update(json.loads((REPO/'content-policy/src/main/resources/empirecraft-content-policy.json').read_text())['replacements'])
    spec=importlib.util.spec_from_file_location('native_ponder_policy',REPO/'native-content/policy.py');native=importlib.util.module_from_spec(spec);spec.loader.exec_module(native)
    if minecraft_client and Path(minecraft_client).is_file():
        with zipfile.ZipFile(minecraft_client) as game:
            for name in game.namelist():
                match=re.fullmatch(r'assets/minecraft/(?:models/item|blockstates)/([^/]+)\.json',name)
                if match and native.blocked('minecraft:'+match[1]):removed.add('minecraft:'+match[1])
    removed.update('minecraft:'+item for item in ['blaze_rod','blaze_powder','blaze_spawn_egg','ghast_tear','nether_wart','magma_cream','soul_sand','soul_soil','nether_bricks','nether_brick','netherrack','netherite_ingot','netherite_scrap'])
    reasons={};descriptions=set()
    with zipfile.ZipFile(create) as z:
        allowed=set(z.read('empirecraft/medieval-create-allowed.txt').decode().splitlines())
        def forbidden(identifier):
            return bool(re.fullmatch(r'[a-z0-9_.-]+:[a-z0-9_./-]+',identifier)) and (identifier in removed or native.blocked(identifier) or identifier.startswith('create:') and identifier not in allowed)
        all_scenes=[]
        for name in z.namelist():
            if not name.startswith('assets/create/ponder/') or not name.endswith('.nbt'):continue
            scene='create:'+name.removeprefix('assets/create/ponder/').removesuffix('.nbt');all_scenes.append(scene)
            found=sorted({s for s in content_ids(nbt(z.read(name))) if forbidden(s) or s in {'minecraft:blaze','minecraft:ghast','minecraft:magma_cube','minecraft:wither_skeleton','minecraft:piglin','minecraft:zombified_piglin','minecraft:piglin_brute','minecraft:hoglin','minecraft:zoglin','minecraft:strider'}})
            removed.update(s for s in found if not s.startswith('create:'))
            if found or re.search(r'(?:blaze|nether|haunt|soul)',scene):reasons[scene]=found or ['Nether scene']
        for locale in ['en_us','uk_ua','ru_ru']:
            name='assets/create/lang/'+locale+'.json'
            if name not in z.namelist():continue
            for key,text in json.loads(z.read(name)).items():
                match=re.match(r'(?:item|block)\.create\.([^.]+)\.tooltip(?:\.|$)',key)
                if match and re.search(r'nether|blaze|soul.fire|незер|блейз|іфрит|ифрит|пекель|нижн\w* (?:світ|мир)',text,re.I):descriptions.add('create:'+match[1])
    retired_namespaces=set(json.loads((REPO/'server-cleanup/policy.json').read_text())['removed_server_mods'])
    live_removed={identifier for identifier in removed if identifier.split(':',1)[0] not in retired_namespaces}
    policy={'allowed_create':sorted(allowed),'blocked_components':sorted(live_removed),'blocked_scenes':sorted(reasons),'blocked_descriptions':sorted(descriptions)}
    report={'create':create.name,'sha256':hashlib.sha256(create.read_bytes()).hexdigest(),'scanned_schematics':len(all_scenes),'blocked_schematics':reasons,'safe_schematics':sorted(set(all_scenes)-reasons.keys()),'blocked_descriptions':sorted(descriptions)}
    path=ROOT/'src/main/resources/ponder-safety.json';path.write_text(json.dumps(policy,ensure_ascii=False,indent=2)+'\n')
    audit=ROOT/'audit/ponder-safety.json';audit.parent.mkdir(exist_ok=True);audit.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print('PONDER_POLICY_AUDIT',len(all_scenes),'schematics;',len(reasons),'blocked;',len(descriptions),'descriptions')
    return policy
if __name__=='__main__':generate()
