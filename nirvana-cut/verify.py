#!/usr/bin/env python3
import hashlib,json,zipfile
from build import REPO,NAME,SOURCE_SHA,ROOT
from patch import MARKER,REMOVED_CLASSES,REMOVED_RESOURCES,VERSION,patch_entries

def main():
 raw=(REPO/'mods'/NAME).read_bytes();assert raw==(REPO/'server/mods'/NAME).read_bytes()
 with zipfile.ZipFile(ROOT/'build/original-native.jar') as z:original={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
 with zipfile.ZipFile(REPO/'mods'/NAME) as z:
  assert z.testzip() is None
  entries={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
  assert json.loads(entries[MARKER])['version']==VERSION
  for name in REMOVED_CLASSES+REMOVED_RESOURCES:assert name not in entries,name
  assert 'CreeperMixin' not in json.loads(entries['nirvana.mixins.json'])['mixins']
  assert json.loads(entries['assets/nirvana/mob_head_effects/reefer.json'])['entity_types']==[]
  for n,v in entries.items():
   if n.endswith('.jar'):assert v==original[n],('Embedded dependency changed',n)
  for name in ['galena/nirvana/world/THCCloud.class','galena/nirvana/world/entity/PrimedThc.class','galena/nirvana/world/entity/MinecartThc.class','galena/nirvana/index/NirvanaBlocks.class','galena/nirvana/world/block/renderer/ReeferHeadRenderer.class']:
   assert entries[name]==original[name],('Unrelated implementation changed',name)
  assert patch_entries(entries)==entries,'Native removal is not idempotent'
 print('PASS physically removed entity/renderer/interface/mixin/egg; embedded libraries, blocks, head, clouds and other entities unchanged')
if __name__=='__main__':main()
