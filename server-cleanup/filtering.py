"""Remove retired namespaces from live data without changing unrelated entries."""
import io,json,re,zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parent
REMOVED=frozenset(json.loads((ROOT/'policy.json').read_text())['removed_server_mods'])
DROP=object()
def removed(value):
    if not isinstance(value,str):return False
    value=value.removeprefix('#')
    return any(value.startswith(ns+':') for ns in REMOVED)
def prune(value):
    if isinstance(value,str):return DROP if removed(value) else value
    if isinstance(value,list):
        result=[]
        for child in value:
            clean=prune(child)
            if clean is not DROP:result.append(clean)
        return result
    if isinstance(value,dict):
        # A removed registry entry must disappear as a whole, including optional flags.
        if any(removed(value.get(key)) for key in ['id','item','block','fluid','entity','tag']):return DROP
        result={}
        for key,child in value.items():
            if removed(key):continue
            clean=prune(child)
            if clean is not DROP:result[key]=clean
        return result
    return value
def namespace_path(name):
    parts=name.split('/')
    return any(parts[i] in {'data','assets'} and parts[i+1] in REMOVED for i in range(len(parts)-1))
def encode(value):return (json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
def format_json(value,original):
    """Keep compact files and one-entry-per-line tags in their original format."""
    text=original.decode('utf-8-sig') if isinstance(original,bytes) else original
    if len(text.splitlines())<=2:return (json.dumps(value,ensure_ascii=False,separators=(',',':'))+'\n').encode()
    pattern=re.compile(r'"#?(?:'+ '|'.join(map(re.escape,REMOVED))+r'):')
    candidate=''.join(line for line in text.splitlines(keepends=True) if not pattern.search(line))
    candidate=re.sub(r',([ \t]*\r?\n[ \t]*\])',r'\1',candidate)
    try:
        if json.loads(candidate)==value:return candidate.encode()
    except ValueError:pass
    return encode(value)
def entries(files):
    result={}
    for name,raw in files.items():
        if namespace_path(name):continue
        if name.endswith('.json'):
            try:value=json.loads(raw)
            except (ValueError,UnicodeDecodeError):result[name]=raw;continue
            clean=prune(value)
            if clean is DROP:continue
            if clean!=value:raw=format_json(clean,raw)
        result[name]=raw
    return result
def filter_jar(raw):
    with zipfile.ZipFile(io.BytesIO(raw)) as z:
        infos={n:z.getinfo(n) for n in z.namelist() if not n.endswith('/')}
        files=entries({n:z.read(n) for n in infos})
    out=io.BytesIO()
    with zipfile.ZipFile(out,'w') as z:
        for name,data in sorted(files.items()):z.writestr(infos[name],data)
    return out.getvalue()
def clean_tree(root):
    changed=[]
    for file in root.rglob('*.json'):
        name=file.relative_to(root).as_posix()
        if namespace_path(name):file.unlink();changed.append(str(file));continue
        before=json.loads(file.read_text());after=prune(before)
        if after is DROP:file.unlink();changed.append(str(file))
        elif after!=before:file.write_bytes(format_json(after,file.read_bytes()));changed.append(str(file))
    return changed
def clean_script(path):
    text=path.read_text();match=re.search(r'(var EC_NO_END = )(\{[^\n]+\})(;)',text)
    if not match:return False
    value=json.loads(match.group(2));clean=prune(value)
    if value==clean:return False
    path.write_text(text[:match.start(2)]+json.dumps(clean,ensure_ascii=False,separators=(',',':'))+text[match.end(2):]);return True
