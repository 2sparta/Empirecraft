#!/usr/bin/env python3
from pathlib import Path
from PIL import Image
import hashlib,json,importlib.util
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('skin_generator',ROOT/'tools/generate_skins.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
folder=ROOT/'src/main/resources/assets/townyrecruits/textures/entity/human';manifest=json.loads((ROOT/'assets/skins-manifest.json').read_text());seen=set()
for i in range(128):
 p=folder/f'skin_{i}.png';raw=p.read_bytes();im=Image.open(p);assert im.mode=='RGBA' and im.size==(64,64)
 assert hashlib.sha256(raw).hexdigest()==manifest[p.name];assert im.tobytes()==m.skin(i).tobytes()
 seen.add(hashlib.sha256(im.tobytes()).hexdigest())
 for rect in [(8,8,16,16),(20,20,28,32),(44,20,48,32),(4,20,8,32),(36,52,40,64),(20,52,24,64)]:assert im.crop(rect).getchannel('A').getextrema()==(255,255)
assert len(seen)==128 and len(list(folder.glob('*.png')))==128
print('PASS 128 unique original RGBA skins; correct 64x64 UV dimensions, opaque body faces and deterministic regeneration')
