#!/usr/bin/env python3
"""Adapt the exact user-supplied addon without changing its Java classes."""
import argparse,hashlib,json,re,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent
VERSION='1.0.3-empirecraft.1'
def main():
    p=argparse.ArgumentParser();p.add_argument('--input',type=Path,required=True);a=p.parse_args()
    original=a.input.read_bytes();digest=hashlib.sha256(original).hexdigest()
    pin=ROOT/'input.json'
    if pin.exists():assert json.loads(pin.read_text())['sha256']==digest,'Changed uploaded addon'
    else:pin.write_text(json.dumps({'file':a.input.name,'sha256':digest,'size':len(original),'source':'User attachment file_000000005e5c821099f44c9aa1ae1b64'},indent=2)+'\n')
    with zipfile.ZipFile(a.input) as z:files={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
    assert not any(b'tfcitems/' in d or b'tfc_items' in d for n,d in files.items() if n.endswith('.class')),'Unexpected Java dependency'
    original_classes={n:hashlib.sha256(d).hexdigest() for n,d in files.items() if n.endswith('.class')}
    removed=[]
    for n in list(files):
        if '/recipes/' in n or re.fullmatch(r'META-INF/[^/]+\.(SF|RSA|DSA)',n,re.I):removed.append(n);del files[n]
    text=files['META-INF/mods.toml'].decode().replace('\r\n','\n')
    text=text.replace('version="1.0.3"','version="'+VERSION+'"')
    text=re.sub(r'\[\[dependencies\.tfc_muskets\]\]\s+modId="tfc_items".*?(?=\[\[dependencies\.|\Z)','',text,flags=re.S)
    files['META-INF/mods.toml']=text.encode()
    ua={'blunderbuss_barrel':'Ствол мушкетона','musket_barrel':'Ствол мушкета','wooden_stock':'Дерев’яна ложа',
        'scrap':'Заготовка кулі','bullet':'Куля','gunpowder_pinch':'Мірка пороху'}
    files['assets/tfc_muskets/lang/uk_ua.json']=(json.dumps({'item.tfc_muskets.'+k:v for k,v in ua.items()},ensure_ascii=False,indent=2)+'\n').encode()
    report={'version':VERSION,'upstream_sha256':digest,'removed_recipes':removed,'java_classes':original_classes,
        'recipes_owner':'empirecraft_tfc 1.0.1','tfc_items_dependency':'removed: all recipes replaced with installed pack components'}
    files['META-INF/empirecraft-tfc-muskets.json']=(json.dumps(report,indent=2)+'\n').encode()
    target=ROOT/'dist'/('tfc_muskets-1.20.1-forge-'+VERSION+'.jar');target.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as z:
        for n,d in sorted(files.items()):
            i=zipfile.ZipInfo(n,(2026,11,12,0,0,0));i.compress_type=zipfile.ZIP_DEFLATED;i.external_attr=0o100644<<16;z.writestr(i,d)
    report['sha256']=hashlib.sha256(target.read_bytes()).hexdigest()
    (ROOT/'audit.json').write_text(json.dumps(report,indent=2)+'\n')
    target.with_suffix('.jar.sha256').write_text(report['sha256']+'  '+target.name+'\n')
    print('TFC_MUSKETS_BUILD_PASS classes='+str(len(original_classes))+' sha256='+report['sha256'])
if __name__=='__main__':main()
