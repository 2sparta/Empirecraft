#!/usr/bin/env python3
"""Reproduce the Forge 1.20.1 upgrade from SHA-512 pinned official Modrinth files."""
import argparse,os,hashlib,importlib.util,json,re,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parent;REPO=ROOT.parent
sys.path.insert(0,str(REPO/'native-content'))
import build as native
from policy import blocked,alias

def write(p,v):p.write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n')
def main():
 p=argparse.ArgumentParser();p.add_argument('--inputs',type=Path,required=True);args=p.parse_args()
 sources=json.loads((ROOT/'sources.json').read_text());policy=json.loads((REPO/'native-content/audit/policy.json').read_text())
 aliases=dict(policy['aliases']);removed=set(policy['removed'])
 for source in sources:
  raw=(args.inputs/source['file']).read_bytes();assert hashlib.sha512(raw).hexdigest()==source['sha512'],source['file']
  data=native.entries(raw);ids={n.split('/')[1]+':'+n.split('/')[-1][:-5] for n in data if re.fullmatch(r'assets/[^/]+/(models/item|blockstates)/[^/]+\.json',n)}
  for identity in sorted(i for i in ids if blocked(i)):
   removed.add(identity);aliases.setdefault(identity,alias(identity,ids,set()))
 native.BUILD.mkdir(exist_ok=True)
 subprocess.run([str(Path(os.environ.get('JAVA_HOME','/usr'))/'bin/java'),'com.sun.tools.javac.Main',*native.EXPORTS,'-proc:none','-d',str(native.BUILD),str(REPO/'native-content/Patch.java')],check=True)
 subprocess.run([str(Path(os.environ.get('JAVA_HOME','/usr'))/'bin/java'),'com.sun.tools.javac.Main','--release','17','-proc:none','-d',str(native.BUILD/'shared'),str(REPO/'native-content/NativeAliases.java'),str(REPO/'native-content/legacy/PolicyHooks.java')],check=True)
 audit=json.loads((REPO/'native-content/audit/build.json').read_text());pins=json.loads((REPO/'native-content/inputs.json').read_text())
 for collection in [audit,pins]:
  for key in list(collection):
   if Path(key).name.startswith('starcatcher-2.'):del collection[key]
 for source in sources:
  raw=(args.inputs/source['file']).read_bytes();report={'deleted':[],'rewritten':[],'boundaries':0}
  output=native.patch_jar(raw,aliases,removed,report,True)
  if source['file'].startswith('starcatcher-'):
   entries=native.entries(output);entries['META-INF/LICENSE-starcatcher.txt']=(ROOT/'UPSTREAM-LICENSE.txt').read_bytes();output=native.archive(entries)
  for side in ['mods','server/mods']:
   folder=REPO/side
   for old in folder.glob('starcatcher-2.*'):old.unlink()
   path=folder/source['file'];path.write_bytes(output);path.with_suffix('.jar.sha256').write_text(hashlib.sha256(output).hexdigest()+'  '+path.name+'\n')
   relative=path.relative_to(REPO).as_posix();pins[relative]=hashlib.sha256(raw).hexdigest()
   if output!=raw:audit[relative]={**report,'sha256':hashlib.sha256(output).hexdigest(),'size':len(output)}
  print('STAR_CATCHER_UPGRADE',source['file'],hashlib.sha256(output).hexdigest())
 policy['removed']=sorted(removed);policy['aliases']=dict(sorted(aliases.items()))
 write(REPO/'native-content/audit/policy.json',policy);write(REPO/'native-content/audit/build.json',audit);write(REPO/'native-content/inputs.json',pins)
if __name__=='__main__':main()
