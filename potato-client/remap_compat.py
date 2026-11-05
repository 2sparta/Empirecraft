#!/usr/bin/env python3
"""Namespace-only copies of the actual optimizers for ForgeGradle's official-name test JVM.

ForgeGradle cannot infer Minecraft ownership for @Overwrite/@Shadow member declarations
on a third-party mixin class. Apply its real SRG -> official names to every constant-pool
UTF8 reference and textual mixin selector. Instructions, descriptors, resources and class
structure are otherwise preserved. Originals and all production/distribution files stay intact.
"""
import hashlib, io, json, re, struct, zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parent
SRG=re.compile(rb'(?<![A-Za-z0-9_])[mf]_[0-9]+_(?![A-Za-z0-9_])')

def mappings(path):
    names={}
    for line in path.read_text().splitlines():
        parts=line.split()
        if not parts: continue
        if parts[0]=='CL:':
            assert parts[1]==parts[2], 'Unexpected class namespace change'
        if parts[0] not in ('FD:','MD:'): continue
        source=parts[1].rsplit('/',1)[-1].encode()
        target=parts[3 if parts[0]=='MD:' else 2].rsplit('/',1)[-1].encode()
        if not SRG.fullmatch(source): continue
        previous=names.setdefault(source,target)
        assert previous==target, ('Ambiguous actual Minecraft mapping',source)
    assert b'f_96164_' in names and b'm_252986_' in names and b'm_231551_' in names
    assert all(not SRG.fullmatch(target) for target in names.values())
    return names

def rename(data,names):
    return SRG.sub(lambda match:names.get(match[0],match[0]),data)

def remap_class(data,names):
    assert data[:4]==b'\xca\xfe\xba\xbe'
    count=struct.unpack_from('>H',data,8)[0]
    result=bytearray(data[:10]);offset=10;index=1
    sizes={3:4,4:4,5:8,6:8,7:2,8:2,9:4,10:4,11:4,12:4,15:3,16:2,17:4,18:4,19:2,20:2}
    while index<count:
        tag=data[offset];result.append(tag);offset+=1
        if tag==1:
            length=struct.unpack_from('>H',data,offset)[0];offset+=2
            # Work with bytes: modified UTF8 encodings are preserved exactly outside ASCII SRG names.
            value=rename(data[offset:offset+length],names);offset+=length
            result.extend(struct.pack('>H',len(value)));result.extend(value)
        else:
            size=sizes[tag];result.extend(data[offset:offset+size]);offset+=size
            if tag in (5,6): index+=1
        index+=1
    # No bytecode instruction, flags, attribute, branch, stack frame or constant-pool index is edited.
    result.extend(data[offset:])
    return bytes(result)

def main():
    names=mappings(ROOT/'build/smoke-srg-to-official.srg')
    lock=json.loads((ROOT.parent/'modpack-lock.json').read_text())
    directory=ROOT/'lib/compat';directory.mkdir(parents=True,exist_ok=True)
    for mid in ('embeddium','dynamic_fps'):
        row=next(mod for mod in lock['mods'] if mod['id']==mid)
        raw=(ROOT.parent/'mods'/row['file']).read_bytes()
        assert hashlib.sha256(raw).hexdigest()==row['sha256']
        out=io.BytesIO();changed=0
        with zipfile.ZipFile(io.BytesIO(raw)) as original,zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as converted:
            assert not any(name.upper().endswith(('.SF','.RSA','.DSA')) for name in original.namelist())
            for entry in original.infolist():
                data=original.read(entry.filename);result=data
                if entry.filename.endswith('.class'):
                    result=remap_class(data,names)
                    assert remap_class(result,names)==result, 'Namespace conversion must be idempotent'
                    changed+=result!=data
                elif entry.filename.endswith(('.json','.cfg','.accesswidener','.js','.toml')):
                    result=rename(data,names)
                converted.writestr(entry,result)
        assert changed>10,(mid,changed)
        (directory/row['file']).write_bytes(out.getvalue())
        print('PASS actual optimizer development namespace:',mid,'classes with renamed members:',changed,'original SHA-256:',row['sha256'])
if __name__=='__main__':main()
