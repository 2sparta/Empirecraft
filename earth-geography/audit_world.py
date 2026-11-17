#!/usr/bin/env python3
"""Read-only Anvil coverage audit. Run on an offline COPY; never edits or deletes chunks."""
import argparse,gzip,io,json,struct,zlib
from pathlib import Path
MAX_CHUNK_BYTES=64*1024*1024

def nbt_status(raw):
    stream=io.BytesIO(raw)
    def read(n):
        if n<0 or n>MAX_CHUNK_BYTES:raise ValueError('Invalid NBT length')
        b=stream.read(n)
        if len(b)!=n:raise ValueError('Truncated NBT')
        return b
    def string():return read(struct.unpack('>H',read(2))[0]).decode('utf-8')
    found={}
    def payload(tag,name='',depth=0):
        if depth>64:raise ValueError('NBT nesting limit')
        if tag in (1,2,3,4,5,6):read({1:1,2:2,3:4,4:8,5:4,6:8}[tag])
        elif tag==7:read(struct.unpack('>i',read(4))[0])
        elif tag==8:
            value=string()
            if name=='Status' and depth<=2:found['status']=value
        elif tag==9:
            kind=read(1)[0];length=struct.unpack('>i',read(4))[0]
            if length<0 or length>MAX_CHUNK_BYTES or (kind==0 and length):raise ValueError('Invalid NBT list')
            for _ in range(length):payload(kind,depth=depth+1)
        elif tag==10:
            while True:
                kind=read(1)[0]
                if kind==0:break
                key=string();payload(kind,key,depth+1)
        elif tag in (11,12):read(struct.unpack('>i',read(4))[0]*(4 if tag==11 else 8))
        else:raise ValueError('Unknown NBT tag '+str(tag))
    if read(1)!=b'\x0a':raise ValueError('NBT root is not a compound')
    string();payload(10)
    return found.get('status','missing_status')

def region(path,rx,rz,bounds):
    result={}
    if not path.exists():return result
    raw=path.read_bytes()
    if len(raw)<8192:raise ValueError('Truncated region header: '+str(path))
    for i in range(1024):
        x,z=rx*32+i%32,rz*32+i//32
        if not(bounds[0]<=x<=bounds[2] and bounds[1]<=z<=bounds[3]):continue
        entry=int.from_bytes(raw[i*4:i*4+4],'big');offset,count=entry>>8,entry&255
        if not entry:continue
        try:
            if offset<2 or count==0 or (offset+count)*4096>len(raw):raise ValueError('Invalid sector allocation')
            start=offset*4096;length=int.from_bytes(raw[start:start+4],'big')
            if length<1 or length>count*4096-4:raise ValueError('Invalid chunk length')
            compression=raw[start+4];data=raw[start+5:start+4+length]
            if compression&128:
                data=path.parent.joinpath(f'c.{x}.{z}.mcc').read_bytes();compression&=127
            if compression==1:
                with gzip.GzipFile(fileobj=io.BytesIO(data)) as f:decoded=f.read(MAX_CHUNK_BYTES+1)
            elif compression==2:
                decoder=zlib.decompressobj();decoded=decoder.decompress(data,MAX_CHUNK_BYTES+1)
                if not decoder.eof:raise ValueError('Oversized or truncated zlib chunk')
            elif compression==3:decoded=data
            else:raise ValueError(f'Unsupported compression {compression}; not counted as complete')
            if len(decoded)>MAX_CHUNK_BYTES:raise ValueError('Chunk decompression limit')
            result[x,z]=nbt_status(decoded)
        except (ValueError,OSError,zlib.error,EOFError) as ex:result[x,z]='corrupt: '+str(ex)
    return result

def audit(world,bounds,reference=None,max_findings=10000):
    findings=[];counts={};refcounts={}
    for rz in range(bounds[1]//32,bounds[3]//32+1):
        for rx in range(bounds[0]//32,bounds[2]//32+1):
            filename=f'r.{rx}.{rz}.mca'
            try:actual=region(world/'region'/filename,rx,rz,bounds)
            except ValueError as ex:actual={};findings.append(dict(region=filename,error=str(ex)))
            ref=region(reference/'region'/filename,rx,rz,bounds) if reference else {}
            for z in range(max(bounds[1],rz*32),min(bounds[3],rz*32+31)+1):
                for x in range(max(bounds[0],rx*32),min(bounds[2],rx*32+31)+1):
                    status=actual.get((x,z),'absent');counts[status]=counts.get(status,0)+1
                    origin=ref.get((x,z),'absent');refcounts[origin]=refcounts.get(origin,0)+1
                    issue=None
                    if status not in ('full','minecraft:full'):issue='missing_or_incomplete'
                    elif reference and origin not in ('full','minecraft:full'):issue='outside_reference_coverage'
                    if issue and len(findings)<max_findings:findings.append(dict(chunk_x=x,chunk_z=z,block_x=x*16,block_z=z*16,status=status,reference_status=origin if reference else None,issue=issue))
    return dict(chunk_bounds=bounds,status_counts=counts,reference_status_counts=refcounts if reference else None,findings=findings,findings_limit=max_findings,note='Coverage only. FULL status cannot prove Earth terrain or rule out player construction. No files modified.')

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--world',required=True,type=Path);p.add_argument('--reference',type=Path)
    p.add_argument('--chunk-bounds',required=True,nargs=4,type=int,metavar=('MIN_X','MIN_Z','MAX_X','MAX_Z'));p.add_argument('--report',required=True,type=Path);p.add_argument('--max-findings',type=int,default=10000);a=p.parse_args()
    if a.chunk_bounds[0]>a.chunk_bounds[2] or a.chunk_bounds[1]>a.chunk_bounds[3]:p.error('Inverted bounds')
    if not (a.world/'region').is_dir():p.error('World has no region directory')
    if a.reference and not (a.reference/'region').is_dir():p.error('Reference has no region directory')
    # Keep deliverables outside both input worlds.
    for world in [a.world,a.reference]:
        if world and a.report.resolve().is_relative_to(world.resolve()):p.error('Report must be outside input worlds')
    report=audit(a.world,a.chunk_bounds,a.reference,a.max_findings)
    a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print(json.dumps(report['status_counts']))
if __name__=='__main__':main()
