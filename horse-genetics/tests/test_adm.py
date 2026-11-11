#!/usr/bin/env python3
import copy,importlib.util,json,sys,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('portable_adm',ROOT/'update_adm.py');adm=importlib.util.module_from_spec(spec);spec.loader.exec_module(adm)
class ADMUpdateTests(unittest.TestCase):
 def setUp(self):
  self.release=json.loads((ROOT/'release.json').read_text())
  self.old=dict(osName='fixture',cpuInfo='cpu',ramInfo='ram',gpuInfo='gpu',anticheatVersion='1.9.3',lastUpdated=1,
   modIds=[dict(id='unrelated',version='2',sha256='keep',filePresent=True),dict(id='horse_colors',version='13.5',sha256='old',filePresent=True),dict(id='empirecraft_armor',version='1.0.1',sha256='old',filePresent=True)],resourcePackIds=[dict(filename='my-pack.zip',sha256='keep')])
  self.old['integrityHash']=adm.integrity(self.old)
 def test_preserves_every_unrelated_field_and_resource_pack(self):
  before=copy.deepcopy(self.old);out=adm.prepare(self.old,self.release)
  self.assertEqual(before,self.old);self.assertEqual(self.old['modIds'][0],out['modIds'][0]);self.assertEqual(self.old['resourcePackIds'],out['resourcePackIds'])
  self.assertFalse(any(e['id']=='horse_colors' for e in out['modIds']))
  for wanted in self.release['mods']:
   entry=next(e for e in out['modIds'] if e['id']==wanted['id']);self.assertEqual(wanted['sha256'],entry['sha256']);self.assertTrue(entry['filePresent'])
  self.assertEqual(adm.integrity(out),out['integrityHash'])
 def test_is_idempotent(self):
  out=adm.prepare(self.old,self.release);self.assertEqual(out,adm.prepare(out,self.release))
 def test_rejects_tampering_and_duplicate_target_ids(self):
  self.old['integrityHash']='tampered'
  with self.assertRaises(ValueError):adm.prepare(self.old,self.release)
  self.old['modIds'].append(copy.deepcopy(self.old['modIds'][-1]));self.old['integrityHash']=adm.integrity(self.old)
  with self.assertRaises(ValueError):adm.prepare(self.old,self.release)
if __name__=='__main__':unittest.main(verbosity=2)
