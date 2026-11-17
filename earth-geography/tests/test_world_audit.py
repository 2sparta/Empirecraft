import importlib.util,tempfile,unittest,zlib
from pathlib import Path
SPEC=importlib.util.spec_from_file_location('audit',Path(__file__).resolve().parents[1]/'audit_world.py');audit=importlib.util.module_from_spec(SPEC);SPEC.loader.exec_module(audit)
def text(s):b=s.encode();return len(b).to_bytes(2,'big')+b
def nbt(status):return b'\x0a\x00\x00\x08'+text('Status')+text(status)+b'\x00'
def make_region(path,entries):
    raw=bytearray(8192);sector=2
    for index,status in entries.items():
        data=b'\x02'+zlib.compress(nbt(status));blob=len(data).to_bytes(4,'big')+data
        raw[index*4:index*4+4]=((sector<<8)|1).to_bytes(4,'big');raw.extend(blob+b'\0'*(4096-len(blob)));sector+=1
    path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(raw)
class AuditTest(unittest.TestCase):
    def test_full_hole_incomplete_reference(self):
        with tempfile.TemporaryDirectory() as tmp:
            w=Path(tmp)/'world';r=Path(tmp)/'reference';make_region(w/'region/r.0.0.mca',{0:'minecraft:full',1:'minecraft:noise',3:'minecraft:full'});make_region(r/'region/r.0.0.mca',{0:'minecraft:full',1:'minecraft:full',2:'minecraft:full'})
            before=(w/'region/r.0.0.mca').read_bytes();report=audit.audit(w,[0,0,3,0],r)
            self.assertEqual(report['status_counts'],{'minecraft:full':2,'minecraft:noise':1,'absent':1})
            self.assertEqual([f['issue'] for f in report['findings']],['missing_or_incomplete','missing_or_incomplete','outside_reference_coverage'])
            self.assertEqual(before,(w/'region/r.0.0.mca').read_bytes())
    def test_negative_chunks(self):
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp)/'region/r.-1.-1.mca';make_region(p,{1023:'minecraft:full'})
            self.assertEqual(audit.region(p,-1,-1,[-1,-1,-1,-1]),{(-1,-1):'minecraft:full'})
    def test_truncated(self):
        with self.assertRaises(ValueError):audit.nbt_status(b'\x0a')
    def test_corrupt_sector(self):
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp)/'region/r.0.0.mca';make_region(p,{0:'minecraft:full'});raw=bytearray(p.read_bytes());raw[0:4]=(0xFFFFFF01).to_bytes(4,'big');p.write_bytes(raw)
            self.assertTrue(audit.region(p,0,0,[0,0,0,0])[0,0].startswith('corrupt'))
if __name__=='__main__':unittest.main()
