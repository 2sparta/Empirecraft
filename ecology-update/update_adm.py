#!/usr/bin/env python3
"""Update this release's client addon pins, preserving every other ADM entry."""
import argparse,copy,hashlib,json,time
from pathlib import Path

def integrity(data):
    text=lambda value:'null' if value is None else str(value)
    value=''.join(text(data[k])+'|' for k in ('osName','cpuInfo','ramInfo','gpuInfo','anticheatVersion','lastUpdated'))
    value+=''.join('{}@{}[{}]{{{}}};'.format(text(e['id']),text(e['version']),text(e['sha256']),1 if e['filePresent'] else 0) for e in data['modIds'])
    value+=''.join('{}[{}];'.format(text(e['filename']),text(e['sha256'])) for e in data['resourcePackIds'])
    return hashlib.sha256(value.encode()).hexdigest()

def prepare(original,release):
    if integrity(original)!=original['integrityHash']:raise ValueError('Existing ADM integrityHash does not match')
    result=copy.deepcopy(original)
    for wanted in release['mods']:
        if 'client' not in wanted['sides']:continue
        matches=[e for e in result['modIds'] if e['id']==wanted['id']]
        if len(matches)>1:raise ValueError('Duplicate mod IDs in ADM: '+wanted['id'])
        entry=dict(id=wanted['id'],version=wanted['version'],sha256=wanted['sha256'],filePresent=True)
        if matches:matches[0].update(entry)
        else:result['modIds'].append(entry)
    if result==original:return result
    result['lastUpdated']=int(time.time()*1000);result['integrityHash']=integrity(result);return result

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('adm',type=Path);parser.add_argument('--apply',action='store_true');parser.add_argument('--release',type=Path,default=Path(__file__).with_name('release.json'));args=parser.parse_args()
    raw=args.adm.read_bytes();original=json.loads(raw.decode('utf-8-sig'));planned=prepare(original,json.loads(args.release.read_text()))
    if original==planned:print('ADM already matches this update.');return
    if not args.apply:raise SystemExit('ADM needs updating; install the bundled JARs, then rerun with --apply.')
    backup=args.adm.with_name(args.adm.name+'.before-ecology-'+hashlib.sha256(raw).hexdigest()[:12])
    if not backup.exists():backup.write_bytes(raw)
    args.adm.write_text(json.dumps(planned,ensure_ascii=False,indent=2)+'\n',encoding='utf-8');print('Updated addon pins; original ADM saved as '+backup.name)

if __name__=='__main__':main()
