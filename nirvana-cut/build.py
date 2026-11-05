#!/usr/bin/env python3
import argparse,hashlib,json,zipfile
from pathlib import Path
from patch import ROOT,BUILD,archive,patch_entries

REPO=ROOT.parent
NAME='nirvana-forge-1.2.5 (1).jar'
SOURCE_SHA='2552e481c6016945ce1487e156c567af9f3c2ce5a2304f890b54cd05eab770f0'

def main():
 p=argparse.ArgumentParser();p.add_argument('--source',type=Path);a=p.parse_args();BUILD.mkdir(exist_ok=True)
 source=a.source or BUILD/'original-native.jar'
 if not source.exists():source.write_bytes((REPO/'mods'/NAME).read_bytes())
 raw=source.read_bytes();assert hashlib.sha256(raw).hexdigest()==SOURCE_SHA,'Expected the exact installed Nirvana 1.2.5 with its existing native Registrate fixes'
 with zipfile.ZipFile(source) as z:original={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
 data=patch_entries(original);result=archive(data)
 for folder in ['mods','server/mods']:
  path=REPO/folder/NAME;path.write_bytes(result);path.with_suffix('.jar.sha256').write_text(hashlib.sha256(result).hexdigest()+'  '+NAME+'\n')
 report={'source_sha256':SOURCE_SHA,'patched_sha256':hashlib.sha256(result).hexdigest(),'changed_entries':[n for n in data if original.get(n)!=data[n]],'deleted_entries':sorted(set(original)-set(data)),'unchanged_entries':sum(original.get(n)==v for n,v in data.items()),'runtime_tested':False}
 (ROOT/'audit').mkdir(exist_ok=True);(ROOT/'audit/build.json').write_text(json.dumps(report,indent=2)+'\n')
 print('PASS patched native Nirvana on both sides:',report['patched_sha256'])
if __name__=='__main__':main()
